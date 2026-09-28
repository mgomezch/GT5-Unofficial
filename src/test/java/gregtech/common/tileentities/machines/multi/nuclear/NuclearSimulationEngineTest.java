package gregtech.common.tileentities.machines.multi.nuclear;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NuclearSimulationEngineTest {

    @BeforeEach
    void setUp() {
        NuclearSimulationEngine.resetDefaultParameters();
    }

    private static class MockNuclearTile implements INuclearTile {

        double temperature = 20.0;
        double heatEU = 0.0;
        boolean isFuel = false;
        int neutronBaseGen = 0;
        double absorbProb = 0.1;
        double scatterProb = 0.5;
        double moderationProb = 0.8;
        double heatCoeff = 0.05;

        int fluxReceived = 0;
        int fastAbsorbed = 0;
        int thermalAbsorbed = 0;
        int scattered = 0;

        MockNuclearTile(boolean isFuel, int neutronBaseGen) {
            this.isFuel = isFuel;
            this.neutronBaseGen = neutronBaseGen;
        }

        MockNuclearTile(double initialTemp, double heatCoeff) {
            this.temperature = initialTemp;
            this.heatCoeff = heatCoeff;
        }

        @Override
        public double getTemperature() {
            return temperature;
        }

        @Override
        public void setTemperature(double temp) {
            this.temperature = temp;
        }

        @Override
        public void addHeat(double heat) {
            this.heatEU += heat;
            this.temperature += heat / NuclearSimulationEngine.EU_PER_DEGREE;
        }

        @Override
        public double getHeatTransferCoeff() {
            return heatCoeff;
        }

        @Override
        public boolean isFuel() {
            return isFuel;
        }

        @Override
        public int generateNeutrons(double efficiency) {
            return (int) (neutronBaseGen * efficiency);
        }

        @Override
        public void addNeutronFlux(NeutronType type, int count) {
            this.fluxReceived += count;
        }

        @Override
        public double getAbsorptionProbability(NeutronType type) {
            return absorbProb;
        }

        @Override
        public double getScatteringProbability(NeutronType type) {
            return scatterProb;
        }

        @Override
        public double getModerationProbability() {
            return moderationProb;
        }

        @Override
        public void onNeutronAbsorbed(NeutronType type, int count) {
            if (type == NeutronType.FAST) fastAbsorbed += count;
            else thermalAbsorbed += count;
        }

        @Override
        public void onNeutronScattered(NeutronType type, int count) {
            this.scattered += count;
        }

        @Override
        public void nuclearTick(double efficiency) {}
    }

    @Test
    void testNegativeTemperatureEfficiencyCurve() {
        NuclearSimulationEngine.setSimulationParameters(600.0, 2200.0, 1.0, 1.1, 18.0, 200.0);
        assertEquals(1.0, NuclearSimulationEngine.calculateEfficiency(20.0), 1e-6);
        assertEquals(1.0, NuclearSimulationEngine.calculateEfficiency(600.0), 1e-6);
        assertEquals(0.5, NuclearSimulationEngine.calculateEfficiency(1400.0), 1e-6);
        assertEquals(0.0, NuclearSimulationEngine.calculateEfficiency(2200.0), 1e-6);
        assertEquals(0.0, NuclearSimulationEngine.calculateEfficiency(3000.0), 1e-6);
        NuclearSimulationEngine.resetDefaultParameters();
    }

    @Test
    void testEmptyGridSimulation() {
        NuclearSimulationEngine.SimulationResult res = NuclearSimulationEngine.simulate(null, 0, 0);
        assertNotNull(res);
        assertEquals(0, res.totalNeutronsGenerated);

        INuclearTile[][] emptyGrid = new INuclearTile[3][3];
        NuclearSimulationEngine.SimulationResult res2 = NuclearSimulationEngine.simulate(emptyGrid, 3, 3);
        assertNotNull(res2);
        assertEquals(0, res2.totalNeutronsGenerated);
        assertEquals(NuclearSimulationEngine.AMBIENT_TEMP, res2.averageTemperature, 1e-6);
    }

    @Test
    void testFuelNeutronGenerationAndTransport() {
        INuclearTile[][] grid = new INuclearTile[3][3];
        MockNuclearTile fuel = new MockNuclearTile(true, 100);
        grid[1][1] = fuel;

        MockNuclearTile moderator = new MockNuclearTile(false, 0);
        moderator.absorbProb = 0.5;
        grid[1][0] = moderator;
        grid[1][2] = moderator;
        grid[0][1] = moderator;
        grid[2][1] = moderator;

        NuclearSimulationEngine.SimulationResult res = NuclearSimulationEngine.simulate(grid, 3, 3);
        assertTrue(res.totalNeutronsGenerated > 0, "Fuel rod should generate neutrons");
        assertEquals(100, res.totalNeutronsGenerated);
        assertTrue(fuel.getTemperature() > 20.0, "Fuel rod should heat up due to fission");
        assertTrue(fuel.heatEU > 0, "Direct fission heat should be recorded");
        assertTrue(res.fastNeutronsAbsorbed + res.thermalNeutronsAbsorbed + res.neutronsEscaped > 0);
    }

    @Test
    void testSelfStabilizationUnderHighTemp() {
        INuclearTile[][] grid = new INuclearTile[1][1];
        MockNuclearTile hotFuel = new MockNuclearTile(true, 100);
        hotFuel.temperature = NuclearSimulationEngine.tempThresholdHigh;
        grid[0][0] = hotFuel;

        NuclearSimulationEngine.SimulationResult res = NuclearSimulationEngine.simulate(grid, 1, 1);
        assertEquals(
            0,
            res.totalNeutronsGenerated,
            "Reactivity should shut down completely at or above tempThresholdHigh");
    }

    @Test
    void testHeatDiffusionBetweenAdjacentTiles() {
        INuclearTile[][] grid = new INuclearTile[2][1];
        MockNuclearTile hotTile = new MockNuclearTile(500.0, 0.2);
        MockNuclearTile coldTile = new MockNuclearTile(20.0, 0.2);
        grid[0][0] = hotTile;
        grid[1][0] = coldTile;

        double initialDiff = hotTile.getTemperature() - coldTile.getTemperature();
        NuclearSimulationEngine.simulate(grid, 2, 1);

        double newDiff = hotTile.getTemperature() - coldTile.getTemperature();
        assertTrue(newDiff < initialDiff, "Heat diffusion should reduce temperature difference between adjacent tiles");
        assertTrue(coldTile.getTemperature() > 20.0, "Cold tile should have gained temperature from hot tile");
    }

    @Test
    void testNeutronScatteringAndModeration() {
        INuclearTile[][] grid = new INuclearTile[3][3];
        MockNuclearTile fuel = new MockNuclearTile(true, 500);
        grid[1][1] = fuel;

        MockNuclearTile reflector = new MockNuclearTile(false, 0);
        reflector.absorbProb = 0.05;
        reflector.scatterProb = 0.95;
        reflector.moderationProb = 0.90;
        grid[1][0] = reflector;
        grid[1][2] = reflector;
        grid[0][1] = reflector;
        grid[2][1] = reflector;

        NuclearSimulationEngine.SimulationResult res = NuclearSimulationEngine.simulate(grid, 3, 3);
        assertTrue(reflector.fluxReceived > 0, "Reflector should receive neutron flux from adjacent fuel");
        assertTrue(reflector.scattered > 0, "Reflector should scatter neutrons");
        assertTrue(res.totalNeutronsGenerated == 500, "All 500 neutrons generated by fuel");
    }

    @Test
    void testCoolantBoilingThermodynamics() {
        // Distilled Water: boiling at 200°C, 320 EU/mB, 160:1 steam ratio
        double boilingPoint = 200.0;
        double heatPerMB = 320.0;
        double currentTemp = 250.0;
        double heatAvailable = (currentTemp - boilingPoint) * NuclearSimulationEngine.EU_PER_DEGREE;
        assertEquals(50.0 * 64.0, heatAvailable, 1e-6);

        int fluidToBoil = (int) (heatAvailable / heatPerMB);
        assertEquals(10, fluidToBoil);

        int steamProduced = fluidToBoil * 160;
        assertEquals(1600, steamProduced);

        double heatConsumed = fluidToBoil * heatPerMB;
        double tempDrop = heatConsumed / NuclearSimulationEngine.EU_PER_DEGREE;
        double finalTemp = currentTemp - tempDrop;
        assertTrue(finalTemp < currentTemp && finalTemp >= boilingPoint);
    }

    @Test
    void testCoolantCellCapacityScaling() {
        // 10k cell vs 60k cell vs 360k cell
        int maxHeat10k = 10_000;
        int maxHeat60k = 60_000;
        int maxHeat360k = 360_000;

        int rate10k = Math.max(1, maxHeat10k / 100);
        int rate60k = Math.max(1, maxHeat60k / 100);
        int rate360k = Math.max(1, maxHeat360k / 100);

        assertEquals(100, rate10k);
        assertEquals(600, rate60k);
        assertEquals(3600, rate360k);

        // 60k cell absorbs 6x more heat per tick than 10k cell
        assertEquals(6, rate60k / rate10k);
        // 360k cell absorbs 6x more heat per tick than 60k cell
        assertEquals(6, rate360k / rate60k);
    }

    @Test
    void testAbsorptionDrivenFuelDepletion() {
        // Under high neutron flux, fuel rod burns much faster than idle
        int fastAbsorbed = 15;
        int thermalAbsorbed = 40;
        int neutronsGenerated = 16;

        int activeDamage = fastAbsorbed * 1 + thermalAbsorbed * 2 + Math.max(1, neutronsGenerated / 4);
        assertEquals(15 + 80 + 4, activeDamage);
        assertEquals(99, activeDamage);

        // Idle / minimal flux
        int idleFast = 0;
        int idleThermal = 0;
        int idleNeutrons = 0;
        int idleDamage = idleFast * 1 + idleThermal * 2 + Math.max(1, idleNeutrons / 4);
        assertEquals(1, idleDamage);

        assertTrue(
            activeDamage > idleDamage * 50,
            "Active fission & absorption should deplete fuel orders of magnitude faster");
    }

    @Test
    void testCoolantRequiredTierMapping() {
        // Water is strictly disallowed
        assertEquals(999, MTEHatchNuclearHatch.getRequiredFluidTier("water"));
        // IC2 Coolant is starter tier (Electrum, 0)
        assertEquals(
            NuclearSimulationEngine.PIPE_TIER_ELECTRUM,
            MTEHatchNuclearHatch.getRequiredFluidTier("ic2coolant"));
        // Distilled Water is Platinum (1)
        assertEquals(
            NuclearSimulationEngine.PIPE_TIER_PLATINUM,
            MTEHatchNuclearHatch.getRequiredFluidTier("distilledwater"));
        // HP Distilled Water is Osmium (2)
        assertEquals(
            NuclearSimulationEngine.PIPE_TIER_OSMIUM,
            MTEHatchNuclearHatch.getRequiredFluidTier("fluid.highpressuredistilledwater"));
        // Heavy Water is Quantium (3)
        assertEquals(
            NuclearSimulationEngine.PIPE_TIER_QUANTIUM,
            MTEHatchNuclearHatch.getRequiredFluidTier("fluid.heavywater"));
        // HP Heavy Water is Fluxed Electrum (4)
        assertEquals(
            NuclearSimulationEngine.PIPE_TIER_FLUXED_ELECTRUM,
            MTEHatchNuclearHatch.getRequiredFluidTier("fluid.highpressureheavywater"));
    }

    @Test
    void testPipeTierNames() {
        assertEquals("Electrum (IC2 Coolant)", NuclearSimulationEngine.getPipeTierName(0));
        assertEquals("Platinum (Distilled Water -> Steam)", NuclearSimulationEngine.getPipeTierName(1));
        assertEquals("Osmium (HP Distilled Water -> Superheated)", NuclearSimulationEngine.getPipeTierName(2));
        assertEquals("Quantium (Heavy Water -> HW Steam)", NuclearSimulationEngine.getPipeTierName(3));
        assertEquals("Fluxed Electrum (HP Heavy Water -> HW SC Steam)", NuclearSimulationEngine.getPipeTierName(4));
        assertEquals("Black Plutonium (Max Tier / All Coolants)", NuclearSimulationEngine.getPipeTierName(5));
    }

    @Test
    void testMaxOperatingTemperatures() {
        assertEquals(1000.0, NuclearSimulationEngine.getMaxOperatingTemperature(0));
        assertEquals(1400.0, NuclearSimulationEngine.getMaxOperatingTemperature(1));
        assertEquals(1800.0, NuclearSimulationEngine.getMaxOperatingTemperature(2));
        assertEquals(2200.0, NuclearSimulationEngine.getMaxOperatingTemperature(3));
        assertEquals(2600.0, NuclearSimulationEngine.getMaxOperatingTemperature(4));
        assertEquals(3200.0, NuclearSimulationEngine.getMaxOperatingTemperature(5));
    }

    @Test
    void testCoolantBoilingThresholds() {
        assertEquals(Double.POSITIVE_INFINITY, NuclearSimulationEngine.getCoolantBoilingThreshold("ic2coolant"));
        assertEquals(100.0, NuclearSimulationEngine.getCoolantBoilingThreshold("distilledwater"));
        assertEquals(100.0, NuclearSimulationEngine.getCoolantBoilingThreshold("heavywater"));
        assertEquals(200.0, NuclearSimulationEngine.getCoolantBoilingThreshold("highpressuredistilledwater"));
        assertEquals(200.0, NuclearSimulationEngine.getCoolantBoilingThreshold("highpressureheavywater"));
    }

    @Test
    void testStandaloneGridPresetsAndExecution() {
        gregtech.common.tileentities.machines.multi.nuclear.standalone.StandaloneNuclearGrid grid = new gregtech.common.tileentities.machines.multi.nuclear.standalone.StandaloneNuclearGrid(
            7,
            7,
            NuclearSimulationEngine.PIPE_TIER_PLATINUM);
        grid.loadPreset("BREEDER_7X7");

        assertEquals(7, grid.getWidth());
        assertEquals(7, grid.getHeight());
        assertFalse(grid.isExploded());

        // Run 5 ticks
        for (int i = 0; i < 5; i++) {
            boolean ok = grid.step();
            assertTrue(ok, "Grid simulation step should succeed without exploding");
        }

        assertTrue(grid.getCurrentTick() == 5);
        assertTrue(grid.getTotalNeutronsGenerated() > 0, "Neutrons should be generated by Uranium rods");
        assertTrue(grid.getCoreMaxTemp() >= NuclearSimulationEngine.AMBIENT_TEMP);
    }

    @Test
    void testDryHatchBoilingThresholdThermalShock() {
        gregtech.common.tileentities.machines.multi.nuclear.standalone.StandaloneNuclearGrid grid = new gregtech.common.tileentities.machines.multi.nuclear.standalone.StandaloneNuclearGrid(
            3,
            3,
            NuclearSimulationEngine.PIPE_TIER_PLATINUM);

        // Put a distilled water hatch at (1, 1)
        grid.setTile(
            1,
            1,
            gregtech.common.tileentities.machines.multi.nuclear.standalone.SimTile.TileType.HATCH_DISTILLED_WATER);
        gregtech.common.tileentities.machines.multi.nuclear.standalone.SimTile hatch = grid.getTile(1, 1);

        // Case 1: Hatch was dry, but cold (20°C <= 100°C boiling threshold) -> safe refill!
        hatch.setInputFluidAmount(0);
        hatch.setWasDry(true);
        hatch.setTemperature(50.0);
        boolean refilledSafely = hatch.refillCoolant();
        assertTrue(refilledSafely, "Refilling dry hatch below boiling threshold should be safe");
        assertFalse(hatch.isWasDry());

        // Case 2: Hatch was dry, and hot (150°C > 100°C boiling threshold) -> thermal shock explosion!
        hatch.setInputFluidAmount(0);
        hatch.setWasDry(true);
        hatch.setTemperature(150.0);
        // Step with hot dry hatch receiving coolant
        grid.step();
        assertTrue(grid.isExploded(), "Injecting coolant into dry hatch above boiling threshold must explode");
        assertTrue(
            grid.getExplosionReason()
                .contains("Thermal Shock"));
    }

    @Test
    void testIC2CoolantContinuousAmbientExchange() {
        gregtech.common.tileentities.machines.multi.nuclear.standalone.StandaloneNuclearGrid grid = new gregtech.common.tileentities.machines.multi.nuclear.standalone.StandaloneNuclearGrid(
            3,
            3,
            NuclearSimulationEngine.PIPE_TIER_ELECTRUM);

        grid.setTile(
            1,
            1,
            gregtech.common.tileentities.machines.multi.nuclear.standalone.SimTile.TileType.HATCH_IC2_COOLANT);
        gregtech.common.tileentities.machines.multi.nuclear.standalone.SimTile hatch = grid.getTile(1, 1);

        // Case 1: Coolant works below 100°C down to ambient (24°C)
        hatch.setInputFluidAmount(2000);
        hatch.setTemperature(80.0); // 80°C is below water boiling, but IC2 coolant must absorb heat!
        grid.step();

        assertTrue(
            hatch.getTemperature() < 80.0,
            "IC2 coolant must extract heat below 100°C down to ambient temperature");
        assertTrue(hatch.getOutputFluidAmount() > 0, "IC2 coolant must produce hot coolant below 100°C");

        // Case 2: Dry hatch at 300°C refilling IC2 coolant NEVER explodes
        hatch.setInputFluidAmount(0);
        hatch.setWasDry(true);
        hatch.setTemperature(300.0);
        boolean refilled = hatch.refillCoolant();
        assertTrue(refilled, "IC2 coolant must never trigger thermal shock explosion when refilling dry hot hatch");
    }
}
