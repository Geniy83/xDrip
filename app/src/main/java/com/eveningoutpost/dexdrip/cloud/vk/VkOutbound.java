package com.eveningoutpost.dexdrip.cloud.vk;

import com.eveningoutpost.dexdrip.R;
import com.eveningoutpost.dexdrip.models.BgReading;
import com.eveningoutpost.dexdrip.models.JoH;
import com.eveningoutpost.dexdrip.models.Sensor;
import com.eveningoutpost.dexdrip.models.Treatments;
import com.eveningoutpost.dexdrip.models.UserError;
import com.eveningoutpost.dexdrip.utilitymodels.Constants;
import com.eveningoutpost.dexdrip.utilitymodels.Inevitable;
import com.eveningoutpost.dexdrip.utilitymodels.Unitized;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static com.eveningoutpost.dexdrip.xdrip.gs;

/**
 * VK Community Messages outbound: enqueue on new glucose, test send from settings.
 */
public class VkOutbound {

    private static final String TAG = "VkOutbound";
    private static final AtomicInteger pending = new AtomicInteger();
    private static volatile VkMessagesSender sender = new VkMessagesSender(new VkOkHttpClient());

    private VkOutbound() {
    }

    public static boolean enabled() {
        return VkOutboundPrefs.enabled();
    }

    public static void enqueueGlucose(BgReading bgReading) {
        if (!enabled()) {
            return;
        }
        enqueue(bgReading, false);
    }

    public static void sendTest() {
        enqueue(BgReading.last(), true);
    }

    static void setSenderForTests(VkMessagesSender testSender) {
        sender = testSender;
    }

    public static String statusSummary() {
        VkOutboundDestination dest = VkOutboundPrefs.load();
        if (!dest.isReady()) {
            return gs(R.string.vk_outbound_not_ready);
        }
        String never = gs(R.string.vk_outbound_never);
        String success = dest.lastSuccessAtMs > 0 ? JoH.dateTimeText(dest.lastSuccessAtMs) : never;
        String attempt = dest.lastAttemptAtMs > 0 ? JoH.dateTimeText(dest.lastAttemptAtMs) : never;
        String extra = dest.lastResponseCode != 0 ? " (" + dest.lastResponseCode + ")" : "";
        if (!VkOutboundDestination.isBlank(dest.lastError)) {
            extra += " / " + dest.lastError;
        }
        return gs(R.string.vk_outbound_status_line, success, attempt) + extra;
    }

    private static void enqueue(BgReading bgReading, boolean testSend) {
        try {
            VkOutboundDestination dest = VkOutboundPrefs.load();
            VkReading reading = buildReading(bgReading, dest, testSend);
            reading.eventId = VkMessageRenderer.eventId(reading);

            if (!VkOutboundGating.shouldEnqueue(dest, reading.eventId, reading.mgdl, JoH.tsl(), testSend)) {
                if (testSend) {
                    JoH.static_toast_long(gs(R.string.vk_outbound_toast_not_ready));
                }
                return;
            }
            if (!testSend && !JoH.isAnyNetworkConnected()) {
                UserError.Log.d(TAG, "Skipping VK send: offline");
                return;
            }
            if (!VkOutboundGating.canAcceptPending(pending.get(), VkOutboundGating.MAX_PENDING)) {
                UserError.Log.e(TAG, "Skipping VK send: pending cap reached");
                if (testSend) {
                    JoH.static_toast_long(gs(R.string.vk_outbound_toast_pending_cap));
                }
                return;
            }

            dest.lastQueuedEventId = reading.eventId;
            dest.lastQueuedAtMs = JoH.tsl();
            VkOutboundPrefs.saveRuntimeStatus(dest);

            pending.incrementAndGet();
            Inevitable.stackableTask("vk-outbound-send", 200, () -> {
                try {
                    deliver(dest, reading, testSend, 0);
                } finally {
                    pending.decrementAndGet();
                }
            });
        } catch (Exception e) {
            UserError.Log.e(TAG, "VK enqueue failed: " + VkMessagesSender.redact(e.getMessage(),
                    VkOutboundPrefs.load().token));
            if (testSend) {
                JoH.static_toast_long(gs(R.string.vk_outbound_toast_failed));
            }
        }
    }

