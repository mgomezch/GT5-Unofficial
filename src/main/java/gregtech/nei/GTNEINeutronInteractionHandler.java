package gregtech.nei;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.TemplateRecipeHandler;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTUtility;

public class GTNEINeutronInteractionHandler extends TemplateRecipeHandler {

    public static final ResourceLocation TEXTURE_ATLAS = new ResourceLocation(
        "gregtech",
        "textures/gui/nei/neutron_interaction_atlas.png");

    public static final ResourceLocation LONG_ARROW = new ResourceLocation(
        "gregtech",
        "textures/gui/nei/long_arrow.png");

    public static final String OVERLAY_ID = "gt.nei.neutron_interaction";

    public static class NeutronComponentData {

        public final ItemStack stack;
        public final String name;
        public final String category;

        // Page 1: Interaction
        public final double fastScattering;
        public final double fastAbsorption;
        public final double slowingProbability;
        public final double thermalScattering;
        public final double thermalAbsorption;

        // Page 2: Capture (Fission / Reaction)
        public final boolean hasCapture;
        public final int fastNeutronEnergyEU;
        public final double directEU;
        public final double directHeatC;
        public final double maxNeutronsEmitted;

        // Page 2: Absorption (Depletion / Transmutation)
        public final boolean hasAbsorption;
        public final ItemStack absorptionOutput;
        public final long neutronsRequired;

        public final String extraInfo;

        public NeutronComponentData(ItemStack stack, String name, String category, double fastScattering,
            double fastAbsorption, double slowingProbability, double thermalScattering, double thermalAbsorption,
            boolean hasCapture, int fastNeutronEnergyEU, double directEU, double directHeatC, double maxNeutronsEmitted,
            boolean hasAbsorption, ItemStack absorptionOutput, long neutronsRequired, String extraInfo) {
            this.stack = stack;
            this.name = name;
            this.category = category;
            this.fastScattering = fastScattering;
            this.fastAbsorption = fastAbsorption;
            this.slowingProbability = slowingProbability;
            this.thermalScattering = thermalScattering;
            this.thermalAbsorption = thermalAbsorption;
            this.hasCapture = hasCapture;
            this.fastNeutronEnergyEU = fastNeutronEnergyEU;
            this.directEU = directEU;
            this.directHeatC = directHeatC;
            this.maxNeutronsEmitted = maxNeutronsEmitted;
            this.hasAbsorption = hasAbsorption;
            this.absorptionOutput = absorptionOutput;
            this.neutronsRequired = neutronsRequired;
            this.extraInfo = extraInfo;
        }
    }

    /**
     * Page 1: Fast & Thermal Neutron scattering and moderation.
     */
    public class CachedNeutronInteractionRecipe extends CachedRecipe {

        public final PositionedStack input;
        public final PositionedStack inputThermal;
        public final PositionedStack catalyst;
        public final NeutronComponentData data;

        public CachedNeutronInteractionRecipe(NeutronComponentData data) {
            this.data = data;
            this.input = new PositionedStack(data.stack, 67, 31);
            this.inputThermal = new PositionedStack(data.stack, 67, 97);
            this.catalyst = new PositionedStack(ItemList.Machine_Nuclear_Reactor.get(1L), 6, 135);
        }

        @Override
        public PositionedStack getIngredient() {
            return input;
        }

        @Override
        public PositionedStack getResult() {
            return input;
        }

        @Override
        public List<PositionedStack> getOtherStacks() {
            List<PositionedStack> list = new ArrayList<>();
            list.add(inputThermal);
            list.add(catalyst);
            return list;
        }
    }

    /**
     * Page 2: Single Neutron Capture (fission energy, heat, multiplication)
     * and Neutron Absorption (depletion / transmutation product).
     */
    public class CachedNeutronCaptureRecipe extends CachedRecipe {

        public final PositionedStack captureInput;
        public final PositionedStack absorptionInput;
        public final PositionedStack absorptionOutput;
        public final PositionedStack catalyst;
        public final NeutronComponentData data;

        public CachedNeutronCaptureRecipe(NeutronComponentData data) {
            this.data = data;
            this.captureInput = new PositionedStack(data.stack, 58, 27);
            this.absorptionInput = new PositionedStack(data.stack, 31, 97);
            this.absorptionOutput = data.absorptionOutput != null ? new PositionedStack(data.absorptionOutput, 101, 97)
                : null;
            this.catalyst = new PositionedStack(ItemList.Machine_Nuclear_Reactor.get(1L), 6, 135);
        }

        @Override
        public PositionedStack getIngredient() {
            return absorptionInput;
        }

        @Override
        public PositionedStack getResult() {
            return absorptionOutput != null ? absorptionOutput : captureInput;
        }

        @Override
        public List<PositionedStack> getOtherStacks() {
            List<PositionedStack> list = new ArrayList<>();
            list.add(captureInput);
            list.add(catalyst);
            if (absorptionOutput != null) {
                list.add(absorptionOutput);
            }
            return list;
        }
    }

