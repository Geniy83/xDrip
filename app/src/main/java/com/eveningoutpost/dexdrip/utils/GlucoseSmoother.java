package com.eveningoutpost.dexdrip.utils;

import android.content.Context;

import com.eveningoutpost.dexdrip.models.InterAppRawValue;
import com.eveningoutpost.dexdrip.utilitymodels.Pref;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Universal weighted-average smoothing for glucose data sources.
 * Same algorithm as Libre (patched app): weight = 1 - (now - timestamp) / duration;
 * used for DashX, Ottai, Anytime and other inter-app sources.
 */
public final class GlucoseSmoother {

    /** Source id for DashX. Pref key: smoothing_filter_length_dashx */
    public static final String SOURCE_DASHX = "dashx";
    /** Source id for Ottai. Pref key: smoothing_filter_length_ottai */
    public static final String SOURCE_OTTAI = "ottai";
    /** Source id for Anytime. Pref key: smoothing_filter_length_anytime */
    public static final String SOURCE_ANYTIME = "anytime";

    private static final String PREF_KEY_PREFIX = "smoothing_filter_length_";

    private GlucoseSmoother() {
    }

    /**
     * Single point (timestamp + glucose) for the smoothing algorithm.
     */
    public static final class GlucosePoint {
        public final long timestamp;
        public final double glucose;

        public GlucosePoint(long timestamp, double glucose) {
            this.timestamp = timestamp;
            this.glucose = glucose;
        }
    }

    /**
     * Weighted average over points: weight = 1 - (now - timestamp) / durationMs.
     * Same formula as LibreReceiver.calculateWeightedAverage.
     *
     * @param points   list of (timestamp, glucose), can be from any source
     * @param now      reference time (e.g. current reading timestamp)
     * @param durationMs smoothing window in milliseconds
     * @return rounded weighted average, or 0 if no valid points
     */
    public static double calculateWeightedAverage(List<GlucosePoint> points, long now, long durationMs) {
        if (points == null || points.isEmpty() || durationMs <= 0) return 0;
        double sum = 0;
        double weightSum = 0;
        for (GlucosePoint p : points) {
            double weight = 1 - ((now - p.timestamp) / (double) durationMs);
            if (weight > 0) {
                sum += p.glucose * weight;
                weightSum += weight;
            }
        }
        return weightSum > 0 ? Math.round(sum / weightSum) : 0;
    }

    /**
     * Returns the smoothing interval in minutes for the given source (0 = no smoothing).
     * Pref key: smoothing_filter_length_&lt;sourceId&gt; (e.g. smoothing_filter_length_dashx).
     */
    public static int getSmoothingMinutesForSource(String sourceId) {
        if (sourceId == null || sourceId.isEmpty()) return 0;
        return Pref.getStringToInt(PREF_KEY_PREFIX + sourceId, 0);
    }

    /**
     * For inter-app sources (DashX, Ottai, Anytime): saves the reading, then returns
     * either the raw value (if smoothing is 0) or the weighted-average smoothed value
     * over the configured window. Use this value for BgReading insert.
     *
     * @param context  app context (for Pref if needed)
     * @param sourceId one of SOURCE_DASHX, SOURCE_OTTAI, SOURCE_ANYTIME or any string (pref key = smoothing_filter_length_&lt;sourceId&gt;)
     * @param timestamp reading time in ms
     * @param glucose   value in mg/dL
     * @return value to insert (smoothed or raw)
     */
    public static double getSmoothedValueForInterApp(Context context, String sourceId, long timestamp, double glucose) {
        InterAppRawValue.add(sourceId, timestamp, glucose);
        int minutes = getSmoothingMinutesForSource(sourceId);
        if (minutes <= 0) return glucose;

        long durationMs = TimeUnit.MINUTES.toMillis(minutes);
        long fetchSince = timestamp - (minutes == 25 ? 20 : minutes) * 60 * 1000L; // same as Libre: 25 min window → 20 min fetch
        List<InterAppRawValue> raw = InterAppRawValue.getRecentForSource(sourceId, fetchSince);
        List<GlucosePoint> points = new ArrayList<>(raw.size() + 1);
        for (InterAppRawValue r : raw) {
            points.add(new GlucosePoint(r.timestamp, r.glucose));
        }
        points.add(new GlucosePoint(timestamp, glucose));

        double smoothed = calculateWeightedAverage(points, timestamp, durationMs);
        return smoothed > 0 ? smoothed : glucose;
    }
}
