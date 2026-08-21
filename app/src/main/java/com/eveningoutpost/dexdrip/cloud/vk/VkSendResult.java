package com.eveningoutpost.dexdrip.cloud.vk;

public class VkSendResult {
    public final boolean success;
    public final boolean retryable;
    public final int httpCode;
    public final Integer vkErrorCode;
    public final String errorMessage;

    public VkSendResult(boolean success, boolean retryable, int httpCode, Integer vkErrorCode, String errorMessage) {
        this.success = success;
        this.retryable = retryable;
        this.httpCode = httpCode;
        this.vkErrorCode = vkErrorCode;
        this.errorMessage = errorMessage == null ? "" : errorMessage;
    }

    public static VkSendResult ok(int httpCode) {
        return new VkSendResult(true, false, httpCode, null, "");
    }

    public static VkSendResult fail(boolean retryable, int httpCode, Integer vkErrorCode, String errorMessage) {
        return new VkSendResult(false, retryable, httpCode, vkErrorCode, errorMessage);
    }
}