    private static final List<NeutronComponentData> ALL_COMPONENTS = new ArrayList<>();
    private static boolean initialized = false;

    public static synchronized void initData() {
        if (initialized) return;
        initialized = true;

        // 1. MODERATORS (Graphite, Carbon) - High scattering & moderation, no fission/depletion
        addModerator(Materials.Graphite.getBlocks(1), "Graphite Block", 0.93, 0.002, 0.50, 0.621, 0.009);
        addModerator(Materials.Graphite.getIngots(1), "Graphite Ingot", 0.93, 0.002, 0.50, 0.621, 0.009);
        addModerator(Materials.Graphite.getDust(1), "Graphite Dust", 0.93, 0.002, 0.50, 0.621, 0.009);
        addModerator(Materials.Graphite.getPlates(1), "Graphite Plate", 0.93, 0.002, 0.50, 0.621, 0.009);
        addModerator(Materials.Carbon.getPlates(1), "Carbon Plate", 0.93, 0.002, 0.50, 0.621, 0.009);
        addModerator(Materials.Carbon.getDust(1), "Carbon Dust", 0.93, 0.002, 0.50, 0.621, 0.009);

        // 2. REFLECTORS - Extreme scattering, low absorption, indestructible or long life
        addIC2Reflector("reactorReflector", "Neutron Reflector", 0.95, 0.01, 0.20, 0.98, 0.02, 10_000);
        addIC2Reflector("reactorReflectorThick", "Thick Neutron Reflector", 0.95, 0.01, 0.20, 0.98, 0.02, 40_000);
        addGTReflector(ItemList.Neutron_Reflector, "Iridium Neutron Reflector", 0.95, 0.01, 0.20, 0.98, 0.02);
        addReflector(Materials.Beryllium.getPlates(1), "Beryllium Plate", 0.95, 0.01, 0.20, 0.98, 0.02);
        addReflector(Materials.Beryllium.getBlocks(1), "Beryllium Block", 0.95, 0.01, 0.20, 0.98, 0.02);

        // 3. CONTROL RODS - Extreme absorption, low scattering, burnout capacity
        addControlRod(
            GTOreDictUnificator.get(OrePrefixes.stick, Materials.Cadmium, 1L),
            "Cadmium Rod",
            0.10,
            0.85,
            0.05,
            0.05,
            0.95,
            100_000_000L);
        addControlRod(Materials.Cadmium.getPlates(1), "Cadmium Plate", 0.10, 0.85, 0.05, 0.05, 0.95, 100_000_000L);
        addControlRod(
            GTOreDictUnificator.get(OrePrefixes.stick, Materials.Boron, 1L),
            "Boron Rod",
            0.10,
            0.85,
            0.05,
            0.05,
            0.95,
            100_000_000L);
        addControlRod(Materials.Boron.getPlates(1), "Boron Plate", 0.10, 0.85, 0.05, 0.05, 0.95, 100_000_000L);

        // 4. BETAVOLTAIC PLATES - 100% absorption, Direct EU generation
        addBetavoltaic(ItemList.Betavoltaic_Plate_HV, "Betavoltaic Plate (HV)", 1, 1024, 20.0);
        addBetavoltaic(ItemList.Betavoltaic_Plate_EV, "Betavoltaic Plate (EV)", 2, 4096, 40.0);

        // 5. FUEL RODS - Fission, heat, neutron multiplication, and depleted fuel rods
        // Uranium
        addFuelRod(
            ItemList.RodUranium,
            "Uranium Fuel Rod",
            14.0,
            0.22,
            4.0,
            ItemList.DepletedRodUranium.get(1L),
            40_960_000L,
            "Base: 4 Fast Neutrons/t | Standard fission fuel");
        addFuelRod(
            ItemList.RodUranium2,
            "Dual Uranium Fuel Rod",
            28.0,
            0.44,
            8.0,
            ItemList.DepletedRodUranium2.get(1L),
            81_920_000L,
            "Base: 8 Fast Neutrons/t | Standard fission fuel");
        addFuelRod(
            ItemList.RodUranium4,
            "Quad Uranium Fuel Rod",
            56.0,
            0.88,
            16.0,
            ItemList.DepletedRodUranium4.get(1L),
            163_840_000L,
            "Base: 16 Fast Neutrons/t | Standard fission fuel");

        // MOX (Higher yield, 2x reaction)
        addFuelRod(
            ItemList.RodMOX,
            "MOX Fuel Rod",
            28.0,
            0.44,
            8.0,
            ItemList.DepletedRodMOX.get(1L),
            40_960_000L,
            "Base: 8 Fast Neutrons/t | High reaction rate");
        addFuelRod(
            ItemList.RodMOX2,
            "Dual MOX Fuel Rod",
            56.0,
            0.88,
            16.0,
            ItemList.DepletedRodMOX2.get(1L),
            81_920_000L,
            "Base: 16 Fast Neutrons/t | High reaction rate");
        addFuelRod(
            ItemList.RodMOX4,
            "Quad MOX Fuel Rod",
            112.0,
            1.75,
            32.0,
            ItemList.DepletedRodMOX4.get(1L),
            163_840_000L,
            "Base: 32 Fast Neutrons/t | High reaction rate");

        // Thorium (Long-lived breeder fuel, 4x lifetime)
        addFuelRod(
            ItemList.RodThorium,
            "Thorium Fuel Rod",
            10.0,
            0.16,
            4.0,
            ItemList.DepletedRodThorium.get(1L),
            163_840_000L,
            "Base: 4 Fast Neutrons/t | Extended core life");
        addFuelRod(
            ItemList.RodThorium2,
            "Dual Thorium Fuel Rod",
            20.0,
            0.31,
            8.0,
            ItemList.DepletedRodThorium2.get(1L),
            327_680_000L,
            "Base: 8 Fast Neutrons/t | Extended core life");
        addFuelRod(
            ItemList.RodThorium4,
            "Quad Thorium Fuel Rod",
            40.0,
            0.63,
            16.0,
            ItemList.DepletedRodThorium4.get(1L),
            655_360_000L,
            "Base: 16 Fast Neutrons/t | Extended core life");

        // Naquadah (Extremely energetic advanced fuel)
        addFuelRod(
            ItemList.RodNaquadah,
            "Naquadah Fuel Rod",
            64.0,
            1.00,
            16.0,
            ItemList.DepletedRodNaquadah.get(1L),
            80_000_000L,
            "Base: 16 Fast Neutrons/t | 4x Fission power");
        addFuelRod(
            ItemList.RodNaquadah2,
            "Dual Naquadah Fuel Rod",
            128.0,
            2.00,
            32.0,
            ItemList.DepletedRodNaquadah2.get(1L),
            160_000_000L,
            "Base: 32 Fast Neutrons/t | 4x Fission power");
        addFuelRod(
            ItemList.RodNaquadah4,
            "Quad Naquadah Fuel Rod",
            256.0,
            4.00,
            64.0,
            ItemList.DepletedRodNaquadah4.get(1L),
            320_000_000L,
            "Base: 64 Fast Neutrons/t | 4x Fission power");

        // 6. COOLANT CELLS (IC2 Heat Sinks)
        addIC2CoolantCell("reactorCoolantSimple", "10k Coolant Cell", 10_000);
        addIC2CoolantCell("reactorCoolantTriple", "30k Coolant Cell", 30_000);
        addIC2CoolantCell("reactorCoolantSix", "60k Coolant Cell", 60_000);
        addIC2CoolantCell("reactorCoolantHelium1", "60k He-Coolant Cell", 60_000);
        addIC2CoolantCell("reactorCoolantHelium2", "180k He-Coolant Cell", 180_000);
        addIC2CoolantCell("reactorCoolantHelium3", "360k He-Coolant Cell", 360_000);
        addIC2CoolantCell("reactorCoolantNaK1", "60k NaK-Coolant Cell", 60_000);
        addIC2CoolantCell("reactorCoolantNaK2", "180k NaK-Coolant Cell", 180_000);
        addIC2CoolantCell("reactorCoolantNaK3", "360k NaK-Coolant Cell", 360_000);

        // 7. FLUID COOLANTS & TRANSMUTATION PRODUCTS
        addFluidCoolant(
            "ic2coolant",
            "IC2 Coolant",
            0.45,
            0.03,
            0.40,
            0.45,
            0.12,
            false,
            null,
            0,
            "Electrum Tier (EV) | Continuous cooling -> Hot Coolant");
        addFluidCoolant(
            "ic2distilledwater",
            "Distilled Water",
            0.70,
            0.05,
            0.80,
            0.70,
            0.10,
            true,
            getFluidDisplay("deuterium", 1000),
            1_000L,
            "Platinum Tier (IV) | Boils at 100°C (1:160 Steam) -> Transmutes to Deuterium");
        addFluidCoolant(
            "highpressuredistilledwater",
            "HP Distilled Water",
            0.70,
            0.05,
            0.80,
            0.70,
            0.10,
            true,
            getFluidDisplay("deuterium", 2000),
            1_000L,
            "Osmium Tier (LuV) | Boils at 200°C (1:320 Superheated Steam) -> 2x Deuterium");
        addFluidCoolant(
            "heavywater",
            "Heavy Water",
            0.85,
            0.005,
            0.90,
            0.85,
            0.01,
            true,
            getFluidDisplay("tritium", 1000),
            1_000L,
            "Quantium Tier (ZPM) | Boils at 100°C (1:160 HW Steam) -> Transmutes to Tritium");
        addFluidCoolant(
            "highpressureheavywater",
            "HP Heavy Water",
            0.85,
            0.005,
            0.90,
            0.85,
            0.01,
            true,
            getFluidDisplay("tritium", 2000),
            1_000L,
            "Fluxed Electrum Tier (UV) | Boils at 200°C (1:320 SC HW Steam) -> 2x Tritium");
    }

