package gregtech.common.tileentities.machines.multi.nuclear.standalone;

import java.util.Random;

import gregtech.common.tileentities.machines.multi.nuclear.INuclearTile;
import gregtech.common.tileentities.machines.multi.nuclear.NeutronType;
import gregtech.common.tileentities.machines.multi.nuclear.NuclearSimulationEngine;

/**
 * Pure Java implementation of INuclearTile for standalone simulation and tooling.
 * Completely decoupled from Minecraft, Forge, and IC2 runtime classes.
 */
public class SimTile implements INuclearTile {

    public enum TileType {

        EMPTY("Empty", "."),
        FUEL_URANIUM_SINGLE("Uranium Single", "U1"),
        FUEL_URANIUM_DUAL("Uranium Dual", "U2"),
        FUEL_URANIUM_QUAD("Uranium Quad", "U4"),
        FUEL_MOX_SINGLE("MOX Single", "M1"),
        FUEL_MOX_DUAL("MOX Dual", "M2"),
        FUEL_MOX_QUAD("MOX Quad", "M4"),
        FUEL_THORIUM_SINGLE("Thorium Single", "T1"),
        FUEL_THORIUM_DUAL("Thorium Dual", "T2"),
        FUEL_THORIUM_QUAD("Thorium Quad", "T4"),
        FUEL_NAQUADAH("Naquadah Rod", "NQ"),
        HATCH_DISTILLED_WATER("Distilled Water Hatch", "HD"),
        HATCH_HP_DISTILLED_WATER("HP Distilled Water Hatch", "HP"),
        HATCH_HEAVY_WATER("Heavy Water Hatch", "HW"),
        HATCH_HP_HEAVY_WATER("HP Heavy Water Hatch", "HH"),
        HATCH_IC2_COOLANT("IC2 Coolant Hatch", "HC"),
        REFLECTOR_BERYLLIUM("Beryllium Reflector", "RB"),
        REFLECTOR_CARBON("Carbon Reflector", "RC"),
        CONTROL_ROD("Boron Control Rod", "CR"),
        COOLANT_CELL_10K("10k Coolant Cell", "C1"),
        COOLANT_CELL_60K("60k Coolant Cell", "C6"),
        COOLANT_CELL_360K("360k Coolant Cell", "C3");

        public final String displayName;
        public final String code;

        TileType(String displayName, String code) {
            this.displayName = displayName;
            this.code = code;
        }

        public static TileType fromCode(String code) {
            if (code == null) return EMPTY;
            String trimmed = code.trim()
                .toUpperCase();
            for (TileType type : values()) {
                if (type.code.equalsIgnoreCase(trimmed) || type.name()
                    .equalsIgnoreCase(trimmed)) {
                    return type;
                }
            }
            return EMPTY;
        }
    }

    private static final Random RAND = new Random();

    private TileType type = TileType.EMPTY;
    private double temperature = NuclearSimulationEngine.AMBIENT_TEMP;
    private double heatEU = 0;

    // Fuel state
    private int maxDurability = 10000;
    private int durability = 10000;
    private double durabilityLossAccumulator = 0.0;
    private boolean depleted = false;
    private int lastNeutronsGenerated = 0;

    // Coolant hatch state
    private String inputFluidName = "";
    private int inputFluidAmount = 0;
    private int inputFluidCapacity = 16000;
    private String outputFluidName = "";
    private int outputFluidAmount = 0;
    private boolean wasDry = false;
    private boolean autoRefill = true;
    private int totalSteamProduced = 0;
    private int totalDeuteriumProduced = 0;
    private int totalTritiumProduced = 0;
    private int lastTickProduced = 0;

    // Coolant cell state
    private int maxCellHeat = 0;
    private int currentCellHeat = 0;

    // Transient flux stats
    private int fastFlux = 0;
    private int thermalFlux = 0;
    private int fastAbsorbed = 0;
    private int thermalAbsorbed = 0;
    private int lastThermalAbsorbed = 0;
    private String lastCoolingDetails = "";

    public String getLastCoolingDetails() {
        return lastCoolingDetails;
    }

    public SimTile(TileType type) {
        setType(type);
    }

