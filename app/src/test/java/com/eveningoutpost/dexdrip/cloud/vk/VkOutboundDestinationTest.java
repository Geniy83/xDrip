package com.eveningoutpost.dexdrip.cloud.vk;

import static com.google.common.truth.Truth.assertWithMessage;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class VkOutboundDestinationTest {

    @Test
    public void isReady_falseWhenTokenBlank() {
        VkOutboundDestination dest = readyDestination();
        dest.token = "  ";
        assertWithMessage("blank token").that(dest.isReady()).isFalse();
        dest.token = "";
        assertWithMessage("empty token").that(dest.isReady()).isFalse();
        dest.token = null;
        assertWithMessage("null token").that(dest.isReady()).isFalse();
    }

    @Test
    public void isReady_falseWhenNoRecipients() {
        VkOutboundDestination dest = readyDestination();
        dest.chatId = "";
        assertWithMessage("empty recipients").that(dest.isReady()).isFalse();
        dest.chatId = "  , ; \n";
        assertWithMessage("whitespace only").that(dest.isReady()).isFalse();
    }

    @Test
    public void isReady_falseWhenNonNumericRecipient() {
        VkOutboundDestination dest = readyDestination();
        dest.chatId = "12345,abc";
        assertWithMessage("mixed invalid").that(dest.isReady()).isFalse();
        dest.chatId = "user_1";
        assertWithMessage("non numeric").that(dest.isReady()).isFalse();
    }

    @Test
    public void isReady_trueWhenTokenAndNumericRecipients() {
        VkOutboundDestination dest = readyDestination();
        dest.chatId = "12345; 67890\n2000000001";
        assertWithMessage("comma/semicolon/newline").that(dest.isReady()).isTrue();
        assertWithMessage("parsed ids").that(dest.recipients())
                .isEqualTo(Arrays.asList("12345", "67890", "2000000001"));
    }

    @Test
    public void resolvedUrl_usesDefaultWhenBlank() {
        VkOutboundDestination dest = readyDestination();
        dest.url = "";
        assertWithMessage("blank url").that(dest.resolvedUrl())
                .isEqualTo(VkOutboundDestination.DEFAULT_VK_URL);
        dest.url = "https://api.vk.com/method/messages.send";
        assertWithMessage("custom url").that(dest.resolvedUrl())
                .isEqualTo("https://api.vk.com/method/messages.send");
    }

    @Test
    public void resolvedTemplate_usesPresetDefaults() {
        VkOutboundDestination dest = readyDestination();
        dest.preset = VkOutboundDestination.PRESET_VK_MESSAGES;
        dest.messageTemplate = "";
        assertWithMessage("chat template").that(dest.resolvedTemplate())
                .isEqualTo(VkOutboundDestination.DEFAULT_CHAT_TEMPLATE);

        dest.preset = VkOutboundDestination.PRESET_GLUCOWATCH;
        assertWithMessage("glucowatch template").that(dest.resolvedTemplate())
                .isEqualTo(VkOutboundDestination.DEFAULT_GLUCO_WATCH_TEMPLATE);

        dest.messageTemplate = "custom {value}";
        assertWithMessage("custom wins").that(dest.resolvedTemplate())
                .isEqualTo("custom {value}");
    }

    @Test
    public void shouldSendForGlucose_respectsTriggerModes() {
        VkOutboundDestination dest = readyDestination();
        dest.triggerLowMgdl = 70;
        dest.triggerHighMgdl = 180;

        dest.triggerMode = VkOutboundDestination.TRIGGER_ALWAYS;
        assertWithMessage("always mid").that(dest.shouldSendForGlucose(100)).isTrue();

        dest.triggerMode = VkOutboundDestination.TRIGGER_AT_OR_BELOW;
        assertWithMessage("at or below 70").that(dest.shouldSendForGlucose(70)).isTrue();
        assertWithMessage("at or below 71").that(dest.shouldSendForGlucose(71)).isFalse();

        dest.triggerMode = VkOutboundDestination.TRIGGER_AT_OR_ABOVE;
        assertWithMessage("at or above 180").that(dest.shouldSendForGlucose(180)).isTrue();
        assertWithMessage("at or above 179").that(dest.shouldSendForGlucose(179)).isFalse();

        dest.triggerMode = VkOutboundDestination.TRIGGER_OUTSIDE_RANGE;
        assertWithMessage("outside low").that(dest.shouldSendForGlucose(69)).isTrue();
        assertWithMessage("outside high").that(dest.shouldSendForGlucose(181)).isTrue();
        assertWithMessage("inside range").that(dest.shouldSendForGlucose(100)).isFalse();
    }

    @Test
    public void intervalAndDuplicateGating() {
        VkOutboundDestination dest = readyDestination();
        dest.minIntervalMinutes = 5;
        dest.lastQueuedEventId = "sensor:1:100";
        dest.lastQueuedAtMs = 1_000_000L;

        assertWithMessage("duplicate").that(dest.isDuplicate("sensor:1:100")).isTrue();
        assertWithMessage("new event").that(dest.isDuplicate("sensor:2:100")).isFalse();

        assertWithMessage("inside interval").that(dest.shouldSkipDueToInterval(1_000_000L + 4 * 60_000L)).isTrue();
        assertWithMessage("after interval").that(dest.shouldSkipDueToInterval(1_000_000L + 5 * 60_000L)).isFalse();

        dest.minIntervalMinutes = 0;
        assertWithMessage("zero interval never skips").that(dest.shouldSkipDueToInterval(1_000_001L)).isFalse();
    }

    @Test
    public void recipients_emptyWhenNone() {
        VkOutboundDestination dest = readyDestination();
        dest.chatId = "";
        assertWithMessage("none").that(dest.recipients()).isEqualTo(Collections.emptyList());
    }

    private static VkOutboundDestination readyDestination() {
        VkOutboundDestination dest = VkOutboundDestination.defaults();
        dest.enabled = true;
        dest.token = "vk1.community.token";
        dest.chatId = "12345";
        return dest;
    }
}