    private static void addModerator(ItemStack stack, String name, double fScat, double fAbs, double slow, double tScat,
        double tAbs) {
        if (stack != null) {
            ALL_COMPONENTS.add(
                new NeutronComponentData(
                    stack,
                    name,
                    "Moderator",
                    fScat,
                    fAbs,
                    slow,
                    tScat,
                    tAbs,
                    false,
                    0,
                    0,
                    0,
                    0,
                    false,
                    null,
                    0,
                    "Slows fast neutrons into thermal neutrons"));
        }
    }

    private static void addReflector(ItemStack stack, String name, double fScat, double fAbs, double slow, double tScat,
        double tAbs) {
        if (stack != null) {
            ALL_COMPONENTS.add(
                new NeutronComponentData(
                    stack,
                    name,
                    "Reflector",
                    fScat,
                    fAbs,
                    slow,
                    tScat,
                    tAbs,
                    false,
                    0,
                    0,
                    0,
                    0,
                    false,
                    null,
                    0,
                    "Reflects scattered neutrons back into core"));
        }
    }

    private static void addIC2Reflector(String ic2Name, String name, double fScat, double fAbs, double slow,
        double tScat, double tAbs, long wearCount) {
        ItemStack stack = GTModHandler.getIC2Item(ic2Name, 1L, 0);
        if (stack == null) stack = GTModHandler.getIC2Item(ic2Name, 1L, 1);
        if (stack != null) {
            ALL_COMPONENTS.add(
                new NeutronComponentData(
                    stack,
                    name,
                    "Reflector",
                    fScat,
                    fAbs,
                    slow,
                    tScat,
                    tAbs,
                    false,
                    0,
                    0,
                    0,
                    0,
                    true,
                    null,
                    wearCount,
                    "Reflects scattered neutrons back into core"));
        }
    }

