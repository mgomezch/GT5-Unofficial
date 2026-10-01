package gregtech.common.tileentities.machines.multi.nuclear;

import java.util.Random;

public class NuclearSimulationEngine {

    public static final double EU_FOR_FAST_NEUTRON = 8.0;
    public static final double EU_PER_DEGREE = 64.0;
    public static final double BASE_HEAT_CONDUCTION = 0.01;
    public static final double DEFAULT_AMBIENT_TEMP = 20.0;
    public static double ambientTemp = DEFAULT_AMBIENT_TEMP;
    public static double AMBIENT_TEMP = DEFAULT_AMBIENT_TEMP;
    public static final double DEFAULT_TEMP_THRESHOLD_LOW = 800.0;
    public static final double DEFAULT_TEMP_THRESHOLD_HIGH = 3200.0;
    public static final double DEFAULT_REACTIVITY_POWER = 1.2;
    public static final double DEFAULT_THERMAL_FISSION_MULT = 1.30;
    public static final double DEFAULT_FISSION_HEAT_PER_NEUTRON = 38.0;
    public static final double DEFAULT_HP_WATER_BOILING_POINT = 180.0;

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

    public static final int DEFAULT_HATCH_CAPACITY = 8000;
    public static final TurnoverCurve DEFAULT_TURNOVER_CURVE = TurnoverCurve.SIGMOID;
    public static final double DEFAULT_TURNOVER_DELTA_T_MAX = 100.0;
    public static final double DEFAULT_TURNOVER_EXPONENT = 1.0;
    public static final int DEFAULT_COOLANT_FEED_RATE = 999999;
    public static final double DEFAULT_COOLING_HEAT_PER_LITER = 5.0;
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

    public static final double DEFAULT_FUEL_BURNUP_MULTIPLIER = 0.005;
    public static double fuelBurnupMultiplier = DEFAULT_FUEL_BURNUP_MULTIPLIER;

    public static final double DEFAULT_WALL_REFLECTION_CHANCE = 0.0;
    public static double wallReflectionChance = DEFAULT_WALL_REFLECTION_CHANCE;
    public static final double DEFAULT_WALL_ABSORB_HEAT_PER_NEUTRON = 0.0;
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
        if (name.contains("naquadah")) return 999; // Disallow Naquadah liquid fuels to avoid overlap with LNR
        if (name.contains("coolant") && !name.contains("hot")) return PIPE_TIER_ELECTRUM;
        if (name.contains("distilledwater") && !name.contains("highpressure")) return PIPE_TIER_PLATINUM;
        if (name.contains("highpressuredistilledwater")) return PIPE_TIER_OSMIUM;
        if (name.contains("heavywater") && !name.contains("highpressure")) return PIPE_TIER_QUANTIUM;
        if (name.contains("highpressureheavywater")) return PIPE_TIER_FLUXED_ELECTRUM;

        // Nuclear liquid fuels
        if (name.contains("thoriumbasedliquidfuel") || (name.contains("thorium") && name.contains("liquidfuel"))) {
            return name.contains("excited") ? PIPE_TIER_FLUXED_ELECTRUM : PIPE_TIER_ELECTRUM;
        }
        if (name.contains("uraniumbasedliquidfuel") || (name.contains("uranium") && name.contains("liquidfuel"))) {
            return name.contains("excited") ? PIPE_TIER_QUANTIUM : PIPE_TIER_PLATINUM;
        }
        if (name.contains("plutoniumbasedliquidfuel") || (name.contains("plutonium") && name.contains("liquidfuel"))) {
            return name.contains("excited") ? PIPE_TIER_FLUXED_ELECTRUM : PIPE_TIER_OSMIUM;
        }
        if (name.contains("uraniumhexafluoride")) return PIPE_TIER_OSMIUM;

