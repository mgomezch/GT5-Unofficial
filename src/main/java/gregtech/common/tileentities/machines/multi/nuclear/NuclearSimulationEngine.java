package gregtech.common.tileentities.machines.multi.nuclear;

import java.util.Random;

public class NuclearSimulationEngine {

    public static final double EU_FOR_FAST_NEUTRON = 8.0;
    public static final double EU_PER_DEGREE = 64.0;
    public static final double BASE_HEAT_CONDUCTION = 0.01;
    public static final double DEFAULT_AMBIENT_TEMP = 24.0;
    public static double ambientTemp = DEFAULT_AMBIENT_TEMP;
    public static double AMBIENT_TEMP = DEFAULT_AMBIENT_TEMP;
    public static final double DEFAULT_TEMP_THRESHOLD_LOW = 800.0;
    public static final double DEFAULT_TEMP_THRESHOLD_HIGH = 2800.0;
    public static final double DEFAULT_REACTIVITY_POWER = 1.2;
    public static final double DEFAULT_THERMAL_FISSION_MULT = 1.1;
    public static final double DEFAULT_FISSION_HEAT_PER_NEUTRON = 18.0;
    public static final double DEFAULT_HP_WATER_BOILING_POINT = 200.0;

    public enum TurnoverCurve {

        EXPONENTIAL,
        LINEAR,
        SIGMOID,
        STEP;

        public static TurnoverCurve fromString(String str) {
            if (str == null) return EXPONENTIAL;
            try {
                return valueOf(
                    str.trim()
                        .toUpperCase());
            } catch (Exception e) {
                return EXPONENTIAL;
            }
        }
    }

    public static final int DEFAULT_HATCH_CAPACITY = 2000;
    public static final TurnoverCurve DEFAULT_TURNOVER_CURVE = TurnoverCurve.EXPONENTIAL;
    public static final double DEFAULT_TURNOVER_DELTA_T_MAX = 100.0;
    public static final double DEFAULT_TURNOVER_EXPONENT = 1.5;
    public static final int DEFAULT_COOLANT_FEED_RATE = 2000;
    public static final double DEFAULT_COOLING_HEAT_PER_LITER = 4.0;
    public static final double DEFAULT_IC2_COOLANT_HEAT_PER_LITER = 20.0;

    public static double tempThresholdLow = DEFAULT_TEMP_THRESHOLD_LOW;
    public static double tempThresholdHigh = DEFAULT_TEMP_THRESHOLD_HIGH;
    public static double reactivityPower = DEFAULT_REACTIVITY_POWER;
    public static double thermalFissionMultiplier = DEFAULT_THERMAL_FISSION_MULT;
    public static double fissionHeatPerNeutron = DEFAULT_FISSION_HEAT_PER_NEUTRON;
    public static double hpWaterBoilingPoint = DEFAULT_HP_WATER_BOILING_POINT;

    public static int hatchCoolantCapacity = DEFAULT_HATCH_CAPACITY;
    public static TurnoverCurve turnoverCurve = DEFAULT_TURNOVER_CURVE;
    public static double turnoverDeltaTMax = DEFAULT_TURNOVER_DELTA_T_MAX;
    public static double turnoverExponent = DEFAULT_TURNOVER_EXPONENT;
    public static int coolantFeedRate = DEFAULT_COOLANT_FEED_RATE;
    public static double coolingHeatPerLiter = DEFAULT_COOLING_HEAT_PER_LITER;
    public static double ic2CoolantHeatPerLiter = DEFAULT_IC2_COOLANT_HEAT_PER_LITER;

    public static final double DEFAULT_FUEL_BURNUP_MULTIPLIER = 1.0;
    public static double fuelBurnupMultiplier = DEFAULT_FUEL_BURNUP_MULTIPLIER;

    public static final double DEFAULT_WALL_REFLECTION_CHANCE = 0.50;
    public static double wallReflectionChance = DEFAULT_WALL_REFLECTION_CHANCE;
    public static final double DEFAULT_WALL_ABSORB_HEAT_PER_NEUTRON = 12.0;
    public static double wallAbsorbHeatPerNeutron = DEFAULT_WALL_ABSORB_HEAT_PER_NEUTRON;

    public static void setAmbientTemperature(double temp) {
        ambientTemp = temp;
        AMBIENT_TEMP = temp;
    }

