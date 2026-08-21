package com.eveningoutpost.dexdrip.cloud.vk;

public class VkOutboundGating {

    public static final int MAX_PENDING = 12;

    private VkOutboundGating() {
    }

    public static boolean shouldEnqueue(VkOutboundDestination dest, String eventId, double mgdl,
                                        long nowMs, boolean testSend) {
        if (dest == null || !dest.isReady()) {
            return false;
        }
        if (testSend) {
            return true;
        }
        if (!dest.enabled) {
            return false;
        }
        if (!dest.shouldSendForGlucose(mgdl)) {
            return false;
        }
        if (dest.isDuplicate(eventId)) {
            return false;
        }
        return !dest.shouldSkipDueToInterval(nowMs);
    }

    public static boolean canAcceptPending(int pending, int cap) {
        return pending < cap;
    }
}
