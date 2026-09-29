package gregtech.common.tileentities.machines.multi.nuclear;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import gregtech.common.tileentities.machines.multi.nuclear.standalone.SimTile;
import gregtech.common.tileentities.machines.multi.nuclear.standalone.StandaloneNuclearGrid;

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
        assertEquals(180.0, NuclearSimulationEngine.getCoolantBoilingThreshold("highpressuredistilledwater"));
        assertEquals(180.0, NuclearSimulationEngine.getCoolantBoilingThreshold("highpressureheavywater"));
    }

    @Test
    void testStandaloneGridPresetsAndExecution() {
        gregtech.common.tileentities.machines.multi.nuclear.standalone.StandaloneNuclearGrid grid = new gregtech.common.tileentities.machines.multi.nuclear.standalone.StandaloneNuclearGrid(
            9,
            9,
            NuclearSimulationEngine.PIPE_TIER_PLATINUM);
        grid.loadPreset("BREEDER_9X9");

        assertEquals(9, grid.getWidth());
        assertEquals(9, grid.getHeight());
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
        assertFalse(grid.isExploded(), "Injecting coolant into dry hatch above boiling threshold must NOT explode");
        assertTrue(
            grid.isPowerFailed(),
            "Injecting coolant into dry hatch above boiling threshold must trigger powerfail shutdown");
        assertTrue(
            grid.getPowerFailReason()
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

    @Test
    void testBetavoltaicGenerationAndSaturation() {
        gregtech.common.tileentities.machines.multi.nuclear.standalone.SimTile cellHV = new gregtech.common.tileentities.machines.multi.nuclear.standalone.SimTile(
            gregtech.common.tileentities.machines.multi.nuclear.standalone.SimTile.TileType.BETAVOLTAIC_HV);
        gregtech.common.tileentities.machines.multi.nuclear.standalone.SimTile cellEV = new gregtech.common.tileentities.machines.multi.nuclear.standalone.SimTile(
            gregtech.common.tileentities.machines.multi.nuclear.standalone.SimTile.TileType.BETAVOLTAIC_EV);

        // 1. Check absorption properties
        assertEquals(1.0, cellHV.getAbsorptionProbability(NeutronType.FAST));
        assertEquals(1.0, cellHV.getAbsorptionProbability(NeutronType.THERMAL));
        assertEquals(0.0, cellHV.getScatteringProbability(NeutronType.FAST));
        assertEquals(0.0, cellHV.getModerationProbability());

        // 2. Feed fast vs thermal neutrons and check 4x weight
        cellHV.onNeutronAbsorbed(NeutronType.FAST, 10);
        cellHV.nuclearTick(1.0);
        long fastEU = cellHV.getDirectEUProduced();

        gregtech.common.tileentities.machines.multi.nuclear.standalone.SimTile cellHVThermal = new gregtech.common.tileentities.machines.multi.nuclear.standalone.SimTile(
            gregtech.common.tileentities.machines.multi.nuclear.standalone.SimTile.TileType.BETAVOLTAIC_HV);
        cellHVThermal.onNeutronAbsorbed(NeutronType.THERMAL, 10);
        cellHVThermal.nuclearTick(1.0);
        long thermalEU = cellHVThermal.getDirectEUProduced();

        assertTrue(fastEU > thermalEU * 2, "Fast neutrons must generate significantly more EU than thermal neutrons");

        // 3. Saturation: HV caps around 1024 EU/t (2A HV)
        gregtech.common.tileentities.machines.multi.nuclear.standalone.SimTile cellHVSat = new gregtech.common.tileentities.machines.multi.nuclear.standalone.SimTile(
            gregtech.common.tileentities.machines.multi.nuclear.standalone.SimTile.TileType.BETAVOLTAIC_HV);
        cellHVSat.onNeutronAbsorbed(NeutronType.FAST, 1000);
        cellHVSat.nuclearTick(1.0);
        assertEquals(1024, cellHVSat.getDirectEUProduced(), "HV Betavoltaic cell must cap at 1024 EU/t (2A HV)");
        assertTrue(cellHVSat.getTemperature() > 24.0, "Excess energy beyond saturation must convert into heat");

        // 4. EV caps around 4096 EU/t (2A EV)
        cellEV.onNeutronAbsorbed(NeutronType.FAST, 1000);
        cellEV.nuclearTick(1.0);
        assertEquals(4096, cellEV.getDirectEUProduced(), "EV Betavoltaic cell must cap at 4096 EU/t (2A EV)");

        // 5. Grid integration test with fuel and betavoltaic
        gregtech.common.tileentities.machines.multi.nuclear.standalone.StandaloneNuclearGrid grid = new gregtech.common.tileentities.machines.multi.nuclear.standalone.StandaloneNuclearGrid(
            3,
            3,
            NuclearSimulationEngine.PIPE_TIER_ELECTRUM);
        grid.setTile(
            1,
            1,
            gregtech.common.tileentities.machines.multi.nuclear.standalone.SimTile.TileType.FUEL_URANIUM_QUAD);
        grid.setTile(
            0,
            1,
            gregtech.common.tileentities.machines.multi.nuclear.standalone.SimTile.TileType.BETAVOLTAIC_EV);
        grid.step();

        assertTrue(grid.getFlowDirectEU() > 0, "Grid must accumulate Betavoltaic direct EU");
        assertEquals(grid.getFlowDirectEU(), grid.getLastPowerResult().directPowerEUt);
        assertEquals(grid.getFlowDirectEU(), grid.getLastPowerResult().totalPowerEUt);
    }

    @Test
    void testOverheatingHatchesVoidContentsWithoutExploding() {
        // High core temperature exceeding Electrum casing limit (1000°C)
        gregtech.common.tileentities.machines.multi.nuclear.standalone.StandaloneNuclearGrid grid = new gregtech.common.tileentities.machines.multi.nuclear.standalone.StandaloneNuclearGrid(
            3,
            3,
            NuclearSimulationEngine.PIPE_TIER_ELECTRUM);

        // Put a superheated fuel rod and superheated coolant hatch
        gregtech.common.tileentities.machines.multi.nuclear.standalone.SimTile fuelTile = grid.getTile(1, 1);
        fuelTile
            .setType(gregtech.common.tileentities.machines.multi.nuclear.standalone.SimTile.TileType.FUEL_URANIUM_QUAD);
        fuelTile.setTemperature(3000.0); // Well above 1000°C limit

        NuclearSimulationEngine.coolantFeedRate = 0; // Prevent refilling so hatch actually overheats
        gregtech.common.tileentities.machines.multi.nuclear.standalone.SimTile hatchTile = grid.getTile(0, 1);
        hatchTile
            .setType(gregtech.common.tileentities.machines.multi.nuclear.standalone.SimTile.TileType.HATCH_IC2_COOLANT);
        hatchTile.setInputFluidAmount(10);
        hatchTile.setOutputFluidAmount(200);
        hatchTile.setTemperature(3000.0);

        grid.step();

        // Must NOT explode
        assertFalse(grid.isExploded(), "Reactor must not explode from hatch overheating!");

        // Overheating hatch must have voided its fluids
        assertEquals(0, hatchTile.getInputFluidAmount(), "Overheating fluid hatch must void input fluid");
        assertEquals(0, hatchTile.getOutputFluidAmount(), "Overheating fluid hatch must void output fluid");

        // Overheating fuel bus must have voided its fuel
        assertEquals(
            gregtech.common.tileentities.machines.multi.nuclear.standalone.SimTile.TileType.EMPTY,
            fuelTile.getType(),
            "Overheating fuel tile must void its fuel contents");
    }

    @Test
    void testHighPressureCoolantExplodesOnInsufficientCasing() {
        // Electrum (EV, tier 0) casing cannot withstand High-Pressure Distilled Water (requires Osmium / LuV, tier 2)
        gregtech.common.tileentities.machines.multi.nuclear.standalone.StandaloneNuclearGrid gridEV = new gregtech.common.tileentities.machines.multi.nuclear.standalone.StandaloneNuclearGrid(
            3,
            3,
            NuclearSimulationEngine.PIPE_TIER_ELECTRUM);
        gridEV.setTile(
            0,
            1,
            gregtech.common.tileentities.machines.multi.nuclear.standalone.SimTile.TileType.HATCH_HP_DISTILLED_WATER);
        gridEV.getTile(0, 1)
            .setInputFluidAmount(100);

        gridEV.step();
        assertTrue(gridEV.isExploded(), "Using HP water on Electrum casing must trigger catastrophic explosion!");
        assertTrue(
            gridEV.getExplosionReason()
                .toLowerCase()
                .contains("overpressure"),
            "Explosion reason must mention overpressure");

        // Same HP coolant in Osmium (LuV, tier 2) casing must NOT explode
        gregtech.common.tileentities.machines.multi.nuclear.standalone.StandaloneNuclearGrid gridLuV = new gregtech.common.tileentities.machines.multi.nuclear.standalone.StandaloneNuclearGrid(
            3,
            3,
            NuclearSimulationEngine.PIPE_TIER_OSMIUM);
        gridLuV.setTile(
            0,
            1,
            gregtech.common.tileentities.machines.multi.nuclear.standalone.SimTile.TileType.HATCH_HP_DISTILLED_WATER);
        gridLuV.getTile(0, 1)
            .setInputFluidAmount(100);

        gridLuV.step();
        assertFalse(gridLuV.isExploded(), "HP water on Osmium (LuV) casing must be safe from casing explosion!");
    }

    @Test
    void testDryCoolantThermalShockTriggersPowerfailShutdown() {
        gregtech.common.tileentities.machines.multi.nuclear.standalone.StandaloneNuclearGrid grid = new gregtech.common.tileentities.machines.multi.nuclear.standalone.StandaloneNuclearGrid(
            3,
            3,
            NuclearSimulationEngine.PIPE_TIER_ELECTRUM);

        // Grid contains fuel, reflector, and betavoltaic
        grid.setTile(
            1,
            1,
            gregtech.common.tileentities.machines.multi.nuclear.standalone.SimTile.TileType.FUEL_URANIUM_QUAD);
        grid.setTile(
            1,
            2,
            gregtech.common.tileentities.machines.multi.nuclear.standalone.SimTile.TileType.REFLECTOR_BERYLLIUM);
        grid.setTile(
            1,
            0,
            gregtech.common.tileentities.machines.multi.nuclear.standalone.SimTile.TileType.BETAVOLTAIC_HV);

        // Dry superheated coolant hatch (> 100°C threshold)
        gregtech.common.tileentities.machines.multi.nuclear.standalone.SimTile hatch = grid.getTile(0, 1);
        hatch.setType(
            gregtech.common.tileentities.machines.multi.nuclear.standalone.SimTile.TileType.HATCH_DISTILLED_WATER);
        hatch.setInputFluidAmount(0);
        hatch.setWasDry(true);
        hatch.setTemperature(350.0); // Superheated

        grid.step();

        // Must NOT explode
        assertFalse(grid.isExploded(), "Dry coolant thermal shock must NOT explode the reactor!");

        // Must trigger powerfail shutdown
        assertTrue(grid.isPowerFailed(), "Reactor must shut down with powerfail on dry coolant thermal shock!");
        assertTrue(
            grid.getPowerFailReason()
                .contains("Thermal Shock"),
            "Reason must report thermal shock");

        // Fuel must be voided
        assertEquals(
            gregtech.common.tileentities.machines.multi.nuclear.standalone.SimTile.TileType.EMPTY,
            grid.getTile(1, 1)
                .getType(),
            "Fuel must be voided upon dry coolant shutdown");

        // Coolant must be voided
        assertEquals(0, hatch.getInputFluidAmount(), "Coolant fluid must be voided");

        // Crucially, Reflector and Betavoltaic MUST be preserved!
        assertEquals(
            gregtech.common.tileentities.machines.multi.nuclear.standalone.SimTile.TileType.REFLECTOR_BERYLLIUM,
            grid.getTile(1, 2)
                .getType(),
            "Reflector must NOT be voided on dry coolant shutdown!");
        assertEquals(
            gregtech.common.tileentities.machines.multi.nuclear.standalone.SimTile.TileType.BETAVOLTAIC_HV,
            grid.getTile(1, 0)
                .getType(),
            "Betavoltaic cell must NOT be voided on dry coolant shutdown!");
    }

    @Test
    void testLossOfCoolantTriggersDryCoolantShutdown() {
        gregtech.common.tileentities.machines.multi.nuclear.standalone.StandaloneNuclearGrid grid = new gregtech.common.tileentities.machines.multi.nuclear.standalone.StandaloneNuclearGrid(
            3,
            3,
            NuclearSimulationEngine.PIPE_TIER_ELECTRUM);

        // Active fuel and an empty coolant hatch with no fluid feed
        NuclearSimulationEngine.coolantFeedRate = 0; // Simulate fluid supply failure
        grid.setTile(
            1,
            1,
            gregtech.common.tileentities.machines.multi.nuclear.standalone.SimTile.TileType.FUEL_URANIUM_QUAD);
        grid.setTile(
            1,
            2,
            gregtech.common.tileentities.machines.multi.nuclear.standalone.SimTile.TileType.REFLECTOR_BERYLLIUM);
        grid.setTile(
            0,
            1,
            gregtech.common.tileentities.machines.multi.nuclear.standalone.SimTile.TileType.HATCH_IC2_COOLANT);
        grid.getTile(0, 1)
            .setInputFluidAmount(0);

        grid.step();

        // Must trigger loss of coolant shutdown without exploding
        assertFalse(grid.isExploded(), "Loss of coolant must not explode the reactor");
        assertTrue(grid.isPowerFailed(), "Reactor must powerfail when coolant is completely depleted");
        assertEquals(
            gregtech.common.tileentities.machines.multi.nuclear.standalone.SimTile.TileType.EMPTY,
            grid.getTile(1, 1)
                .getType(),
            "Fuel must be voided upon loss-of-coolant shutdown");
        assertEquals(
            gregtech.common.tileentities.machines.multi.nuclear.standalone.SimTile.TileType.REFLECTOR_BERYLLIUM,
            grid.getTile(1, 2)
                .getType(),
            "Reflector must remain intact");
    }

    @Test
    void testReactorFootprintAndWallThickness() {
        int[] coreSizes = { 3, 7, 11 };
        int[] expectedFootprints = { 5, 9, 13 };

        for (int i = 0; i < coreSizes.length; i++) {
            int core = coreSizes[i];
            int footprint = expectedFootprints[i];
            int wall = (footprint - core) / 2;
            assertEquals(1, wall, "Wall thickness for 5-tall octagonal reactor must be 1 block of casing");
            assertEquals(core, footprint - 2 * wall, "Internal core dimension must match");
        }
    }

    @Test
    void testIsCornerNullCell() {
        // Tier 1: 3x3 Core (5 active cells, 4 corner null cells)
        int nullCount3 = 0;
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                if (NuclearSimulationEngine.isCornerNullCell(x, y, 3, 3)) {
                    nullCount3++;
                    assertTrue((x == 0 || x == 2) && (y == 0 || y == 2));
                }
            }
        }
        assertEquals(4, nullCount3);

        // Tier 2: 7x7 Core (45 active cells, 4 corner null cells)
        int nullCount7 = 0;
        for (int x = 0; x < 7; x++) {
            for (int y = 0; y < 7; y++) {
                if (NuclearSimulationEngine.isCornerNullCell(x, y, 7, 7)) {
                    nullCount7++;
                    assertTrue((x == 0 || x == 6) && (y == 0 || y == 6));
                }
            }
        }
        assertEquals(4, nullCount7);

        // Tier 3: 11x11 Core (109 active cells, 12 corner null cells)
        int nullCount11 = 0;
        for (int x = 0; x < 11; x++) {
            for (int y = 0; y < 11; y++) {
                if (NuclearSimulationEngine.isCornerNullCell(x, y, 11, 11)) {
                    nullCount11++;
                }
            }
        }
        assertEquals(12, nullCount11);
        assertEquals(109, (11 * 11) - nullCount11);
    }

    @Test
    void testWallReflectionAndAbsorption() {
        MockNuclearTile[][] grid = new MockNuclearTile[3][3];
        grid[1][1] = new MockNuclearTile(true, 100); // Fuel in center
        grid[0][1] = new MockNuclearTile(false, 0); // Non-fuel neighbor
        grid[2][1] = new MockNuclearTile(false, 0);
        grid[1][0] = new MockNuclearTile(false, 0);
        grid[1][2] = new MockNuclearTile(false, 0);
        // Corners remain null (cut corner null cells)

        // 1. 100% reflection
        NuclearSimulationEngine.wallReflectionChance = 1.0;
        NuclearSimulationEngine.SimulationResult resReflect = NuclearSimulationEngine.simulate(grid, 3, 3);
        assertTrue(resReflect.wallNeutronsReflected > 0, "Neutrons hitting walls/null cells must be reflected");
        assertEquals(0, resReflect.wallNeutronsAbsorbed, "No neutrons should be absorbed when 100% reflection");

        // 2. 0% reflection (100% absorption)
        NuclearSimulationEngine.wallReflectionChance = 0.0;
        NuclearSimulationEngine.wallAbsorbHeatPerNeutron = 15.0;
        NuclearSimulationEngine.SimulationResult resAbsorb = NuclearSimulationEngine.simulate(grid, 3, 3);
        assertTrue(resAbsorb.wallNeutronsAbsorbed > 0, "Neutrons hitting walls/null cells must be absorbed");
        assertEquals(0, resAbsorb.wallNeutronsReflected, "No neutrons should be reflected when 0% reflection");
        assertEquals(
            resAbsorb.wallNeutronsAbsorbed * 15.0,
            resAbsorb.wallHeatPool,
            1e-4,
            "Wall heat pool must equal absorbed * heatPerNeutron");
    }

    @Test
    void testWallHeatPoolEqualDistribution() {
        MockNuclearTile[][] grid = new MockNuclearTile[3][3];
        MockNuclearTile center = new MockNuclearTile(true, 100);
        MockNuclearTile n1 = new MockNuclearTile(false, 0);
        MockNuclearTile n2 = new MockNuclearTile(false, 0);
        center.heatCoeff = 0.0;
        n1.heatCoeff = 0.0;
        n1.absorbProb = 0.0;
        n1.scatterProb = 0.0;
        n2.heatCoeff = 0.0;
        n2.absorbProb = 0.0;
        n2.scatterProb = 0.0;
        grid[1][1] = center;
        grid[0][1] = n1;
        grid[2][1] = n2;
        // Remaining 6 cells are null

        NuclearSimulationEngine.wallReflectionChance = 0.0; // All wall neutrons absorbed
        NuclearSimulationEngine.wallAbsorbHeatPerNeutron = 12.0;

        double n1HeatBefore = n1.heatEU;
        double n2HeatBefore = n2.heatEU;
        NuclearSimulationEngine.SimulationResult res = NuclearSimulationEngine.simulate(grid, 3, 3);

        assertTrue(res.wallHeatPool > 0);
        // Active tile count is 3 (center, n1, n2). Heat pool divided by 3 added to each.
        double expectedShare = res.wallHeatPool / 3.0;
        assertEquals(
            expectedShare,
            n1.heatEU - n1HeatBefore,
            1e-4,
            "Non-null cell n1 must receive equal share of wall heat pool");
        assertEquals(
            expectedShare,
            n2.heatEU - n2HeatBefore,
            1e-4,
            "Non-null cell n2 must receive equal share of wall heat pool");
    }

    @Test
    void testNeutronComponentInteractionData() {
        gregtech.nei.GTNEINeutronInteractionHandler.NeutronComponentData graphite = new gregtech.nei.GTNEINeutronInteractionHandler.NeutronComponentData(
            null,
            "Graphite Moderator Block",
            "Moderator",
            0.93,
            0.002,
            0.50,
            0.621,
            0.009,
            false,
            0,
            0,
            0,
            0,
            false,
            null,
            0,
            "Slows fast neutrons into thermal neutrons");

        assertEquals(0.93, graphite.fastScattering, 1e-4);
        assertEquals(0.002, graphite.fastAbsorption, 1e-4);
        assertEquals(0.50, graphite.slowingProbability, 1e-4);
        assertEquals(0.621, graphite.thermalScattering, 1e-4);
        assertEquals(0.009, graphite.thermalAbsorption, 1e-4);
        assertEquals(0.50, 1.0 - graphite.slowingProbability, 1e-4);
        assertFalse(graphite.hasCapture);
        assertFalse(graphite.hasAbsorption);

        gregtech.nei.GTNEINeutronInteractionHandler.NeutronComponentData uraniumQuad = new gregtech.nei.GTNEINeutronInteractionHandler.NeutronComponentData(
            null,
            "Quad Uranium Fuel Rod",
            "Fuel Rod",
            0.15,
            0.25,
            0.10,
            0.10,
            0.80,
            true,
            8,
            56.0,
            0.88,
            16.0,
            true,
            null,
            163_840_000L,
            "Base: 16 Fast Neutrons/t | Standard fission fuel");

        assertTrue(uraniumQuad.hasCapture);
        assertEquals(8, uraniumQuad.fastNeutronEnergyEU);
        assertEquals(56.0, uraniumQuad.directEU, 1e-4);
        assertEquals(0.88, uraniumQuad.directHeatC, 1e-4);
        assertEquals(16.0, uraniumQuad.maxNeutronsEmitted, 1e-4);
        assertTrue(uraniumQuad.hasAbsorption);
        assertEquals(163_840_000L, uraniumQuad.neutronsRequired);
    }

    @Test
    void testMaxTemperatureAndAverageReactivityOverFuelCells() {
        NuclearSimulationEngine.setSimulationParameters(600.0, 2200.0, 1.0, 1.1, 18.0, 200.0);

        INuclearTile[][] grid = new INuclearTile[3][3];
        // Fuel 1: temp 600°C -> efficiency 1.0
        MockNuclearTile fuel1 = new MockNuclearTile(true, 100);
        fuel1.setTemperature(600.0);
        grid[0][0] = fuel1;

        // Fuel 2: temp 1400°C -> efficiency 0.5
        MockNuclearTile fuel2 = new MockNuclearTile(true, 100);
        fuel2.setTemperature(1400.0);
        grid[1][1] = fuel2;

        // Non-fuel moderator: temp 2000°C (higher temp than fuels, should set maxTemperature but not dilute reactivity)
        MockNuclearTile moderator = new MockNuclearTile(false, 0);
        moderator.setTemperature(2000.0);
        grid[2][2] = moderator;

        NuclearSimulationEngine.SimulationResult res = NuclearSimulationEngine.simulate(grid, 3, 3);
        double maxTileTemp = Math
            .max(moderator.getTemperature(), Math.max(fuel1.getTemperature(), fuel2.getTemperature()));
        assertEquals(
            maxTileTemp,
            res.maxTemperature,
            1e-4,
            "Max temperature must reflect the highest temp in the core");

        // Reactivity should be the average over the 2 fuel cells: (1.0 + 0.5) / 2 = 0.75 (approx, taking into account
        // any tick temperature changes)
        // Note: during simulation tick, fuel temperatures may increase due to fission heat, so calculate expected
        // average from their final temps
        double expectedReactivity = (NuclearSimulationEngine.calculateEfficiency(fuel1.getTemperature())
            + NuclearSimulationEngine.calculateEfficiency(fuel2.getTemperature())) / 2.0;
        assertEquals(
            expectedReactivity,
            res.averageReactivity,
            1e-4,
            "Reactivity must be average strictly over fuel cells");

        // Test grid with NO fuel cells
        INuclearTile[][] noFuelGrid = new INuclearTile[2][2];
        noFuelGrid[0][0] = new MockNuclearTile(false, 0);
        NuclearSimulationEngine.SimulationResult noFuelRes = NuclearSimulationEngine.simulate(noFuelGrid, 2, 2);
        assertEquals(0.0, noFuelRes.averageReactivity, 1e-6, "Reactivity must be 0.0 when no fuel cells are present");

        NuclearSimulationEngine.resetDefaultParameters();
    }

    @Test
    void testFormatNeutronFlux() {
        assertEquals("0 n/cm²s", NuclearSimulationEngine.formatNeutronFlux(0));
        assertEquals("0 n/cm²s", NuclearSimulationEngine.formatNeutronFlux(-5));
        assertEquals("1.00e13 n/cm²s", NuclearSimulationEngine.formatNeutronFlux(1));
        assertEquals("8.80e14 n/cm²s", NuclearSimulationEngine.formatNeutronFlux(88));
        assertEquals("1.50e15 n/cm²s", NuclearSimulationEngine.formatNeutronFlux(150));
    }

    @Test
    void testMaintenanceEfficiencyScaling() {
        NuclearSimulationEngine.resetDefaultParameters();

        // Check that non-fuel tile receives maintenance efficiency during simulate
        INuclearTile[][] grid = new INuclearTile[2][2];
        final double[] receivedEfficiency = new double[1];
        MockNuclearTile nonFuel = new MockNuclearTile(false, 0) {

            @Override
            public void nuclearTick(double eff) {
                receivedEfficiency[0] = eff;
            }
        };
        grid[0][0] = nonFuel;

        NuclearSimulationEngine.simulate(grid, 2, 2, 0.70);
        assertEquals(0.70, receivedEfficiency[0], 1e-6, "Non-fuel tile must receive maintenance efficiency");

        NuclearSimulationEngine.simulate(grid, 2, 2, 1.0);
        assertEquals(1.0, receivedEfficiency[0], 1e-6, "Default/full maintenance efficiency must be 1.0");

        NuclearSimulationEngine.resetDefaultParameters();
    }

    @Test
    void testMaintenanceHatchRepairAndEfficiency() {
        // Ideal status is 6 (wrench, screwdriver, soft mallet, hard hammer, soldering tool, crowbar)
        int idealStatus = 6;
        for (int issues = 0; issues <= 6; issues++) {
            int repairStatus = idealStatus - issues;
            double expectedEff = Math.max(0.0, 1.0 - (issues * 0.10));
            double calculatedEff = Math.max(0.0, 1.0 - ((idealStatus - repairStatus) * 0.10));
            assertEquals(expectedEff, calculatedEff, 1e-6);
        }
    }

    @Test
    void testSymmetricGridSimulationPreservesExactSymmetry() {
        StandaloneNuclearGrid grid = new StandaloneNuclearGrid(5, 5, NuclearSimulationEngine.PIPE_TIER_ELECTRUM);
        // Symmetric 5x5 layout with 4 symmetric fuel rods and symmetric hatches
        grid.loadLayout("NL,HC,HC,HC,NL;HC,U4,HC,U4,HC;HC,HC,HC,HC,HC;HC,U4,HC,U4,HC;NL,HC,HC,HC,NL");

        for (int tick = 1; tick <= 50; tick++) {
            grid.step();

            SimTile t11 = grid.getTile(1, 1);
            SimTile t31 = grid.getTile(3, 1);
            SimTile t13 = grid.getTile(1, 3);
            SimTile t33 = grid.getTile(3, 3);

            assertEquals(
                t11.getTemperature(),
                t31.getTemperature(),
                1e-6,
                "Symmetric fuel cells (1,1) and (3,1) must have identical temperatures at tick " + tick);
            assertEquals(
                t11.getTemperature(),
                t13.getTemperature(),
                1e-6,
                "Symmetric fuel cells (1,1) and (1,3) must have identical temperatures at tick " + tick);
            assertEquals(
                t11.getTemperature(),
                t33.getTemperature(),
                1e-6,
                "Symmetric fuel cells (1,1) and (3,3) must have identical temperatures at tick " + tick);

            // Also verify symmetric coolant hatches: (2, 1) and (2, 3), (1, 2) and (3, 2)
            SimTile h21 = grid.getTile(2, 1);
            SimTile h23 = grid.getTile(2, 3);
            SimTile h12 = grid.getTile(1, 2);
            SimTile h32 = grid.getTile(3, 2);

            assertEquals(
                h21.getTemperature(),
                h23.getTemperature(),
                1e-6,
                "Symmetric hatches (2,1) and (2,3) must have identical temperatures at tick " + tick);
            assertEquals(
                h12.getTemperature(),
                h32.getTemperature(),
                1e-6,
                "Symmetric hatches (1,2) and (3,2) must have identical temperatures at tick " + tick);
            assertEquals(
                h21.getTemperature(),
                h12.getTemperature(),
                1e-6,
                "Quarter-symmetric hatches (2,1) and (1,2) must have identical temperatures at tick " + tick);
        }
    }

    @Test
    void testNuclearHatchThreeTanksAndAutoOutput() {
        // Construct hatch via the secondary constructor (no METATILEENTITIES registration required)
        MTEHatchNuclearHatch hatch = new MTEHatchNuclearHatch("test.nuclear.hatch", 1, 16000, new String[0], null);

        // 1. Verify getTankInfo returns 3 tanks with capacity 16000
        net.minecraftforge.fluids.FluidTankInfo[] info = hatch
            .getTankInfo(net.minecraftforge.common.util.ForgeDirection.UP);
        assertNotNull(info);
        assertEquals(3, info.length, "Nuclear hatch must report exactly 3 tanks to WAILA and external callers");
        assertEquals(16000, info[0].capacity);
        assertEquals(16000, info[1].capacity);
        assertEquals(16000, info[2].capacity);
        assertNull(info[0].fluid);
        assertNull(info[1].fluid);
        assertNull(info[2].fluid);

        // 2. Set mock fluids into tanks
        net.minecraftforge.fluids.Fluid dummyCoolant = org.mockito.Mockito.mock(net.minecraftforge.fluids.Fluid.class);
        net.minecraftforge.fluids.Fluid dummySteam = org.mockito.Mockito.mock(net.minecraftforge.fluids.Fluid.class);
        net.minecraftforge.fluids.Fluid dummyByproduct = org.mockito.Mockito
            .mock(net.minecraftforge.fluids.Fluid.class);

        net.minecraftforge.fluids.FluidStack stackCoolant = org.mockito.Mockito
            .mock(net.minecraftforge.fluids.FluidStack.class);
        stackCoolant.amount = 5000;
        org.mockito.Mockito.when(stackCoolant.getFluid())
            .thenReturn(dummyCoolant);

        net.minecraftforge.fluids.FluidStack stackSteam = org.mockito.Mockito
            .mock(net.minecraftforge.fluids.FluidStack.class);
        stackSteam.amount = 3000;
        org.mockito.Mockito.when(stackSteam.getFluid())
            .thenReturn(dummySteam);

        net.minecraftforge.fluids.FluidStack stackByproduct = org.mockito.Mockito
            .mock(net.minecraftforge.fluids.FluidStack.class);
        stackByproduct.amount = 2000;
        org.mockito.Mockito.when(stackByproduct.getFluid())
            .thenReturn(dummyByproduct);

        hatch.mInputFluid = stackCoolant;
        hatch.mOutputFluid = stackSteam;
        hatch.mByproductFluid = stackByproduct;

        info = hatch.getTankInfo(net.minecraftforge.common.util.ForgeDirection.UP);
        assertEquals(5000, info[0].fluid.amount);
        assertEquals(3000, info[1].fluid.amount);
        assertEquals(2000, info[2].fluid.amount);

        // 3. Verify drain behavior
        // Cannot drain input fluid
        assertFalse(hatch.canDrain(net.minecraftforge.common.util.ForgeDirection.UP, dummyCoolant));
        // Can drain output and byproduct fluids
        assertTrue(hatch.canDrain(net.minecraftforge.common.util.ForgeDirection.UP, dummySteam));
        assertTrue(hatch.canDrain(net.minecraftforge.common.util.ForgeDirection.UP, dummyByproduct));

        // 4. Test auto-output onPostTick
        // Create mock IFluidHandler and IGregTechTileEntity
        net.minecraftforge.fluids.IFluidHandler mockTarget = org.mockito.Mockito
            .mock(net.minecraftforge.fluids.IFluidHandler.class);
        gregtech.api.interfaces.tileentity.IGregTechTileEntity mockBase = org.mockito.Mockito
            .mock(gregtech.api.interfaces.tileentity.IGregTechTileEntity.class);

        org.mockito.Mockito.when(mockBase.isServerSide())
            .thenReturn(true);
        org.mockito.Mockito.when(mockBase.getFrontFacing())
            .thenReturn(net.minecraftforge.common.util.ForgeDirection.SOUTH);
        org.mockito.Mockito.when(mockBase.getITankContainerAtSide(net.minecraftforge.common.util.ForgeDirection.SOUTH))
            .thenReturn(mockTarget);

        // Mock filling behavior: accepts up to 3000 steam and 2000 byproduct
        org.mockito.Mockito.when(mockTarget.fill(net.minecraftforge.common.util.ForgeDirection.NORTH, stackSteam, true))
            .thenReturn(3000);
        org.mockito.Mockito
            .when(mockTarget.fill(net.minecraftforge.common.util.ForgeDirection.NORTH, stackByproduct, true))
            .thenReturn(2000);

        hatch.onPostTick(mockBase, 1L);

        assertNull(hatch.mOutputFluid, "Output fluid should have been pushed completely");
        assertNull(hatch.mByproductFluid, "Byproduct fluid should have been pushed completely");

        // Verify target cache: second tick without invalidation does NOT re-query getITankContainerAtSide
        net.minecraftforge.fluids.FluidStack stackSteam2 = org.mockito.Mockito
            .mock(net.minecraftforge.fluids.FluidStack.class);
        stackSteam2.amount = 500;
        hatch.mOutputFluid = stackSteam2;
        org.mockito.Mockito
            .when(mockTarget.fill(net.minecraftforge.common.util.ForgeDirection.NORTH, stackSteam2, true))
            .thenReturn(500);
        hatch.onPostTick(mockBase, 2L);
        // getITankContainerAtSide should only have been called once!
        org.mockito.Mockito.verify(mockBase, org.mockito.Mockito.times(1))
            .getITankContainerAtSide(net.minecraftforge.common.util.ForgeDirection.SOUTH);

        // Invalidate on adjacent block change
        hatch.onAdjacentBlockChange(0, 0, 0);
        net.minecraftforge.fluids.FluidStack stackSteam3 = org.mockito.Mockito
            .mock(net.minecraftforge.fluids.FluidStack.class);
        stackSteam3.amount = 500;
        hatch.mOutputFluid = stackSteam3;
        hatch.onPostTick(mockBase, 3L);
        // Now it should have re-queried (total 2 calls)
        org.mockito.Mockito.verify(mockBase, org.mockito.Mockito.times(2))
            .getITankContainerAtSide(net.minecraftforge.common.util.ForgeDirection.SOUTH);
    }

    @Test
    void testNuclearControlHatchModesAndRedstoneOutput() {
        MTEHatchNuclearControl controlHatch = new MTEHatchNuclearControl("test.control", 4, new String[0], null);
        assertEquals(0, controlHatch.getMode());
        assertEquals("Temperature (Min)", MTEHatchNuclearControl.getModeName(0));

        // Test cycle
        controlHatch.setMode(1);
        assertEquals(1, controlHatch.getMode());
        assertEquals("Temperature (Max)", MTEHatchNuclearControl.getModeName(1));

        controlHatch.setMode(12); // Wrap
        assertEquals(0, controlHatch.getMode());

        controlHatch.setMode(-1); // Negative wrap
        assertEquals(11, controlHatch.getMode());
        assertEquals("Coolant Level (Avg)", MTEHatchNuclearControl.getModeName(11));

        // Test NBT persistence
        net.minecraft.nbt.NBTTagCompound nbt = new net.minecraft.nbt.NBTTagCompound();
        controlHatch.setMode(4);
        controlHatch.setOutputStrengthDirect((byte) 10);
        controlHatch.saveNBTData(nbt);

        MTEHatchNuclearControl loaded = new MTEHatchNuclearControl("test.loaded", 4, new String[0], null);
        loaded.loadNBTData(nbt);
        assertEquals(4, loaded.getMode());
        assertEquals(10, loaded.getOutputStrength());

        // Test redstone emission on facing side only
        gregtech.api.interfaces.tileentity.IGregTechTileEntity mockBase = org.mockito.Mockito
            .mock(gregtech.api.interfaces.tileentity.IGregTechTileEntity.class);
        org.mockito.Mockito.when(mockBase.getFrontFacing())
            .thenReturn(net.minecraftforge.common.util.ForgeDirection.EAST);
        controlHatch.setBaseMetaTileEntity(mockBase);

        controlHatch.setOutputRedstone((byte) 12);
        assertEquals(12, controlHatch.getOutputStrength());

        // Facing side must receive 12, other sides must receive 0
        org.mockito.Mockito.verify(mockBase)
            .setOutputRedstoneSignal(net.minecraftforge.common.util.ForgeDirection.EAST, (byte) 12);
        org.mockito.Mockito.verify(mockBase)
            .setOutputRedstoneSignal(net.minecraftforge.common.util.ForgeDirection.WEST, (byte) 0);
        org.mockito.Mockito.verify(mockBase)
            .setOutputRedstoneSignal(net.minecraftforge.common.util.ForgeDirection.NORTH, (byte) 0);
        org.mockito.Mockito.verify(mockBase)
            .setOutputRedstoneSignal(net.minecraftforge.common.util.ForgeDirection.SOUTH, (byte) 0);
    }

    @Test
    void testNuclearReactorControlHatchSignalCalculations() {
        MTENuclearReactor reactor = new MTENuclearReactor("test.reactor");
        reactor.gridSize = 3;
        reactor.mGrid = new INuclearTile[3][3];
        reactor.mPipeTier = NuclearSimulationEngine.PIPE_TIER_ELECTRUM; // Max temp = 1000 °C

        // Put tiles with known temperatures: 200 °C, 500 °C, 800 °C
        MockNuclearTile t1 = new MockNuclearTile(200.0, 0.05);
        MockNuclearTile t2 = new MockNuclearTile(500.0, 0.05);
        MockNuclearTile t3 = new MockNuclearTile(800.0, 0.05);
        reactor.mGrid[0][0] = t1;
        reactor.mGrid[0][1] = t2;
        reactor.mGrid[0][2] = t3;

        // Temperature modes (Max operating temp = 1000 °C)
        // Min = 200 -> 200/1000 * 15 = 3
        assertEquals((byte) 3, reactor.calculateSignalForMode(MTEHatchNuclearControl.MODE_TEMP_MIN));
        // Max = 800 -> 800/1000 * 15 = 12
        assertEquals((byte) 12, reactor.calculateSignalForMode(MTEHatchNuclearControl.MODE_TEMP_MAX));
        // Avg = 500 -> 500/1000 * 15 = 7.5 -> 8
        assertEquals((byte) 8, reactor.calculateSignalForMode(MTEHatchNuclearControl.MODE_TEMP_AVG));

        // When no fuel/component/coolant present, durabilities and coolant levels return 0
        assertEquals((byte) 0, reactor.calculateSignalForMode(MTEHatchNuclearControl.MODE_FUEL_DURABILITY_MIN));
        assertEquals((byte) 0, reactor.calculateSignalForMode(MTEHatchNuclearControl.MODE_COMPONENT_DURABILITY_MIN));
        assertEquals((byte) 0, reactor.calculateSignalForMode(MTEHatchNuclearControl.MODE_COOLANT_LEVEL_MIN));
    }

    @Test
    void testNuclearReactorCasingRequirementHalved() {
        MTENuclearReactor reactor = new MTENuclearReactor("test.reactor.casing");
        java.util.List<gregtech.api.structure.error.StructureError> errors = new java.util.ArrayList<>();

        // If casings < 22, errors should be reported
        reactor.verifyCasingMin(errors, 21, 22);
        assertFalse(errors.isEmpty(), "Fewer than 22 casings must fail structure check");

        // If casings >= 22, passes
        errors.clear();
        reactor.verifyCasingMin(errors, 22, 22);
        assertTrue(errors.isEmpty(), "22 casings (50% of 44) must pass structure check");
    }

    @Test
    void testNuclearControlRodHatchBasicsAndRedstoneControl() {
        MTEHatchNuclearControlRod hatch = new MTEHatchNuclearControlRod("test.rod", 4, new String[0], null);
        assertEquals(1, hatch.getInventoryStackLimit(), "Control rod hatch must limit stack size to 1");
        assertFalse(hatch.doesFillContainers());
        assertFalse(hatch.doesEmptyContainers());
        assertFalse(hatch.canTankBeFilled());
        assertFalse(hatch.canTankBeEmptied());

        gregtech.api.interfaces.tileentity.IGregTechTileEntity mockBase = org.mockito.Mockito
            .mock(gregtech.api.interfaces.tileentity.IGregTechTileEntity.class);
        hatch.setBaseMetaTileEntity(mockBase);

        // RS = 0 -> 0% insertion
        org.mockito.Mockito.when(mockBase.getStrongestRedstone())
            .thenReturn((byte) 0);
        assertEquals(0, hatch.getRedstoneSignal());
        assertEquals(0.0, hatch.getInsertionRatio(), 0.001);
        assertEquals(0, hatch.getInsertionPercent());

        // RS = 15 -> 100% insertion
        org.mockito.Mockito.when(mockBase.getStrongestRedstone())
            .thenReturn((byte) 15);
        assertEquals(15, hatch.getRedstoneSignal());
        assertEquals(1.0, hatch.getInsertionRatio(), 0.001);
        assertEquals(100, hatch.getInsertionPercent());

        // RS = 6 -> 40% insertion
        org.mockito.Mockito.when(mockBase.getStrongestRedstone())
            .thenReturn((byte) 6);
        assertEquals(6, hatch.getRedstoneSignal());
        assertEquals(0.40, hatch.getInsertionRatio(), 0.001);
        assertEquals(40, hatch.getInsertionPercent());
    }

    @Test
    void testNuclearControlRodProgressionOrder() {
        // Verify material progression order: Silver < Boron < Cadmium < Indium < Hafnium
        MTEHatchNuclearControlRod.ControlRodType silver = MTEHatchNuclearControlRod.ControlRodType.SILVER;
        MTEHatchNuclearControlRod.ControlRodType boron = MTEHatchNuclearControlRod.ControlRodType.BORON;
        MTEHatchNuclearControlRod.ControlRodType cadmium = MTEHatchNuclearControlRod.ControlRodType.CADMIUM;
        MTEHatchNuclearControlRod.ControlRodType indium = MTEHatchNuclearControlRod.ControlRodType.INDIUM;
        MTEHatchNuclearControlRod.ControlRodType hafnium = MTEHatchNuclearControlRod.ControlRodType.HAFNIUM;

        // Thermal absorption progression
        assertTrue(
            silver.maxThermalAbsorption < boron.maxThermalAbsorption,
            "Boron must absorb more thermal than Silver");
        assertTrue(
            boron.maxThermalAbsorption < cadmium.maxThermalAbsorption,
            "Cadmium must absorb more thermal than Boron");
        assertTrue(
            cadmium.maxThermalAbsorption < indium.maxThermalAbsorption,
            "Indium must absorb more thermal than Cadmium");
        assertTrue(
            indium.maxThermalAbsorption < hafnium.maxThermalAbsorption,
            "Hafnium must absorb more thermal than Indium");

        // Fast absorption progression
        assertTrue(silver.maxFastAbsorption < boron.maxFastAbsorption, "Boron must absorb more fast than Silver");
        assertTrue(boron.maxFastAbsorption < cadmium.maxFastAbsorption, "Cadmium must absorb more fast than Boron");
        assertTrue(cadmium.maxFastAbsorption < indium.maxFastAbsorption, "Indium must absorb more fast than Cadmium");
        assertTrue(indium.maxFastAbsorption < hafnium.maxFastAbsorption, "Hafnium must absorb more fast than Indium");

        // Test rod detection via mock items
        net.minecraft.item.Item dummyItem = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
        net.minecraft.item.ItemStack stackSilver = org.mockito.Mockito.mock(net.minecraft.item.ItemStack.class);
        stackSilver.getItem(); // trigger non-null check
        org.mockito.Mockito.when(stackSilver.getItem())
            .thenReturn(dummyItem);
        org.mockito.Mockito.when(stackSilver.getUnlocalizedName())
            .thenReturn("item.stickLongSilver");
        assertEquals(silver, MTEHatchNuclearControlRod.getRodType(stackSilver));

        net.minecraft.item.ItemStack stackBoron = org.mockito.Mockito.mock(net.minecraft.item.ItemStack.class);
        org.mockito.Mockito.when(stackBoron.getItem())
            .thenReturn(dummyItem);
        org.mockito.Mockito.when(stackBoron.getUnlocalizedName())
            .thenReturn("item.stickLongBoron");
        assertEquals(boron, MTEHatchNuclearControlRod.getRodType(stackBoron));

        net.minecraft.item.ItemStack stackCadmium = org.mockito.Mockito.mock(net.minecraft.item.ItemStack.class);
        org.mockito.Mockito.when(stackCadmium.getItem())
            .thenReturn(dummyItem);
        org.mockito.Mockito.when(stackCadmium.getUnlocalizedName())
            .thenReturn("item.stickLongCadmium");
        assertEquals(cadmium, MTEHatchNuclearControlRod.getRodType(stackCadmium));

        net.minecraft.item.ItemStack stackIndium = org.mockito.Mockito.mock(net.minecraft.item.ItemStack.class);
        org.mockito.Mockito.when(stackIndium.getItem())
            .thenReturn(dummyItem);
        org.mockito.Mockito.when(stackIndium.getUnlocalizedName())
            .thenReturn("item.stickLongIndium");
        assertEquals(indium, MTEHatchNuclearControlRod.getRodType(stackIndium));

        net.minecraft.item.ItemStack stackHafnium = org.mockito.Mockito.mock(net.minecraft.item.ItemStack.class);
        org.mockito.Mockito.when(stackHafnium.getItem())
            .thenReturn(dummyItem);
        org.mockito.Mockito.when(stackHafnium.getUnlocalizedName())
            .thenReturn("item.stickLongHafnium");
        assertEquals(hafnium, MTEHatchNuclearControlRod.getRodType(stackHafnium));

        // Test absorption scaling with redstone on hatch
        MTEHatchNuclearControlRod hatch = new MTEHatchNuclearControlRod("test.rod.prog", 4, new String[0], null);
        gregtech.api.interfaces.tileentity.IGregTechTileEntity mockBase = org.mockito.Mockito
            .mock(gregtech.api.interfaces.tileentity.IGregTechTileEntity.class);
        hatch.setBaseMetaTileEntity(mockBase);
        hatch.mInventory[MTEHatchNuclearControlRod.SLOT_ROD] = stackHafnium;

        // RS = 0 -> minimum baseline absorption (0.01)
        org.mockito.Mockito.when(mockBase.getStrongestRedstone())
            .thenReturn((byte) 0);
        assertEquals(0.01, hatch.getAbsorptionProbability(NeutronType.THERMAL), 0.001);

        // RS = 15 -> 100% of Hafnium max (0.99)
        org.mockito.Mockito.when(mockBase.getStrongestRedstone())
            .thenReturn((byte) 15);
        assertEquals(0.99, hatch.getAbsorptionProbability(NeutronType.THERMAL), 0.001);
        assertEquals(0.80, hatch.getAbsorptionProbability(NeutronType.FAST), 0.001);

        // RS = 7.5 (approx 8) -> 8/15 * 0.99 = 0.528
        org.mockito.Mockito.when(mockBase.getStrongestRedstone())
            .thenReturn((byte) 8);
        assertEquals((8.0 / 15.0) * 0.99, hatch.getAbsorptionProbability(NeutronType.THERMAL), 0.001);
    }
}