    public void setType(TileType newType) {
        this.type = (newType == null) ? TileType.EMPTY : newType;
        this.depleted = false;
        this.wasDry = false;
        this.outputFluidAmount = 0;
        this.totalSteamProduced = 0;
        this.totalDeuteriumProduced = 0;
        this.totalTritiumProduced = 0;
        this.lastThermalAbsorbed = 0;
        this.durabilityLossAccumulator = 0.0;

        switch (this.type) {
            case FUEL_URANIUM_SINGLE, FUEL_URANIUM_DUAL, FUEL_URANIUM_QUAD -> {
                this.maxDurability = 20000;
                this.durability = 20000;
                this.inputFluidAmount = 0;
            }
            case FUEL_MOX_SINGLE, FUEL_MOX_DUAL, FUEL_MOX_QUAD -> {
                this.maxDurability = 10000;
                this.durability = 10000;
                this.inputFluidAmount = 0;
            }
            case FUEL_THORIUM_SINGLE, FUEL_THORIUM_DUAL, FUEL_THORIUM_QUAD -> {
                this.maxDurability = 50000;
                this.durability = 50000;
                this.inputFluidAmount = 0;
            }
            case FUEL_NAQUADAH -> {
                this.maxDurability = 100000;
                this.durability = 100000;
                this.inputFluidAmount = 0;
            }
            case HATCH_DISTILLED_WATER -> {
                this.inputFluidName = "distilledwater";
                this.inputFluidCapacity = NuclearSimulationEngine.hatchCoolantCapacity;
                this.inputFluidAmount = this.inputFluidCapacity;
                this.outputFluidName = "steam";
            }
            case HATCH_HP_DISTILLED_WATER -> {
                this.inputFluidName = "highpressuredistilledwater";
                this.inputFluidCapacity = NuclearSimulationEngine.hatchCoolantCapacity;
                this.inputFluidAmount = this.inputFluidCapacity;
                this.outputFluidName = "ic2superheatedsteam";
            }
            case HATCH_HEAVY_WATER -> {
                this.inputFluidName = "heavywater";
                this.inputFluidCapacity = NuclearSimulationEngine.hatchCoolantCapacity;
                this.inputFluidAmount = this.inputFluidCapacity;
                this.outputFluidName = "fluid.heavywatersteam";
            }
            case HATCH_HP_HEAVY_WATER -> {
                this.inputFluidName = "highpressureheavywater";
                this.inputFluidCapacity = NuclearSimulationEngine.hatchCoolantCapacity;
                this.inputFluidAmount = this.inputFluidCapacity;
                this.outputFluidName = "fluid.highpressureheavywatersteam";
            }
            case HATCH_IC2_COOLANT -> {
                this.inputFluidName = "ic2coolant";
                this.inputFluidCapacity = NuclearSimulationEngine.hatchCoolantCapacity;
                this.inputFluidAmount = this.inputFluidCapacity;
                this.outputFluidName = "ic2hotcoolant";
            }
            case COOLANT_CELL_10K -> {
                this.maxCellHeat = 10000;
                this.currentCellHeat = 0;
            }
            case COOLANT_CELL_60K -> {
                this.maxCellHeat = 60000;
                this.currentCellHeat = 0;
            }
            case COOLANT_CELL_360K -> {
                this.maxCellHeat = 360000;
                this.currentCellHeat = 0;
            }
            default -> {
                this.inputFluidAmount = 0;
                this.maxCellHeat = 0;
                this.currentCellHeat = 0;
            }
        }
    }

    public TileType getType() {
        return type;
    }

    @Override
    public double getTemperature() {
        return temperature;
    }

    @Override
    public void setTemperature(double temp) {
        this.temperature = Math.max(NuclearSimulationEngine.AMBIENT_TEMP, temp);
    }

    @Override
    public void addHeat(double heat) {
        this.heatEU += heat;
        this.temperature = Math
            .max(NuclearSimulationEngine.AMBIENT_TEMP, this.temperature + heat / NuclearSimulationEngine.EU_PER_DEGREE);
    }

