package gregtech.loaders;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import gregtech.api.enums.ItemList;

public class MTERecipeLoaderSafetyTest {

    @Test
    void testPlanetarySiphonInitializationContract() {
        // PlanetarySiphon is registered by gtnhintergalactic, which loads after gregtech.
        // During gregtech's initialization phase, ItemList.PlanetarySiphon has NOT been set yet.
        // Therefore, calling .hasBeenSet() must safely return false without throwing.
        assertFalse(
            ItemList.PlanetarySiphon.hasBeenSet(),
            "PlanetarySiphon must not be assumed set prior to gtnhintergalactic initialization");
        assertFalse(
            ItemList.PlanetaryGasSiphonController.hasBeenSet(),
            "PlanetaryGasSiphonController must not be assumed set prior to gtnhintergalactic initialization");

        // Calling .get() before .set() throws IllegalAccessError.
        // This test ensures the contract that guarding with hasBeenSet() prevents calling .get()
        assertThrows(IllegalAccessError.class, () -> ItemList.PlanetarySiphon.get(1));
        assertThrows(IllegalAccessError.class, () -> ItemList.PlanetaryGasSiphonController.get(1));
    }
}
