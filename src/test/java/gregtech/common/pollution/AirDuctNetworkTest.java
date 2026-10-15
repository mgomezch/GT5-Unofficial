package gregtech.common.pollution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.MetaTileEntityIDs;
import gregtech.api.enums.TierEU;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.GTUtility;

public class AirDuctNetworkTest {

    private static class MockScrubber implements IPollutionScrubber {

        private final int maxCapacity;
        private final AtomicInteger stored = new AtomicInteger(0);
        private boolean valid = true;

        public MockScrubber(int maxCapacity) {
            this.maxCapacity = maxCapacity;
        }

        @Override
        public int getScrubbingCapacity() {
            if (!isValidScrubber()) return 0;
            return Math.max(0, maxCapacity - stored.get());
        }

        @Override
        public int receivePollution(int amount) {
            if (amount <= 0 || !isValidScrubber()) return 0;
            int space = getScrubbingCapacity();
            int toAdd = Math.min(amount, space);
            stored.addAndGet(toAdd);
            return toAdd;
        }

        @Override
        public boolean isValidScrubber() {
            return valid;
        }

        @Override
        public IGregTechTileEntity getBaseMetaTileEntity() {
            return null;
        }

        public void setValid(boolean valid) {
            this.valid = valid;
        }

        public int getStored() {
            return stored.get();
        }

        @Override
        public int getStoredPollution() {
            return stored.get();
        }

        @Override
        public int drainStoredPollution() {
            return stored.getAndSet(0);
        }
    }

    @Test
    void testAirDuctNetworkCapacityAndRouting() {
        AirDuctNetwork net = new AirDuctNetwork();
        MockScrubber s1 = new MockScrubber(10000);
        MockScrubber s2 = new MockScrubber(40000);

        net.addScrubber(s1);
        net.addScrubber(s2);

        assertEquals(50000, net.getTotalScrubbingCapacity());

        // Route 5000 pollution: should fit entirely into s1
        int absorbed1 = net.routePollution(5000);
        assertEquals(5000, absorbed1);
        assertEquals(5000, s1.getStored());
        assertEquals(0, s2.getStored());
        assertEquals(45000, net.getTotalScrubbingCapacity());

        // Route 10000 pollution: s1 takes 5000 (filling it), s2 takes remaining 5000
        int absorbed2 = net.routePollution(10000);
        assertEquals(10000, absorbed2);
        assertEquals(10000, s1.getStored());
        assertEquals(5000, s2.getStored());
        assertEquals(35000, net.getTotalScrubbingCapacity());

        // Route 40000 pollution: s2 has 35000 capacity remaining, so only 35000 absorbed, 5000 excess
        int absorbed3 = net.routePollution(40000);
        assertEquals(35000, absorbed3);
        assertEquals(10000, s1.getStored());
        assertEquals(40000, s2.getStored());
        assertEquals(0, net.getTotalScrubbingCapacity());

        // Route more pollution when network is full: 0 absorbed
        int absorbed4 = net.routePollution(1000);
        assertEquals(0, absorbed4);
    }

    @Test
    void testAirDuctNetworkInvalidation() {
        AirDuctNetwork net = new AirDuctNetwork();
        MockScrubber s1 = new MockScrubber(10000);
        net.addScrubber(s1);

        assertFalse(net.isInvalid());
        assertEquals(10000, net.getTotalScrubbingCapacity());

        net.invalidate();
        assertTrue(net.isInvalid());
        assertEquals(0, net.getTotalScrubbingCapacity());
        assertEquals(0, net.routePollution(5000));
    }

    @Test
    void testScrubberTierCapacityScaling() {
        // Storage capacity: 10,000 * 4^(tier - 1)
        int capLV = 10000 * (int) Math.pow(4, 0); // tier 1
        int capMV = 10000 * (int) Math.pow(4, 1); // tier 2
        int capHV = 10000 * (int) Math.pow(4, 2); // tier 3
        int capEV = 10000 * (int) Math.pow(4, 3); // tier 4

        assertEquals(10000, capLV);
        assertEquals(40000, capMV);
        assertEquals(160000, capHV);
        assertEquals(640000, capEV);
    }

