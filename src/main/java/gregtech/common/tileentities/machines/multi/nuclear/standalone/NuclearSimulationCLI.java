package gregtech.common.tileentities.machines.multi.nuclear.standalone;

import gregtech.common.tileentities.machines.multi.nuclear.NuclearSimulationEngine;

/**
 * Command-line interface tool for batch-testing and simulating GTNH nuclear reactor designs
 * outside the game context using the mod's native NuclearSimulationEngine.
 */
public class NuclearSimulationCLI {

    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_PURPLE = "\u001B[35m";
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_WHITE_BOLD = "\u001B[1;37m";

    public static void main(String[] args) {
        String preset = "BASIC_ELECTRUM_5X5";
        String layout = null;
        int size = 5;
        int ticks = 100;
        int tier = NuclearSimulationEngine.PIPE_TIER_ELECTRUM;
        boolean tierSpecified = false;
        boolean turbSpecified = false;
        boolean startWeb = false;
        int webPort = 8085;
        TurbineCalculator.TurbineMaterial turbMat = TurbineCalculator.TurbineMaterial.HSS_E;
        TurbineCalculator.TurbineSize turbSize = TurbineCalculator.TurbineSize.LARGE;
        TurbineCalculator.FittingMode turbFitting = TurbineCalculator.FittingMode.TIGHT;
        boolean jsonOutput = false;
        boolean batchMode = false;
        boolean traceEnabled = false;
        int traceSteps = 50;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--batch" -> {
                    batchMode = true;
                }
                case "--web" -> {
                    startWeb = true;
                    if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                        try {
                            webPort = Integer.parseInt(args[++i]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--layout" -> {
                    if (i + 1 < args.length) layout = args[++i];
                }
                case "--preset" -> {
                    if (i + 1 < args.length) preset = args[++i];
                }
                case "--size" -> {
                    if (i + 1 < args.length) {
                        try {
                            size = Integer.parseInt(args[++i]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--ticks" -> {
                    if (i + 1 < args.length) {
                        try {
                            ticks = Integer.parseInt(args[++i]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--tier" -> {
                    if (i + 1 < args.length) {
                        tierSpecified = true;
                        String t = args[++i].toLowerCase();
                        if (t.contains("elec")) tier = NuclearSimulationEngine.PIPE_TIER_ELECTRUM;
                        else if (t.contains("plat")) tier = NuclearSimulationEngine.PIPE_TIER_PLATINUM;
                        else if (t.contains("osmi")) tier = NuclearSimulationEngine.PIPE_TIER_OSMIUM;
                        else if (t.contains("quan")) tier = NuclearSimulationEngine.PIPE_TIER_QUANTIUM;
                        else if (t.contains("flux")) tier = NuclearSimulationEngine.PIPE_TIER_FLUXED_ELECTRUM;
                        else if (t.contains("plut")) tier = NuclearSimulationEngine.PIPE_TIER_BLACK_PLUTONIUM;
                        else {
                            try {
                                tier = Integer.parseInt(t);
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
                case "--material", "--turb-mat" -> {
                    if (i + 1 < args.length) {
                        turbSpecified = true;
                        turbMat = TurbineCalculator.TurbineMaterial.fromString(args[++i]);
                    }
                }
                case "--turb-size" -> {
                    if (i + 1 < args.length) {
                        turbSpecified = true;
                        turbSize = TurbineCalculator.TurbineSize.fromString(args[++i]);
                    }
                }
                case "--fitting", "--turb-fit" -> {
                    if (i + 1 < args.length) {
                        turbSpecified = true;
                        turbFitting = TurbineCalculator.FittingMode.fromString(args[++i]);
                    }
                }
                case "--temp-low" -> {
                    if (i + 1 < args.length) {
                        try {
                            NuclearSimulationEngine.tempThresholdLow = Double.parseDouble(args[++i]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--temp-high" -> {
                    if (i + 1 < args.length) {
                        try {
                            NuclearSimulationEngine.tempThresholdHigh = Double.parseDouble(args[++i]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--reactivity-pow" -> {
                    if (i + 1 < args.length) {
                        try {
                            NuclearSimulationEngine.reactivityPower = Double.parseDouble(args[++i]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--fission-mult" -> {
                    if (i + 1 < args.length) {
                        try {
                            NuclearSimulationEngine.thermalFissionMultiplier = Double.parseDouble(args[++i]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--fission-heat" -> {
                    if (i + 1 < args.length) {
                        try {
                            NuclearSimulationEngine.fissionHeatPerNeutron = Double.parseDouble(args[++i]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--hp-boil" -> {
                    if (i + 1 < args.length) {
                        try {
                            NuclearSimulationEngine.hpWaterBoilingPoint = Double.parseDouble(args[++i]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--hatch-cap" -> {
                    if (i + 1 < args.length) {
                        try {
                            NuclearSimulationEngine.hatchCoolantCapacity = Integer.parseInt(args[++i]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--turnover-curve" -> {
                    if (i + 1 < args.length) {
                        NuclearSimulationEngine.turnoverCurve = NuclearSimulationEngine.TurnoverCurve
                            .fromString(args[++i]);
                    }
                }
                case "--turnover-dt" -> {
                    if (i + 1 < args.length) {
                        try {
                            NuclearSimulationEngine.turnoverDeltaTMax = Double.parseDouble(args[++i]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--turnover-pow", "--turnover-exp" -> {
                    if (i + 1 < args.length) {
                        try {
                            NuclearSimulationEngine.turnoverExponent = Double.parseDouble(args[++i]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--feed-rate" -> {
                    if (i + 1 < args.length) {
                        try {
                            NuclearSimulationEngine.coolantFeedRate = Integer.parseInt(args[++i]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--cooling-heat" -> {
                    if (i + 1 < args.length) {
                        try {
                            NuclearSimulationEngine.coolingHeatPerLiter = Double.parseDouble(args[++i]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--ambient-temp", "--ambient" -> {
                    if (i + 1 < args.length) {
                        try {
                            NuclearSimulationEngine.setAmbientTemperature(Double.parseDouble(args[++i]));
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--ic2-heat", "--ic2-cooling-heat" -> {
                    if (i + 1 < args.length) {
                        try {
                            NuclearSimulationEngine.ic2CoolantHeatPerLiter = Double.parseDouble(args[++i]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--burnup-mult" -> {
                    if (i + 1 < args.length) {
                        try {
                            NuclearSimulationEngine.fuelBurnupMultiplier = Double.parseDouble(args[++i]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--trace" -> {
                    traceEnabled = true;
                    if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                        try {
                            traceSteps = Integer.parseInt(args[++i]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--json" -> {
                    jsonOutput = true;
                }
                case "--help", "-h" -> {
                    printHelp();
                    return;
                }
                default -> {}
            }
        }

        if (startWeb) {
            NuclearSimulationWebServer.startServer(webPort);
            return;
        }

        if (batchMode) {
            runBatchLoop();
            return;
        }

        if (jsonOutput) {
            runJsonSimulation(
                preset,
                layout,
                size,
                tier,
                ticks,
                turbMat,
                turbSize,
                turbFitting,
                tierSpecified,
                turbSpecified);
        } else {
            runCliSimulation(
                preset,
                layout,
                size,
                tier,
                ticks,
                turbMat,
                turbSize,
                turbFitting,
                tierSpecified,
                turbSpecified,
                traceEnabled,
                traceSteps);
        }
    }

    private static void runBatchLoop() {
        try (java.io.BufferedReader br = new java.io.BufferedReader(
            new java.io.InputStreamReader(System.in, java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) break;

                String preset = "BREEDER_7X7";
                String layout = null;
                int size = 7;
                int tier = NuclearSimulationEngine.PIPE_TIER_PLATINUM;
                int ticks = 300;
                TurbineCalculator.TurbineMaterial turbMat = TurbineCalculator.TurbineMaterial.HSS_E;
                TurbineCalculator.TurbineSize turbSize = TurbineCalculator.TurbineSize.LARGE;
                TurbineCalculator.FittingMode turbFitting = TurbineCalculator.FittingMode.TIGHT;

                boolean tierSpecified = false;
                boolean turbSpecified = false;

                String[] tokens = line.split("\\s+");
                for (String token : tokens) {
                    int eq = token.indexOf('=');
                    if (eq <= 0) continue;
                    String k = token.substring(0, eq)
                        .toLowerCase();
                    String v = token.substring(eq + 1);
                    switch (k) {
                        case "preset" -> preset = v;
                        case "layout" -> layout = v;
                        case "size" -> {
                            try {
                                size = Integer.parseInt(v);
                            } catch (Exception ignored) {}
                        }
                        case "tier" -> {
                            tierSpecified = true;
                            String t = v.toLowerCase();
                            if (t.contains("elec")) tier = NuclearSimulationEngine.PIPE_TIER_ELECTRUM;
                            else if (t.contains("plat")) tier = NuclearSimulationEngine.PIPE_TIER_PLATINUM;
                            else if (t.contains("osmi")) tier = NuclearSimulationEngine.PIPE_TIER_OSMIUM;
                            else if (t.contains("quan")) tier = NuclearSimulationEngine.PIPE_TIER_QUANTIUM;
                            else if (t.contains("flux")) tier = NuclearSimulationEngine.PIPE_TIER_FLUXED_ELECTRUM;
                            else if (t.contains("plut")) tier = NuclearSimulationEngine.PIPE_TIER_BLACK_PLUTONIUM;
                            else {
                                try {
                                    tier = Integer.parseInt(t);
                                } catch (Exception ignored) {}
                            }
                        }
                        case "ticks" -> {
                            try {
                                ticks = Integer.parseInt(v);
                            } catch (Exception ignored) {}
                        }
                        case "temp_low", "templow" -> {
                            try {
                                NuclearSimulationEngine.tempThresholdLow = Double.parseDouble(v);
                            } catch (Exception ignored) {}
                        }
                        case "temp_high", "temphigh" -> {
                            try {
                                NuclearSimulationEngine.tempThresholdHigh = Double.parseDouble(v);
                            } catch (Exception ignored) {}
                        }
                        case "reactivity_pow", "reactivitypow" -> {
                            try {
                                NuclearSimulationEngine.reactivityPower = Double.parseDouble(v);
                            } catch (Exception ignored) {}
                        }
                        case "fission_mult", "fissionmult" -> {
                            try {
                                NuclearSimulationEngine.thermalFissionMultiplier = Double.parseDouble(v);
                            } catch (Exception ignored) {}
                        }
                        case "fission_heat", "fissionheat" -> {
                            try {
                                NuclearSimulationEngine.fissionHeatPerNeutron = Double.parseDouble(v);
                            } catch (Exception ignored) {}
                        }
                        case "hp_boil", "hpboil" -> {
                            try {
                                NuclearSimulationEngine.hpWaterBoilingPoint = Double.parseDouble(v);
                            } catch (Exception ignored) {}
                        }
                        case "hatch_cap", "hatchcap" -> {
                            try {
                                NuclearSimulationEngine.hatchCoolantCapacity = Integer.parseInt(v);
                            } catch (Exception ignored) {}
                        }
                        case "turnover_curve", "turnovercurve" -> NuclearSimulationEngine.turnoverCurve = NuclearSimulationEngine.TurnoverCurve
                            .fromString(v);
                        case "turnover_dt", "turnoverdt" -> {
                            try {
                                NuclearSimulationEngine.turnoverDeltaTMax = Double.parseDouble(v);
                            } catch (Exception ignored) {}
                        }
                        case "turnover_pow", "turnover_exp", "turnoverexp" -> {
                            try {
                                NuclearSimulationEngine.turnoverExponent = Double.parseDouble(v);
                            } catch (Exception ignored) {}
                        }
                        case "feed_rate", "feedrate" -> {
                            try {
                                NuclearSimulationEngine.coolantFeedRate = Integer.parseInt(v);
                            } catch (Exception ignored) {}
                        }
                        case "cooling_heat", "coolingheat" -> {
                            try {
                                NuclearSimulationEngine.coolingHeatPerLiter = Double.parseDouble(v);
                            } catch (Exception ignored) {}
                        }
                        case "ambient_temp", "ambient" -> {
                            try {
                                NuclearSimulationEngine.setAmbientTemperature(Double.parseDouble(v));
                            } catch (Exception ignored) {}
                        }
                        case "ic2_heat", "ic2_cooling_heat" -> {
                            try {
                                NuclearSimulationEngine.ic2CoolantHeatPerLiter = Double.parseDouble(v);
                            } catch (Exception ignored) {}
                        }
                        case "burnup_mult", "burnupmult" -> {
                            try {
                                NuclearSimulationEngine.fuelBurnupMultiplier = Double.parseDouble(v);
                            } catch (Exception ignored) {}
                        }
                        case "material", "turb_mat" -> {
                            turbMat = TurbineCalculator.TurbineMaterial.fromString(v);
                            turbSpecified = true;
                        }
                        case "turb_size" -> {
                            turbSize = TurbineCalculator.TurbineSize.fromString(v);
                            turbSpecified = true;
                        }
                        case "fitting", "turb_fit" -> {
                            turbFitting = TurbineCalculator.FittingMode.fromString(v);
                            turbSpecified = true;
                        }
                        default -> {}
                    }
                }
                runJsonSimulation(
                    preset,
                    layout,
                    size,
                    tier,
                    ticks,
                    turbMat,
                    turbSize,
                    turbFitting,
                    tierSpecified,
                    turbSpecified);
                System.out.flush();
            }
        } catch (java.io.IOException e) {
            System.err.println("Batch error: " + e.getMessage());
        }
    }

    private static void runJsonSimulation(String preset, String layout, int size, int tier, int ticks,
        TurbineCalculator.TurbineMaterial turbMat, TurbineCalculator.TurbineSize turbSize,
        TurbineCalculator.FittingMode turbFitting, boolean tierSpecified, boolean turbSpecified) {
        StandaloneNuclearGrid grid = new StandaloneNuclearGrid(size, size, tier);
        if (layout != null && !layout.trim()
            .isEmpty()) {
            grid.loadLayout(layout);
            if (tierSpecified) grid.setPipeTier(tier);
        } else {
            grid.loadPreset(preset);
            if (tierSpecified) grid.setPipeTier(tier);
        }
        if (turbSpecified) {
            grid.setTurbineMaterial(turbMat);
            grid.setTurbineSize(turbSize);
            grid.setTurbineFitting(turbFitting);
        }

        int tickReached = 0;
        for (int t = 1; t <= ticks; t++) {
            tickReached = t;
            boolean ok = grid.step();
            if (!ok) break;
        }

        TurbineCalculator.PowerEstimationResult p = grid.getLastPowerResult();
        double power = p != null ? p.totalPowerEUt : 0.0;
        double minLongevity = grid.getMinFuelRodLongevityMinutes();
        double avgLongevity = grid.getAvgFuelRodLongevityMinutes();
        int activeFuelRods = grid.getActiveFuelRodCount();
        int totalDurabilityLost = grid.getTotalDurabilityLost();
        int actualTier = grid.getPipeTier();
        long tierVoltage = NuclearSimulationEngine.getPipeTierVoltage(actualTier);
        String nominalTierName = NuclearSimulationEngine.getPipeTierVoltageName(actualTier);
        double targetPower60A = 60.0 * tierVoltage;
        double actualAmps = power / (double) tierVoltage;

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"preset\":\"")
            .append(preset)
            .append("\",");
        sb.append("\"layout\":\"")
            .append(grid.toLayoutString())
            .append("\",");
        sb.append("\"size\":")
            .append(grid.getWidth())
            .append(",");
        sb.append("\"tier\":")
            .append(actualTier)
            .append(",");
        sb.append("\"ticksSimulated\":")
            .append(tickReached)
            .append(",");
        sb.append("\"ticksTarget\":")
            .append(ticks)
            .append(",");
        sb.append("\"exploded\":")
            .append(grid.isExploded())
            .append(",");
        sb.append("\"explosionReason\":\"")
            .append(
                grid.getExplosionReason()
                    .replace("\"", "\\\""))
            .append("\",");
        sb.append("\"powerFailed\":")
            .append(grid.isPowerFailed())
            .append(",");
        sb.append("\"powerFailReason\":\"")
            .append(
                grid.getPowerFailReason()
                    .replace("\"", "\\\""))
            .append("\",");
        sb.append("\"coreMaxTemp\":")
            .append(String.format(java.util.Locale.US, "%.2f", grid.getCoreMaxTemp()))
            .append(",");
        sb.append("\"coreAvgTemp\":")
            .append(String.format(java.util.Locale.US, "%.2f", grid.getCoreAvgTemp()))
            .append(",");
        sb.append("\"efficiency\":")
            .append(String.format(java.util.Locale.US, "%.4f", grid.getEfficiency()))
            .append(",");
        sb.append("\"totalNeutrons\":")
            .append(grid.getTotalNeutronsGenerated())
            .append(",");
        sb.append("\"totalSteam\":")
            .append(grid.getTotalSteamProduced())
            .append(",");
        sb.append("\"totalDeuterium\":")
            .append(grid.getTotalDeuteriumProduced())
            .append(",");
        sb.append("\"totalTritium\":")
            .append(grid.getTotalTritiumProduced())
            .append(",");
        sb.append("\"powerEUt\":")
            .append(String.format(java.util.Locale.US, "%.1f", power))
            .append(",");
        sb.append("\"directPowerEUt\":")
            .append(String.format(java.util.Locale.US, "%.1f", p != null ? p.directPowerEUt : 0.0))
            .append(",");
        sb.append("\"totalTurbinesNeeded\":")
            .append(
                String.format(
                    java.util.Locale.US,
                    "%.2f",
                    p != null ? (p.xlstTurbinesNeeded + p.xlstHpTurbinesNeeded + p.xlstScTurbinesNeeded) : 0.0))
            .append(",");
        sb.append("\"xlstTurbinesNeeded\":")
            .append(String.format(java.util.Locale.US, "%.2f", p != null ? p.xlstTurbinesNeeded : 0.0))
            .append(",");
        sb.append("\"xlstHpTurbinesNeeded\":")
            .append(String.format(java.util.Locale.US, "%.2f", p != null ? p.xlstHpTurbinesNeeded : 0.0))
            .append(",");
        sb.append("\"xlstScTurbinesNeeded\":")
            .append(String.format(java.util.Locale.US, "%.2f", p != null ? p.xlstScTurbinesNeeded : 0.0))
            .append(",");
        sb.append("\"eheMode\":\"")
            .append(p != null ? p.eheMode : "Inactive")
            .append("\",");
        sb.append("\"eheSteamProduced\":")
            .append(String.format(java.util.Locale.US, "%.1f", p != null ? p.eheSteamProduced : 0.0))
            .append(",");
        sb.append("\"voltageTier\":\"")
            .append(getVoltageTier(power))
            .append("\",");
        sb.append("\"activeFuelRods\":")
            .append(activeFuelRods)
            .append(",");
        sb.append("\"totalDurabilityLost\":")
            .append(totalDurabilityLost)
            .append(",");
        sb.append("\"minLongevityMinutes\":")
            .append(
                Double.isInfinite(minLongevity) ? "\"Infinity\""
                    : String.format(java.util.Locale.US, "%.2f", minLongevity))
            .append(",");
        sb.append("\"avgLongevityMinutes\":")
            .append(
                Double.isInfinite(avgLongevity) ? "\"Infinity\""
                    : String.format(java.util.Locale.US, "%.2f", avgLongevity))
            .append(",");
        sb.append("\"casingNominalTier\":\"")
            .append(nominalTierName)
            .append("\",");
        sb.append("\"casingVoltage\":")
            .append(tierVoltage)
            .append(",");
        sb.append("\"targetPower60A\":")
            .append(String.format(java.util.Locale.US, "%.1f", targetPower60A))
            .append(",");
        sb.append("\"actualAmps\":")
            .append(String.format(java.util.Locale.US, "%.2f", actualAmps))
            .append(",");
        sb.append("\"fuelBurnupMultiplier\":")
            .append(NuclearSimulationEngine.fuelBurnupMultiplier)
            .append(",");
        sb.append("\"tempThresholdLow\":")
            .append(NuclearSimulationEngine.tempThresholdLow)
            .append(",");
        sb.append("\"tempThresholdHigh\":")
            .append(NuclearSimulationEngine.tempThresholdHigh)
            .append(",");
        sb.append("\"reactivityPower\":")
            .append(NuclearSimulationEngine.reactivityPower)
            .append(",");
        sb.append("\"thermalFissionMultiplier\":")
            .append(NuclearSimulationEngine.thermalFissionMultiplier)
            .append(",");
        sb.append("\"fissionHeatPerNeutron\":")
            .append(NuclearSimulationEngine.fissionHeatPerNeutron)
            .append(",");
        sb.append("\"hatchCoolantCapacity\":")
            .append(NuclearSimulationEngine.hatchCoolantCapacity)
            .append(",");
        sb.append("\"turnoverCurve\":\"")
            .append(NuclearSimulationEngine.turnoverCurve.name())
            .append("\",");
        sb.append("\"turnoverDeltaTMax\":")
            .append(NuclearSimulationEngine.turnoverDeltaTMax)
            .append(",");
        sb.append("\"turnoverExponent\":")
            .append(NuclearSimulationEngine.turnoverExponent)
            .append(",");
        sb.append("\"coolantFeedRate\":")
            .append(NuclearSimulationEngine.coolantFeedRate)
            .append(",");
        sb.append("\"coolingHeatPerLiter\":")
            .append(NuclearSimulationEngine.coolingHeatPerLiter)
            .append(",");
        sb.append("\"ambientTemp\":")
            .append(NuclearSimulationEngine.ambientTemp)
            .append(",");
        sb.append("\"ic2CoolantHeatPerLiter\":")
            .append(NuclearSimulationEngine.ic2CoolantHeatPerLiter);
        sb.append("}");
        System.out.println(sb.toString());
    }

    private static void printHelp() {
        System.out.println(ANSI_WHITE_BOLD + "GTNH Nuclear Simulation CLI & Diagnostic Tool" + ANSI_RESET);
        System.out.println("Usage: gtnh-nuclear-sim [options]");
        System.out.println("Options:");
        System.out.println(
            "  --preset <name>       Preset layout: BEST_ELECTRUM_5X5, BEST_PLATINUM_7X7, BEST_OSMIUM_7X7, BEST_QUANTIUM_9X9, BEST_FLUXED_9X9, BEST_PLUTONIUM_9X9");
        System.out.println("  --size <N>            Grid dimensions N x N (default 5)");
        System.out.println(
            "  --tier <name/#>       Pipe casing tier: electrum(0), platinum(1), osmium(2), quantium(3), fluxed(4), black_plutonium(5)");
        System.out.println("  --ticks <N>           Simulation duration in ticks (default 100)");
        System.out.println("  --material <name>     Turbine rotor material (default HSS-E)");
        System.out.println("  --turb-size <size>    Turbine size: small, normal, large, huge (default large)");
        System.out.println("  --fitting <mode>      Housing fitting: tight, loose (default tight)");
        System.out.println("  --hatch-cap <L>       Hatch coolant capacity in liters (default 2000)");
        System.out.println(
            "  --turnover-curve <C>  Turnover curve: EXPONENTIAL, LINEAR, SIGMOID, STEP (default EXPONENTIAL)");
        System.out.println("  --turnover-dt <T>     Turnover delta-T max in °C above boiling (default 100.0)");
        System.out.println("  --turnover-pow <P>    Turnover power-law exponent (default 1.5)");
        System.out.println("  --feed-rate <L/t>     Coolant refill rate per tick (default 2000)");
        System.out
            .println("  --cooling-heat <H>    Latent cooling heat extracted per liter of steam in EU (default 4.0)");
        System.out.println("  --ambient-temp <T>    Ambient temperature baseline in °C (default 24.0)");
        System.out.println(
            "  --ic2-heat <H>        Continuous cooling heat extracted per liter of IC2 coolant in EU (default 20.0)");
        System.out.println("  --temp-low <T>        Negative reactivity low threshold in °C (default 800)");
        System.out.println("  --temp-high <T>       Negative reactivity high threshold in °C (default 2800)");
        System.out.println("  --reactivity-pow <P>  Negative reactivity curve exponent (default 1.2)");
        System.out.println("  --layout <codes>      Custom layout string (rows separated by ';', cells by ',')");
        System.out.println("  --fission-mult <K>    Thermal neutron induced fission multiplier (default 1.1)");
        System.out.println("  --fission-heat <H>    Direct heat EU generated per fission neutron (default 18.0)");
        System.out.println("  --hp-boil <T>         High pressure coolant boiling point in °C (default 200.0)");
        System.out.println(
            "  --trace [N]           Enable intermediate step snapshot ring buffer (up to N steps, default 500)");
        System.out.println("  --json                Output compact JSON summary for automated test scripts");
        System.out.println("  --web [port]          Launch standalone Web GUI on specified port (default 8085)");
        System.out.println("  --help, -h            Display this help message");
    }

    private static void runCliSimulation(String preset, String layout, int size, int tier, int ticks,
        TurbineCalculator.TurbineMaterial turbMat, TurbineCalculator.TurbineSize turbSize,
        TurbineCalculator.FittingMode turbFitting, boolean tierSpecified, boolean turbSpecified, boolean traceEnabled,
        int traceSteps) {
        System.out.println(ANSI_CYAN + "============================================================" + ANSI_RESET);
        System.out.println(ANSI_WHITE_BOLD + "   GTNH MODULAR NUCLEAR REACTOR STANDALONE SIMULATOR" + ANSI_RESET);
        System.out.println(ANSI_CYAN + "============================================================" + ANSI_RESET);

        StandaloneNuclearGrid grid = new StandaloneNuclearGrid(size, size, tier);
        if (traceEnabled) {
            grid.enableDiagnosticTrace(traceSteps);
        }
        if (layout != null && !layout.trim()
            .isEmpty()) {
            grid.loadLayout(layout);
            if (tierSpecified) grid.setPipeTier(tier);
        } else {
            grid.loadPreset(preset);
            if (tierSpecified) grid.setPipeTier(tier);
        }
        if (turbSpecified) {
            grid.setTurbineMaterial(turbMat);
            grid.setTurbineSize(turbSize);
            grid.setTurbineFitting(turbFitting);
        }

        if (layout != null && !layout.trim()
            .isEmpty()) {
            System.out.println("Layout:        " + ANSI_GREEN + grid.toLayoutString() + ANSI_RESET);
        } else {
            System.out.println("Preset:        " + ANSI_GREEN + preset + ANSI_RESET);
        }
        System.out.println("Grid Size:     " + grid.getWidth() + "x" + grid.getHeight());
        System.out.println("Casing Tier:   " + NuclearSimulationEngine.getPipeTierName(grid.getPipeTier()));
        System.out.println(
            "Max Safe Temp: " + NuclearSimulationEngine.getMaxOperatingTemperature(grid.getPipeTier()) + " °C");
        TurbineCalculator.TurbineMaterial activeMat = grid.getTurbineMaterial();
        TurbineCalculator.TurbineSize activeSize = grid.getTurbineSize();
        TurbineCalculator.FittingMode activeFit = grid.getTurbineFitting();
        System.out.println(
            "Turbine Rotor: " + (activeMat != null ? activeMat.displayName : "None")
                + " ("
                + (activeSize != null ? activeSize.name() : "None")
                + ", "
                + (activeFit != null ? activeFit.name() : "None")
                + ")");
        System.out.println("Ticks to Sim:  " + ticks);
        System.out.println();

        System.out.println(ANSI_WHITE_BOLD + "Initial Grid Layout:" + ANSI_RESET);
        System.out.print(grid.toAscii());
        System.out.println();

        long startTime = System.currentTimeMillis();
        int reportedInterval = Math.max(1, ticks / 10);

        System.out.println(
            String.format(
                "%-8s | %-12s | %-12s | %-10s | %-10s | %-14s",
                "Tick",
                "Max Temp (°C)",
                "Avg Temp (°C)",
                "Efficiency",
                "Neutrons",
                "Power (EU/t)"));
        System.out.println("------------------------------------------------------------------------------");

        for (int t = 1; t <= ticks; t++) {
            boolean ok = grid.step();
            if (!ok) {
                System.out.println();
                System.out.println(ANSI_RED + ">>> EXPLOSION OCCURRED AT TICK " + t + " <<<" + ANSI_RESET);
                System.out.println(ANSI_RED + "Cause: " + grid.getExplosionReason() + ANSI_RESET);
                break;
            }

            if (t % reportedInterval == 0 || t == ticks) {
                TurbineCalculator.PowerEstimationResult pr = grid.getLastPowerResult();
                double powerEUt = pr != null ? pr.totalPowerEUt : 0.0;
                String tierName = getVoltageTier(powerEUt);
                System.out.println(
                    String.format(
                        "%-8d | %-12.1f | %-12.1f | %-10.2f | %-10d | %-14s",
                        t,
                        grid.getCoreMaxTemp(),
                        grid.getCoreAvgTemp(),
                        grid.getEfficiency(),
                        grid.getLastNeutronsProduced(),
                        String.format("%.0f (%s)", powerEUt, tierName)));
            }
        }

        long elapsedMs = System.currentTimeMillis() - startTime;
        System.out.println("------------------------------------------------------------------------------");
        System.out.println(ANSI_GREEN + "Simulation complete in " + elapsedMs + " ms." + ANSI_RESET);
        System.out.println();

        if (grid.isNegativeTempDetected() || grid.isDiagnosticTraceEnabled()) {
            java.util.List<StandaloneNuclearGrid.IntermediateStepSnapshot> trace = grid.getStepTrace();
            if (!trace.isEmpty()) {
                System.out
                    .println(ANSI_YELLOW + "============================================================" + ANSI_RESET);
                System.out.println(
                    ANSI_YELLOW + "   DIAGNOSTIC INTERMEDIATE STEP TRACE ("
                        + trace.size()
                        + " steps in memory)"
                        + ANSI_RESET);
                System.out
                    .println(ANSI_YELLOW + "============================================================" + ANSI_RESET);
                for (StandaloneNuclearGrid.IntermediateStepSnapshot snap : trace) {
                    System.out.println(snap.toString());
                }
                System.out
                    .println(ANSI_YELLOW + "============================================================" + ANSI_RESET);
                System.out.println();
            }
        }

        printFinalReport(grid);
    }

    private static void printFinalReport(StandaloneNuclearGrid grid) {
        System.out.println(ANSI_CYAN + "=== FINAL SIMULATION TELEMETRY ===" + ANSI_RESET);
        System.out.println(
            "Status:                 "
                + (grid.isExploded() ? ANSI_RED + "DESTROYED (Meltdown)" : ANSI_GREEN + "STABLE / OPERATIONAL")
                + ANSI_RESET);
        System.out.println("Peak Core Temperature:  " + String.format("%.2f", grid.getCoreMaxTemp()) + " °C");
        System.out.println("Average Core Temp:      " + String.format("%.2f", grid.getCoreAvgTemp()) + " °C");
        System.out.println(
            "Casing Max Allowed:     " + NuclearSimulationEngine.getMaxOperatingTemperature(grid.getPipeTier())
                + " °C");
        System.out.println("Reactivity Efficiency:  " + String.format("%.2f %%", grid.getEfficiency() * 100.0));
        System.out.println("Total Neutrons Emitted: " + grid.getTotalNeutronsGenerated());
        System.out.println("Total Steam Produced:   " + grid.getTotalSteamProduced() + " L");
        System.out.println("Total Deuterium Bred:   " + grid.getTotalDeuteriumProduced() + " L");
        System.out.println("Total Tritium Bred:     " + grid.getTotalTritiumProduced() + " L");
        System.out.println("Total Energy Equivalent:" + String.format("%.0f EU", grid.getTotalEnergyEU()));
        System.out.println();

        TurbineCalculator.PowerEstimationResult p = grid.getLastPowerResult();
        if (p != null) {
            System.out.println(ANSI_WHITE_BOLD + "Turbine Power Generation (XLST & EHE at Optimum Flow):" + ANSI_RESET);
            System.out.println("  Rotor Material:       " + grid.getTurbineMaterial().displayName);
            System.out.println(
                "  Rotor Size / Fitting: " + grid.getTurbineSize()
                    .name()
                    + " ("
                    + grid.getTurbineSize().multiplier
                    + "x) / "
                    + grid.getTurbineFitting()
                        .name());
            System.out.println("  Rotor Efficiency:     " + String.format("%.2f %%", p.efficiency * 100.0));
            System.out.println("  Opt Flow / Turbine:   " + String.format("%.0f L/t", p.optFlowPerTurbine));
            System.out.println(
                "  Total Estimated Power:" + ANSI_GREEN
                    + String.format(" %.1f EU/t (%s)", p.totalPowerEUt, getVoltageTier(p.totalPowerEUt))
                    + ANSI_RESET);
            if (p.xlstScPowerEUt > 0) {
                System.out.println(
                    "    - XLST-SC Power:    "
                        + String.format("%.1f EU/t (%.2f turbines needed)", p.xlstScPowerEUt, p.xlstScTurbinesNeeded));
            }
            if (p.xlstHpPowerEUt > 0) {
                System.out.println(
                    "    - XLST-HP Power:    "
                        + String.format("%.1f EU/t (%.2f turbines needed)", p.xlstHpPowerEUt, p.xlstHpTurbinesNeeded));
            }
            if (p.xlstPowerEUt > 0) {
                System.out.println(
                    "    - XLST Power:       "
                        + String.format("%.1f EU/t (%.2f turbines needed)", p.xlstPowerEUt, p.xlstTurbinesNeeded));
            }
            if (p.directPowerEUt > 0) {
                System.out.println(
                    "    - Betavoltaic Direct EU: "
                        + String.format("%.1f EU/t (%s)", p.directPowerEUt, getVoltageTier(p.directPowerEUt)));
            }
            if (!"NONE".equals(p.eheMode)) {
                System.out.println(
                    "  EHE Status:           " + p.eheMode
                        + " (Produced: "
                        + String.format("%.1f L/t", p.eheSteamProduced)
                        + " steam, Consumed: "
                        + String.format("%.1f L/t", p.eheDistilledWaterConsumed)
                        + " DW)");
            }
            System.out.println(
                "  Instant Steam Flows:  " + String.format(
                    "Reg: %.1f | SH: %.1f | SC: %.1f | HW: %.1f | HP HW: %.1f L/t",
                    grid.getFlowRegularSteam(),
                    grid.getFlowSuperheatedSteam(),
                    grid.getFlowSupercriticalSteam(),
                    grid.getFlowHeavyWaterSteam(),
                    grid.getFlowHPHeavyWaterSteam()));
            System.out.println();
        }

        System.out.println(ANSI_WHITE_BOLD + "Core Heat Map (°C):" + ANSI_RESET);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                double temp = grid.getTile(x, y)
                    .getTemperature();
                String color = (temp > 1500) ? ANSI_PURPLE
                    : (temp > 800) ? ANSI_RED : (temp > 300) ? ANSI_YELLOW : ANSI_GREEN;
                System.out.print(color + String.format("%5.0f ", temp) + ANSI_RESET);
            }
            System.out.println();
        }
        System.out.println();
    }

    private static String getVoltageTier(double eut) {
        if (eut <= 0) return "OFF";
        if (eut <= 32) return "LV";
        if (eut <= 128) return "MV";
        if (eut <= 512) return "HV";
        if (eut <= 2048) return "EV";
        if (eut <= 8192) return "IV";
        if (eut <= 32768) return "LuV";
        if (eut <= 131072) return "ZPM";
        if (eut <= 524288) return "UV";
        if (eut <= 2097152) return "UHV";
        if (eut <= 8388608) return "UEV";
        if (eut <= 33554432) return "UIV";
        if (eut <= 134217728) return "UMV";
        return "UXV+";
    }
}