    @Override
    public double getHeatTransferCoeff() {
        return switch (type) {
            case HATCH_IC2_COOLANT -> 0.50;
            case HATCH_DISTILLED_WATER, HATCH_HP_DISTILLED_WATER, HATCH_HEAVY_WATER, HATCH_HP_HEAVY_WATER -> 0.25;
            case COOLANT_CELL_10K, COOLANT_CELL_60K, COOLANT_CELL_360K -> 0.40;
            case REFLECTOR_BERYLLIUM, REFLECTOR_CARBON -> 0.15;
            case FUEL_URANIUM_SINGLE, FUEL_URANIUM_DUAL, FUEL_URANIUM_QUAD, FUEL_MOX_SINGLE, FUEL_MOX_DUAL, FUEL_MOX_QUAD, FUEL_THORIUM_SINGLE, FUEL_THORIUM_DUAL, FUEL_THORIUM_QUAD, FUEL_NAQUADAH -> 0.05;
            default -> 0.02;
        };
    }

    @Override
    public boolean isFuel() {
        if (depleted) return false;
        return switch (type) {
            case FUEL_URANIUM_SINGLE, FUEL_URANIUM_DUAL, FUEL_URANIUM_QUAD, FUEL_MOX_SINGLE, FUEL_MOX_DUAL, FUEL_MOX_QUAD, FUEL_THORIUM_SINGLE, FUEL_THORIUM_DUAL, FUEL_THORIUM_QUAD, FUEL_NAQUADAH -> true;
            default -> false;
        };
    }

    public boolean isHatch() {
        return switch (type) {
            case HATCH_DISTILLED_WATER, HATCH_HP_DISTILLED_WATER, HATCH_HEAVY_WATER, HATCH_HP_HEAVY_WATER, HATCH_IC2_COOLANT -> true;
            default -> false;
        };
    }

    @Override
    public int generateNeutrons(double efficiency) {
        if (!isFuel() || depleted) {
            lastNeutronsGenerated = 0;
            return 0;
        }

        int baseNeutrons = switch (type) {
            case FUEL_URANIUM_SINGLE -> 4;
            case FUEL_URANIUM_DUAL -> 8;
            case FUEL_URANIUM_QUAD -> 16;
            case FUEL_MOX_SINGLE -> 8;
            case FUEL_MOX_DUAL -> 16;
            case FUEL_MOX_QUAD -> 32;
            case FUEL_THORIUM_SINGLE -> 2;
            case FUEL_THORIUM_DUAL -> 4;
            case FUEL_THORIUM_QUAD -> 8;
            case FUEL_NAQUADAH -> 16;
            default -> 0;
        };

        int chainNeutrons = (int) Math.round(lastThermalAbsorbed * NuclearSimulationEngine.thermalFissionMultiplier);
        int produced = (int) Math.round((baseNeutrons + chainNeutrons) * efficiency);
        lastNeutronsGenerated = produced;
        return produced;
    }

    @Override
    public double getAbsorptionProbability(NeutronType nType) {
        if (type == TileType.CONTROL_ROD) {
            return (nType == NeutronType.THERMAL) ? 0.95 : 0.85;
        }
        if (isFuel()) {
            return (nType == NeutronType.THERMAL) ? 0.80 : 0.25;
        }
        if (isHatch()) {
            if (inputFluidAmount <= 0) return 0.01;
            return switch (type) {
                case HATCH_HEAVY_WATER, HATCH_HP_HEAVY_WATER -> (nType == NeutronType.THERMAL) ? 0.01 : 0.005;
                case HATCH_DISTILLED_WATER, HATCH_HP_DISTILLED_WATER -> (nType == NeutronType.THERMAL) ? 0.10 : 0.05;
                case HATCH_IC2_COOLANT -> (nType == NeutronType.THERMAL) ? 0.12 : 0.03;
                default -> 0.05;
            };
        }
        return switch (type) {
            case REFLECTOR_BERYLLIUM, REFLECTOR_CARBON -> 0.02;
            case COOLANT_CELL_10K, COOLANT_CELL_60K, COOLANT_CELL_360K -> 0.05;
            default -> 0.01;
        };
    }

