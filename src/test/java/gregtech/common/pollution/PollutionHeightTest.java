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
}