    public static void setSimulationParameters(double low, double high, double power, double fissionMult,
        double heatPerNeutron, double hpBoil) {
        tempThresholdLow = low;
        tempThresholdHigh = high;
        reactivityPower = power;
        thermalFissionMultiplier = fissionMult;
        fissionHeatPerNeutron = heatPerNeutron;
        hpWaterBoilingPoint = hpBoil;
    }

    public static void setExtendedParameters(int hatchCap, TurnoverCurve curve, double dtMax, double exp, int feedRate,
        double coolingHeat, double low, double high, double power, double fissionMult, double heatPerNeutron,
        double hpBoil) {
        hatchCoolantCapacity = Math.max(100, hatchCap);
        turnoverCurve = (curve != null) ? curve : DEFAULT_TURNOVER_CURVE;
        turnoverDeltaTMax = Math.max(10.0, dtMax);
        turnoverExponent = Math.max(0.1, exp);
        coolantFeedRate = Math.max(0, feedRate);
        coolingHeatPerLiter = Math.max(0.1, coolingHeat);
        setSimulationParameters(low, high, power, fissionMult, heatPerNeutron, hpBoil);
    }

    public static void setExtendedParameters(int hatchCap, TurnoverCurve curve, double dtMax, double exp, int feedRate,
        double coolingHeat, double low, double high, double power, double fissionMult, double heatPerNeutron,
        double hpBoil, double ic2Heat, double ambient) {
        setExtendedParameters(
            hatchCap,
            curve,
            dtMax,
            exp,
            feedRate,
            coolingHeat,
            low,
            high,
            power,
            fissionMult,
            heatPerNeutron,
            hpBoil);
        ic2CoolantHeatPerLiter = Math.max(0.1, ic2Heat);
        setAmbientTemperature(ambient);
    }

    public static void resetDefaultParameters() {
        tempThresholdLow = DEFAULT_TEMP_THRESHOLD_LOW;
        tempThresholdHigh = DEFAULT_TEMP_THRESHOLD_HIGH;
        reactivityPower = DEFAULT_REACTIVITY_POWER;
        thermalFissionMultiplier = DEFAULT_THERMAL_FISSION_MULT;
        fissionHeatPerNeutron = DEFAULT_FISSION_HEAT_PER_NEUTRON;
        hpWaterBoilingPoint = DEFAULT_HP_WATER_BOILING_POINT;
        hatchCoolantCapacity = DEFAULT_HATCH_CAPACITY;
        turnoverCurve = DEFAULT_TURNOVER_CURVE;
        turnoverDeltaTMax = DEFAULT_TURNOVER_DELTA_T_MAX;
        turnoverExponent = DEFAULT_TURNOVER_EXPONENT;
        coolantFeedRate = DEFAULT_COOLANT_FEED_RATE;
        coolingHeatPerLiter = DEFAULT_COOLING_HEAT_PER_LITER;
        fuelBurnupMultiplier = DEFAULT_FUEL_BURNUP_MULTIPLIER;
        wallReflectionChance = DEFAULT_WALL_REFLECTION_CHANCE;
        wallAbsorbHeatPerNeutron = DEFAULT_WALL_ABSORB_HEAT_PER_NEUTRON;
        ic2CoolantHeatPerLiter = DEFAULT_IC2_COOLANT_HEAT_PER_LITER;
        setAmbientTemperature(DEFAULT_AMBIENT_TEMP);
    }

    /**
     * Calculates the fraction of hatch coolant capacity that turns over into steam
     * this tick based on excess temperature above boiling point.
     */
    public static double calculateTurnoverFraction(double deltaT) {
        if (deltaT <= 0) return 0.0;
        double dtMax = Math.max(1.0, turnoverDeltaTMax);
        return switch (turnoverCurve) {
            case LINEAR -> Math.min(1.0, deltaT / dtMax);
            case EXPONENTIAL -> Math.min(1.0, Math.pow(Math.min(deltaT / dtMax, 1.0), turnoverExponent));
            case SIGMOID -> {
                double k = 6.0 / dtMax;
                double val = 1.0 / (1.0 + Math.exp(-k * (deltaT - 0.5 * dtMax)));
                double v0 = 1.0 / (1.0 + Math.exp(3.0));
                double v1 = 1.0 / (1.0 + Math.exp(-3.0));
                yield Math.max(0.0, Math.min(1.0, (val - v0) / (v1 - v0)));
            }
            case STEP -> {
                if (deltaT < 0.25 * dtMax) yield 0.10;
                if (deltaT < 0.50 * dtMax) yield 0.35;
                if (deltaT < 0.75 * dtMax) yield 0.70;
                yield 1.0;
            }
        };
    }