    @Override
    public double getScatteringProbability(NeutronType nType) {
        if (type == TileType.REFLECTOR_BERYLLIUM || type == TileType.REFLECTOR_CARBON) {
            return 0.95;
        }
        if (isHatch()) {
            if (inputFluidAmount <= 0) return 0.02;
            return switch (type) {
                case HATCH_HEAVY_WATER, HATCH_HP_HEAVY_WATER -> 0.85;
                case HATCH_DISTILLED_WATER, HATCH_HP_DISTILLED_WATER -> 0.70;
                case HATCH_IC2_COOLANT -> 0.45;
                default -> 0.10;
            };
        }
        if (isFuel()) return 0.15;
        return 0.05;
    }

    @Override
    public double getModerationProbability() {
        if (type == TileType.REFLECTOR_BERYLLIUM || type == TileType.REFLECTOR_CARBON) {
            return 0.65;
        }
        if (isHatch()) {
            if (inputFluidAmount <= 0) return 0.05;
            return switch (type) {
                case HATCH_HEAVY_WATER, HATCH_HP_HEAVY_WATER -> 0.90;
                case HATCH_DISTILLED_WATER, HATCH_HP_DISTILLED_WATER -> 0.80;
                case HATCH_IC2_COOLANT -> 0.40;
                default -> 0.20;
            };
        }
        return 0.10;
    }

    @Override
    public void onNeutronAbsorbed(NeutronType nType, int count) {
        if (nType == NeutronType.FAST) fastAbsorbed += count;
        else thermalAbsorbed += count;

        if (isFuel() && nType == NeutronType.THERMAL) {
            // Fission chain reaction heat bonus
            addHeat(count * NuclearSimulationEngine.fissionHeatPerNeutron * 1.25);
        }

        // Fast neutron capture transmutation
        if (nType == NeutronType.FAST && isHatch() && inputFluidAmount > 0) {
            if (type == TileType.HATCH_DISTILLED_WATER || type == TileType.HATCH_HP_DISTILLED_WATER) {
                if (RAND.nextInt(100) < Math.min(100, count * 5)) {
                    inputFluidAmount -= 1;
                    totalDeuteriumProduced += 1;
                }
            } else if (type == TileType.HATCH_HEAVY_WATER || type == TileType.HATCH_HP_HEAVY_WATER) {
                if (RAND.nextInt(100) < Math.min(100, count * 5)) {
                    inputFluidAmount -= 1;
                    totalTritiumProduced += 1;
                }
            }
        }
    }

    @Override
    public void onNeutronScattered(NeutronType nType, int count) {}

    @Override
    public void addNeutronFlux(NeutronType nType, int count) {
        if (nType == NeutronType.FAST) fastFlux += count;
        else thermalFlux += count;
    }