    private static void addGTReflector(ItemList item, String name, double fScat, double fAbs, double slow, double tScat,
        double tAbs) {
        if (item != null && item.hasBeenSet()) {
            addReflector(item.get(1L), name, fScat, fAbs, slow, tScat, tAbs);
        }
    }

    private static void addControlRod(ItemStack stack, String name, double fScat, double fAbs, double slow,
        double tScat, double tAbs, long capacity) {
        if (stack != null) {
            ALL_COMPONENTS.add(
                new NeutronComponentData(
                    stack,
                    name,
                    "Control Rod",
                    fScat,
                    fAbs,
                    slow,
                    tScat,
                    tAbs,
                    false,
                    0,
                    0,
                    0,
                    0,
                    true,
                    null,
                    capacity,
                    "Absorbs neutrons to prevent runaway heat"));
        }
    }

    private static void addBetavoltaic(ItemList item, String name, int tier, long maxEU, double euPerNeutron) {
        if (item != null && item.hasBeenSet()) {
            ALL_COMPONENTS.add(
                new NeutronComponentData(
                    item.get(1L),
                    name,
                    "Betavoltaic Cell",
                    0.00,
                    1.00,
                    0.00,
                    0.00,
                    1.00,
                    true,
                    8,
                    euPerNeutron,
                    0.0,
                    0.0,
                    true,
                    null,
                    0,
                    "Generates up to " + maxEU + " EU/t (2A " + (tier >= 2 ? "EV" : "HV") + ") directly"));
        }
    }

    private static void addFuelRod(ItemList item, String name, double directEU, double directHeatC, double maxNeutrons,
        ItemStack depleted, long neutronsRequired, String extra) {
        if (item != null && item.hasBeenSet()) {
            ALL_COMPONENTS.add(
                new NeutronComponentData(
                    item.get(1L),
                    name,
                    "Fuel Rod",
                    0.15,
                    0.25,
                    0.10,
                    0.10,
                    0.80,
                    true,
                    8,
                    directEU,
                    directHeatC,
                    maxNeutrons,
                    true,
                    depleted,
                    neutronsRequired,
                    extra));
        }
    }

    private static void addIC2CoolantCell(String ic2Name, String name, int maxHeat) {
        ItemStack stack = GTModHandler.getIC2Item(ic2Name, 1L, 0);
        if (stack == null) stack = GTModHandler.getIC2Item(ic2Name, 1L, 1);
        if (stack != null) {
            ALL_COMPONENTS.add(
                new NeutronComponentData(
                    stack,
                    name,
                    "Coolant Cell",
                    0.45,
                    0.03,
                    0.40,
                    0.45,
                    0.12,
                    false,
                    0,
                    0,
                    0,
                    0,
                    false,
                    null,
                    0,
                    "Stores " + NumberFormat.getNumberInstance(Locale.US)
                        .format(maxHeat) + " Heat | Ejected when full"));
        }
    }

