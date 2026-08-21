package com.eveningoutpost.dexdrip.cloud.vk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Single VK outbound destination. Persistence is handled by {@link VkOutboundPrefs}.
 */
public class VkOutboundDestination {

    public static final String DEFAULT_VK_URL = "https://api.vk.com/method/messages.send";
    public static final String DEFAULT_VK_API_VERSION = "5.199";
    public static final String DEFAULT_CHAT_TEMPLATE = "{status_emoji} {value} {unit} {trend_arrow} {time}";
    public static final String DEFAULT_GLUCO_WATCH_TEMPLATE =
            "GV:{mmol}|RAW:{raw}|TR:{trend_arrow}|AL:{alarm}|RT:{rate_mmol}|IOB:{iob}|COB:{cob}|TS:{timestamp}";

    public static final String PRESET_VK_MESSAGES = "vk_messages";
    public static final String PRESET_GLUCOWATCH = "glucowatch_vk";

    public static final String TRIGGER_ALWAYS = "always";
    public static final String TRIGGER_AT_OR_BELOW = "at_or_below";
    public static final String TRIGGER_AT_OR_ABOVE = "at_or_above";
    public static final String TRIGGER_OUTSIDE_RANGE = "outside_range";

    public boolean enabled;
    public String name = "";
    public String preset = PRESET_VK_MESSAGES;
    public String url = "";
    public String token = "";
    public String chatId = "";
    public String apiVersion = DEFAULT_VK_API_VERSION;
    public String messageTemplate = DEFAULT_CHAT_TEMPLATE;
    public int minIntervalMinutes = 5;
    public String triggerMode = TRIGGER_ALWAYS;
    public double triggerLowMgdl = 70;
    public double triggerHighMgdl = 180;
    public String lastQueuedEventId = "";
    public long lastQueuedAtMs;
    public long lastAttemptAtMs;
    public long lastSuccessAtMs;
    public int lastResponseCode;
    public String lastError = "";

    public static VkOutboundDestination defaults() {
        return new VkOutboundDestination();
    }

    public static String defaultTemplateFor(String preset) {
        if (PRESET_GLUCOWATCH.equals(preset)) {
            return DEFAULT_GLUCO_WATCH_TEMPLATE;
        }
        return DEFAULT_CHAT_TEMPLATE;
    }

    public boolean isReady() {
        return !isBlank(token) && !recipients().isEmpty() && !hasInvalidRecipient();
    }

    public String resolvedUrl() {
        return isBlank(url) ? DEFAULT_VK_URL : url.trim();
    }

    public String resolvedApiVersion() {
        return isBlank(apiVersion) ? DEFAULT_VK_API_VERSION : apiVersion.trim();
    }

    public String resolvedTemplate() {
        if (!isBlank(messageTemplate)) {
            return messageTemplate;
        }
        return defaultTemplateFor(preset);
    }

    public List<String> recipients() {
        if (chatId == null) {
            return Collections.emptyList();
        }
        List<String> ids = new ArrayList<>();
        String[] parts = chatId.split("[,;\\s]+");
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (isNumericId(part)) {
                ids.add(part);
            }
        }
        return ids;
    }

    public boolean hasInvalidRecipient() {
        if (chatId == null) {
            return false;
        }
        String[] parts = chatId.split("[,;\\s]+");
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!isNumericId(part)) {
                return true;
            }
        }
        return false;
    }

    public boolean shouldSendForGlucose(double mgdl) {
        String mode = triggerMode == null ? TRIGGER_ALWAYS : triggerMode;
        switch (mode) {
            case TRIGGER_AT_OR_BELOW:
                return mgdl <= triggerLowMgdl;
            case TRIGGER_AT_OR_ABOVE:
                return mgdl >= triggerHighMgdl;
            case TRIGGER_OUTSIDE_RANGE:
                return mgdl <= triggerLowMgdl || mgdl >= triggerHighMgdl;
            case TRIGGER_ALWAYS:
            default:
                return true;
        }
    }

    public boolean isDuplicate(String eventId) {
        return eventId != null && eventId.equals(lastQueuedEventId);
    }

    public boolean shouldSkipDueToInterval(long nowMs) {
        if (minIntervalMinutes <= 0 || lastQueuedAtMs <= 0) {
            return false;
        }
        return nowMs < lastQueuedAtMs + minIntervalMinutes * 60_000L;
    }

    public static boolean isNumericId(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        int i = 0;
        if (value.charAt(0) == '-') {
            if (value.length() == 1) {
                return false;
            }
            i = 1;
        }
        for (; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public String formatStatusLine() {
        String success = lastSuccessAtMs > 0 ? String.format(Locale.US, "%tF %<tT", lastSuccessAtMs) : "never";
        String attempt = lastAttemptAtMs > 0 ? String.format(Locale.US, "%tF %<tT", lastAttemptAtMs) : "never";
        String error = isBlank(lastError) ? "" : (" / " + lastError);
        return "Last success: " + success + " / last attempt: " + attempt
                + (lastResponseCode != 0 ? " (" + lastResponseCode + ")" : "")
                + error;
    }
}
