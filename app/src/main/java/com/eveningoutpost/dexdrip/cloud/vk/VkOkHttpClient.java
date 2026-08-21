package com.eveningoutpost.dexdrip.cloud.vk;

import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class VkOkHttpClient implements VkHttpClient {

    private static final MediaType FORM =
            MediaType.parse("application/x-www-form-urlencoded; charset=UTF-8");

    private final OkHttpClient client;

    public VkOkHttpClient() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public VkHttpResponse execute(String url, String formBody) throws Exception {
        RequestBody body = RequestBody.create(FORM, formBody == null ? "" : formBody);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();
        Response response = client.newCall(request).execute();
        try {
            String respBody = response.body() != null ? response.body().string() : "";
            return new VkHttpResponse(response.code(), respBody);
        } finally {
            response.close();
        }
    }
}
