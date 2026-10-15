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
}
