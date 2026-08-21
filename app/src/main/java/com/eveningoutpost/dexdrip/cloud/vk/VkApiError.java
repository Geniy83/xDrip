package com.eveningoutpost.dexdrip.cloud.vk;

public class VkApiError {
    public final int errorCode;
    public final String errorMsg;

    public VkApiError(int errorCode, String errorMsg) {
        this.errorCode = errorCode;
        this.errorMsg = errorMsg == null ? "" : errorMsg;
    }
}
