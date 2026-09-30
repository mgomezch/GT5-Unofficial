package gregtech.common.tileentities.machines.multi.nuclear.standalone;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import gregtech.common.tileentities.machines.multi.nuclear.INuclearTile;
import gregtech.common.tileentities.machines.multi.nuclear.NuclearSimulationEngine;

/**
 * Manages an N x M grid of nuclear tiles, casing constraints, safety checks,
 * and historical telemetry for standalone simulation.
 */
public class StandaloneNuclearGrid {

    private int width;
    private int height;
    private SimTile[][] grid;
    private int pipeTier = NuclearSimulationEngine.PIPE_TIER_ELECTRUM;

    // Simulation metrics
    private long currentTick = 0;
    private boolean exploded = false;
    private String explosionReason = "";
    private boolean powerFailed = false;
    private String powerFailReason = "";
    private double coreMaxTemp = NuclearSimulationEngine.AMBIENT_TEMP;
    private double coreAvgTemp = NuclearSimulationEngine.AMBIENT_TEMP;
    private double efficiency = 1.0;
    private int lastNeutronsProduced = 0;
    private int lastFastAbsorbed = 0;
    private int lastThermalAbsorbed = 0;
    private int lastEscapedNeutrons = 0;
    private int lastWallReflected = 0;
    private int lastWallAbsorbed = 0;
    private double lastWallHeatPool = 0;

    // Historical accumulators
    private long totalNeutronsGenerated = 0;
    private long totalSteamProduced = 0;
    private double totalEnergyEU = 0;
    private int totalDeuteriumProduced = 0;
    private int totalTritiumProduced = 0;

    // Turbine configuration
    private TurbineCalculator.TurbineMaterial turbineMaterial = TurbineCalculator.TurbineMaterial.HSS_E;
    private TurbineCalculator.TurbineSize turbineSize = TurbineCalculator.TurbineSize.LARGE;
    private TurbineCalculator.FittingMode turbineFitting = TurbineCalculator.FittingMode.TIGHT;

    // Instantaneous flows (L/t)
    private double flowRegularSteam = 0;
    private double flowSuperheatedSteam = 0;
    private double flowSupercriticalSteam = 0;
    private double flowHeavyWaterSteam = 0;
    private double flowHPHeavyWaterSteam = 0;
    private double flowHotCoolant = 0;
    private double flowDirectEU = 0;

    private TurbineCalculator.PowerEstimationResult lastPowerResult = new TurbineCalculator.PowerEstimationResult();

    public record TickTelemetry(long tick, double maxTemp, double avgTemp, double efficiency, int neutrons,
        double powerEUt, boolean safe) {}

    private final List<TickTelemetry> history = new ArrayList<>();

    public static class IntermediateStepSnapshot {

        public final long tick;
        public final String phase;
        public final double minTemp;
        public final int minX;
        public final int minY;
        public final String minTileCode;
        public final double maxTemp;
        public final double avgTemp;
        public final String details;

        public IntermediateStepSnapshot(long tick, String phase, double minTemp, int minX, int minY, String minTileCode,
            double maxTemp, double avgTemp, String details) {
            this.tick = tick;
            this.phase = phase;
            this.minTemp = minTemp;
            this.minX = minX;
            this.minY = minY;
            this.minTileCode = minTileCode;
            this.maxTemp = maxTemp;
            this.avgTemp = avgTemp;
            this.details = details;
        }

        @Override
        public String toString() {
            return String.format(
                java.util.Locale.US,
                "[Tick %4d | %-20s] Min: %8.2f°C at (%d,%d) [%-2s] | Max: %8.2f°C | Avg: %8.2f°C | %s",
                tick,
                phase,
                minTemp,
                minX,
                minY,
                minTileCode,
                maxTemp,
                avgTemp,
                details);
        }
    }

    private boolean diagnosticTraceEnabled = false;
    private int maxTraceSteps = 50;
    private final List<IntermediateStepSnapshot> stepTraceBuffer = new ArrayList<>();
    private boolean negativeTempDetected = false;
    private String negativeTempReason = "";

    public void enableDiagnosticTrace(int maxSteps) {
        this.diagnosticTraceEnabled = true;
        this.maxTraceSteps = Math.max(10, maxSteps);
        this.stepTraceBuffer.clear();
    }

    public boolean isDiagnosticTraceEnabled() {
        return diagnosticTraceEnabled;
    }

