package com.eveningoutpost.dexdrip.glucosemeter;

import com.eveningoutpost.dexdrip.models.UserError;
import com.eveningoutpost.dexdrip.utilitymodels.Constants;

import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper class for the ELTA "Satellite" (Сателлит+) Bluetooth glucose meter.
 * <p>
 * The meter exposes a Nordic UART Service (NUS) and speaks a plain-ASCII
 * request/response protocol: xDrip writes a text command to the RX
 * characteristic and the meter replies with exactly one text notification
 * on the TX characteristic before the next command may be sent.
 * <p>
 * Protocol reverse engineered from the official "Сателлит+Online" Android app
 * (package com.elta.android, decompiled with jadx).
 * <p>
 * Service UUID: 6e400001-b5a3-f393-e0a9-e50e24dcca9e
 * RX Characteristic (write): 6e400002-b5a3-f393-e0a9-e50e24dcca9e
 * TX Characteristic (notify): 6e400003-b5a3-f393-e0a9-e50e24dcca9e
 * <p>
 * Every connection starts with a 3-digit PIN (printed on the meter) sent as
 * "pin.<code>"; the meter must reply "pin.ok" before any other command is
 * accepted.
 * <p>
 * Readings are requested by ring-buffer slot index (0 = newest) with
 * "rd.<3-digit index>" and come back as "rd<12-digit yyMMddHHmmss><3-digit
 * temperature/meal flag><3-digit glucose x10>", or "rd000000000000000000"
 * for an empty slot. The meter's clock (and therefore the embedded
 * timestamp) is treated as UTC by the official app, which always sets it via
 * "settime.<UTC yyMMddHHmmss>" straight after PIN acceptance - we do the same
 * so the timestamps we read back are unambiguous.
 * <p>
 * The meter reports whole-blood (capillary) glucose in mmol/L x10. xDrip
 * works in plasma-equivalent values, so we apply the same 1.12 capillary to
 * plasma coefficient the official app uses before converting to mg/dL.
 */
public class SatelliteHelper {

    private static final String TAG = "GlucoseSatellite";
    private static final boolean d = false;

    public static final UUID NUS_SERVICE = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    public static final UUID NUS_RX = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e"); // write
    public static final UUID NUS_TX = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e"); // notify

    public static final int MAX_BACKFILL_RECORDS = 10;

    private static final String PIN_OK_RESPONSE = "pin.ok";
    private static final String EMPTY_SLOT_MARKER = "rd000000000000000000";

    // Whole-blood (capillary) -> plasma-equivalent coefficient, matches the official app
    private static final double CAPILLARY_TO_PLASMA_COEFFICIENT = 1.12;

    private static final double MIN_PLAUSIBLE_MGDL = 20;
    private static final double MAX_PLAUSIBLE_MGDL = 700;

    private static final Pattern RD_EVENT_PATTERN = Pattern.compile("^rd(\\d{12})(\\d{3})(\\d{3})$", Pattern.CASE_INSENSITIVE);

    public static String decode(byte[] value) {
        if (value == null) return "";
        return new String(value, StandardCharsets.US_ASCII).trim();
    }

    public static byte[] getPinCMD(String pin) {
        return ("pin." + pin).getBytes(StandardCharsets.US_ASCII);
    }

    public static byte[] getSetTimeCMD(long epochMillisUtc) {
        return ("settime." + formatUtcDateTime(epochMillisUtc)).getBytes(StandardCharsets.US_ASCII);
    }

    public static byte[] getRecordCMD(int index) {
        return String.format(Locale.US, "rd.%03d", index).getBytes(StandardCharsets.US_ASCII);
    }

    public static boolean isPinOk(String response) {
        return PIN_OK_RESPONSE.equalsIgnoreCase(response);
    }

    public static boolean isEmptySlot(String response) {
        return response != null && response.contains(EMPTY_SLOT_MARKER);
    }

    /**
     * Parses an "rd.<idx>" response into a glucose reading, converting the
     * meter's native capillary mmol/L x10 value to plasma-equivalent mg/dL.
     *
     * @return the reading, or null if the response isn't a recognisable rd event
     */
    public synchronized static GlucoseReadingRx parseRecord(String response) {
        if (response == null) return null;
        final Matcher matcher = RD_EVENT_PATTERN.matcher(response);
        if (!matcher.matches()) {
            if (d) UserError.Log.d(TAG, "Not a Satellite rd event: " + response);
            return null;
        }

        final int glucoseRaw = Integer.parseInt(matcher.group(3));
        final double capillaryMmol = glucoseRaw / 10.0;
        final double plasmaMmol = capillaryMmol * CAPILLARY_TO_PLASMA_COEFFICIENT;
        final double mgdl = plasmaMmol * Constants.MMOLL_TO_MGDL;

        if (mgdl < MIN_PLAUSIBLE_MGDL || mgdl > MAX_PLAUSIBLE_MGDL) {
            UserError.Log.e(TAG, "Satellite: implausible glucose value ignored: " + mgdl + " mg/dl from raw " + glucoseRaw);
            return null;
        }

        final GlucoseReadingRx gtb = new GlucoseReadingRx();
        gtb.mgdl = mgdl;
        gtb.time = parseUtcDateToken(matcher.group(1));
        gtb.offset = 0;
        gtb.device = "Satellite";
        // the meter has no exposed monotonic record counter over this protocol,
        // so synthesize one from the reading's own timestamp (5 second resolution)
        gtb.sequence = (int) (gtb.time / 5000);
        return gtb;
    }

    private static String formatUtcDateTime(long epochMillis) {
        final Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.setTimeInMillis(epochMillis);
        return String.format(Locale.US, "%02d%02d%02d%02d%02d%02d",
                cal.get(Calendar.YEAR) % 100,
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                cal.get(Calendar.SECOND));
    }

    // token format: yyMMddHHmmss (12 digits), always UTC on this meter
    private static long parseUtcDateToken(String token) {
        final int year = 2000 + Integer.parseInt(token.substring(0, 2));
        final int month = Integer.parseInt(token.substring(2, 4));
        final int day = Integer.parseInt(token.substring(4, 6));
        final int hour = Integer.parseInt(token.substring(6, 8));
        final int minute = Integer.parseInt(token.substring(8, 10));
        final int second = Integer.parseInt(token.substring(10, 12));
        final Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.clear();
        cal.set(year, month - 1, day, hour, minute, second);
        return cal.getTimeInMillis();
    }
}
