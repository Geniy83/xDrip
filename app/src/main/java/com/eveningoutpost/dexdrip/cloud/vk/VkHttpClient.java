package com.eveningoutpost.dexdrip.cloud.vk;

public interface VkHttpClient {
    VkHttpResponse execute(String url, String formBody) throws Exception;
}
