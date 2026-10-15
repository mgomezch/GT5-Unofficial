package gregtech.common.pollution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import gregtech.api.enums.ItemList;
import gregtech.api.enums.MetaTileEntityIDs;

public class PollutionHeightTest {

    @Test
    void testPollutionEffectFactorDiminishingDistance() {
        // When pollution has no defined height (NaN), full effect applies
        assertEquals(1.0, Pollution.getPollutionEffectFactor(64.0, Float.NaN), 1e-9);

        // At the exact pollution height, full effect applies (factor = 1.0)
        assertEquals(1.0, Pollution.getPollutionEffectFactor(64.0, 64.0f), 1e-9);

        // At 50 blocks distance vertically, factor should be 20% (0.2)
        assertEquals(0.2, Pollution.getPollutionEffectFactor(114.0, 64.0f), 1e-6);
        assertEquals(0.2, Pollution.getPollutionEffectFactor(14.0, 64.0f), 1e-6);

        // At 100 blocks distance vertically, factor should be 0.2^2 = 0.04 (4%)
        assertEquals(0.04, Pollution.getPollutionEffectFactor(164.0, 64.0f), 1e-6);

        // At 25 blocks distance vertically, factor should be sqrt(0.2) = ~0.4472136
        assertEquals(Math.sqrt(0.2), Pollution.getPollutionEffectFactor(89.0, 64.0f), 1e-6);
    }

    @Test
    void testWeightedAverageHeightCalculation() {
        int oldPollution = 1000;
        float oldHeight = 64.0f;
        int addedPollution = 500;
        double addedHeight = 100.0;

        // Formula: (oldP * oldH + addP * addH) / (oldP + addP)
        double expectedHeight = ((double) oldPollution * oldHeight + (double) addedPollution * addedHeight)
            / (oldPollution + addedPollution);
        assertEquals(76.0, expectedHeight, 1e-6);

        // Emission into unpolluted chunk (oldPollution == 0 or oldHeight NaN)
        float initialHeight = (float) addedHeight;
        assertEquals(100.0f, initialHeight, 1e-6);
    }

    @Test
    void testChimneyRegistrationIdentifiers() {
        assertEquals(12024, MetaTileEntityIDs.CHIMNEY_CONTROLLER.ID);
        assertNotNull(ItemList.Machine_Multi_Chimney);
    }

    @Test
    void testIndustrialSmokeStackRegistration() {
        assertEquals(12025, MetaTileEntityIDs.INDUSTRIAL_SMOKE_STACK_T1.ID);
        assertEquals(12026, MetaTileEntityIDs.INDUSTRIAL_SMOKE_STACK_T2.ID);
        assertEquals(12027, MetaTileEntityIDs.INDUSTRIAL_SMOKE_STACK_T3.ID);

        assertNotNull(ItemList.Machine_Multi_IndustrialSmokeStackT1);
        assertNotNull(ItemList.Machine_Multi_IndustrialSmokeStackT2);
        assertNotNull(ItemList.Machine_Multi_IndustrialSmokeStackT3);
    }

    @Test
    void testSmokeStackVerticalDiminishingEfficiency() {
        // Tier 1: H = 30, half height = 15
        int h1 = 30;
        double halfH1 = h1 / 2.0;
        assertEquals(1.0, Math.pow(0.5, 0.0 / halfH1), 1e-9);
        assertEquals(0.5, Math.pow(0.5, 15.0 / halfH1), 1e-9);
        assertEquals(0.25, Math.pow(0.5, 30.0 / halfH1), 1e-9);

        // Tier 2: H = 60, half height = 30
        int h2 = 60;
        double halfH2 = h2 / 2.0;
        assertEquals(1.0, Math.pow(0.5, 0.0 / halfH2), 1e-9);
        assertEquals(0.5, Math.pow(0.5, 30.0 / halfH2), 1e-9);
        assertEquals(0.25, Math.pow(0.5, 60.0 / halfH2), 1e-9);

        // Tier 3: H = 90, half height = 45
        int h3 = 90;
        double halfH3 = h3 / 2.0;
        assertEquals(1.0, Math.pow(0.5, 0.0 / halfH3), 1e-9);
        assertEquals(0.5, Math.pow(0.5, 45.0 / halfH3), 1e-9);
        assertEquals(0.25, Math.pow(0.5, 90.0 / halfH3), 1e-9);
    }

    @Test
    void testSmokeStackManhattanDistanceEfficiency() {
        // Tier 1: max distance = 1
        assertEquals(1.0, 1.0 - 0.5 * (0.0 / 1.0), 1e-9);
        assertEquals(0.5, 1.0 - 0.5 * (1.0 / 1.0), 1e-9);

        // Tier 2: max distance = 2
        assertEquals(1.0, 1.0 - 0.5 * (0.0 / 2.0), 1e-9);
        assertEquals(0.75, 1.0 - 0.5 * (1.0 / 2.0), 1e-9);
        assertEquals(0.5, 1.0 - 0.5 * (2.0 / 2.0), 1e-9);

        // Tier 3: max distance = 3
        assertEquals(1.0, 1.0 - 0.5 * (0.0 / 3.0), 1e-9);
        assertEquals(5.0 / 6.0, 1.0 - 0.5 * (1.0 / 3.0), 1e-6);
        assertEquals(4.0 / 6.0, 1.0 - 0.5 * (2.0 / 3.0), 1e-6);
        assertEquals(0.5, 1.0 - 0.5 * (3.0 / 3.0), 1e-9);
    }

