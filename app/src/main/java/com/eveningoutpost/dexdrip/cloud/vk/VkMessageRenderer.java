package com.eveningoutpost.dexdrip.cloud.vk;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;

public class VkMessageRenderer {

    private VkMessageRenderer() {
    }

    public static String eventId(VkReading reading) {
        if (reading == null) {
            return "unknown:0:0";
        }
        if (reading.test) {
            return "test-" + reading.timeMillis;
        }
        String sensor = VkOutboundDestination.isBlank(reading.sensor) ? "unknown" : reading.sensor;
        return sensor + ":" + reading.timeMillis + ":" + Math.round(reading.mgdl);
    }

    public static int randomId(String eventId, String recipient, long timeMillis, double mgdl) {
        int hash = Objects.hash(eventId, recipient, timeMillis, (int) Math.round(mgdl));
        if (hash == Integer.MIN_VALUE) {
            return 0;
        }
        return Math.abs(hash);
    }

    public static String renderMessage(String template, VkReading reading) {
        if (template == null) {
            return "";
        }
        if (reading == null) {
            return template;
        }
        Map<String, String> tokens = new LinkedHashMap<>();
        tokens.put("auto_value", nvl(reading.displayText));
        tokens.put("auto_mgdl", formatMgdl(reading.mgdl));
        tokens.put("auto_mmol", formatMmol(reading.mmol));
        tokens.put("auto", nvl(reading.displayText) + (VkOutboundDestination.isBlank(reading.unit) ? "" : " " + reading.unit));
        tokens.put("raw_mgdl", formatMgdl(reading.rawMgdl));
        tokens.put("raw_mmol", formatMmol(reading.rawMmol));
        tokens.put("rate_mgdl", formatRate(reading.rateMgdl));
        tokens.put("rate_mmol", formatRate(reading.rateMmol));
        tokens.put("journal_events", nvl(reading.journalEvents));
        tokens.put("journal_iob", nvl(reading.journalIob));
        tokens.put("journal_cob", nvl(reading.journalCob));
        tokens.put("status_emoji", nvl(reading.statusEmoji));
        tokens.put("event_id", nvl(reading.eventId));
        tokens.put("trend_arrow", nvl(reading.trendArrow));
        tokens.put("trend_name", nvl(reading.trendName));
        tokens.put("timestamp", Long.toString(reading.timeMillis));
        tokens.put("recipient", nvl(reading.recipient));
        tokens.put("journal", nvl(reading.journal));
        tokens.put("status", nvl(reading.status));
        tokens.put("sensor", nvl(reading.sensor));
        tokens.put("alarm", nvl(reading.alarm));
        tokens.put("value", nvl(reading.displayText));
        tokens.put("unit", nvl(reading.unit));
        tokens.put("mgdl", formatMgdl(reading.mgdl));
        tokens.put("mmol", formatMmol(reading.mmol));
        tokens.put("raw", formatRaw(reading));
        tokens.put("time", formatTime(reading.timeMillis));
        tokens.put("test", reading.test ? "1" : "");
        tokens.put("iob", nvl(reading.iob));
        tokens.put("cob", nvl(reading.cob));

        String rendered = template;
        for (Map.Entry<String, String> entry : tokens.entrySet()) {
            rendered = rendered.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return rendered;
    }

    private static String formatRaw(VkReading reading) {
        if (reading.unit != null && reading.unit.toLowerCase(Locale.US).contains("mmol")) {
            return formatMmol(reading.rawMmol);
        }
        return formatMgdl(reading.rawMgdl);
    }

    private static String formatTime(long timeMillis) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.US);
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(new Date(timeMillis));
    }

    private static String formatMgdl(double mgdl) {
        return Long.toString(Math.round(mgdl));
    }

    private static String formatMmol(double mmol) {
        DecimalFormat df = decimalFormat(1);
        return df.format(mmol);
    }

    private static String formatRate(double rate) {
        DecimalFormat df = decimalFormat(2);
        return df.format(rate);
    }

    private static DecimalFormat decimalFormat(int digits) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setDecimalSeparator('.');
        DecimalFormat df = new DecimalFormat();
        df.setDecimalFormatSymbols(symbols);
        df.setGroupingUsed(false);
        df.setMinimumFractionDigits(digits);
        df.setMaximumFractionDigits(digits);
        return df;
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }
}
