package com.eveningoutpost.dexdrip.cloud.vk;

import static com.google.common.truth.Truth.assertWithMessage;

import org.junit.Test;

public class VkOutboundStatusTest {

    @Test
    public void applyStatus_lowHighOkStale() {
        VkReading reading = new VkReading();
        reading.mgdl = 65;
        VkOutbound.applyStatus(reading, 70, 180);
        assertWithMessage("low status").that(reading.status).isEqualTo("LOW");
        assertWithMessage("low alarm").that(reading.alarm).isEqualTo("LOW");
        assertWithMessage("low emoji").that(reading.statusEmoji).isEqualTo("🔻");

        reading.mgdl = 200;
        VkOutbound.applyStatus(reading, 70, 180);
        assertWithMessage("high status").that(reading.status).isEqualTo("HIGH");
        assertWithMessage("high alarm").that(reading.alarm).isEqualTo("HIGH");

        reading.mgdl = 100;
        VkOutbound.applyStatus(reading, 70, 180);
        assertWithMessage("ok").that(reading.status).isEqualTo("OK");
        assertWithMessage("ok alarm empty").that(reading.alarm).isEmpty();
        assertWithMessage("ok emoji").that(reading.statusEmoji).isEqualTo("🟢");

        reading.stale = true;
        VkOutbound.applyStatus(reading, 70, 180);
        assertWithMessage("stale").that(reading.status).isEqualTo("STALE");
        assertWithMessage("stale no alarm").that(reading.alarm).isEmpty();
    }
}
