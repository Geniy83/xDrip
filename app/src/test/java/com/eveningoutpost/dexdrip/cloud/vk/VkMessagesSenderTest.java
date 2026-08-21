package com.eveningoutpost.dexdrip.cloud.vk;

import static com.google.common.truth.Truth.assertWithMessage;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

public class VkMessagesSenderTest {

    @Test
    public void parseVkError_readsCodeAndMessage() {
        VkApiError error = VkMessagesSender.parseVkError(
                "{\"error\":{\"error_code\":5,\"error_msg\":\"User authorization failed: invalid access_token.\"}}");
        assertWithMessage("code").that(error.errorCode).isEqualTo(5);
        assertWithMessage("msg").that(error.errorMsg).contains("invalid access_token");
    }

    @Test
    public void parseVkError_nullWhenSuccess() {
        assertWithMessage("response only").that(VkMessagesSender.parseVkError("{\"response\":42}")).isNull();
        assertWithMessage("empty").that(VkMessagesSender.parseVkError("")).isNull();
        assertWithMessage("null").that(VkMessagesSender.parseVkError(null)).isNull();
    }

    @Test
    public void isRetryable_knownCodes() {
        assertWithMessage("flood 6").that(VkMessagesSender.isRetryable(200, 6)).isTrue();
        assertWithMessage("flood 9").that(VkMessagesSender.isRetryable(200, 9)).isTrue();
        assertWithMessage("internal 10").that(VkMessagesSender.isRetryable(200, 10)).isTrue();
        assertWithMessage("auth 5").that(VkMessagesSender.isRetryable(200, 5)).isFalse();
        assertWithMessage("http 429").that(VkMessagesSender.isRetryable(429, null)).isTrue();
        assertWithMessage("http 503").that(VkMessagesSender.isRetryable(503, null)).isTrue();
        assertWithMessage("http 200").that(VkMessagesSender.isRetryable(200, null)).isFalse();
    }

    @Test
    public void encodeForm_urlEncodesCyrillicAndEmoji() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("message", "привет 🟢");
        fields.put("peer_id", "123");
        String encoded = VkMessagesSender.encodeForm(fields);
        assertWithMessage("cyrillic").that(encoded).contains("%D0%BF%D1%80%D0%B8%D0%B2%D0%B5%D1%82");
        assertWithMessage("emoji").that(encoded).contains("%F0%9F%9F%A2");
        assertWithMessage("peer").that(encoded).contains("peer_id=123");
        assertWithMessage("no raw cyrillic").that(encoded).doesNotContain("привет");
    }

    @Test
    public void send_happyPathPostsFormAndSucceeds() {
        RecordingClient client = new RecordingClient(200, "{\"response\":99}");
        VkMessagesSender sender = new VkMessagesSender(client);
        VkOutboundDestination dest = VkOutboundDestination.defaults();
        dest.token = "secret-token";
        dest.apiVersion = "5.199";
        dest.url = VkOutboundDestination.DEFAULT_VK_URL;

        VkSendResult result = sender.send(dest, "12345", "5.5 mmol", "evt-1", 1609502400000L, 99);

        assertWithMessage("success").that(result.success).isTrue();
        assertWithMessage("http").that(result.httpCode).isEqualTo(200);
        assertWithMessage("url").that(client.lastUrl).isEqualTo(VkOutboundDestination.DEFAULT_VK_URL);
        assertWithMessage("token field").that(client.lastBody).contains("access_token=secret-token");
        assertWithMessage("version").that(client.lastBody).contains("v=5.199");
        assertWithMessage("peer").that(client.lastBody).contains("peer_id=12345");
        assertWithMessage("message").that(client.lastBody).contains("message=5.5");
        assertWithMessage("random_id").that(client.lastBody).contains("random_id=");
    }

    @Test
    public void send_errorJsonIsFailureWithoutTokenInMessage() {
        RecordingClient client = new RecordingClient(200,
                "{\"error\":{\"error_code\":5,\"error_msg\":\"invalid access_token (secret-token)\"}}");
        VkMessagesSender sender = new VkMessagesSender(client);
        VkOutboundDestination dest = VkOutboundDestination.defaults();
        dest.token = "secret-token";

        VkSendResult result = sender.send(dest, "12345", "hi", "evt-1", 1L, 100);

        assertWithMessage("not success").that(result.success).isFalse();
        assertWithMessage("vk code").that(result.vkErrorCode).isEqualTo(5);
        assertWithMessage("no token leak").that(result.errorMessage).doesNotContain("secret-token");
        assertWithMessage("not retryable auth").that(result.retryable).isFalse();
    }

    @Test
    public void send_http500IsRetryable() {
        RecordingClient client = new RecordingClient(500, "oops");
        VkMessagesSender sender = new VkMessagesSender(client);
        VkOutboundDestination dest = VkOutboundDestination.defaults();
        dest.token = "t";

        VkSendResult result = sender.send(dest, "1", "hi", "e", 1L, 100);
        assertWithMessage("fail").that(result.success).isFalse();
        assertWithMessage("retryable").that(result.retryable).isTrue();
        assertWithMessage("http").that(result.httpCode).isEqualTo(500);
    }

    private static class RecordingClient implements VkHttpClient {
        final int code;
        final String body;
        String lastUrl;
        String lastBody;

        RecordingClient(int code, String body) {
            this.code = code;
            this.body = body;
        }

        @Override
        public VkHttpResponse execute(String url, String formBody) {
            lastUrl = url;
            lastBody = formBody;
            return new VkHttpResponse(code, body);
        }
    }
}