    private static void deliver(VkOutboundDestination dest, VkReading reading, boolean testSend, int attempt) {
        List<String> recipients = dest.recipients();
        VkSendResult last = null;
        boolean allOk = true;
        for (String recipient : recipients) {
            reading.recipient = recipient;
            String message = VkMessageRenderer.renderMessage(dest.resolvedTemplate(), reading);
            last = sender.send(dest, recipient, message, reading.eventId, reading.timeMillis, reading.mgdl);
            dest.lastAttemptAtMs = JoH.tsl();
            dest.lastResponseCode = last.httpCode;
            if (last.success) {
                dest.lastSuccessAtMs = dest.lastAttemptAtMs;
                dest.lastError = "";
                UserError.Log.d(TAG, "VK send ok peer=" + recipient + " http=" + last.httpCode);
            } else {
                allOk = false;
                dest.lastError = last.errorMessage;
                UserError.Log.e(TAG, "VK send failed peer=" + recipient + " http=" + last.httpCode
                        + " vk=" + last.vkErrorCode + " " + last.errorMessage);
            }
            VkOutboundPrefs.saveRuntimeStatus(dest);
        }

        if (!allOk && last != null && last.retryable && attempt == 0) {
            Inevitable.task("vk-outbound-retry", 5000, () -> deliver(dest, reading, testSend, 1));
            return;
        }
        if (testSend) {
            if (allOk) {
                JoH.static_toast_long(gs(R.string.vk_outbound_toast_sent));
            } else {
                JoH.static_toast_long(last != null && !last.errorMessage.isEmpty()
                        ? last.errorMessage
                        : gs(R.string.vk_outbound_toast_failed));
            }
        }
    }

    static VkReading buildReading(BgReading bgReading, VkOutboundDestination dest, boolean test) {
        VkReading reading = new VkReading();
        reading.test = test;
        double mgdl;
        long timeMillis;
        String trendArrow;
        double slopePerMs;
        double rawMgdl;
        if (bgReading != null) {
            mgdl = bgReading.getDg_mgdl();
            timeMillis = bgReading.timestamp;
            trendArrow = bgReading.hide_slope ? "" : bgReading.slopeArrow();
            slopePerMs = bgReading.getDg_slope();
            rawMgdl = bgReading.raw_data > 0 ? bgReading.raw_data : mgdl;
            reading.stale = JoH.msSince(bgReading.timestamp) > 11 * Constants.MINUTE_IN_MS;
        } else {
            mgdl = 100;
            timeMillis = JoH.tsl();
            trendArrow = "→";
            slopePerMs = 0;
            rawMgdl = 100;
            reading.stale = false;
        }
        reading.mgdl = mgdl;
        reading.mmol = mgdl * Constants.MGDL_TO_MMOLL;
        reading.rawMgdl = rawMgdl;
        reading.rawMmol = rawMgdl * Constants.MGDL_TO_MMOLL;
        reading.rateMgdl = slopePerMs * Constants.MINUTE_IN_MS;
        reading.rateMmol = reading.rateMgdl * Constants.MGDL_TO_MMOLL;
        reading.timeMillis = timeMillis;
        reading.trendArrow = trendArrow == null ? "" : trendArrow;
        reading.sensor = currentSensorId();
        boolean useMgdl = Unitized.usingMgDl();
        reading.unit = useMgdl ? "mg/dL" : "mmol/L";
        reading.displayText = formatDisplay(mgdl, useMgdl);
        applyStatus(reading, dest.triggerLowMgdl, dest.triggerHighMgdl);
        reading.iob = formatOptionalDouble(currentIob());
        reading.cob = "0";
        reading.journalIob = reading.iob;
        reading.journalCob = reading.cob;
        return reading;
    }

    static void applyStatus(VkReading reading, double lowMgdl, double highMgdl) {
        if (reading.stale) {
            reading.status = "STALE";
            reading.statusEmoji = "⚠️";
            reading.alarm = "";
            return;
        }
        if (reading.mgdl <= lowMgdl) {
            reading.status = "LOW";
            reading.statusEmoji = "🔻";
            reading.alarm = "LOW";
        } else if (reading.mgdl >= highMgdl) {
            reading.status = "HIGH";
            reading.statusEmoji = "🔺";
            reading.alarm = "HIGH";
        } else {
            reading.status = "OK";
            reading.statusEmoji = "🟢";
            reading.alarm = "";
        }
    }

    private static String formatDisplay(double mgdl, boolean useMgdl) {
        if (useMgdl) {
            return Long.toString(Math.round(mgdl));
        }
        return String.format(Locale.US, "%.1f", mgdl * Constants.MGDL_TO_MMOLL);
    }

    private static String currentSensorId() {
        try {
            Sensor sensor = Sensor.currentSensor();
            if (sensor != null && sensor.uuid != null) {
                return sensor.uuid;
            }
        } catch (Exception e) {
            // no sensor table in some test contexts
        }
        return "unknown";
    }

    private static Double currentIob() {
        try {
            return Treatments.getCurrentIoB();
        } catch (Exception e) {
            return null;
        }
    }

    private static String formatOptionalDouble(Double value) {
        if (value == null) {
            return "0";
        }
        return String.format(Locale.US, "%.1f", value);
    }
}
