package com.eveningoutpost.dexdrip.cloud.vk;

import static com.google.common.truth.Truth.assertWithMessage;

import org.junit.Before;
import org.junit.Test;

import java.util.TimeZone;

public class VkMessageRendererTest {

    @Before
    public void setUtc() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Test
    public void render_vkMessagesPreset() {
        VkReading reading = specimen();
        String rendered = VkMessageRenderer.renderMessage(
                VkOutboundDestination.DEFAULT_CHAT_TEMPLATE, reading);
        assertWithMessage("chat template").that(rendered).isEqualTo("🟢 5.5 mmol/L → 12:00");
    }

    @Test
    public void render_glucoWatchPreset() {
        VkReading reading = specimen();
        String rendered = VkMessageRenderer.renderMessage(
                VkOutboundDestination.DEFAULT_GLUCO_WATCH_TEMPLATE, reading);
        assertWithMessage("glucowatch").that(rendered)
                .isEqualTo("GV:5.5|RAW:5.6|TR:→|AL:|RT:0.11|IOB:1.2|COB:10|TS:1609502400000");
    }

    @Test
    public void render_doesNotReplaceAutoInsideAutoValue() {
        VkReading reading = specimen();
        String rendered = VkMessageRenderer.renderMessage("{auto} / {auto_value}", reading);
        assertWithMessage("auto tokens").that(rendered).isEqualTo("5.5 mmol/L / 5.5");
    }

    @Test
    public void eventId_liveAndTest() {
        VkReading live = specimen();
        live.test = false;
        assertWithMessage("live id").that(VkMessageRenderer.eventId(live))
                .isEqualTo("sensor-1:1609502400000:99");

        VkReading test = specimen();
        test.test = true;
        test.timeMillis = 1609502400123L;
        assertWithMessage("test id").that(VkMessageRenderer.eventId(test))
                .isEqualTo("test-1609502400123");
    }

    @Test
    public void randomId_stableAndNonNegative() {
        int a = VkMessageRenderer.randomId("e1", "123", 1609459200000L, 99);
        int b = VkMessageRenderer.randomId("e1", "123", 1609459200000L, 99);
        int c = VkMessageRenderer.randomId("e1", "124", 1609459200000L, 99);
        assertWithMessage("stable").that(a).isEqualTo(b);
        assertWithMessage("non-neg").that(a).isAtLeast(0);
        assertWithMessage("changes with recipient").that(a).isNotEqualTo(c);
    }

    private static VkReading specimen() {
        VkReading reading = new VkReading();
        reading.eventId = "sensor-1:1609502400000:99";
        reading.recipient = "12345";
        reading.timeMillis = 1609502400000L; // 2021-01-01 12:00 UTC
        reading.test = false;
        reading.stale = false;
        reading.displayText = "5.5";
        reading.unit = "mmol/L";
        reading.mgdl = 99.1;
        reading.mmol = 5.5;
        reading.rawMgdl = 100.9;
        reading.rawMmol = 5.6;
        reading.rateMgdl = 2.0;
        reading.rateMmol = 0.11;
        reading.trendName = "Flat";
        reading.trendArrow = "→";
        reading.alarm = "";
        reading.status = "OK";
        reading.statusEmoji = "🟢";
        reading.iob = "1.2";
        reading.cob = "10";
        reading.sensor = "sensor-1";
        return reading;
    }
}
