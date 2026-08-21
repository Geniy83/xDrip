package com.eveningoutpost.dexdrip.cloud.vk;

import static com.google.common.truth.Truth.assertWithMessage;

import com.eveningoutpost.dexdrip.utilitymodels.Constants;

import org.junit.Test;

public class VkGlucoseThresholdsTest {

    @Test
    public void normalizeDecimal_convertsCommaToDot() {
        assertWithMessage("comma").that(VkGlucoseThresholds.normalizeDecimal("3,9")).isEqualTo("3.9");
        assertWithMessage("dot").that(VkGlucoseThresholds.normalizeDecimal("3.9")).isEqualTo("3.9");
        assertWithMessage("spaces").that(VkGlucoseThresholds.normalizeDecimal(" 10,0 ")).isEqualTo("10.0");
        assertWithMessage("null").that(VkGlucoseThresholds.normalizeDecimal(null)).isEqualTo("");
    }

    @Test
    public void parseToMgdl_usesAppUnits() {
        assertWithMessage("mgdl 70").that(VkGlucoseThresholds.parseToMgdl("70", true, 70)).isWithin(0.01).of(70);
        assertWithMessage("mmol 3.9").that(VkGlucoseThresholds.parseToMgdl("3.9", false, 70))
                .isWithin(0.3).of(70);
        assertWithMessage("mmol comma").that(VkGlucoseThresholds.parseToMgdl("3,9", false, 70))
                .isWithin(0.3).of(70);
        assertWithMessage("empty uses default").that(VkGlucoseThresholds.parseToMgdl("", true, 70))
                .isWithin(0.01).of(70);
    }

    @Test
    public void formatForDisplay_matchesUnits() {
        assertWithMessage("mgdl").that(VkGlucoseThresholds.formatForDisplay(70, true)).isEqualTo("70");
        assertWithMessage("mmol").that(VkGlucoseThresholds.formatForDisplay(70, false)).isEqualTo("3.9");
        assertWithMessage("high mmol").that(VkGlucoseThresholds.formatForDisplay(180, false)).isEqualTo("10.0");
    }

    @Test
    public void defaults_followUnits() {
        assertWithMessage("low mgdl").that(VkGlucoseThresholds.defaultLowDisplay(true)).isEqualTo("70");
        assertWithMessage("high mgdl").that(VkGlucoseThresholds.defaultHighDisplay(true)).isEqualTo("180");
        assertWithMessage("low mmol").that(VkGlucoseThresholds.defaultLowDisplay(false)).isEqualTo("3.9");
        assertWithMessage("high mmol").that(VkGlucoseThresholds.defaultHighDisplay(false)).isEqualTo("10.0");
    }

    @Test
    public void convertStored_whenUnitsChange() {
        assertWithMessage("70 to mmol").that(VkGlucoseThresholds.convertStoredForUnitsChange("70", false))
                .isEqualTo("3.9");
        assertWithMessage("3.9 to mgdl").that(VkGlucoseThresholds.convertStoredForUnitsChange("3,9", true))
                .isEqualTo("70");
        assertWithMessage("already mmol").that(VkGlucoseThresholds.convertStoredForUnitsChange("3.9", false))
                .isEqualTo("3.9");
        assertWithMessage("already mgdl").that(VkGlucoseThresholds.convertStoredForUnitsChange("70", true))
                .isEqualTo("70");
        assertWithMessage("180 to mmol").that(VkGlucoseThresholds.convertStoredForUnitsChange("180", false))
                .isEqualTo("10.0");
    }

    @Test
    public void migrateLegacyMgdl_whenAppIsMmol() {
        assertWithMessage("legacy 70").that(VkGlucoseThresholds.migrateToUserUnits("70", false)).isEqualTo("3.9");
        assertWithMessage("already mmol").that(VkGlucoseThresholds.migrateToUserUnits("3.9", false)).isEqualTo("3.9");
        assertWithMessage("mgdl stays").that(VkGlucoseThresholds.migrateToUserUnits("70", true)).isEqualTo("70");
    }

    @Test
    public void filterTypedDecimal_commaBecomesDotAndRejectsSecondSeparator() {
        assertWithMessage("comma").that(String.valueOf(
                VkGlucoseThresholds.filterTypedDecimal(",", 0, 1, "", 0, 0))).isEqualTo(".");
        assertWithMessage("digit").that(VkGlucoseThresholds.filterTypedDecimal("9", 0, 1, "3.", 2, 2)).isNull();
        assertWithMessage("second dot").that(String.valueOf(
                VkGlucoseThresholds.filterTypedDecimal(".", 0, 1, "3.9", 3, 3))).isEqualTo("");
        assertWithMessage("second comma").that(String.valueOf(
                VkGlucoseThresholds.filterTypedDecimal(",", 0, 1, "3.9", 3, 3))).isEqualTo("");
    }

    @Test
    public void unitLabel_matchesAppPicker() {
        assertWithMessage("mgdl").that(VkGlucoseThresholds.unitLabel(true)).isEqualTo("mg/dL");
        assertWithMessage("mmol").that(VkGlucoseThresholds.unitLabel(false)).isEqualTo("mmol/L");
    }

    @Test
    public void mmolFactor_matchesAppConstant() {
        assertWithMessage("factor").that(Constants.MMOLL_TO_MGDL).isWithin(0.0001).of(18.0182);
    }
}