    private static void addFluidCoolant(String fluidName, String name, double fScat, double fAbs, double slow,
        double tScat, double tAbs, boolean transmutes, ItemStack product, long reqNeutrons, String extra) {
        Fluid fluid = FluidRegistry.getFluid(fluidName);
        if (fluid == null && fluidName.startsWith("ic2")) {
            fluid = FluidRegistry.getFluid(fluidName.substring(3));
        }
        if (fluid != null) {
            ItemStack displayStack = GTUtility.getFluidDisplayStack(new FluidStack(fluid, 1000), false);
            if (displayStack != null) {
                ALL_COMPONENTS.add(
                    new NeutronComponentData(
                        displayStack,
                        name,
                        "Fluid Coolant",
                        fScat,
                        fAbs,
                        slow,
                        tScat,
                        tAbs,
                        true,
                        8,
                        0,
                        0,
                        0,
                        transmutes,
                        product,
                        reqNeutrons,
                        extra));
            }
        }
    }

    private static ItemStack getFluidDisplay(String fluidName, int amount) {
        Fluid fluid = FluidRegistry.getFluid(fluidName);
        if (fluid == null && !fluidName.startsWith("fluid.")) {
            fluid = FluidRegistry.getFluid("fluid." + fluidName);
        }
        if (fluid != null) {
            return GTUtility.getFluidDisplayStack(new FluidStack(fluid, amount), false);
        }
        return null;
    }

    public GTNEINeutronInteractionHandler() {
        initData();
    }

    @Override
    public String getRecipeName() {
        return StatCollector.translateToLocal("gt.nei.neutron_interaction.name");
    }

    @Override
    public String getGuiTexture() {
        return "gregtech:textures/gui/nei/neutron_interaction_atlas.png";
    }

    @Override
    public String getOverlayIdentifier() {
        return OVERLAY_ID;
    }

    @Override
    public int recipiesPerPage() {
        return 1;
    }

    @Override
    public void loadTransferRects() {
        this.transferRects.add(new RecipeTransferRect(new Rectangle(6, 135, 18, 18), getOverlayIdentifier()));
    }

    @Override
    public void loadCraftingRecipes(String outputId, Object... results) {
        initData();
        if (outputId != null && outputId.equals(getOverlayIdentifier())) {
            for (NeutronComponentData data : ALL_COMPONENTS) {
                this.arecipes.add(new CachedNeutronInteractionRecipe(data));
                if (data.hasCapture || data.hasAbsorption) {
                    this.arecipes.add(new CachedNeutronCaptureRecipe(data));
                }
            }
        } else {
            super.loadCraftingRecipes(outputId, results);
        }
    }

    @Override
    public void loadCraftingRecipes(ItemStack result) {
        initData();
        if (result == null) return;
        for (NeutronComponentData data : ALL_COMPONENTS) {
            if (matches(result, data.stack)) {
                this.arecipes.add(new CachedNeutronInteractionRecipe(data));
                if (data.hasCapture || data.hasAbsorption) {
                    this.arecipes.add(new CachedNeutronCaptureRecipe(data));
                }
            } else if (data.absorptionOutput != null && matches(result, data.absorptionOutput)) {
                this.arecipes.add(new CachedNeutronCaptureRecipe(data));
            }
        }
    }

    @Override
    public void loadUsageRecipes(ItemStack ingredient) {
        initData();
        if (ingredient == null) return;

        // Clicking on the nuclear reactor catalyst shows all recipes
        if (ItemList.Machine_Nuclear_Reactor.isStackEqual(ingredient, false, true)) {
            for (NeutronComponentData data : ALL_COMPONENTS) {
                this.arecipes.add(new CachedNeutronInteractionRecipe(data));
                if (data.hasCapture || data.hasAbsorption) {
                    this.arecipes.add(new CachedNeutronCaptureRecipe(data));
                }
            }
            return;
        }

        for (NeutronComponentData data : ALL_COMPONENTS) {
            if (matches(ingredient, data.stack)) {
                this.arecipes.add(new CachedNeutronInteractionRecipe(data));
                if (data.hasCapture || data.hasAbsorption) {
                    this.arecipes.add(new CachedNeutronCaptureRecipe(data));
                }
            }
        }
    }

    private boolean matches(ItemStack target, ItemStack component) {
        if (target == null || component == null) return false;
        if (GTUtility.areStacksEqual(target, component, true)) return true;
        if (target.getItem() == component.getItem()) {
            return target.getItemDamage() == OreDictionary.WILDCARD_VALUE
                || component.getItemDamage() == OreDictionary.WILDCARD_VALUE
                || target.getItemDamage() == component.getItemDamage();
        }
        return false;
    }