    public static final int PIPE_TIER_ELECTRUM = 0;
    public static final int PIPE_TIER_PLATINUM = 1;
    public static final int PIPE_TIER_OSMIUM = 2;
    public static final int PIPE_TIER_QUANTIUM = 3;
    public static final int PIPE_TIER_FLUXED_ELECTRUM = 4;
    public static final int PIPE_TIER_BLACK_PLUTONIUM = 5;

    public static String getPipeTierName(int tier) {
        return switch (tier) {
            case PIPE_TIER_ELECTRUM -> "Electrum (IC2 Coolant)";
            case PIPE_TIER_PLATINUM -> "Platinum (Distilled Water -> Steam)";
            case PIPE_TIER_OSMIUM -> "Osmium (HP Distilled Water -> Superheated)";
            case PIPE_TIER_QUANTIUM -> "Quantium (Heavy Water -> HW Steam)";
            case PIPE_TIER_FLUXED_ELECTRUM -> "Fluxed Electrum (HP Heavy Water -> HW SC Steam)";
            case PIPE_TIER_BLACK_PLUTONIUM -> "Black Plutonium (Max Tier / All Coolants)";
            default -> "None";
        };
    }

    public static long getPipeTierVoltage(int tier) {
        return switch (tier) {
            case PIPE_TIER_ELECTRUM -> 2048L; // EV
            case PIPE_TIER_PLATINUM -> 8192L; // IV
            case PIPE_TIER_OSMIUM -> 32768L; // LuV
            case PIPE_TIER_QUANTIUM -> 131072L; // ZPM
            case PIPE_TIER_FLUXED_ELECTRUM -> 524288L; // UV
            case PIPE_TIER_BLACK_PLUTONIUM -> 2097152L; // UHV
            default -> 2048L;
        };
    }

    public static String getPipeTierVoltageName(int tier) {
        return switch (tier) {
            case PIPE_TIER_ELECTRUM -> "EV";
            case PIPE_TIER_PLATINUM -> "IV";
            case PIPE_TIER_OSMIUM -> "LuV";
            case PIPE_TIER_QUANTIUM -> "ZPM";
            case PIPE_TIER_FLUXED_ELECTRUM -> "UV";
            case PIPE_TIER_BLACK_PLUTONIUM -> "UHV";
            default -> "EV";
        };
    }

    public static double getMaxOperatingTemperature(int tier) {
        return switch (tier) {
            case PIPE_TIER_ELECTRUM -> 1000.0;
            case PIPE_TIER_PLATINUM -> 1400.0;
            case PIPE_TIER_OSMIUM -> 1800.0;
            case PIPE_TIER_QUANTIUM -> 2200.0;
            case PIPE_TIER_FLUXED_ELECTRUM -> 2600.0;
            case PIPE_TIER_BLACK_PLUTONIUM -> 3200.0;
            default -> 800.0;
        };
    }

    public static double getCoolantBoilingThreshold(String fluidName) {
        if (fluidName == null || fluidName.contains("coolant")) {
            return Double.POSITIVE_INFINITY; // IC2 coolant never explodes
        }
        if (fluidName.contains("highpressure")) {
            return hpWaterBoilingPoint;
        }
        return 100.0;
    }

    public static int getRequiredFluidTier(String fluidName) {
        if (fluidName == null) return 999;
        String name = fluidName.toLowerCase();
        if (name.equals("water")) return 999; // Regular water is completely disallowed
        if (name.contains("coolant") && !name.contains("hot")) return PIPE_TIER_ELECTRUM;
        if (name.contains("distilledwater") && !name.contains("highpressure")) return PIPE_TIER_PLATINUM;
        if (name.contains("highpressuredistilledwater")) return PIPE_TIER_OSMIUM;
        if (name.contains("heavywater") && !name.contains("highpressure")) return PIPE_TIER_QUANTIUM;
        if (name.contains("highpressureheavywater")) return PIPE_TIER_FLUXED_ELECTRUM;
        return 999;
    }

