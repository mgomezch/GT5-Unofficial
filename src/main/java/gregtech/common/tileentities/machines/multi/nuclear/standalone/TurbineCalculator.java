package gregtech.common.tileentities.machines.multi.nuclear.standalone;

/**
 * Calculates optimal power generation and turbine requirements for XLST, XLST-HP, XLST-SC,
 * and Extreme Heat Exchanger (EHE) cascading stages based on GTNH Wiki formulas.
 */
public class TurbineCalculator {

    public enum TurbineMaterial {

        HSS_E("HSS-E", 1.75, 1.16, 76800, 1409106),
        HSS_S("HSS-S", 1.85, 1.23, 76800, 1705018),
        ELVEN_ELEMENTIUM("Elven Elementium", 1.75, 1.16, 48000, 880691),
        ORINARUKON("Oriharukon", 1.55, 1.05, 76800, 1100000),
        SHADOW_METAL("Shadow Metal", 1.45, 0.98, 76800, 950000),
        ICHORIUM("Ichorium", 2.25, 1.52, 460800, 12000000),
        DURANIUM("Duranium", 2.15, 1.66, 1228800, 103778507),
        GAIA_SPIRIT("Gaia Spirit", 1.95, 1.74, 1500000, 7848249),
        ADAMANTIUM("Adamantium", 1.80, 1.58, 1200000, 5360459),
        EXT_UNST_NAQUADAH("Ext. Unst. Naquadah", 1.90, 1.66, 4000000, 20269239),
        COSMIC_NEUTRONIUM("Cosmic Neutronium", 2.00, 1.74, 5000000, 23544749),
        INFINITY("Infinity", 2.40, 2.12, 35000000, 162850748),
        TUNGSTENSTEEL("Tungstensteel", 1.30, 0.88, 48000, 600000),
        TITANIUM("Titanium", 1.20, 0.81, 32000, 400000);

        public final String displayName;
        public final double tightEff;
        public final double looseEff;
        public final double optFlowLargeTight;
        public final double optFlowLargeLoose;

        TurbineMaterial(String displayName, double tightEff, double looseEff, double optFlowLargeTight,
            double optFlowLargeLoose) {
            this.displayName = displayName;
            this.tightEff = tightEff;
            this.looseEff = looseEff;
            this.optFlowLargeTight = optFlowLargeTight;
            this.optFlowLargeLoose = optFlowLargeLoose;
        }

        public static TurbineMaterial fromString(String name) {
            if (name == null) return HSS_E;
            for (TurbineMaterial m : values()) {
                if (m.name()
                    .equalsIgnoreCase(name) || m.displayName.equalsIgnoreCase(name)) {
                    return m;
                }
            }
            return HSS_E;
        }
    }

    public enum TurbineSize {

        SMALL("Small", 1.0 / 3.0),
        NORMAL("Normal", 2.0 / 3.0),
        LARGE("Large", 1.0),
        HUGE("Huge", 4.0 / 3.0);

        public final String displayName;
        public final double multiplier;

        TurbineSize(String displayName, double multiplier) {
            this.displayName = displayName;
            this.multiplier = multiplier;
        }

        public static TurbineSize fromString(String name) {
            if (name == null) return LARGE;
            for (TurbineSize s : values()) {
                if (s.name()
                    .equalsIgnoreCase(name) || s.displayName.equalsIgnoreCase(name)) {
                    return s;
                }
            }
            return LARGE;
        }
    }

    public enum FittingMode {

        TIGHT("Tight Fitting (Max Efficiency)"),
        LOOSE("Loose Fitting (High Throughput)");

        public final String displayName;

        FittingMode(String displayName) {
            this.displayName = displayName;
        }

        public static FittingMode fromString(String name) {
            if (name == null) return TIGHT;
            for (FittingMode m : values()) {
                if (m.name()
                    .equalsIgnoreCase(name) || m.displayName.equalsIgnoreCase(name)) {
                    return m;
                }
            }
            return TIGHT;
        }
    }

    public static class PowerEstimationResult {

        public double totalPowerEUt = 0;
        public double xlstPowerEUt = 0;
        public double xlstHpPowerEUt = 0;
        public double xlstScPowerEUt = 0;
        public double directPowerEUt = 0;

        public String eheMode = "Inactive";
        public double eheSteamProduced = 0;
        public double eheDistilledWaterConsumed = 0;

        public double xlstTurbinesNeeded = 0;
        public double xlstHpTurbinesNeeded = 0;
        public double xlstScTurbinesNeeded = 0;

