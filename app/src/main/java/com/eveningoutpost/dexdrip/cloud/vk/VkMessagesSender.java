package com.eveningoutpost.dexdrip.cloud.vk;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Map;

public class VkMessagesSender {

    private final VkHttpClient httpClient;

    public VkMessagesSender(VkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public VkSendResult send(VkOutboundDestination dest, String recipient, String message,
                             String eventId, long timeMillis, double mgdl) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("access_token", dest.token == null ? "" : dest.token);
        fields.put("v", dest.resolvedApiVersion());
        fields.put("peer_id", recipient == null ? "" : recipient);
        fields.put("random_id", Integer.toString(VkMessageRenderer.randomId(eventId, recipient, timeMillis, mgdl)));
        fields.put("message", message == null ? "" : message);
        String form = encodeForm(fields);
        try {
            VkHttpResponse response = httpClient.execute(dest.resolvedUrl(), form);
            int httpCode = response.code;
            VkApiError apiError = parseVkError(response.body);
            if (httpCode >= 200 && httpCode < 300 && apiError == null) {
                return VkSendResult.ok(httpCode);
            }
            Integer vkCode = apiError != null ? apiError.errorCode : null;
            String rawMsg = apiError != null && !apiError.errorMsg.isEmpty()
                    ? apiError.errorMsg
                    : ("HTTP " + httpCode);
            boolean retryable = isRetryable(httpCode, vkCode);
            return VkSendResult.fail(retryable, httpCode, vkCode, redact(rawMsg, dest.token));
        } catch (Exception e) {
            return VkSendResult.fail(true, 0, null, redact(e.getMessage(), dest.token));
        }
    }

    public static String encodeForm(Map<String, String> fields) {
        StringBuilder sb = new StringBuilder();
        try {
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                if (sb.length() > 0) {
                    sb.append('&');
                }
                sb.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
                sb.append('=');
                sb.append(URLEncoder.encode(entry.getValue() == null ? "" : entry.getValue(), "UTF-8"));
            }
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
        return sb.toString();
    }

    public static VkApiError parseVkError(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            JsonElement rootElement = new JsonParser().parse(json);
            if (!rootElement.isJsonObject()) {
                return null;
            }
            JsonObject root = rootElement.getAsJsonObject();
            if (!root.has("error") || !root.get("error").isJsonObject()) {
                return null;
            }
            JsonObject error = root.getAsJsonObject("error");
            int code = error.has("error_code") ? error.get("error_code").getAsInt() : 0;
            String msg = error.has("error_msg") ? error.get("error_msg").getAsString() : "";
            return new VkApiError(code, msg);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isRetryable(int httpCode, Integer vkErrorCode) {
        if (httpCode == 429 || httpCode >= 500) {
            return true;
        }
        return vkErrorCode != null && (vkErrorCode == 6 || vkErrorCode == 9 || vkErrorCode == 10);
    }

    public static String redact(String text, String token) {
        if (text == null) {
            return "";
        }
        if (token == null || token.isEmpty()) {
            return text;
        }
        return text.replace(token, "***");
    }
}