    public static double getCoolingOperatingThreshold(String fluidName) {
        if (fluidName == null || fluidName.contains("coolant")) {
            return ambientTemp;
        }
        if (fluidName.contains("highpressure")) {
            return hpWaterBoilingPoint;
        }
        return 100.0;
    }

    private static final int[] dX = { 1, 0, -1, 0 };
    private static final int[] dY = { 0, 1, 0, -1 };
    private static final Random RAND = new Random();

    public static class SimulationResult {

        public int totalNeutronsGenerated = 0;
        public int fastNeutronsAbsorbed = 0;
        public int thermalNeutronsAbsorbed = 0;
        public int wallNeutronsReflected = 0;
        public int wallNeutronsAbsorbed = 0;
        public double wallHeatPool = 0;
        public int neutronsEscaped = 0;
        public double maxTemperature = AMBIENT_TEMP;
        public double averageTemperature = AMBIENT_TEMP;
        public double averageReactivity = 0.0;
        public double totalHeatEU = 0;
    }

    /**
     * Formats neutron flux into a realistic physical unit (multiples of 10¹³ n/cm²s).
     */
    public static String formatNeutronFlux(int neutronsProduced) {
        if (neutronsProduced <= 0) {
            return "0 n/cm²s";
        }
        double physicalFlux = (double) neutronsProduced * 1.0e13;
        return String.format(java.util.Locale.US, "%.2e n/cm²s", physicalFlux)
            .replace("+0", "")
            .replace("+", "");
    }

    /**
     * Executes one reactor simulation tick over the 2D grid with full (100%) maintenance efficiency.
     */
    public static SimulationResult simulate(INuclearTile[][] grid, int sizeX, int sizeY) {
        return simulate(grid, sizeX, sizeY, 1.0);
    }

