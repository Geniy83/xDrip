package com.eveningoutpost.dexdrip.cloud.vk;

import android.content.SharedPreferences;
import com.eveningoutpost.dexdrip.utilitymodels.Pref;
import com.eveningoutpost.dexdrip.utilitymodels.Unitized;

/**
 * SharedPreferences adapter for the single VK destination.
 */
public class VkOutboundPrefs {

    public static final String ENABLE = "cloud_storage_vk_enable";
    public static final String NAME = "cloud_storage_vk_name";
    public static final String PRESET = "cloud_storage_vk_preset";
    public static final String URL = "cloud_storage_vk_url";
    public static final String TOKEN = "cloud_storage_vk_token";
    public static final String RECIPIENTS = "cloud_storage_vk_recipients";
    public static final String API_VERSION = "cloud_storage_vk_api_version";
    public static final String TEMPLATE = "cloud_storage_vk_template";
    public static final String MIN_INTERVAL = "cloud_storage_vk_min_interval";
    public static final String TRIGGER = "cloud_storage_vk_trigger";
    public static final String TRIGGER_LOW = "cloud_storage_vk_trigger_low";
    public static final String TRIGGER_HIGH = "cloud_storage_vk_trigger_high";
    public static final String THRESHOLDS_USER_UNITS = "cloud_storage_vk_thresholds_user_units";
    public static final String LAST_QUEUED_EVENT_ID = "cloud_storage_vk_last_queued_event_id";
    public static final String LAST_QUEUED_AT = "cloud_storage_vk_last_queued_at";
    public static final String LAST_ATTEMPT_AT = "cloud_storage_vk_last_attempt_at";
    public static final String LAST_SUCCESS_AT = "cloud_storage_vk_last_success_at";
    public static final String LAST_RESPONSE_CODE = "cloud_storage_vk_last_response_code";
    public static final String LAST_ERROR = "cloud_storage_vk_last_error";

    private VkOutboundPrefs() {
    }

    public static boolean enabled() {
        return Pref.getBooleanDefaultFalse(ENABLE);
    }

    public static VkOutboundDestination load() {
        VkOutboundDestination dest = VkOutboundDestination.defaults();
        dest.enabled = enabled();
        dest.name = Pref.getString(NAME, dest.name);
        dest.preset = Pref.getString(PRESET, dest.preset);
        dest.url = Pref.getString(URL, dest.url);
        dest.token = Pref.getString(TOKEN, dest.token);
        dest.chatId = Pref.getString(RECIPIENTS, dest.chatId);
        dest.apiVersion = Pref.getString(API_VERSION, dest.apiVersion);
        dest.messageTemplate = Pref.getString(TEMPLATE, dest.messageTemplate);
        dest.minIntervalMinutes = Pref.getStringToInt(MIN_INTERVAL, dest.minIntervalMinutes);
        dest.triggerMode = Pref.getString(TRIGGER, dest.triggerMode);
        final boolean usingMgdl = Unitized.usingMgDl();
        migrateThresholdsToUserUnits(usingMgdl);
        dest.triggerLowMgdl = VkGlucoseThresholds.parseToMgdl(
                Pref.getString(TRIGGER_LOW, ""), usingMgdl, VkGlucoseThresholds.DEFAULT_LOW_MGDL);
        dest.triggerHighMgdl = VkGlucoseThresholds.parseToMgdl(
                Pref.getString(TRIGGER_HIGH, ""), usingMgdl, VkGlucoseThresholds.DEFAULT_HIGH_MGDL);
        dest.lastQueuedEventId = Pref.getString(LAST_QUEUED_EVENT_ID, dest.lastQueuedEventId);
        dest.lastQueuedAtMs = Pref.getLong(LAST_QUEUED_AT, 0);
        dest.lastAttemptAtMs = Pref.getLong(LAST_ATTEMPT_AT, 0);
        dest.lastSuccessAtMs = Pref.getLong(LAST_SUCCESS_AT, 0);
        dest.lastResponseCode = Pref.getInt(LAST_RESPONSE_CODE, 0);
        dest.lastError = Pref.getString(LAST_ERROR, dest.lastError);
        return dest;
    }

    public static void migrateThresholdsToUserUnits(boolean usingMgdl) {
        if (Pref.getBooleanDefaultFalse(THRESHOLDS_USER_UNITS)) {
            return;
        }
        String low = Pref.getString(TRIGGER_LOW, "");
        String high = Pref.getString(TRIGGER_HIGH, "");
        if (!VkOutboundDestination.isBlank(low)) {
            Pref.setString(TRIGGER_LOW, VkGlucoseThresholds.migrateToUserUnits(low, usingMgdl));
        }
        if (!VkOutboundDestination.isBlank(high)) {
            Pref.setString(TRIGGER_HIGH, VkGlucoseThresholds.migrateToUserUnits(high, usingMgdl));
        }
        Pref.setBoolean(THRESHOLDS_USER_UNITS, true);
    }

    public static void convertThresholdsForUnitsChange(SharedPreferences preferences, boolean toMgdl) {
        convertOne(preferences, TRIGGER_LOW, toMgdl);
        convertOne(preferences, TRIGGER_HIGH, toMgdl);
        Pref.setBoolean(THRESHOLDS_USER_UNITS, true);
    }

    private static void convertOne(SharedPreferences preferences, String key, boolean toMgdl) {
        String stored = preferences.getString(key, "");
        if (VkOutboundDestination.isBlank(stored)) {
            return;
        }
        preferences.edit().putString(key, VkGlucoseThresholds.convertStoredForUnitsChange(stored, toMgdl)).apply();
    }

    public static void saveRuntimeStatus(VkOutboundDestination dest) {
        Pref.setString(LAST_QUEUED_EVENT_ID, dest.lastQueuedEventId == null ? "" : dest.lastQueuedEventId);
        Pref.setLong(LAST_QUEUED_AT, dest.lastQueuedAtMs);
        Pref.setLong(LAST_ATTEMPT_AT, dest.lastAttemptAtMs);
        Pref.setLong(LAST_SUCCESS_AT, dest.lastSuccessAtMs);
        Pref.setInt(LAST_RESPONSE_CODE, dest.lastResponseCode);
        Pref.setString(LAST_ERROR, dest.lastError == null ? "" : dest.lastError);
    }
}
