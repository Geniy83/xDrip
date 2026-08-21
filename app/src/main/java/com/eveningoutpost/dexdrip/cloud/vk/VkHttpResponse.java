package com.eveningoutpost.dexdrip.cloud.vk;

public class VkHttpResponse {
    public final int code;
    public final String body;

    public VkHttpResponse(int code, String body) {
        this.code = code;
        this.body = body == null ? "" : body;
    }
}