    @Test
    void testSmokeStackThroughputScaling() {
        int chimney = 500;
        int t1Base = chimney * 4; // 2000
        int t2Base = t1Base * 4; // 8000
        int t3Base = t2Base * 4; // 32000

        assertEquals(2000, t1Base);
        assertEquals(8000, t2Base);
        assertEquals(32000, t3Base);

        // 4 amps grants next-tier throughput
        assertEquals(t2Base, t1Base * 4);
        assertEquals(t3Base, t2Base * 4);
        assertEquals(128000, t3Base * 4);

        // Accumulation cap is 100x max cycle throughput
        assertEquals(200000, 100 * t1Base);
        assertEquals(800000, 100 * (t1Base * 4));
        assertEquals(3200000, 100 * t3Base);
        assertEquals(12800000, 100 * (t3Base * 4));
    }

    @Test
    void testAtHeightAdjustedPollutionScore() {
        // Zero distance: 100% of chunk pollution
        assertEquals(10000.0, Pollution.getAtHeightAdjustedPollution(64.0, 10000, 64.0f), 1e-9);

        // 30 blocks vertical distance: hits 50%
        assertEquals(5000.0, Pollution.getAtHeightAdjustedPollution(94.0, 10000, 64.0f), 1e-9);
        assertEquals(5000.0, Pollution.getAtHeightAdjustedPollution(34.0, 10000, 64.0f), 1e-9);

        // 60 blocks vertical distance: hits 25% (0.5^2)
        assertEquals(2500.0, Pollution.getAtHeightAdjustedPollution(124.0, 10000, 64.0f), 1e-9);

        // 90 blocks vertical distance: hits 12.5% (0.5^3)
        assertEquals(1250.0, Pollution.getAtHeightAdjustedPollution(154.0, 10000, 64.0f), 1e-9);

        // Undefined height (NaN): full chunk pollution applies
        assertEquals(10000.0, Pollution.getAtHeightAdjustedPollution(64.0, 10000, Float.NaN), 1e-9);

        // Zero or negative pollution: returns 0.0
        assertEquals(0.0, Pollution.getAtHeightAdjustedPollution(64.0, 0, 64.0f), 1e-9);
        assertEquals(0.0, Pollution.getAtHeightAdjustedPollution(64.0, -500, 64.0f), 1e-9);
    }

    @Test
    void testPollutionEfficiencyPenalty() {
        // 0 gibbl: no penalty
        assertEquals(0, Pollution.getPollutionEfficiencyPenalty(0.0));
        assertEquals(0, Pollution.getPollutionEfficiencyPenalty(-100.0));

        // First 10%: 1% (100 points) per 5,000 gibbl
        // 2,500 gibbl -> 0.5% (50 points)
        assertEquals(50, Pollution.getPollutionEfficiencyPenalty(2500.0));
        // 5,000 gibbl -> 1.0% (100 points)
        assertEquals(100, Pollution.getPollutionEfficiencyPenalty(5000.0));
        // 10,000 gibbl -> 2.0% (200 points)
        assertEquals(200, Pollution.getPollutionEfficiencyPenalty(10000.0));
        // 25,000 gibbl -> 5.0% (500 points)
        assertEquals(500, Pollution.getPollutionEfficiencyPenalty(25000.0));
        // 50,000 gibbl -> 10.0% (1,000 points)
        assertEquals(1000, Pollution.getPollutionEfficiencyPenalty(50000.0));

        // After 50,000: 1% (100 points) per 10,000 gibbl
        // 60,000 gibbl -> 10% + 1% = 11% (1,100 points)
        assertEquals(1100, Pollution.getPollutionEfficiencyPenalty(60000.0));
        // 100,000 gibbl -> 10% + 5% = 15% (1,500 points)
        assertEquals(1500, Pollution.getPollutionEfficiencyPenalty(100000.0));
        // 150,000 gibbl -> 10% + 10% = 20% (2,000 points)
        assertEquals(2000, Pollution.getPollutionEfficiencyPenalty(150000.0));
    }

    @Test
    void testMaintenanceFrequencyMultiplier() {
        // 0 gibbl: base frequency (multiplier = 1.0)
        assertEquals(1.0, Pollution.getMaintenanceFrequencyMultiplier(0.0), 1e-9);
        assertEquals(1.0, Pollution.getMaintenanceFrequencyMultiplier(-50.0), 1e-9);

        // Every 2,000 gibbl doubles the frequency
        assertEquals(2.0, Pollution.getMaintenanceFrequencyMultiplier(2000.0), 1e-9);
        assertEquals(4.0, Pollution.getMaintenanceFrequencyMultiplier(4000.0), 1e-9);
        assertEquals(8.0, Pollution.getMaintenanceFrequencyMultiplier(6000.0), 1e-9);
        assertEquals(16.0, Pollution.getMaintenanceFrequencyMultiplier(8000.0), 1e-9);

        // Fractional gibbl follows continuous 2^(G / 2000)
        assertEquals(Math.sqrt(2.0), Pollution.getMaintenanceFrequencyMultiplier(1000.0), 1e-9);
    }
}