    @Override
    public void drawBackground(int recipeIndex) {
        if (recipeIndex < 0 || recipeIndex >= arecipes.size()) return;
        CachedRecipe cached = this.arecipes.get(recipeIndex);

        // Standard panel containers (top & bottom)
        drawPanel(6, 2, 154, 64);
        drawPanel(6, 68, 154, 64);
        drawSlotBox(5, 134);

        if (cached instanceof CachedNeutronInteractionRecipe recipe) {
            // Page 1: Fast & Thermal interaction diagrams
            drawSlotBox(66, 30);
            drawSlotBox(66, 96);

            GuiDraw.changeTexture(TEXTURE_ATLAS);
            // Fast diagram: (0, 0, 88, 54) at (14, 12)
            GuiDraw.drawTexturedModalRect(14, 12, 0, 0, 88, 54);
            // Thermal diagram: (0, 54, 88, 54) at (14, 78)
            GuiDraw.drawTexturedModalRect(14, 78, 0, 54, 88, 54);

            // Pie chart: (u=index*16, v=240, w=16, h=16) at (115, 28)
            double slowing = recipe.data.slowingProbability;
            int index = 1 + (int) Math.floor(slowing * 9.0);
            if (slowing <= 0.0) index = 0;
            else if (slowing >= 1.0) index = 10;
            GuiDraw.drawTexturedModalRect(115, 28, index * 16, 240, 16, 16);

        } else if (cached instanceof CachedNeutronCaptureRecipe recipe) {
            // Page 2: Single Neutron Capture & Neutron Absorption
            // Top slot for Single Neutron Capture
            drawSlotBox(57, 26);
            // Bottom slots for Neutron Absorption (input & output)
            drawSlotBox(30, 96);
            if (recipe.data.absorptionOutput != null) {
                drawSlotBox(100, 96);
            }

            // Draw Single Neutron Capture diagram: (0, 109, 92, 31) at (30, 20)
            GuiDraw.changeTexture(TEXTURE_ATLAS);
            GuiDraw.drawTexturedModalRect(30, 20, 0, 109, 92, 31);

            // Draw animated progress arrow in bottom box: (40x40 texture at 58, 95)
            GuiDraw.changeTexture(LONG_ARROW);
            drawCustomTexturedModalRect(58, 95, 0, 0, 40, 20, 40, 40);
            long cycle = System.currentTimeMillis() % 3000L;
            int fillW = (int) (40.0 * (cycle / 3000.0));
            if (fillW > 0) {
                drawCustomTexturedModalRect(58, 95, 0, 20, fillW, 20, 40, 40);
            }
        }
    }

    @Override
    public void drawExtras(int recipeIndex) {
        if (recipeIndex < 0 || recipeIndex >= arecipes.size()) return;
        CachedRecipe cached = this.arecipes.get(recipeIndex);

        if (cached instanceof CachedNeutronInteractionRecipe recipe) {
            // --- Page 1: Fast Neutron Panel ---
            GuiDraw.drawStringC(
                StatCollector.translateToLocal("gt.nei.neutron_interaction.fast"),
                83,
                5,
                0x404040,
                false);
            String fastScatStr = String.format(Locale.US, "%.1f %%", recipe.data.fastScattering * 100.0);
            GuiDraw.drawStringC(fastScatStr, 47, 16, 0x404040, false);

            String fastAbsStr = String.format(Locale.US, "%.1f %%", recipe.data.fastAbsorption * 100.0);
            GuiDraw.drawStringC(fastAbsStr, 97, 54, 0x00A000, false);

            String thermFracStr = String.format(Locale.US, "%.1f %%", recipe.data.slowingProbability * 100.0);
            GuiDraw.drawStringC(thermFracStr, 123, 16, 0x0C27A7, false);

            String fastFracStr = String.format(Locale.US, "%.1f %%", (1.0 - recipe.data.slowingProbability) * 100.0);
            GuiDraw.drawStringC(fastFracStr, 123, 46, 0xBC1A1A, false);

            // --- Page 1: Thermal Neutron Panel ---
            GuiDraw.drawStringC(
                StatCollector.translateToLocal("gt.nei.neutron_interaction.thermal"),
                83,
                71,
                0x404040,
                false);
            String thermScatStr = String.format(Locale.US, "%.1f %%", recipe.data.thermalScattering * 100.0);
            GuiDraw.drawStringC(thermScatStr, 47, 82, 0x404040, false);

            String thermAbsStr = String.format(Locale.US, "%.1f %%", recipe.data.thermalAbsorption * 100.0);
            GuiDraw.drawStringC(thermAbsStr, 97, 120, 0x00A000, false);

            // Bottom summary
            GuiDraw.drawString(
                EnumChatFormatting.DARK_GRAY + recipe.data.category,
                28,
                137,
                0x202020,
                false);
            if (recipe.data.extraInfo != null) {
                GuiDraw.drawString(
                    EnumChatFormatting.GRAY + recipe.data.extraInfo,
                    28,
                    147,
                    0x505050,
                    false);
            }

        } else if (cached instanceof CachedNeutronCaptureRecipe recipe) {
            // --- Page 2: Single Neutron Capture Panel ---
            GuiDraw.drawStringC(
                StatCollector.translateToLocal("gt.nei.neutron_interaction.capture"),
                83,
                5,
                0x404040,
                false);
            GuiDraw.drawString(recipe.data.fastNeutronEnergyEU + " EU", 36, 52, 0x00A000, false);
            GuiDraw.drawString(String.format(Locale.US, "%.2f °C", recipe.data.directHeatC), 113, 18, 0x404040, false);
            GuiDraw.drawString(
                String.format(Locale.US, "%.0f EU", recipe.data.directEU),
                113,
                52,
                0x404040,
                false);

            if (recipe.data.maxNeutronsEmitted > 0) {
                String emitted = String
                    .format(Locale.US, "Max %.1f neutrons emitted", recipe.data.maxNeutronsEmitted);
                GuiDraw.drawStringC(emitted, 83, 56, 0x00A000, false);
            }

            // --- Page 2: Neutron Absorption Panel ---
            GuiDraw.drawStringC(
                StatCollector.translateToLocal("gt.nei.neutron_interaction.absorption"),
                83,
                71,
                0x404040,
                false);
            if (recipe.data.neutronsRequired > 0) {
                String reqStr = recipe.data.neutronsRequired >= 1_000_000 ? String.valueOf(recipe.data.neutronsRequired)
                    : NumberFormat.getNumberInstance(Locale.US)
                        .format(recipe.data.neutronsRequired);
                String nText = (recipe.data.neutronsRequired > 1 ? reqStr + " Neutrons" : reqStr + " Neutron");
                GuiDraw.drawStringC(nText, 83, 120, 0x404040, false);
            }

            // Bottom summary
            GuiDraw.drawString(
                EnumChatFormatting.DARK_GRAY + recipe.data.category,
                28,
                137,
                0x202020,
                false);
            if (recipe.data.extraInfo != null) {
                GuiDraw.drawString(
                    EnumChatFormatting.GRAY + recipe.data.extraInfo,
                    28,
                    147,
                    0x505050,
                    false);
            }
        }
    }