    /**
     * Executes one reactor simulation tick over the 2D grid with specified maintenance efficiency factor [0.0, 1.0].
     */
    public static SimulationResult simulate(INuclearTile[][] grid, int sizeX, int sizeY, double maintenanceEfficiency) {
        SimulationResult result = new SimulationResult();
        if (grid == null || sizeX <= 0 || sizeY <= 0) return result;

        double sumTemp = 0;
        int activeTileCount = 0;
        double sumFuelReactivity = 0;
        int fuelTileCount = 0;

        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                INuclearTile tile = grid[x][y];
                if (tile != null) {
                    double temp = tile.getTemperature();
                    if (temp > result.maxTemperature) {
                        result.maxTemperature = temp;
                    }
                    sumTemp += temp;
                    activeTileCount++;
                    if (tile.isFuel()) {
                        sumFuelReactivity += calculateEfficiency(temp);
                        fuelTileCount++;
                    }
                }
            }
        }
        if (activeTileCount > 0) {
            result.averageTemperature = sumTemp / activeTileCount;
        }
        result.averageReactivity = (fuelTileCount > 0) ? (sumFuelReactivity / fuelTileCount) : 0.0;

        // 1. NEUTRON GENERATION & RAYCASTING
        for (int i = 0; i < sizeX; i++) {
            for (int j = 0; j < sizeY; j++) {
                INuclearTile tile = grid[i][j];
                if (tile == null || !tile.isFuel()) continue;

                double localEfficiency = calculateEfficiency(tile.getTemperature());
                int neutronsProduced = tile.generateNeutrons(localEfficiency);
                if (neutronsProduced <= 0) continue;

                result.totalNeutronsGenerated += neutronsProduced;
                // Direct fission heat
                tile.addHeat(neutronsProduced * fissionHeatPerNeutron);

                int splits = Math.min(neutronsProduced, 20);
                int neutronsPerSplit = neutronsProduced / splits;

                for (int s = 0; s < splits; s++) {
                    int batch = (s == splits - 1) ? (neutronsProduced - neutronsPerSplit * (splits - 1))
                        : neutronsPerSplit;
                    if (batch <= 0) continue;

                    NeutronType type = NeutronType.FAST;
                    int dir = RAND.nextInt(4);
                    int posX = i + dX[dir];
                    int posY = j + dY[dir];
                    int steps = 0;
                    final int MAX_STEPS = (sizeX + sizeY) * 2;

                    while (steps++ < MAX_STEPS) {
                        boolean isOutOfBounds = (posX < 0 || posX >= sizeX || posY < 0 || posY >= sizeY);
                        boolean isNullCell = !isOutOfBounds && (grid[posX][posY] == null);

                        if (isOutOfBounds || isNullCell) {
                            // Hit reactor outer wall or corner null cell!
                            if (RAND.nextDouble() < wallReflectionChance) {
                                // Wall reflection: bounce back in the direction it came from!
                                result.wallNeutronsReflected += batch;
                                dir = (dir + 2) % 4; // reverse 180 degrees
                                posX += dX[dir];
                                posY += dY[dir];
                                // Moderate fast neutrons slightly on wall bounce (casing reflection)
                                if (type == NeutronType.FAST && RAND.nextDouble() < 0.25) {
                                    type = NeutronType.THERMAL;
                                }
                                continue;
                            } else {
                                // Absorbed by the walls and turn into heat!
                                result.wallNeutronsAbsorbed += batch;
                                double heat = batch * wallAbsorbHeatPerNeutron;
                                result.wallHeatPool += heat;
                                break;
                            }
                        }

                        INuclearTile hitTile = grid[posX][posY];
                        if (hitTile != null) {
                            hitTile.addNeutronFlux(type, batch);

                            double pAbsorb = hitTile.getAbsorptionProbability(type);
                            double pScatter = hitTile.getScatteringProbability(type);
                            double pTotal = Math.min(1.0, pAbsorb + pScatter);

                            if (RAND.nextDouble() < pTotal) {
                                double selector = RAND.nextDouble() * pTotal;
                                if (selector <= pAbsorb) {
                                    // Absorbed!
                                    hitTile.onNeutronAbsorbed(type, batch);
                                    if (type == NeutronType.FAST) {
                                        hitTile.addHeat(batch * EU_FOR_FAST_NEUTRON);
                                        result.fastNeutronsAbsorbed += batch;
                                    } else {
                                        result.thermalNeutronsAbsorbed += batch;
                                    }
                                    break;
                                } else {
                                    // Scattered!
                                    hitTile.onNeutronScattered(type, batch);
                                    dir = RAND.nextInt(4);
                                    if (type == NeutronType.FAST
                                        && RAND.nextDouble() < hitTile.getModerationProbability()) {
                                        type = NeutronType.THERMAL;
                                        hitTile.addHeat(batch * EU_FOR_FAST_NEUTRON);
                                    }
                                }
                            }
                        }

                        posX += dX[dir];
                        posY += dY[dir];
                    }

                    if (steps >= MAX_STEPS) {
                        result.neutronsEscaped += batch;
                    }
                }
            }
        }

        // 2. HEAT DIFFUSION ACROSS THE GRID
        final int SUBSTEPS = 5;
        double[][] deltaTemp = new double[sizeX][sizeY];

        for (int sub = 0; sub < SUBSTEPS; sub++) {
            for (int x = 0; x < sizeX; x++) {
                for (int y = 0; y < sizeY; y++) {
                    deltaTemp[x][y] = 0;
                }
            }

            for (int x = 0; x < sizeX; x++) {
                for (int y = 0; y < sizeY; y++) {
                    INuclearTile tileA = grid[x][y];
                    if (tileA == null) continue;

                    double tempA = tileA.getTemperature();
                    double coeffA = Math.max(BASE_HEAT_CONDUCTION, tileA.getHeatTransferCoeff());

                    for (int k = 0; k < 4; k++) {
                        int nx = x + dX[k];
                        int ny = y + dY[k];

                        if (nx >= 0 && nx < sizeX && ny >= 0 && ny < sizeY) {
                            INuclearTile tileB = grid[nx][ny];
                            if (tileB != null) {
                                double tempB = tileB.getTemperature();
                                double coeffB = Math.max(BASE_HEAT_CONDUCTION, tileB.getHeatTransferCoeff());
                                double transferCoeff = 0.5 * (coeffA + coeffB) / SUBSTEPS;
                                if (tempA > tempB) {
                                    double flow = (tempA - tempB) * transferCoeff;
                                    deltaTemp[x][y] -= flow;
                                    deltaTemp[nx][ny] += flow;
                                }
                            } else {
                                // Radiation to casing / null cell wall
                                double loss = (tempA - AMBIENT_TEMP) * (0.5 * coeffA / SUBSTEPS);
                                deltaTemp[x][y] -= loss;
                            }
                        } else {
                            // Core edge boundary heat loss to outer wall
                            double loss = (tempA - AMBIENT_TEMP) * (coeffA / (SUBSTEPS * 2.0));
                            deltaTemp[x][y] -= loss;
                        }
                    }
                }
            }

            for (int x = 0; x < sizeX; x++) {
                for (int y = 0; y < sizeY; y++) {
                    INuclearTile tile = grid[x][y];
                    if (tile != null) {
                        tile.setTemperature(Math.max(AMBIENT_TEMP, tile.getTemperature() + deltaTemp[x][y]));
                    }
                }
            }
        }

        // 3. TILE NUCLEAR UPDATE (Durability, fluid boiling, cooling, transmutation)
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                INuclearTile tile = grid[x][y];
                if (tile != null) {
                    double tileEfficiency = tile.isFuel() ? calculateEfficiency(tile.getTemperature())
                        : Math.max(0.0, Math.min(1.0, maintenanceEfficiency));
                    tile.nuclearTick(tileEfficiency);
                }
            }
        }

        // 4. WALL HEAT DISTRIBUTION
        // Heat dissipated into the walls is added to the reactor-structure heat increment pool,
        // and distributed in equal shares to all non-null cells.
        result.totalHeatEU += result.wallHeatPool;
        if (activeTileCount > 0 && result.wallHeatPool > 0) {
            double heatPerCell = result.wallHeatPool / activeTileCount;
            for (int x = 0; x < sizeX; x++) {
                for (int y = 0; y < sizeY; y++) {
                    INuclearTile tile = grid[x][y];
                    if (tile != null) {
                        tile.addHeat(heatPerCell);
                    }
                }
            }
        }

        // Recalculate max and average temperature, and average reactivity over fuel cells at end of tick
        result.maxTemperature = AMBIENT_TEMP;
        sumTemp = 0;
        sumFuelReactivity = 0;
        fuelTileCount = 0;
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                INuclearTile tile = grid[x][y];
                if (tile != null) {
                    double temp = tile.getTemperature();
                    if (temp > result.maxTemperature) {
                        result.maxTemperature = temp;
                    }
                    sumTemp += temp;
                    if (tile.isFuel()) {
                        sumFuelReactivity += calculateEfficiency(temp);
                        fuelTileCount++;
                    }
                }
            }
        }
        if (activeTileCount > 0) {
            result.averageTemperature = sumTemp / activeTileCount;
        }
        result.averageReactivity = (fuelTileCount > 0) ? (sumFuelReactivity / fuelTileCount) : 0.0;

        return result;
    }

    /**
     * Checks if (x,y) in an N x N grid is a cut corner (null cell).
     */
    public static boolean isCornerNullCell(int x, int y, int sizeX, int sizeY) {
        if (sizeX != sizeY) return false;
        int n = sizeX;
        if (n <= 3) {
            return (x == 0 || x == n - 1) && (y == 0 || y == n - 1);
        } else if (n <= 7) {
            return (x == 0 || x == n - 1) && (y == 0 || y == n - 1);
        } else {
            boolean xCorner = (x == 0 || x == n - 1);
            boolean yCorner = (y == 0 || y == n - 1);
            boolean xSub = (x == 1 || x == n - 2);
            boolean ySub = (y == 1 || y == n - 2);
            return (xCorner && yCorner) || (xCorner && ySub) || (xSub && yCorner);
        }
    }

    /**
     * Reactivity efficiency curve: self-stabilizing negative temperature feedback.
     */
    public static double calculateEfficiency(double avgTemp) {
        if (avgTemp <= tempThresholdLow) {
            return 1.0;
        } else if (avgTemp >= tempThresholdHigh) {
            return 0.0;
        } else {
            double fraction = (avgTemp - tempThresholdLow) / (tempThresholdHigh - tempThresholdLow);
            return Math.max(0.0, 1.0 - Math.pow(fraction, reactivityPower));
        }
    }
}