    @Override
    public void nuclearTick(double efficiency) {
        lastThermalAbsorbed = thermalAbsorbed;
        lastTickProduced = 0;

        // 1. Fuel burnup
        if (isFuel() && !depleted) {
            double rawDamage = (fastAbsorbed * 1.0 + thermalAbsorbed * 2.0 + Math.max(1.0, lastNeutronsGenerated / 4.0))
                * NuclearSimulationEngine.fuelBurnupMultiplier;
            durabilityLossAccumulator += rawDamage;
            int intDamage = (int) durabilityLossAccumulator;
            if (intDamage > 0) {
                durability = Math.max(0, durability - intDamage);
                durabilityLossAccumulator -= intDamage;
                if (durability == 0) {
                    depleted = true;
                }
            }
        }

        fastFlux = 0;
        thermalFlux = 0;
        fastAbsorbed = 0;
        thermalAbsorbed = 0;

        // 2. Coolant cell absorption
        if (maxCellHeat > 0 && currentCellHeat < maxCellHeat) {
            double tempDiff = temperature - NuclearSimulationEngine.AMBIENT_TEMP;
            if (tempDiff > 0) {
                int heatToAbsorb = (int) Math
                    .min(tempDiff * NuclearSimulationEngine.EU_PER_DEGREE * 0.1, maxCellHeat - currentCellHeat);
                currentCellHeat += heatToAbsorb;
                temperature = Math.max(
                    NuclearSimulationEngine.AMBIENT_TEMP,
                    temperature - heatToAbsorb / NuclearSimulationEngine.EU_PER_DEGREE);
            }
        }

        // 3. Fluid cooling & heat exchange
        if (isHatch()) {
            if (type == TileType.HATCH_IC2_COOLANT) {
                double operatingThreshold = NuclearSimulationEngine.AMBIENT_TEMP;
                double heatPerL = NuclearSimulationEngine.ic2CoolantHeatPerLiter;

                if (inputFluidAmount > 0 && temperature > operatingThreshold) {
                    double deltaT = temperature - operatingThreshold;
                    double heatAvailable = deltaT * NuclearSimulationEngine.EU_PER_DEGREE;
                    int maxCoolByHeat = (heatPerL > 0) ? (int) Math.floor(heatAvailable / heatPerL) : inputFluidAmount;

                    double frac = NuclearSimulationEngine.calculateTurnoverFraction(deltaT);
                    int desiredTurnover = Math.max(1, (int) Math.round(inputFluidCapacity * frac));
                    int mbToCool = Math.min(inputFluidAmount, Math.min(desiredTurnover, maxCoolByHeat));

                    if (mbToCool > 0) {
                        inputFluidAmount -= mbToCool;
                        lastTickProduced = mbToCool;
                        outputFluidAmount += mbToCool;
                        totalSteamProduced += mbToCool;

                        double heatConsumed = mbToCool * heatPerL;
                        double tempDrop = heatConsumed / NuclearSimulationEngine.EU_PER_DEGREE;
                        double tempBefore = temperature;
                        temperature = Math.max(operatingThreshold, temperature - tempDrop);
                        lastCoolingDetails = String.format(
                            java.util.Locale.US,
                            "IC2 Coolant %dL (cap %dL, frac %.2f, maxByHeat %dL), consumed %.1f EU, temp %.1f°C -> %.1f°C (drop %.1f°C)",
                            mbToCool,
                            inputFluidCapacity,
                            frac,
                            maxCoolByHeat,
                            heatConsumed,
                            tempBefore,
                            temperature,
                            tempDrop);
                    }
                }

                if (inputFluidAmount <= 0) {
                    inputFluidAmount = 0;
                    wasDry = true;
                }
            } else {
                // Phase-change boiling hatches (Distilled Water, HP Distilled Water, Heavy Water, HP Heavy Water)
                double boilingPoint = 100.0;
                double heatPerL = NuclearSimulationEngine.coolingHeatPerLiter;
                int steamRatio = 160;

                switch (type) {
                    case HATCH_HP_HEAVY_WATER -> {
                        boilingPoint = NuclearSimulationEngine.hpWaterBoilingPoint;
                        heatPerL = NuclearSimulationEngine.coolingHeatPerLiter * 4.0;
                        steamRatio = 160;
                    }
                    case HATCH_HP_DISTILLED_WATER -> {
                        boilingPoint = NuclearSimulationEngine.hpWaterBoilingPoint;
                        heatPerL = NuclearSimulationEngine.coolingHeatPerLiter * 2.0;
                        steamRatio = 160;
                    }
                    case HATCH_HEAVY_WATER -> {
                        boilingPoint = 100.0;
                        heatPerL = NuclearSimulationEngine.coolingHeatPerLiter;
                        steamRatio = 160;
                    }
                    case HATCH_DISTILLED_WATER -> {
                        boilingPoint = 100.0;
                        heatPerL = NuclearSimulationEngine.coolingHeatPerLiter;
                        steamRatio = 160;
                    }
                    default -> {}
                }

                if (inputFluidAmount > 0 && temperature > boilingPoint) {
                    double deltaT = temperature - boilingPoint;
                    double heatAvailable = deltaT * NuclearSimulationEngine.EU_PER_DEGREE;
                    int maxBoilByHeat = (heatPerL > 0) ? (int) Math.floor(heatAvailable / heatPerL) : inputFluidAmount;

                    double frac = NuclearSimulationEngine.calculateTurnoverFraction(deltaT);
                    int desiredTurnover = Math.max(1, (int) Math.round(inputFluidCapacity * frac));
                    int mbToBoil = Math.min(inputFluidAmount, Math.min(desiredTurnover, maxBoilByHeat));

                    if (mbToBoil > 0) {
                        inputFluidAmount -= mbToBoil;
                        int steamProduced = mbToBoil * steamRatio;
                        lastTickProduced = steamProduced;
                        outputFluidAmount += steamProduced;
                        totalSteamProduced += steamProduced;

                        double heatConsumed = mbToBoil * heatPerL;
                        double tempDrop = heatConsumed / NuclearSimulationEngine.EU_PER_DEGREE;
                        double tempBefore = temperature;
                        temperature = Math.max(boilingPoint, temperature - tempDrop);
                        lastCoolingDetails = String.format(
                            java.util.Locale.US,
                            "Boiled %dL (cap %dL, frac %.2f, maxByHeat %dL), consumed %.1f EU, temp %.1f°C -> %.1f°C (drop %.1f°C)",
                            mbToBoil,
                            inputFluidCapacity,
                            frac,
                            maxBoilByHeat,
                            heatConsumed,
                            tempBefore,
                            temperature,
                            tempDrop);
                    }
                }

                if (inputFluidAmount <= 0) {
                    inputFluidAmount = 0;
                    wasDry = true;
                }
            }
        }
    }