        return 999;
    }

    public static double getCoolingOperatingThreshold(String fluidName) {
        return getCoolingOperatingThreshold(fluidName, ambientTemp);
    }

    public static double getCoolingOperatingThreshold(String fluidName, double ambient) {
        if (fluidName == null || fluidName.contains("coolant")) {
            return ambient;
        }
        if (fluidName.contains("highpressure")) {
            return hpWaterBoilingPoint;
        }
        return 100.0;
    }

    private static final int[] dX = { 1, 0, -1, 0, 1, -1, 1, -1 };
    private static final int[] dY = { 0, 1, 0, -1, 1, 1, -1, -1 };
    private static final double[] DIR_WEIGHTS = { 1.0 / 6.0, 1.0 / 6.0, 1.0 / 6.0, 1.0 / 6.0, // 4 Cardinals (weight 1/6
                                                                                              // each)
        1.0 / 12.0, 1.0 / 12.0, 1.0 / 12.0, 1.0 / 12.0 // 4 Diagonals (weight 1/12 each)
    };
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
     * Executes one reactor simulation tick over the 2D grid with full (100%) maintenance efficiency and default ambient
     * temperature.
     */
    public static SimulationResult simulate(INuclearTile[][] grid, int sizeX, int sizeY) {
        return simulate(grid, sizeX, sizeY, 1.0, ambientTemp);
    }

    /**
     * Executes one reactor simulation tick over the 2D grid with specified maintenance efficiency factor [0.0, 1.0] and
     * default ambient temperature.
     */
    public static SimulationResult simulate(INuclearTile[][] grid, int sizeX, int sizeY, double maintenanceEfficiency) {
        return simulate(grid, sizeX, sizeY, maintenanceEfficiency, ambientTemp);
    }

    /**
     * Executes one reactor simulation tick over the 2D grid with specified maintenance efficiency and biome ambient
     * temperature.
     */
    public static SimulationResult simulate(INuclearTile[][] grid, int sizeX, int sizeY, double maintenanceEfficiency,
        double ambient) {
        SimulationResult result = new SimulationResult();
        if (grid == null || sizeX <= 0 || sizeY <= 0) return result;

        // --- PASS 1: FROZEN INITIAL STATE SNAPSHOT & SYNCHRONIZED NEUTRON EMISSION ---
        double sumTemp = 0;
        int activeTileCount = 0;
        double sumFuelReactivity = 0;
        int fuelTileCount = 0;

        double[][] initialTemp = new double[sizeX][sizeY];
        int[][] emittedNeutrons = new int[sizeX][sizeY];
        double[][] efficiency = new double[sizeX][sizeY];
        double[][] pendingHeat = new double[sizeX][sizeY];

        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                INuclearTile tile = grid[x][y];
                if (tile != null) {
                    activeTileCount++;
                    double temp = tile.getTemperature();
                    initialTemp[x][y] = temp;
                    if (temp > result.maxTemperature) {
                        result.maxTemperature = temp;
                    }
                    sumTemp += temp;
                    if (tile.isFuel()) {
                        double eff = calculateEfficiency(temp);
                        efficiency[x][y] = eff;
                        sumFuelReactivity += eff;
                        fuelTileCount++;

                        int produced = tile.generateNeutrons(eff);
                        if (produced > 0) {
                            emittedNeutrons[x][y] = produced;
                            result.totalNeutronsGenerated += produced;
                            // Prompt fission heat queued for atomic deposit in Pass 3
                            pendingHeat[x][y] += produced * fissionHeatPerNeutron;
                        }
                    } else {
                        efficiency[x][y] = Math.max(0.0, Math.min(1.0, maintenanceEfficiency));
                    }
                }
            }
        }
        if (activeTileCount > 0) {
            result.averageTemperature = sumTemp / activeTileCount;
        }
        result.averageReactivity = (fuelTileCount > 0) ? (sumFuelReactivity / fuelTileCount) : 0.0;

        // --- PASS 2: HYBRID NEUTRON MODEL (MI FAST RAYS + ISOTROPIC THERMAL STENCIL) ---
        // Fast neutrons emit as discrete directional rays with random directions based on fuel type:
        // single rods emit 1 ray, dual rods emit 2 rays, quad rods & fluid fuels emit 4 rays.
        // Fast rays undergo Monte Carlo random-walk scattering and reflection, or get absorbed.
        // When fast neutrons moderate in coolant/moderators, they convert into a thermal flux pool.
        // Thermal neutrons then diffuse omnidirectionally using the 9-point isotropic stencil.

        final int MAX_FAST_STEPS = (sizeX + sizeY) * 2;
        final int MAX_THERMAL_STEPS = Math.max(12, (sizeX + sizeY) * 2);

        double[][] thermalPool = new double[sizeX][sizeY];
        double[][] tileFluxFast = new double[sizeX][sizeY];
        double[][] tileFluxThermal = new double[sizeX][sizeY];
        double[][] tileAbsThermal = new double[sizeX][sizeY];

        // Part A: Fast Neutron Emission & Monte Carlo Propagation
        for (int i = 0; i < sizeX; i++) {
            for (int j = 0; j < sizeY; j++) {
                int N = emittedNeutrons[i][j];
                if (N <= 0) continue;

                INuclearTile fuelTile = grid[i][j];
                int rayCount = (fuelTile != null) ? Math.max(1, fuelTile.getNeutronEmissionCount()) : 1;
                int nPerRay = N / rayCount;
                int rem = N % rayCount;

                for (int r = 0; r < rayCount; r++) {
                    int rayNeutrons = nPerRay + (r < rem ? 1 : 0);
                    if (rayNeutrons <= 0) continue;

                    int dir = RAND.nextInt(4);
                    int posX = i;
                    int posY = j;
                    int steps = 0;

                    while (steps++ < MAX_FAST_STEPS) {
                        posX += dX[dir];
                        posY += dY[dir];

                        boolean isOutOfBounds = (posX < 0 || posX >= sizeX || posY < 0 || posY >= sizeY);
                        boolean isNullCell = !isOutOfBounds && (grid[posX][posY] == null);

                        if (isOutOfBounds) {
                            if (wallReflectionChance > 0 && RAND.nextDouble() < wallReflectionChance) {
                                result.wallNeutronsReflected += rayNeutrons;
                                dir = (dir + 2) % 4;
                                posX += dX[dir];
                                posY += dY[dir];
                                continue;
                            } else {
                                result.neutronsEscaped += rayNeutrons;
                                break;
                            }
                        }

                        if (isNullCell) {
                            result.neutronsEscaped += rayNeutrons;
                            break;
                        }

                        INuclearTile hitTile = grid[posX][posY];
                        hitTile.addNeutronFlux(NeutronType.FAST, rayNeutrons);
                        tileFluxFast[posX][posY] += rayNeutrons;

                        double pAbs = hitTile.getAbsorptionProbability(NeutronType.FAST);
                        double pScatter = hitTile.getScatteringProbability(NeutronType.FAST);
                        double pTotal = Math.min(1.0, pAbs + pScatter);

                        if (RAND.nextDouble() < pTotal) {
                            double pAbsRel = (pTotal > 0) ? (pAbs / pTotal) : 0.0;
                            if (RAND.nextDouble() < pAbsRel) {
                                hitTile.onNeutronAbsorbed(NeutronType.FAST, rayNeutrons);
                                result.fastNeutronsAbsorbed += rayNeutrons;
                                if (hitTile.getInsulationDampening() < 1.0) {
                                    pendingHeat[posX][posY] += rayNeutrons * EU_FOR_FAST_NEUTRON;
                                }
                                break;
                            } else {
                                hitTile.onNeutronScattered(NeutronType.FAST, rayNeutrons);
                                if (pScatter >= 0.90 && pAbs <= 0.05) {
                                    dir = (dir + 2) % 4;
                                } else {
                                    dir = RAND.nextInt(4);
                                }

                                double pMod = hitTile.getModerationProbability();
                                if (RAND.nextDouble() < pMod) {
                                    if (hitTile.getInsulationDampening() < 1.0) {
                                        pendingHeat[posX][posY] += rayNeutrons * EU_FOR_FAST_NEUTRON;
                                    }
                                    thermalPool[posX][posY] += rayNeutrons;
                                    break;
                                }
                            }
                        }
                    }

                    if (steps >= MAX_FAST_STEPS) {
                        result.neutronsEscaped += rayNeutrons;
                    }
                }
            }
        }

        // Part B: Thermal Neutron 9-Point Isotropic Diffusion Stencil
        double[][] activeThermal = thermalPool;

        for (int step = 0; step < MAX_THERMAL_STEPS; step++) {
            double totalActive = 0.0;
            for (int x = 0; x < sizeX; x++) {
                for (int y = 0; y < sizeY; y++) {
                    totalActive += activeThermal[x][y];
                }
            }
            if (totalActive < 0.001) break;

            double[][] nextThermal = new double[sizeX][sizeY];

            for (int x = 0; x < sizeX; x++) {
                for (int y = 0; y < sizeY; y++) {
                    double arrivingT = activeThermal[x][y];
                    if (arrivingT <= 0.0001) continue;

                    INuclearTile hitTile = grid[x][y];
                    if (hitTile == null) {
                        result.neutronsEscaped += (int) Math.round(arrivingT);
                        continue;
                    }

                    tileFluxThermal[x][y] += arrivingT;

                    double pAbsT = hitTile.getAbsorptionProbability(NeutronType.THERMAL);
                    double absT = arrivingT * pAbsT;
                    tileAbsThermal[x][y] += absT;

                    if (hitTile.getInsulationDampening() < 1.0) {
                        if (absT > 0) {
                            if (hitTile.isFuel()) {
                                pendingHeat[x][y] += absT * fissionHeatPerNeutron * 1.25;
                            } else {
                                pendingHeat[x][y] += absT * (EU_FOR_FAST_NEUTRON * 0.5);
                            }
                        }
                    }

                    double survT = Math.max(0.0, arrivingT - absT);

                    for (int d = 0; d < 8; d++) {
                        double w = DIR_WEIGHTS[d];
                        double outT = survT * w;
                        int nx = x + dX[d];
                        int ny = y + dY[d];

                        boolean isOutOfBounds = (nx < 0 || nx >= sizeX || ny < 0 || ny >= sizeY);
                        boolean isNullCell = !isOutOfBounds && (grid[nx][ny] == null);

                        if (isOutOfBounds) {
                            if (wallReflectionChance > 0) {
                                double reflT = outT * wallReflectionChance;
                                nextThermal[x][y] += reflT;
                                result.wallNeutronsReflected += (int) Math.round(reflT);
                                result.neutronsEscaped += (int) Math.round(outT * (1.0 - wallReflectionChance));
                            } else {
                                result.neutronsEscaped += (int) Math.round(outT);
                            }
                        } else if (isNullCell) {
                            result.neutronsEscaped += (int) Math.round(outT);
                        } else {
                            nextThermal[nx][ny] += outT;
                        }
                    }
                }
            }

            activeThermal = nextThermal;
        }

        // Account for any remaining residual thermal flux
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                double residual = activeThermal[x][y];
                if (residual > 0.001) {
                    result.neutronsEscaped += (int) Math.round(residual);
                }
            }
        }

        // Execute thermal callbacks
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                INuclearTile tile = grid[x][y];
                if (tile == null) continue;

                int fT = (int) Math.round(tileFluxThermal[x][y]);
                if (fT > 0) tile.addNeutronFlux(NeutronType.THERMAL, fT);

                int aT = (int) Math.round(tileAbsThermal[x][y]);
                if (aT > 0) {
                    tile.onNeutronAbsorbed(NeutronType.THERMAL, aT);
                    result.thermalNeutronsAbsorbed += aT;
                }
            }
        }

        // --- PASS 3: ATOMIC NUCLEAR HEAT DEPOSITION ---
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                INuclearTile tile = grid[x][y];
                if (tile != null && pendingHeat[x][y] > 0.0) {
                    tile.addHeat(pendingHeat[x][y]);
                    result.totalHeatEU += pendingHeat[x][y];
                }
            }
        }

        // --- PASS 4: MULTI-SUBSTEP DISCRETE HEAT CONDUCTION & BOUNDARY LOSS ---
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

                        if (nx >= 0 && nx < sizeX && ny >= 0 && ny < sizeY && grid[nx][ny] != null) {
                            INuclearTile tileB = grid[nx][ny];
                            double tempB = tileB.getTemperature();
                            double coeffB = Math.max(BASE_HEAT_CONDUCTION, tileB.getHeatTransferCoeff());
                            double transferCoeff = 0.5 * (coeffA + coeffB) / SUBSTEPS;
                            if (tempA > tempB) {
                                double dampening = Math
                                    .max(tileA.getInsulationDampening(), tileB.getInsulationDampening());
                                double flow = (tempA - tempB) * transferCoeff
                                    * (1.0 - Math.min(1.0, Math.max(0.0, dampening)));
                                deltaTemp[x][y] -= flow;
                                deltaTemp[nx][ny] += flow;
                            }
                        } else {
                            // Heat loss to empty space / outer walls at ambient temperature
                            double dampening = tileA.getInsulationDampening();
                            double loss = (tempA - ambient) * (coeffA / (2.0 * SUBSTEPS))
                                * (1.0 - Math.min(1.0, Math.max(0.0, dampening)));
                            deltaTemp[x][y] -= loss;
                        }
                    }
                }
            }

            for (int x = 0; x < sizeX; x++) {
                for (int y = 0; y < sizeY; y++) {
                    INuclearTile tile = grid[x][y];
                    if (tile != null) {
                        tile.setTemperature(Math.max(ambient, tile.getTemperature() + deltaTemp[x][y]));
                    }
                }
            }
        }

        // --- PASS 5: TILE NUCLEAR UPDATE (BOILING, COOLING, DURABILITY, TRANSMUTATION) ---
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                INuclearTile tile = grid[x][y];
                if (tile != null) {
                    tile.nuclearTick(efficiency[x][y]);
                }
            }
        }

        // --- PASS 6: WALL HEAT DISTRIBUTION ---
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

        // --- PASS 7: END-OF-TICK TELEMETRY & METRICS ---
        result.maxTemperature = ambient;
        result.averageTemperature = ambient;
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
     * Tier 1 (5x5): depth 1 (4 corners cut)
     * Tier 2 (9x9): depth 2 (12 corners cut)
     * Tier 3 (13x13): depth 3 (24 corners cut)
     */
    public static boolean isCornerNullCell(int x, int y, int sizeX, int sizeY) {
        if (sizeX != sizeY) return false;
        int n = sizeX;
        int cutDepth = Math.max(1, (n - 5) / 4 + 1);
        int dX = Math.min(x, n - 1 - x);
        int dY = Math.min(y, n - 1 - y);
        return (dX + dY) < cutDepth;
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