    public boolean isNegativeTempDetected() {
        return negativeTempDetected;
    }

    public String getNegativeTempReason() {
        return negativeTempReason;
    }

    public List<IntermediateStepSnapshot> getStepTrace() {
        return Collections.unmodifiableList(stepTraceBuffer);
    }

    public void recordTraceSnapshot(String phase, String details) {
        if (!diagnosticTraceEnabled) return;
        double minT = Double.POSITIVE_INFINITY;
        double maxT = Double.NEGATIVE_INFINITY;
        double sumT = 0;
        int minX = -1, minY = -1;
        String minCode = "";

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                SimTile t = grid[x][y];
                if (t != null) {
                    double temp = t.getTemperature();
                    sumT += temp;
                    if (temp < minT) {
                        minT = temp;
                        minX = x;
                        minY = y;
                        minCode = t.getType().code;
                    }
                    if (temp > maxT) {
                        maxT = temp;
                    }
                }
            }
        }
        double avgT = (width * height > 0) ? (sumT / (width * height)) : 0;
        if (stepTraceBuffer.size() >= maxTraceSteps) {
            stepTraceBuffer.remove(0);
        }
        stepTraceBuffer
            .add(new IntermediateStepSnapshot(currentTick, phase, minT, minX, minY, minCode, maxT, avgT, details));
    }

    public StandaloneNuclearGrid(int width, int height, int pipeTier) {
        this.width = width;
        this.height = height;
        this.pipeTier = pipeTier;
        this.grid = new SimTile[width][height];
        clearGrid();
    }

    public void clearGrid() {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (NuclearSimulationEngine.isCornerNullCell(x, y, width, height)) {
                    grid[x][y] = new SimTile(SimTile.TileType.NULL_WALL);
                } else {
                    grid[x][y] = new SimTile(SimTile.TileType.EMPTY);
                }
            }
        }
        resetMetrics();
    }

    public void resetMetrics() {
        this.currentTick = 0;
        this.exploded = false;
        this.explosionReason = "";
        this.coreMaxTemp = NuclearSimulationEngine.AMBIENT_TEMP;
        this.coreAvgTemp = NuclearSimulationEngine.AMBIENT_TEMP;
        this.efficiency = 1.0;
        this.lastNeutronsProduced = 0;
        this.lastFastAbsorbed = 0;
        this.lastThermalAbsorbed = 0;
        this.lastEscapedNeutrons = 0;
        this.totalNeutronsGenerated = 0;
        this.totalSteamProduced = 0;
        this.totalEnergyEU = 0;
        this.totalDeuteriumProduced = 0;
        this.totalTritiumProduced = 0;
        this.negativeTempDetected = false;
        this.negativeTempReason = "";
        this.powerFailed = false;
        this.powerFailReason = "";
        this.stepTraceBuffer.clear();
        this.history.clear();
    }

    public void setTile(int x, int y, SimTile.TileType type) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            if (NuclearSimulationEngine.isCornerNullCell(x, y, width, height)) {
                return;
            }
            grid[x][y].setType(type);
        }
    }

    public SimTile getTile(int x, int y) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            return grid[x][y];
        }
        return null;
    }

    public void updateHatchCapacities(int newCap) {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                SimTile tile = grid[x][y];
                if (tile != null && tile.isHatch()) {
                    tile.setInputFluidCapacity(newCap);
                }
            }
        }
    }

    /**
     * Executes one simulation tick over the grid.
     */
    public boolean step() {
        if (exploded || powerFailed) return false;

        currentTick++;
        recordTraceSnapshot("PRE_TICK", "State before coolant feed");

        // 0. Check for high-pressure coolant in insufficient casing tier -> EXPLODE!
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                SimTile tile = grid[x][y];
                if (tile != null && tile.isHatch() && tile.getInputFluidAmount() > 0) {
                    String name = tile.getInputFluidName();
                    if (name != null && name.contains("highpressure")) {
                        int reqTier = NuclearSimulationEngine.getRequiredFluidTier(name);
                        if (pipeTier < reqTier) {
                            triggerExplosion(
                                "Catastrophic overpressure explosion: " + name
                                    + " requires "
                                    + NuclearSimulationEngine.getPipeTierVoltageName(reqTier)
                                    + " ("
                                    + NuclearSimulationEngine.getPipeTierName(reqTier)
                                    + ") casing or higher, but reactor is only "
                                    + NuclearSimulationEngine.getPipeTierVoltageName(pipeTier)
                                    + " ("
                                    + NuclearSimulationEngine.getPipeTierName(pipeTier)
                                    + ")");
                            return false;
                        }
                    }
                }
            }
        }

        // 1. Coolant Feed Phase: Replenish hatches that have space, checking dry thermal shock
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                SimTile tile = grid[x][y];
                if (tile.isCoolantHatch() && tile.isAutoRefill()) {
                    int space = tile.getInputFluidCapacity() - tile.getInputFluidAmount();
                    if (space > 0) {
                        int feed = Math.min(space, NuclearSimulationEngine.coolantFeedRate);
                        if (feed > 0) {
                            if (tile.isWasDry()) {
                                double threshold = NuclearSimulationEngine
                                    .getCoolantBoilingThreshold(tile.getInputFluidName());
                                if (tile.getTemperature() > threshold) {
                                    triggerDryCoolantShutdown(
                                        "Thermal Shock: Cold coolant fed into dry superheated hatch at (" + x
                                            + ","
                                            + y
                                            + ") with temperature "
                                            + String.format("%.1f", tile.getTemperature())
                                            + "°C exceeding boiling threshold "
                                            + threshold
                                            + "°C");
                                    return false;
                                }
                                tile.setWasDry(false);
                            }
                            tile.setInputFluidAmount(tile.getInputFluidAmount() + feed);
                        }
                    }
                }
            }
        }
        recordTraceSnapshot("POST_COOLANT_FEED", "Coolant fed into hatches");

        // 2. Check loss-of-coolant: if active reactor has coolant hatches and all of them are dry
        boolean hasFuel = false;
        boolean hasCoolantHatches = false;
        boolean allCoolantDry = true;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                SimTile tile = grid[x][y];
                if (tile != null) {
                    if (tile.isFuel()) hasFuel = true;
                    else if (tile.isHatch()) {
                        hasCoolantHatches = true;
                        if (tile.getInputFluidAmount() > 0) allCoolantDry = false;
                    }
                }
            }
        }
        if (hasFuel && hasCoolantHatches && allCoolantDry) {
            triggerDryCoolantShutdown("Loss of Coolant: All coolant hatches depleted on active reactor");
            return false;
        }

        // 3. Call the mod's pure Java NuclearSimulationEngine
        INuclearTile[][] simGrid = new INuclearTile[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (grid[x][y] != null && grid[x][y].getType() == SimTile.TileType.NULL_WALL) {
                    simGrid[x][y] = null;
                } else {
                    simGrid[x][y] = grid[x][y];
                }
            }
        }
        NuclearSimulationEngine.SimulationResult res = NuclearSimulationEngine.simulate(simGrid, width, height);

        coreMaxTemp = res.maxTemperature;
        coreAvgTemp = res.averageTemperature;
        lastNeutronsProduced = res.totalNeutronsGenerated;
        lastFastAbsorbed = res.fastNeutronsAbsorbed;
        lastThermalAbsorbed = res.thermalNeutronsAbsorbed;
        lastEscapedNeutrons = res.neutronsEscaped;
        lastWallReflected = res.wallNeutronsReflected;
        lastWallAbsorbed = res.wallNeutronsAbsorbed;
        lastWallHeatPool = res.wallHeatPool;
        efficiency = res.averageReactivity;

        totalNeutronsGenerated += lastNeutronsProduced;
        recordTraceSnapshot("POST_SIMULATE", "Nuclear fission, diffusion and boiling completed");

        // 4. Strict Check for Negative Temperature Anomaly
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                SimTile tile = grid[x][y];
                if (tile != null && tile.getTemperature() < 0.0) {
                    this.negativeTempDetected = true;
                    this.negativeTempReason = String.format(
                        java.util.Locale.US,
                        "Negative temperature anomaly: tile (%d, %d) [%s] reached %.2f°C at tick %d (min allowed: %.1f°C) | Last Cooling: %s",
                        x,
                        y,
                        tile.getType().code,
                        tile.getTemperature(),
                        currentTick,
                        NuclearSimulationEngine.AMBIENT_TEMP,
                        tile.getLastCoolingDetails());
                    recordTraceSnapshot("NEGATIVE_TEMP_DETECTED", negativeTempReason);
                    triggerExplosion("Simulation Logic Failure: " + negativeTempReason);
                    return false;
                }
            }
        }

        // 5. Check casing operating temperature limit:
        // Overheating hatches void items and fluids inside, but do NOT explode!
        double maxTempAllowed = NuclearSimulationEngine.getMaxOperatingTemperature(pipeTier);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                SimTile tile = grid[x][y];
                if (tile != null && tile.getTemperature() > maxTempAllowed) {
                    if (tile.isHatch()) {
                        tile.setInputFluidAmount(0);
                        tile.setOutputFluidAmount(0);
                    } else if (tile.isFuel()) {
                        tile.setType(SimTile.TileType.EMPTY);
                    }
                }
            }
        }

        // 4. Calculate energy and steam generation in this tick
        long cumSteam = 0;
        int dCount = 0;
        int tCount = 0;

        flowRegularSteam = 0;
        flowSuperheatedSteam = 0;
        flowSupercriticalSteam = 0;
        flowHeavyWaterSteam = 0;
        flowHPHeavyWaterSteam = 0;
        flowHotCoolant = 0;
        flowDirectEU = 0;

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                SimTile tile = grid[x][y];
                cumSteam += tile.getTotalSteamProduced();
                dCount += tile.getTotalDeuteriumProduced();
                tCount += tile.getTotalTritiumProduced();
                flowDirectEU += tile.getDirectEUProduced();

                int tickProduced = tile.getLastTickProduced();
                if (tickProduced > 0) {
                    switch (tile.getType()) {
                        case HATCH_DISTILLED_WATER -> flowRegularSteam += tickProduced;
                        case HATCH_HP_DISTILLED_WATER -> flowSuperheatedSteam += tickProduced;
                        case HATCH_HEAVY_WATER -> flowHeavyWaterSteam += tickProduced;
                        case HATCH_HP_HEAVY_WATER -> flowHPHeavyWaterSteam += tickProduced;
                        case HATCH_IC2_COOLANT -> flowHotCoolant += tickProduced;
                        default -> {}
                    }
                }
            }
        }
        totalSteamProduced = cumSteam;
        totalDeuteriumProduced = dCount;
        totalTritiumProduced = tCount;

        // Calculate power estimation with XLST, XLST-HP, XLST-SC at optimum flow & EHE for Hot Coolant
        lastPowerResult = TurbineCalculator.calculatePower(
            flowRegularSteam,
            flowSuperheatedSteam,
            flowSupercriticalSteam,
            flowHeavyWaterSteam,
            flowHPHeavyWaterSteam,
            flowHotCoolant,
            flowDirectEU,
            turbineMaterial,
            turbineSize,
            turbineFitting);

        totalEnergyEU += lastPowerResult.totalPowerEUt;

        // Telemetry sampling (keep last 500 ticks for charts)
        if (history.size() >= 500) {
            history.remove(0);
        }
        history.add(
            new TickTelemetry(
                currentTick,
                coreMaxTemp,
                coreAvgTemp,
                efficiency,
                lastNeutronsProduced,
                lastPowerResult.totalPowerEUt,
                true));

        return true;
    }

    public void triggerExplosion(String reason) {
        this.exploded = true;
        this.explosionReason = reason;
        if (!history.isEmpty()) {
            TickTelemetry last = history.get(history.size() - 1);
            history.set(
                history.size() - 1,
                new TickTelemetry(
                    last.tick(),
                    last.maxTemp(),
                    last.avgTemp(),
                    last.efficiency(),
                    last.neutrons(),
                    last.powerEUt(),
                    false));
        }
    }

    public void triggerDryCoolantShutdown(String reason) {
        this.powerFailed = true;
        this.powerFailReason = reason;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                SimTile tile = grid[x][y];
                if (tile != null) {
                    if (tile.isHatch()) {
                        tile.setInputFluidAmount(0);
                        tile.setOutputFluidAmount(0);
                        tile.setWasDry(true);
                    } else if (tile.isFuel()) {
                        // Void only fuel rods, keep reflectors and betavoltaics!
                        tile.setType(SimTile.TileType.EMPTY);
                    }
                }
            }
        }
        recordTraceSnapshot("DRY_COOLANT_SHUTDOWN", reason);
    }

    /**
     * Loads a preset layout into the grid.
     */
    public void loadPreset(String presetName) {
        resetMetrics();
        NuclearSimulationEngine.resetDefaultParameters();
        switch (presetName.toUpperCase()) {
            case "BEST_ELECTRUM_5X5", "ELECTRUM_POWER_5X5", "BASIC_ELECTRUM_5X5" -> {
                this.pipeTier = NuclearSimulationEngine.PIPE_TIER_ELECTRUM;
                this.turbineMaterial = TurbineCalculator.TurbineMaterial.fromString("Oriharukon");
                this.turbineSize = TurbineCalculator.TurbineSize.NORMAL;
                this.turbineFitting = TurbineCalculator.FittingMode.TIGHT;
                loadLayout("RB,HC,HC,HC,RB;HC,U4,U2,U4,HC;HC,U2,HC,HC,HC;HC,U4,U2,U4,HC;RB,HC,HC,HC,RB");
                updateHatchCapacities(NuclearSimulationEngine.hatchCoolantCapacity);
            }
            case "BEST_PLATINUM_9X9", "BREEDER_9X9", "BEST_PLATINUM_7X7", "BREEDER_7X7" -> {
                this.pipeTier = NuclearSimulationEngine.PIPE_TIER_PLATINUM;
                this.turbineMaterial = TurbineCalculator.TurbineMaterial.ELVEN_ELEMENTIUM;
                this.turbineSize = TurbineCalculator.TurbineSize.NORMAL;
                this.turbineFitting = TurbineCalculator.FittingMode.TIGHT;
                loadLayout(
                    "RB,RB,HC,HC,HC,HC,HC,RB,RB;RB,HC,HC,HC,HC,HC,HC,HC,RB;HC,HC,U4,HC,U4,HC,U4,HC,HC;HC,HC,HC,U4,HC,U4,HC,HC,HC;HC,HC,U4,HC,U4,HC,U4,HC,HC;HC,HC,HC,HC,HC,HC,HC,HC,HC;HC,HC,HC,HC,HC,HC,HC,HC,HC;RB,HC,HC,HC,HC,HC,HC,HC,RB;RB,RB,HC,HC,HC,HC,HC,RB,RB");
                updateHatchCapacities(NuclearSimulationEngine.hatchCoolantCapacity);
            }
            case "BEST_OSMIUM_9X9", "SUPERHEATED_POWER_9X9", "BEST_OSMIUM_7X7", "SUPERHEATED_POWER_7X7" -> {
                this.pipeTier = NuclearSimulationEngine.PIPE_TIER_OSMIUM;
                this.turbineMaterial = TurbineCalculator.TurbineMaterial.HSS_E;
                this.turbineSize = TurbineCalculator.TurbineSize.NORMAL;
                this.turbineFitting = TurbineCalculator.FittingMode.TIGHT;
                loadLayout(
                    "RB,RB,HC,HC,HC,HC,HC,RB,RB;RB,M4,HC,M4,HC,M4,HC,M4,RB;HC,HC,M4,HC,M4,HC,M4,HC,HC;HC,M4,HC,M4,HC,M4,HC,M4,HC;HC,HC,M4,HC,M4,HC,M2,HC,HC;HC,M2,HC,M2,HC,HC,HC,HC,HC;HC,HC,HC,HC,HC,HC,HC,HC,HC;RB,HC,HC,HC,HC,HC,HC,HC,RB;RB,RB,HC,HC,HC,HC,HC,RB,RB");
                updateHatchCapacities(NuclearSimulationEngine.hatchCoolantCapacity);
            }
            case "BEST_QUANTIUM_13X13", "CANDU_HEAVY_WATER_13X13", "BEST_QUANTIUM_9X9", "CANDU_HEAVY_WATER_9X9" -> {
                this.pipeTier = NuclearSimulationEngine.PIPE_TIER_QUANTIUM;
                this.turbineMaterial = TurbineCalculator.TurbineMaterial.HSS_E;
                this.turbineSize = TurbineCalculator.TurbineSize.LARGE;
                this.turbineFitting = TurbineCalculator.FittingMode.TIGHT;
                loadLayout(
                    "RB,RB,RB,HP,HP,HP,HP,HP,HP,HP,RB,RB,RB;RB,RB,HP,HP,HP,HP,HP,HP,HP,HP,HP,RB,RB;RB,HP,HP,HP,HP,HP,HP,HP,HP,HP,HP,HP,RB;HP,HP,HP,NQ,HP,NQ,HP,NQ,HP,NQ,HP,HP,HP;HP,HP,HP,HP,NQ,HP,NQ,HP,NQ,HP,HP,HP,HP;HP,HP,HP,NQ,HP,NQ,HP,NQ,HP,NQ,HP,HP,HP;HP,HP,HP,HP,NQ,HP,NQ,HP,NQ,HP,HP,HP,HP;HP,HP,HP,NQ,HP,NQ,HP,NQ,HP,NQ,HP,HP,HP;HP,HP,HP,HP,NQ,HP,NQ,HP,HP,HP,HP,HP,HP;HP,HP,HP,HP,HP,HP,HP,HP,HP,HP,HP,HP,HP;RB,HP,HP,HP,HP,HP,HP,HP,HP,HP,HP,HP,RB;RB,RB,HP,HP,HP,HP,HP,HP,HP,HP,HP,RB,RB;RB,RB,RB,HP,HP,HP,HP,HP,HP,HP,RB,RB,RB");
                updateHatchCapacities(NuclearSimulationEngine.hatchCoolantCapacity);
            }
            case "BEST_FLUXED_13X13", "FLUXED_SUPERCRITICAL_13X13", "BEST_FLUXED_9X9", "FLUXED_SUPERCRITICAL_9X9" -> {
                this.pipeTier = NuclearSimulationEngine.PIPE_TIER_FLUXED_ELECTRUM;
                this.turbineMaterial = TurbineCalculator.TurbineMaterial.HSS_S;
                this.turbineSize = TurbineCalculator.TurbineSize.LARGE;
                this.turbineFitting = TurbineCalculator.FittingMode.TIGHT;
                loadLayout(
                    "RB,RB,RB,HH,HH,HH,HH,HH,HH,HH,RB,RB,RB;RB,RB,HH,HH,HH,HH,HH,HH,HH,HH,HH,RB,RB;RB,HH,NQ,HH,NQ,HH,NQ,HH,NQ,HH,NQ,HH,RB;HH,HH,HH,NQ,HH,NQ,HH,NQ,HH,NQ,HH,HH,HH;HH,HH,NQ,HH,NQ,HH,NQ,HH,NQ,HH,NQ,HH,HH;HH,HH,HH,NQ,HH,NQ,HH,NQ,HH,NQ,HH,HH,HH;HH,HH,HH,HH,HH,HH,HH,HH,HH,HH,HH,HH,HH;HH,HH,HH,HH,HH,HH,HH,HH,HH,HH,HH,HH,HH;HH,HH,HH,HH,HH,HH,HH,HH,HH,HH,HH,HH,HH;HH,HH,HH,HH,HH,HH,HH,HH,HH,HH,HH,HH,HH;RB,HH,HH,HH,HH,HH,HH,HH,HH,HH,HH,HH,RB;RB,RB,HH,HH,HH,HH,HH,HH,HH,HH,HH,RB,RB;RB,RB,RB,HH,HH,HH,HH,HH,HH,HH,RB,RB,RB");
                updateHatchCapacities(NuclearSimulationEngine.hatchCoolantCapacity);
            }
            case "BEST_PLUTONIUM_13X13", "BLACK_PLUTONIUM_13X13", "BEST_PLUTONIUM_9X9", "BLACK_PLUTONIUM_9X9" -> {
                this.pipeTier = NuclearSimulationEngine.PIPE_TIER_BLACK_PLUTONIUM;
                this.turbineMaterial = TurbineCalculator.TurbineMaterial.INFINITY;
                this.turbineSize = TurbineCalculator.TurbineSize.LARGE;
                this.turbineFitting = TurbineCalculator.FittingMode.TIGHT;
                loadLayout(
                    "RB,RB,RB,HH,HH,HH,HH,HH,HH,HH,RB,RB,RB;RB,NQ,HH,NQ,HH,NQ,HH,NQ,HH,NQ,HH,NQ,RB;RB,HH,NQ,HH,NQ,HH,NQ,HH,NQ,HH,NQ,HH,RB;HH,NQ,HH,NQ,HH,NQ,HH,NQ,HH,NQ,HH,NQ,HH;HH,HH,NQ,HH,NQ,HH,NQ,HH,NQ,HH,NQ,HH,HH;HH,NQ,HH,NQ,HH,NQ,HH,NQ,HH,NQ,HH,NQ,HH;HH,HH,NQ,HH,NQ,HH,NQ,HH,NQ,HH,NQ,HH,HH;HH,NQ,HH,NQ,HH,NQ,HH,NQ,HH,NQ,HH,NQ,HH;HH,HH,NQ,HH,NQ,HH,NQ,HH,NQ,HH,NQ,HH,HH;HH,NQ,HH,NQ,HH,NQ,HH,NQ,HH,NQ,HH,HH,HH;RB,HH,HH,HH,HH,HH,HH,HH,HH,HH,HH,HH,RB;RB,RB,HH,HH,HH,HH,HH,HH,HH,HH,HH,RB,RB;RB,RB,RB,HH,HH,HH,HH,HH,HH,HH,RB,RB,RB");
                updateHatchCapacities(NuclearSimulationEngine.hatchCoolantCapacity);
            }
            default -> {
                this.pipeTier = NuclearSimulationEngine.PIPE_TIER_ELECTRUM;
                this.turbineMaterial = TurbineCalculator.TurbineMaterial.fromString("Oriharukon");
                this.turbineSize = TurbineCalculator.TurbineSize.NORMAL;
                this.turbineFitting = TurbineCalculator.FittingMode.TIGHT;
                loadLayout("RB,HC,HC,HC,RB;HC,U4,U2,U4,HC;HC,U2,HC,HC,HC;HC,U4,U2,U4,HC;RB,HC,HC,HC,RB");
                updateHatchCapacities(NuclearSimulationEngine.hatchCoolantCapacity);
            }
        }
    }

    /**
     * Loads a custom layout string into the grid.
     * Can accept row-separated formats (with ';' or '/' or newline) or flat comma/space-separated codes.
     */
    public void loadLayout(String layoutStr) {
        if (layoutStr == null || layoutStr.trim()
            .isEmpty()) return;
        resetMetrics();
        String trimmed = layoutStr.trim();
        String[] rows = trimmed.split("[;/\\n]+");
        if (rows.length > 1) {
            int h = rows.length;
            String[] firstRowCols = rows[0].trim()
                .split("[,\\s]+");
            int w = firstRowCols.length;
            if (w > 0 && h > 0) {
                this.width = w;
                this.height = h;
                this.grid = new SimTile[w][h];
                clearGrid();
                for (int y = 0; y < h; y++) {
                    String[] cols = rows[y].trim()
                        .split("[,\\s]+");
                    for (int x = 0; x < Math.min(w, cols.length); x++) {
                        setTile(x, y, SimTile.TileType.fromCode(cols[x]));
                    }
                }
                return;
            }
        }

        // Flat sequence of codes
        String[] tokens = trimmed.split("[,\\s]+");
        int sqrt = (int) Math.round(Math.sqrt(tokens.length));
        if (sqrt * sqrt == tokens.length && (this.width * this.height != tokens.length)) {
            this.width = sqrt;
            this.height = sqrt;
            this.grid = new SimTile[sqrt][sqrt];
        }
        clearGrid();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;
                if (idx < tokens.length) {
                    setTile(x, y, SimTile.TileType.fromCode(tokens[idx]));
                }
            }
        }
    }

    /**
     * Serializes the current grid layout to a compact string format: rows separated by ';', cells separated by ','.
     */
    public String toLayoutString() {
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < height; y++) {
            if (y > 0) sb.append(";");
            for (int x = 0; x < width; x++) {
                if (x > 0) sb.append(",");
                sb.append(grid[x][y].getType().code);
            }
        }
        return sb.toString();
    }

    /**
     * Converts grid to ASCII representation.
     */
    public String toAscii() {
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                sb.append(String.format("%-3s", grid[x][y].getType().code));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // Getters and configuration
    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getPipeTier() {
        return pipeTier;
    }

    public void setPipeTier(int pipeTier) {
        this.pipeTier = pipeTier;
    }

    public long getCurrentTick() {
        return currentTick;
    }

    public boolean isExploded() {
        return exploded;
    }

    public String getExplosionReason() {
        return explosionReason;
    }

    public boolean isPowerFailed() {
        return powerFailed;
    }

    public String getPowerFailReason() {
        return powerFailReason;
    }

    public double getCoreMaxTemp() {
        return coreMaxTemp;
    }

    public double getCoreAvgTemp() {
        return coreAvgTemp;
    }

    public double getEfficiency() {
        return efficiency;
    }

    public int getLastNeutronsProduced() {
        return lastNeutronsProduced;
    }

    public int getLastFastAbsorbed() {
        return lastFastAbsorbed;
    }

    public int getLastThermalAbsorbed() {
        return lastThermalAbsorbed;
    }

    public int getLastEscapedNeutrons() {
        return lastEscapedNeutrons;
    }

    public int getLastWallReflected() {
        return lastWallReflected;
    }

    public int getLastWallAbsorbed() {
        return lastWallAbsorbed;
    }

    public double getLastWallHeatPool() {
        return lastWallHeatPool;
    }

    public long getTotalNeutronsGenerated() {
        return totalNeutronsGenerated;
    }

    public long getTotalSteamProduced() {
        return totalSteamProduced;
    }

    public double getTotalEnergyEU() {
        return totalEnergyEU;
    }

    public int getTotalDeuteriumProduced() {
        return totalDeuteriumProduced;
    }

    public int getTotalTritiumProduced() {
        return totalTritiumProduced;
    }

    public List<TickTelemetry> getHistory() {
        return history;
    }

    public TurbineCalculator.TurbineMaterial getTurbineMaterial() {
        return turbineMaterial;
    }

    public void setTurbineMaterial(TurbineCalculator.TurbineMaterial turbineMaterial) {
        this.turbineMaterial = turbineMaterial;
    }

    public TurbineCalculator.TurbineSize getTurbineSize() {
        return turbineSize;
    }

    public void setTurbineSize(TurbineCalculator.TurbineSize turbineSize) {
        this.turbineSize = turbineSize;
    }

    public TurbineCalculator.FittingMode getTurbineFitting() {
        return turbineFitting;
    }

    public void setTurbineFitting(TurbineCalculator.FittingMode turbineFitting) {
        this.turbineFitting = turbineFitting;
    }

    public double getFlowRegularSteam() {
        return flowRegularSteam;
    }

    public double getFlowSuperheatedSteam() {
        return flowSuperheatedSteam;
    }

    public double getFlowSupercriticalSteam() {
        return flowSupercriticalSteam;
    }

    public double getFlowHeavyWaterSteam() {
        return flowHeavyWaterSteam;
    }

    public double getFlowHPHeavyWaterSteam() {
        return flowHPHeavyWaterSteam;
    }

    public double getFlowHotCoolant() {
        return flowHotCoolant;
    }

    public double getFlowDirectEU() {
        return flowDirectEU;
    }

    public TurbineCalculator.PowerEstimationResult getLastPowerResult() {
        return lastPowerResult;
    }

    public int getActiveFuelRodCount() {
        int count = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                SimTile tile = grid[x][y];
                if (tile != null && tile.isFuel() && !tile.isDepleted()) {
                    count++;
                }
            }
        }
        return count;
    }

    public double getMinFuelRodLongevityMinutes() {
        double minMins = Double.POSITIVE_INFINITY;
        if (currentTick <= 0) return minMins;

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                SimTile tile = grid[x][y];
                if (tile != null && tile.isFuel()) {
                    int lost = tile.getMaxDurability() - tile.getDurability();
                    if (lost > 0) {
                        double ratePerTick = (double) lost / (double) currentTick;
                        double ticksRemaining = (double) tile.getMaxDurability() / ratePerTick;
                        double mins = ticksRemaining / (20.0 * 60.0);
                        if (mins < minMins) {
                            minMins = mins;
                        }
                    }
                }
            }
        }
        return minMins;
    }

    public double getAvgFuelRodLongevityMinutes() {
        double sumMins = 0;
        int count = 0;
        if (currentTick <= 0) return Double.POSITIVE_INFINITY;

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                SimTile tile = grid[x][y];
                if (tile != null && tile.isFuel()) {
                    int lost = tile.getMaxDurability() - tile.getDurability();
                    if (lost > 0) {
                        double ratePerTick = (double) lost / (double) currentTick;
                        double ticksRemaining = (double) tile.getMaxDurability() / ratePerTick;
                        double mins = ticksRemaining / (20.0 * 60.0);
                        sumMins += mins;
                        count++;
                    }
                }
            }
        }
        return count > 0 ? (sumMins / count) : Double.POSITIVE_INFINITY;
    }

    public int getTotalDurabilityLost() {
        int sum = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                SimTile tile = grid[x][y];
                if (tile != null && tile.isFuel()) {
                    sum += (tile.getMaxDurability() - tile.getDurability());
                }
            }
        }
        return sum;
    }
}