        public double efficiency = 1.0;
        public double optFlowPerTurbine = 0;
    }

    public static PowerEstimationResult calculatePower(double regularSteamFlow, double superheatedSteamFlow,
        double supercriticalSteamFlow, double heavyWaterSteamFlow, double hpHeavyWaterSteamFlow, double hotCoolantFlow,
        double directPowerEU, TurbineMaterial material, TurbineSize size, FittingMode mode) {
        PowerEstimationResult res = new PowerEstimationResult();

        boolean isTight = (mode == FittingMode.TIGHT);
        res.efficiency = isTight ? material.tightEff : material.looseEff;
        double baseOptFlow = isTight ? material.optFlowLargeTight : material.optFlowLargeLoose;
        res.optFlowPerTurbine = baseOptFlow * size.multiplier;

        // 1. EHE (Extreme Heat Exchanger) calculation for Hot Coolant
        double effectiveSuperheated = superheatedSteamFlow;
        double effectiveSupercritical = supercriticalSteamFlow;

        if (hotCoolantFlow > 0) {
            double coolantLPerSec = hotCoolantFlow * 20.0;
            // 8,000 L/s threshold for supercritical steam in EHE
            if (coolantLPerSec >= 8000.0) {
                res.eheMode = "Supercritical (>= 8,000 L/s)";
                double scSteam = hotCoolantFlow * 200.0;
                effectiveSupercritical += scSteam;
                res.eheSteamProduced = scSteam;
            } else {
                res.eheMode = "Superheated (< 8,000 L/s)";
                double shSteam = hotCoolantFlow * 200.0;
                effectiveSuperheated += shSteam;
                res.eheSteamProduced = shSteam;
            }
            res.eheDistilledWaterConsumed = res.eheSteamProduced / 160.0;
        }

        // 2. XLST-SC (Supercritical Turbine): 1.0 EU/L at optimum flow
        double scFlowTotal = effectiveSupercritical + hpHeavyWaterSteamFlow;
        if (scFlowTotal > 0) {
            res.xlstScPowerEUt = scFlowTotal * 1.0 * res.efficiency;
            res.xlstScTurbinesNeeded = (res.optFlowPerTurbine > 0) ? (scFlowTotal / res.optFlowPerTurbine) : 0;
            // Cascades 1:1 into Superheated Steam (or Heavy Water Steam)
            effectiveSuperheated += effectiveSupercritical;
            heavyWaterSteamFlow += hpHeavyWaterSteamFlow;
        }

        // 3. XLST-HP (High Pressure / Superheated Turbine): 1.0 EU/L at optimum flow
        double hpFlowTotal = effectiveSuperheated + heavyWaterSteamFlow;
        if (hpFlowTotal > 0) {
            res.xlstHpPowerEUt = hpFlowTotal * 1.0 * res.efficiency;
            res.xlstHpTurbinesNeeded = (res.optFlowPerTurbine > 0) ? (hpFlowTotal / res.optFlowPerTurbine) : 0;
            // Regular superheated cascades 1:1 into Regular Steam; Heavy Water steam condenses back to Heavy Water
            regularSteamFlow += effectiveSuperheated;
        }

        // 4. XLST (Regular Steam Turbine): 0.5 EU/L at optimum flow
        if (regularSteamFlow > 0) {
            res.xlstPowerEUt = regularSteamFlow * 0.5 * res.efficiency;
            res.xlstTurbinesNeeded = (res.optFlowPerTurbine > 0) ? (regularSteamFlow / res.optFlowPerTurbine) : 0;
        }

        res.directPowerEUt = directPowerEU;
        res.totalPowerEUt = res.xlstScPowerEUt + res.xlstHpPowerEUt + res.xlstPowerEUt + directPowerEU;
        return res;
    }

    public static PowerEstimationResult calculatePower(double regularSteamFlow, double superheatedSteamFlow,
        double supercriticalSteamFlow, double heavyWaterSteamFlow, double hpHeavyWaterSteamFlow, double hotCoolantFlow,
        TurbineMaterial material, TurbineSize size, FittingMode mode) {
        return calculatePower(
            regularSteamFlow,
            superheatedSteamFlow,
            supercriticalSteamFlow,
            heavyWaterSteamFlow,
            hpHeavyWaterSteamFlow,
            hotCoolantFlow,
            0.0,
            material,
            size,
            mode);
    }
}