    @Override
    public List<String> handleTooltip(GuiRecipe<?> gui, List<String> currenttip, int recipeIndex) {
        currenttip = super.handleTooltip(gui, currenttip, recipeIndex);
        if (recipeIndex < 0 || recipeIndex >= arecipes.size()) return currenttip;

        Point mousepos = GuiDraw.getMousePosition();
        Dimension displaySize = GuiDraw.displaySize();
        int ySize = Math.min(Math.max(displaySize.height - 68, 166), 370);
        int guiLeft = (displaySize.width - 176) / 2;
        int guiTop = (displaySize.height - ySize) / 2 + 10;
        Point offset = gui.getRecipePosition(recipeIndex);
        int relX = mousepos.x - guiLeft - offset.x;
        int relY = mousepos.y - guiTop - offset.y;

        CachedRecipe rec = arecipes.get(recipeIndex);
        if (rec instanceof CachedNeutronInteractionRecipe r1) {
            // Page 1 Tooltips
            if (relX >= 25 && relX <= 65 && relY >= 10 && relY <= 24) {
                currenttip.add(EnumChatFormatting.WHITE + "Scattering Probability");
                currenttip.add(EnumChatFormatting.GRAY + "Chance of fast neutrons bouncing off this component");
            } else if (relX >= 80 && relX <= 115 && relY >= 46 && relY <= 62) {
                currenttip.add(EnumChatFormatting.GREEN + "Absorption Probability");
                currenttip.add(EnumChatFormatting.GRAY + "Chance of fast neutrons being absorbed by this component");
            } else if ((relX >= 105 && relX <= 145 && relY >= 10 && relY <= 24)
                || (relX >= 112 && relX <= 134 && relY >= 26 && relY <= 46)) {
                    currenttip.add(EnumChatFormatting.BLUE + "Thermal Scattered Neutron Fraction");
                    currenttip.add(
                        EnumChatFormatting.GRAY + String.format(
                            Locale.US,
                            "%.1f%% of scattered neutrons become thermal",
                            r1.data.slowingProbability * 100.0));
                } else if (relX >= 105 && relX <= 145 && relY >= 40 && relY <= 54) {
                    currenttip.add(EnumChatFormatting.RED + "Fast Scattered Neutron Fraction");
                    currenttip.add(
                        EnumChatFormatting.GRAY + String.format(
                            Locale.US,
                            "%.1f%% of scattered neutrons remain fast",
                            (1.0 - r1.data.slowingProbability) * 100.0));
                } else if (relX >= 25 && relX <= 65 && relY >= 74 && relY <= 90) {
                    currenttip.add(EnumChatFormatting.WHITE + "Scattering Probability");
                    currenttip.add(EnumChatFormatting.GRAY + "Chance of thermal neutrons bouncing off this component");
                } else if (relX >= 80 && relX <= 115 && relY >= 112 && relY <= 128) {
                    currenttip.add(EnumChatFormatting.GREEN + "Absorption Probability");
                    currenttip
                        .add(EnumChatFormatting.GRAY + "Chance of thermal neutrons being absorbed by this component");
                }
        } else if (rec instanceof CachedNeutronCaptureRecipe r2) {
            // Page 2 Tooltips
            if (relX >= 20 && relX <= 55 && relY >= 44 && relY <= 62) {
                currenttip.add(EnumChatFormatting.GREEN + "Fast Neutron Energy");
                currenttip.add(EnumChatFormatting.GRAY + "Energy transferred to heat upon absorption (8 EU)");
            } else if (relX >= 95 && relX <= 145 && relY >= 12 && relY <= 28) {
                currenttip.add(EnumChatFormatting.GOLD + "Direct Heat by Disintegration");
                currenttip.add(
                    EnumChatFormatting.GRAY + String
                        .format(Locale.US, "Fission heat: %.2f °C per fission reaction", r2.data.directHeatC));
            } else if (relX >= 95 && relX <= 145 && relY >= 44 && relY <= 62) {
                currenttip.add(EnumChatFormatting.YELLOW + "Direct Energy");
                currenttip.add(
                    EnumChatFormatting.GRAY + String
                        .format(Locale.US, "Energy released: %.0f EU per fission reaction", r2.data.directEU));
            } else if (relX >= 25 && relX <= 140 && relY >= 52 && relY <= 66) {
                currenttip.add(EnumChatFormatting.GREEN + "Neutrons Multiplication");
                currenttip.add(EnumChatFormatting.GRAY + "New fast neutrons produced (varies with core temperature)");
            } else if (relX >= 45 && relX <= 120 && relY >= 90 && relY <= 130) {
                currenttip.add(EnumChatFormatting.WHITE + "Neutron Absorption Capacity");
                currenttip
                    .add(EnumChatFormatting.GRAY + "Total neutrons absorbed before component depletion/transmutation");
            }
        }
        return currenttip;
    }

