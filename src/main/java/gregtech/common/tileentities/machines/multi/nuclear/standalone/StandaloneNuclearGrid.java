package gregtech.common.tileentities.machines.multi.nuclear.standalone;

import java.util.ArrayList;
import java.util.List;

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
    private double coreMaxTemp = NuclearSimulationEngine.AMBIENT_TEMP;
    private double coreAvgTemp = NuclearSimulationEngine.AMBIENT_TEMP;
    private double efficiency = 1.0;
    private int lastNeutronsProduced = 0;
    private int lastFastAbsorbed = 0;
    private int lastThermalAbsorbed = 0;
    private int lastEscapedNeutrons = 0;

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

    private TurbineCalculator.PowerEstimationResult lastPowerResult = new TurbineCalculator.PowerEstimationResult();

    public record TickTelemetry(long tick, double maxTemp, double avgTemp, double efficiency, int neutrons,
        double powerEUt, boolean safe) {}

    private final List<TickTelemetry> history = new ArrayList<>();

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
                grid[x][y] = new SimTile(SimTile.TileType.EMPTY);
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
        this.history.clear();
    }

    public void setTile(int x, int y, SimTile.TileType type) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
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
        if (exploded) return false;

        currentTick++;

        // 1. Coolant Feed Phase: Replenish hatches that have space, checking dry thermal shock
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                SimTile tile = grid[x][y];
                if (tile.isHatch()) {
                    int space = tile.getInputFluidCapacity() - tile.getInputFluidAmount();
                    if (space > 0) {
                        int feed = Math.min(space, NuclearSimulationEngine.coolantFeedRate);
                        if (feed > 0) {
                            if (tile.isWasDry()) {
                                double threshold = NuclearSimulationEngine
                                    .getCoolantBoilingThreshold(tile.getInputFluidName());
                                if (tile.getTemperature() > threshold) {
                                    triggerExplosion(
                                        "Thermal Shock: Cold coolant fed into dry superheated hatch at (" + x
                                            + ","
                                            + y
                                            + ") with temperature "
                                            + String.format("%.1f", tile.getTemperature())
                                            + "°C exceeding boiling threshold "
                                            + threshold
                                            + "°C - catastrophic flash steam overpressure!");
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

        // 2. Call the mod's pure Java NuclearSimulationEngine
        NuclearSimulationEngine.SimulationResult res = NuclearSimulationEngine.simulate(grid, width, height);

        coreMaxTemp = res.maxTemperature;
        coreAvgTemp = res.averageTemperature;
        lastNeutronsProduced = res.totalNeutronsGenerated;
        lastFastAbsorbed = res.fastNeutronsAbsorbed;
        lastThermalAbsorbed = res.thermalNeutronsAbsorbed;
        lastEscapedNeutrons = res.neutronsEscaped;
        efficiency = NuclearSimulationEngine.calculateEfficiency(coreAvgTemp);

        totalNeutronsGenerated += lastNeutronsProduced;

        // 3. Check casing operating temperature limit
        double maxTempAllowed = NuclearSimulationEngine.getMaxOperatingTemperature(pipeTier);
        if (coreMaxTemp > maxTempAllowed) {
            triggerExplosion(
                "Meltdown: Core peak temperature " + String.format("%.1f", coreMaxTemp)
                    + "°C exceeded casing tier maximum "
                    + maxTempAllowed
                    + "°C ("
                    + NuclearSimulationEngine.getPipeTierName(pipeTier)
                    + ")");
            return false;
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

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                SimTile tile = grid[x][y];
                cumSteam += tile.getTotalSteamProduced();
                dCount += tile.getTotalDeuteriumProduced();
                tCount += tile.getTotalTritiumProduced();

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

    /**
     * Loads a preset layout into the grid.
     */
    public void loadPreset(String presetName) {
        resetMetrics();
        switch (presetName.toUpperCase()) {
            case "BREEDER_7X7" -> {
                this.width = 7;
                this.height = 7;
                this.grid = new SimTile[7][7];
                this.pipeTier = NuclearSimulationEngine.PIPE_TIER_PLATINUM;
                clearGrid();
                // Outer ring reflectors
                for (int i = 0; i < 7; i++) {
                    setTile(i, 0, SimTile.TileType.REFLECTOR_BERYLLIUM);
                    setTile(i, 6, SimTile.TileType.REFLECTOR_BERYLLIUM);
                    setTile(0, i, SimTile.TileType.REFLECTOR_BERYLLIUM);
                    setTile(6, i, SimTile.TileType.REFLECTOR_BERYLLIUM);
                }
                // Checkerboard Distilled Water hatches and Uranium Quad rods for breeding
                for (int x = 1; x < 6; x++) {
                    for (int y = 1; y < 6; y++) {
                        if ((x + y) % 2 == 0) {
                            setTile(x, y, SimTile.TileType.FUEL_URANIUM_QUAD);
                        } else {
                            setTile(x, y, SimTile.TileType.HATCH_DISTILLED_WATER);
                        }
                    }
                }
            }
            case "SUPERHEATED_POWER_7X7" -> {
                this.width = 7;
                this.height = 7;
                this.grid = new SimTile[7][7];
                this.pipeTier = NuclearSimulationEngine.PIPE_TIER_OSMIUM;
                clearGrid();
                // Outer ring reflectors
                for (int i = 0; i < 7; i++) {
                    setTile(i, 0, SimTile.TileType.REFLECTOR_CARBON);
                    setTile(i, 6, SimTile.TileType.REFLECTOR_CARBON);
                    setTile(0, i, SimTile.TileType.REFLECTOR_CARBON);
                    setTile(6, i, SimTile.TileType.REFLECTOR_CARBON);
                }
                // HP Distilled Water and MOX Quad rods
                for (int x = 1; x < 6; x++) {
                    for (int y = 1; y < 6; y++) {
                        if ((x == 3 && y == 3) || (x == 2 && y == 2)
                            || (x == 4 && y == 4)
                            || (x == 2 && y == 4)
                            || (x == 4 && y == 2)) {
                            setTile(x, y, SimTile.TileType.FUEL_MOX_QUAD);
                        } else {
                            setTile(x, y, SimTile.TileType.HATCH_HP_DISTILLED_WATER);
                        }
                    }
                }
            }
            case "CANDU_HEAVY_WATER_9X9" -> {
                this.width = 9;
                this.height = 9;
                this.grid = new SimTile[9][9];
                this.pipeTier = NuclearSimulationEngine.PIPE_TIER_QUANTIUM;
                clearGrid();
                for (int i = 0; i < 9; i++) {
                    setTile(i, 0, SimTile.TileType.REFLECTOR_BERYLLIUM);
                    setTile(i, 8, SimTile.TileType.REFLECTOR_BERYLLIUM);
                    setTile(0, i, SimTile.TileType.REFLECTOR_BERYLLIUM);
                    setTile(8, i, SimTile.TileType.REFLECTOR_BERYLLIUM);
                }
                for (int x = 1; x < 8; x++) {
                    for (int y = 1; y < 8; y++) {
                        if ((x + y) % 2 == 0) {
                            setTile(x, y, SimTile.TileType.FUEL_URANIUM_QUAD);
                        } else {
                            setTile(x, y, SimTile.TileType.HATCH_HEAVY_WATER);
                        }
                    }
                }
            }
            case "FLUXED_SUPERCRITICAL_9X9" -> {
                this.width = 9;
                this.height = 9;
                this.grid = new SimTile[9][9];
                this.pipeTier = NuclearSimulationEngine.PIPE_TIER_FLUXED_ELECTRUM;
                clearGrid();
                for (int i = 0; i < 9; i++) {
                    setTile(i, 0, SimTile.TileType.REFLECTOR_BERYLLIUM);
                    setTile(i, 8, SimTile.TileType.REFLECTOR_BERYLLIUM);
                    setTile(0, i, SimTile.TileType.REFLECTOR_BERYLLIUM);
                    setTile(8, i, SimTile.TileType.REFLECTOR_BERYLLIUM);
                }
                for (int x = 1; x < 8; x++) {
                    for (int y = 1; y < 8; y++) {
                        if (x % 2 == 0 && y % 2 == 0) {
                            setTile(x, y, SimTile.TileType.FUEL_NAQUADAH);
                        } else {
                            setTile(x, y, SimTile.TileType.HATCH_HP_HEAVY_WATER);
                        }
                    }
                }
            }
            case "ELECTRUM_POWER_5X5" -> {
                this.width = 5;
                this.height = 5;
                this.grid = new SimTile[5][5];
                this.pipeTier = NuclearSimulationEngine.PIPE_TIER_ELECTRUM;
                clearGrid();
                for (int i = 0; i < 5; i++) {
                    setTile(i, 0, SimTile.TileType.REFLECTOR_CARBON);
                    setTile(i, 4, SimTile.TileType.REFLECTOR_CARBON);
                    setTile(0, i, SimTile.TileType.REFLECTOR_CARBON);
                    setTile(4, i, SimTile.TileType.REFLECTOR_CARBON);
                }
                setTile(1, 1, SimTile.TileType.FUEL_URANIUM_QUAD);
                setTile(3, 1, SimTile.TileType.FUEL_URANIUM_QUAD);
                setTile(1, 3, SimTile.TileType.FUEL_URANIUM_QUAD);
                setTile(3, 3, SimTile.TileType.FUEL_URANIUM_QUAD);
                setTile(2, 1, SimTile.TileType.HATCH_IC2_COOLANT);
                setTile(1, 2, SimTile.TileType.HATCH_IC2_COOLANT);
                setTile(2, 2, SimTile.TileType.HATCH_IC2_COOLANT);
                setTile(3, 2, SimTile.TileType.HATCH_IC2_COOLANT);
                setTile(2, 3, SimTile.TileType.HATCH_IC2_COOLANT);
            }
            case "BLACK_PLUTONIUM_9X9" -> {
                this.width = 9;
                this.height = 9;
                this.grid = new SimTile[9][9];
                this.pipeTier = NuclearSimulationEngine.PIPE_TIER_BLACK_PLUTONIUM;
                clearGrid();
                for (int i = 0; i < 9; i++) {
                    setTile(i, 0, SimTile.TileType.REFLECTOR_BERYLLIUM);
                    setTile(i, 8, SimTile.TileType.REFLECTOR_BERYLLIUM);
                    setTile(0, i, SimTile.TileType.REFLECTOR_BERYLLIUM);
                    setTile(8, i, SimTile.TileType.REFLECTOR_BERYLLIUM);
                }
                for (int x = 1; x < 8; x++) {
                    for (int y = 1; y < 8; y++) {
                        if ((x + y) % 2 == 0) {
                            setTile(x, y, SimTile.TileType.FUEL_NAQUADAH);
                        } else {
                            setTile(x, y, SimTile.TileType.HATCH_HP_HEAVY_WATER);
                        }
                    }
                }
            }
            default -> { // BASIC_ELECTRUM_5X5
                this.width = 5;
                this.height = 5;
                this.grid = new SimTile[5][5];
                this.pipeTier = NuclearSimulationEngine.PIPE_TIER_ELECTRUM;
                clearGrid();
                for (int i = 0; i < 5; i++) {
                    setTile(i, 0, SimTile.TileType.REFLECTOR_CARBON);
                    setTile(i, 4, SimTile.TileType.REFLECTOR_CARBON);
                    setTile(0, i, SimTile.TileType.REFLECTOR_CARBON);
                    setTile(4, i, SimTile.TileType.REFLECTOR_CARBON);
                }
                setTile(2, 2, SimTile.TileType.FUEL_URANIUM_DUAL);
                setTile(1, 2, SimTile.TileType.HATCH_IC2_COOLANT);
                setTile(3, 2, SimTile.TileType.HATCH_IC2_COOLANT);
                setTile(2, 1, SimTile.TileType.HATCH_IC2_COOLANT);
                setTile(2, 3, SimTile.TileType.HATCH_IC2_COOLANT);
                setTile(1, 1, SimTile.TileType.COOLANT_CELL_60K);
                setTile(3, 1, SimTile.TileType.COOLANT_CELL_60K);
                setTile(1, 3, SimTile.TileType.COOLANT_CELL_60K);
                setTile(3, 3, SimTile.TileType.COOLANT_CELL_60K);
            }
        }
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
