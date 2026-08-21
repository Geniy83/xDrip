package com.eveningoutpost.dexdrip.cloud.vk;

import com.eveningoutpost.dexdrip.utilitymodels.Constants;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * VK trigger thresholds follow the app glucose units and the app decimal separator (dot).
 * Comma is accepted on input and converted to {@code .}, matching {@code JoH.tolerantParseDouble} / {@code JoH.qs}.
 */
public class VkGlucoseThresholds {

    public static final double DEFAULT_LOW_MGDL = 70;
    public static final double DEFAULT_HIGH_MGDL = 180;
    /** Same heuristic as {@code Preferences.handleUnitsChange}. */
    private static final double UNIT_SWITCH_CUTOFF = 36;

    private VkGlucoseThresholds() {
    }

    public static String unitLabel(boolean usingMgdl) {
        return usingMgdl ? "mg/dL" : "mmol/L";
    }

    public static String normalizeDecimal(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().replace(" ", "").replace(',', '.');
    }

    public static double parseToMgdl(String raw, boolean usingMgdl, double defaultMgdl) {
        String normalized = normalizeDecimal(raw);
        if (normalized.isEmpty()) {
            return defaultMgdl;
        }
        try {
            double display = Double.parseDouble(normalized);
            return usingMgdl ? display : display * Constants.MMOLL_TO_MGDL;
        } catch (NumberFormatException e) {
            return defaultMgdl;
        }
    }

    public static String formatForDisplay(double mgdl, boolean usingMgdl) {
        if (usingMgdl) {
            return Long.toString(Math.round(mgdl));
        }
        return formatMmol(mgdl * Constants.MGDL_TO_MMOLL);
    }

    public static String defaultLowDisplay(boolean usingMgdl) {
        return formatForDisplay(DEFAULT_LOW_MGDL, usingMgdl);
    }

    public static String defaultHighDisplay(boolean usingMgdl) {
        return formatForDisplay(DEFAULT_HIGH_MGDL, usingMgdl);
    }

    public static String convertStoredForUnitsChange(String stored, boolean toMgdl) {
        String normalized = normalizeDecimal(stored);
        if (normalized.isEmpty()) {
            return "";
        }
        final double value;
        try {
            value = Double.parseDouble(normalized);
        } catch (NumberFormatException e) {
            return toMgdl ? defaultLowDisplay(true) : defaultLowDisplay(false);
        }
        if (toMgdl) {
            if (value < UNIT_SWITCH_CUTOFF) {
                return Long.toString(Math.round(value * Constants.MMOLL_TO_MGDL));
            }
            return Long.toString(Math.round(value));
        }
        if (value > UNIT_SWITCH_CUTOFF - 1) {
            return formatMmol(value * Constants.MGDL_TO_MMOLL);
        }
        return formatMmol(value);
    }

    public static String migrateToUserUnits(String stored, boolean usingMgdl) {
        String normalized = normalizeDecimal(stored);
        if (normalized.isEmpty()) {
            return "";
        }
        final double value;
        try {
            value = Double.parseDouble(normalized);
        } catch (NumberFormatException e) {
            return usingMgdl ? defaultLowDisplay(true) : defaultLowDisplay(false);
        }
        if (!usingMgdl && value >= UNIT_SWITCH_CUTOFF) {
            return formatMmol(value * Constants.MGDL_TO_MMOLL);
        }
        if (usingMgdl && value > 0 && value < UNIT_SWITCH_CUTOFF) {
            return Long.toString(Math.round(value * Constants.MMOLL_TO_MGDL));
        }
        return usingMgdl ? Long.toString(Math.round(value)) : formatMmol(value);
    }

    /**
     * InputFilter-compatible: {@code null} keep original, {@code ""} reject, otherwise replacement.
     */
    public static CharSequence filterTypedDecimal(CharSequence source, int start, int end,
                                                  CharSequence dest, int dstart, int dend) {
        if (source == null) {
            return null;
        }
        StringBuilder replacement = new StringBuilder();
        boolean changed = false;
        for (int i = start; i < end; i++) {
            char c = source.charAt(i);
            if (c == ',') {
                c = '.';
                changed = true;
            }
            if ((c >= '0' && c <= '9') || c == '.') {
                replacement.append(c);
            } else {
                changed = true;
            }
        }
        String destStr = dest == null ? "" : dest.toString();
        int destStart = Math.max(0, Math.min(dstart, destStr.length()));
        int destEnd = Math.max(destStart, Math.min(dend, destStr.length()));
        String newFull = destStr.substring(0, destStart) + replacement + destStr.substring(destEnd);
        int dots = 0;
        for (int i = 0; i < newFull.length(); i++) {
            if (newFull.charAt(i) == '.') {
                dots++;
            }
        }
        if (dots > 1) {
            return "";
        }
        if (!changed && replacement.length() == (end - start)) {
            return null;
        }
        return replacement.toString();
    }

    private static String formatMmol(double mmol) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setDecimalSeparator('.');
        DecimalFormat df = new DecimalFormat("0.0", symbols);
        df.setGroupingUsed(false);
        return df.format(mmol);
    }
}
