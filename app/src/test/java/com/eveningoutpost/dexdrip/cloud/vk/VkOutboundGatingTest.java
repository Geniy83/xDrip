package com.eveningoutpost.dexdrip.cloud.vk;

import static com.google.common.truth.Truth.assertWithMessage;

import org.junit.Test;

public class VkOutboundGatingTest {

    @Test
    public void liveSend_requiresReadyEnabledAndGates() {
        VkOutboundDestination dest = VkOutboundDestination.defaults();
        dest.enabled = true;
        dest.token = "t";
        dest.chatId = "123";
        dest.minIntervalMinutes = 5;
        dest.triggerMode = VkOutboundDestination.TRIGGER_ALWAYS;
        dest.lastQueuedEventId = "dup";
        dest.lastQueuedAtMs = 1_000_000L;

        assertWithMessage("duplicate skipped").that(
                VkOutboundGating.shouldEnqueue(dest, "dup", 100, 1_000_000L + 10 * 60_000L, false)).isFalse();
        assertWithMessage("interval skipped").that(
                VkOutboundGating.shouldEnqueue(dest, "new", 100, 1_000_000L + 60_000L, false)).isFalse();
        assertWithMessage("ok after interval").that(
                VkOutboundGating.shouldEnqueue(dest, "new", 100, 1_000_000L + 5 * 60_000L, false)).isTrue();
    }

    @Test
    public void liveSend_skipsDisabledOrNotReady() {
        VkOutboundDestination dest = VkOutboundDestination.defaults();
        dest.enabled = false;
        dest.token = "t";
        dest.chatId = "123";
        assertWithMessage("disabled").that(VkOutboundGating.shouldEnqueue(dest, "e", 100, 1L, false)).isFalse();

        dest.enabled = true;
        dest.token = "";
        assertWithMessage("not ready").that(VkOutboundGating.shouldEnqueue(dest, "e", 100, 1L, false)).isFalse();
    }

    @Test
    public void liveSend_skipsWhenTriggerMisses() {
        VkOutboundDestination dest = VkOutboundDestination.defaults();
        dest.enabled = true;
        dest.token = "t";
        dest.chatId = "123";
        dest.triggerMode = VkOutboundDestination.TRIGGER_AT_OR_BELOW;
        dest.triggerLowMgdl = 70;
        assertWithMessage("above low").that(VkOutboundGating.shouldEnqueue(dest, "e", 80, 1L, false)).isFalse();
        assertWithMessage("at low").that(VkOutboundGating.shouldEnqueue(dest, "e", 70, 1L, false)).isTrue();
    }

    @Test
    public void testSend_bypassesIntervalAndTriggerButNeedsReady() {
        VkOutboundDestination dest = VkOutboundDestination.defaults();
        dest.enabled = true;
        dest.token = "t";
        dest.chatId = "123";
        dest.triggerMode = VkOutboundDestination.TRIGGER_AT_OR_BELOW;
        dest.triggerLowMgdl = 70;
        dest.minIntervalMinutes = 5;
        dest.lastQueuedEventId = "dup";
        dest.lastQueuedAtMs = 1_000_000L;

        assertWithMessage("test always").that(
                VkOutboundGating.shouldEnqueue(dest, "dup", 180, 1_000_001L, true)).isTrue();

        dest.token = "";
        assertWithMessage("test still needs ready").that(
                VkOutboundGating.shouldEnqueue(dest, "dup", 180, 1_000_001L, true)).isFalse();
    }

    @Test
    public void pendingCap() {
        assertWithMessage("under cap").that(VkOutboundGating.canAcceptPending(11, 12)).isTrue();
        assertWithMessage("at cap").that(VkOutboundGating.canAcceptPending(12, 12)).isFalse();
    }
}