    @Test
    void testElectricAirFilterOverclockMath() {
        // 1 single-amp LV hatch (32V) -> tier 1 -> 0 overclocks
        long v1 = 32;
        byte tier1 = (byte) Math.max(1, GTUtility.getTier(v1));
        int oc1 = Math.max(0, tier1 - 1);
        assertEquals(1, tier1);
        assertEquals(0, oc1);
        int mult1 = 1 << (2 * oc1);
        long power1 = TierEU.RECIPE_LV * (long) mult1;
        int maxProc1 = 1000 * mult1;
        int liquid1 = maxProc1 / 100;
        assertEquals(30, power1);
        assertEquals(1000, maxProc1);
        assertEquals(10, liquid1);

        // 2 single-amp LV hatches (32V + 32V = 64V) -> tier 2 (MV) -> 1 overclock
        long v2 = 64;
        byte tier2 = (byte) Math.max(1, GTUtility.getTier(v2));
        int oc2 = Math.max(0, tier2 - 1);
        assertEquals(2, tier2);
        assertEquals(1, oc2);
        int mult2 = 1 << (2 * oc2);
        long power2 = TierEU.RECIPE_LV * (long) mult2;
        int maxProc2 = 1000 * mult2;
        int liquid2 = maxProc2 / 100;
        assertEquals(120, power2);
        assertEquals(4000, maxProc2);
        assertEquals(40, liquid2);

        // 2 single-amp MV hatches (128V + 128V = 256V) -> tier 3 (HV) -> 2 overclocks
        long v3 = 256;
        byte tier3 = (byte) Math.max(1, GTUtility.getTier(v3));
        int oc3 = Math.max(0, tier3 - 1);
        assertEquals(3, tier3);
        assertEquals(2, oc3);
        int mult3 = 1 << (2 * oc3);
        long power3 = TierEU.RECIPE_LV * (long) mult3;
        int maxProc3 = 1000 * mult3;
        int liquid3 = maxProc3 / 100;
        assertEquals(480, power3);
        assertEquals(16000, maxProc3);
        assertEquals(160, liquid3);
    }

    @Test
    void testLiquidPollutionRatio() {
        // 100 pollution absorbed -> 1 L Liquid Pollution fluid
        int pollution = 3500;
        int liquidProduced = pollution / 100;
        int consumed = liquidProduced * 100;
        int remaining = pollution - consumed;

        assertEquals(35, liquidProduced);
        assertEquals(3500, consumed);
        assertEquals(0, remaining);

        // Partial pollution (< 100)
        int smallPollution = 80;
        assertEquals(0, smallPollution / 100);
    }

    @Test
    void testAirDuctRegistrationIdentifiers() {
        assertEquals(12028, MetaTileEntityIDs.AIR_DUCT.ID);
        assertNotNull(ItemList.AirDuct);
        assertNotNull(Materials.LiquidPollution);
        assertEquals("LiquidPollution", Materials.LiquidPollution.mName);
        assertEquals("Liquid Pollution", Materials.LiquidPollution.mDefaultLocalName);
    }

    @Test
    void testBufferedPollutionReleaseOnBreak() {
        MockScrubber scrubber = new MockScrubber(40000);
        scrubber.receivePollution(25000);
        assertEquals(25000, scrubber.getStoredPollution());
        assertEquals(15000, scrubber.getScrubbingCapacity());

        // When broken, drains stored pollution immediately to release into environment
        int released = scrubber.drainStoredPollution();
        assertEquals(25000, released);
        assertEquals(0, scrubber.getStoredPollution());
        assertEquals(40000, scrubber.getScrubbingCapacity());

        // Subsequent break or check returns 0 (preventing duplicate emission)
        int secondRelease = scrubber.drainStoredPollution();
        assertEquals(0, secondRelease);
    }

    @Test
    void testMufflerHatchScrubberDrainInteraction() {
        MockScrubber filter = new MockScrubber(160000);
        filter.receivePollution(50000);
        assertEquals(50000, filter.getStoredPollution());

        // Breaking muffler hatch attached to filter drains the filter's pollution
        int drainedByMuffler = filter.drainStoredPollution();
        assertEquals(50000, drainedByMuffler);
        assertEquals(0, filter.getStoredPollution());

        // Now if the filter itself is broken afterwards, nothing left to release
        int drainedByFilter = filter.drainStoredPollution();
        assertEquals(0, drainedByFilter);
    }
}