    /**
     * Refills coolant fluid, respecting wasDry explosion safety checks.
     * 
     * @return true if refilled safely, false if thermal shock explosion triggered
     */
    public boolean refillCoolant() {
        return refillCoolant(inputFluidCapacity);
    }

    public boolean refillCoolant(int maxFeed) {
        if (inputFluidAmount >= inputFluidCapacity) return true;
        if (wasDry) {
            double threshold = NuclearSimulationEngine.getCoolantBoilingThreshold(inputFluidName);
            if (temperature > threshold) {
                return false; // Thermal shock explosion!
            }
            wasDry = false;
        }
        int toAdd = Math.min(maxFeed, inputFluidCapacity - inputFluidAmount);
        inputFluidAmount += toAdd;
        return true;
    }

    public void setInputFluidCapacity(int capacity) {
        this.inputFluidCapacity = Math.max(100, capacity);
        if (this.inputFluidAmount > this.inputFluidCapacity) {
            this.inputFluidAmount = this.inputFluidCapacity;
        }
    }

    public void setInputFluidAmount(int amount) {
        this.inputFluidAmount = Math.max(0, Math.min(amount, inputFluidCapacity));
        if (this.inputFluidAmount > 0) {
            this.wasDry = false;
        }
    }

    // Getters and helper status methods
    public int getDurability() {
        return durability;
    }

    public int getMaxDurability() {
        return maxDurability;
    }

    public double getDurabilityPercent() {
        return maxDurability <= 0 ? 0.0 : (durability * 100.0 / maxDurability);
    }

    public boolean isDepleted() {
        return depleted;
    }

    public int getLastNeutronsGenerated() {
        return lastNeutronsGenerated;
    }

    public int getLastTickProduced() {
        return lastTickProduced;
    }

    public String getInputFluidName() {
        return inputFluidName;
    }

    public int getInputFluidAmount() {
        return inputFluidAmount;
    }

    public int getInputFluidCapacity() {
        return inputFluidCapacity;
    }

    public String getOutputFluidName() {
        return outputFluidName;
    }

    public int getOutputFluidAmount() {
        return outputFluidAmount;
    }

    public int getTotalSteamProduced() {
        return totalSteamProduced;
    }

    public int getTotalDeuteriumProduced() {
        return totalDeuteriumProduced;
    }

    public int getTotalTritiumProduced() {
        return totalTritiumProduced;
    }

    public boolean isWasDry() {
        return wasDry;
    }

    public void setWasDry(boolean wasDry) {
        this.wasDry = wasDry;
    }

    public boolean isAutoRefill() {
        return autoRefill;
    }

    public void setAutoRefill(boolean autoRefill) {
        this.autoRefill = autoRefill;
    }

    public int getMaxCellHeat() {
        return maxCellHeat;
    }

    public int getCurrentCellHeat() {
        return currentCellHeat;
    }

    public int getFastFlux() {
        return fastFlux;
    }

    public int getThermalFlux() {
        return thermalFlux;
    }

    public int getFastAbsorbed() {
        return fastAbsorbed;
    }

    public int getThermalAbsorbed() {
        return thermalAbsorbed;
    }
}