    @Override
    public List<String> handleItemTooltip(GuiRecipe<?> gui, ItemStack stack, List<String> currenttip,
        int recipeIndex) {
        currenttip = super.handleItemTooltip(gui, stack, currenttip, recipeIndex);
        if (recipeIndex < 0 || recipeIndex >= arecipes.size() || stack == null) return currenttip;

        CachedRecipe rec = arecipes.get(recipeIndex);
        NeutronComponentData data = (rec instanceof CachedNeutronInteractionRecipe r1) ? r1.data
            : (rec instanceof CachedNeutronCaptureRecipe r2) ? r2.data : null;
        if (data == null) return currenttip;

        if (matches(stack, data.stack)) {
            currenttip.add(EnumChatFormatting.GOLD + "Reactor Role: " + EnumChatFormatting.WHITE + data.category);
            currenttip.add(
                EnumChatFormatting.YELLOW + "Fast: "
                    + EnumChatFormatting.WHITE
                    + String.format(
                        Locale.US,
                        "Scatter %.1f%%, Absorb %.1f%%, Slow %.1f%%",
                        data.fastScattering * 100.0,
                        data.fastAbsorption * 100.0,
                        data.slowingProbability * 100.0));
            currenttip.add(
                EnumChatFormatting.AQUA + "Thermal: "
                    + EnumChatFormatting.WHITE
                    + String.format(
                        Locale.US,
                        "Scatter %.1f%%, Absorb %.1f%%",
                        data.thermalScattering * 100.0,
                        data.thermalAbsorption * 100.0));
            if (data.extraInfo != null) {
                currenttip.add(EnumChatFormatting.GRAY + data.extraInfo);
            }
        } else if (data.absorptionOutput != null && matches(stack, data.absorptionOutput)) {
            currenttip
                .add(EnumChatFormatting.DARK_RED + "Depleted Component" + EnumChatFormatting.GRAY + " (Byproduct)");
            currenttip.add(
                EnumChatFormatting.GRAY + "Produced after absorbing full neutron capacity in the Nuclear Reactor");
        }
        return currenttip;
    }

    public static void drawPanel(int x, int y, int w, int h) {
        GuiDraw.drawRect(x + 1, y, w - 2, 1, 0xFF373737);
        GuiDraw.drawRect(x, y + 1, 1, h - 2, 0xFF373737);
        GuiDraw.drawRect(x + 1, y + h - 1, w - 2, 1, 0xFFFFFFFF);
        GuiDraw.drawRect(x + w - 1, y + 1, 1, h - 2, 0xFFFFFFFF);
        GuiDraw.drawRect(x + 1, y + 1, w - 2, h - 2, 0xFFC6C6C6);
    }

    public static void drawSlotBox(int x, int y) {
        GuiDraw.drawRect(x, y, 18, 1, 0xFF373737);
        GuiDraw.drawRect(x, y, 1, 18, 0xFF373737);
        GuiDraw.drawRect(x + 17, y, 1, 18, 0xFFFFFFFF);
        GuiDraw.drawRect(x, y + 17, 18, 1, 0xFFFFFFFF);
        GuiDraw.drawRect(x + 1, y + 1, 16, 16, 0xFF8B8B8B);
    }

    public static void drawCustomTexturedModalRect(int x, int y, int u, int v, int width, int height, int texWidth,
        int texHeight) {
        float f = 1.0F / (float) texWidth;
        float f1 = 1.0F / (float) texHeight;
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + height, 0.0, (float) u * f, (float) (v + height) * f1);
        tessellator.addVertexWithUV(x + width, y + height, 0.0, (float) (u + width) * f, (float) (v + height) * f1);
        tessellator.addVertexWithUV(x + width, y, 0.0, (float) (u + width) * f, (float) v * f1);
        tessellator.addVertexWithUV(x, y, 0.0, (float) u * f, (float) v * f1);
        tessellator.draw();
    }
}
