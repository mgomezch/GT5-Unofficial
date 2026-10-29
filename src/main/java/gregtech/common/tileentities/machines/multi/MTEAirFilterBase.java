package gregtech.common.tileentities.machines.multi;

import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.lazy;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.GTValues.VN;
import static gregtech.api.enums.HatchElement.Energy;
import static gregtech.api.enums.HatchElement.InputBus;
import static gregtech.api.enums.HatchElement.Maintenance;
import static gregtech.api.enums.HatchElement.Muffler;
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_DIESEL_ENGINE;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_DIESEL_ENGINE_ACTIVE;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_DIESEL_ENGINE_ACTIVE_GLOW;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_DIESEL_ENGINE_GLOW;
import static gregtech.api.enums.Textures.BlockIcons.TURBINE_NEW;
import static gregtech.api.enums.Textures.BlockIcons.TURBINE_NEW_ACTIVE;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;

import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Mods;
import gregtech.api.enums.Textures;
import gregtech.api.enums.TierEU;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.IToolStats;
import gregtech.api.interfaces.tileentity.ICasingTextureProvider;
import gregtech.api.interfaces.tileentity.IGregTechDeviceInformation;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.items.MetaGeneratedTool;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.RenderOverlay;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTUtility;
import gregtech.api.util.GTUtilityClient;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.items.MetaGeneratedTool01;
import gregtech.common.pollution.IPollutionScrubber;
import gregtech.common.pollution.Pollution;

public abstract class MTEAirFilterBase extends MTEEnhancedMultiBlockBase<MTEAirFilterBase>
    implements ISurvivalConstructable, ICasingTextureProvider, IPollutionScrubber {

    // Formerly configurable values
    public static final int USES_PER_FILTER = 30;

    protected int multiTier = 0;
    protected int mStoredPollution = 0;
    protected boolean isFilterLoaded = false;
    protected int filterUsageRemaining = 0;
    protected final List<RenderOverlay.OverlayTicket> overlayTickets = new ArrayList<>();
    private boolean mFormed;
    protected static final String STRUCTURE_PIECE_MAIN = "main";
    protected static final ClassValue<IStructureDefinition<MTEAirFilterBase>> STRUCTURE_DEFINITION = new ClassValue<>() {

        @Override
        protected IStructureDefinition<MTEAirFilterBase> computeValue(Class<?> type) {
            return StructureDefinition.<MTEAirFilterBase>builder()
                .addShape(
                    STRUCTURE_PIECE_MAIN,
                    transpose(
                        new String[][] { { "xxx", "xxx", "xxx" }, { "vmv", "m-m", "vmv" }, { "vmv", "m-m", "vmv" },
                            { "c~c", "ccc", "ccc" }, }))
                .addElement(
                    'c',
                    lazy(
                        x -> ofChain(
                            buildHatchAdder(MTEAirFilterBase.class)
                                .atLeast(Maintenance, InputBus, OutputBus, OutputHatch, Energy)
                                .hint(1)
                                .casingIndex(x.getCasingIndex())
                                .build(),
                            ofBlock(GregTechAPI.sBlockCasingsNH, x.getCasingMeta()))))
                .addElement('x', lazy(x -> ofBlock(GregTechAPI.sBlockCasingsNH, x.getCasingMeta())))
                .addElement('v', lazy(x -> ofBlock(GregTechAPI.sBlockCasingsNH, x.getPipeMeta())))
                .addElement(
                    'm',
                    lazy(
                        x -> ofChain(
                            buildHatchAdder(MTEAirFilterBase.class).atLeast(Muffler)
                                .hint(2)
                                .casingIndex(x.getCasingIndex())
                                .build(),
                            ofBlock(GregTechAPI.sBlockCasingsNH, x.getCasingMeta()))))
                .build();
        }
    };

    @Override
    public final IStructureDefinition<MTEAirFilterBase> getStructureDefinition() {
        return STRUCTURE_DEFINITION.get(getClass());
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, 1, 3, 0);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        return survivalBuildPiece(STRUCTURE_PIECE_MAIN, stackSize, 1, 3, 0, elementBudget, env, false, true);
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        if (!checkPiece(STRUCTURE_PIECE_MAIN, 1, 3, 0, errors)) return;
        checkHasEnergyHatch(errors);
        checkHasMaintenanceHatch(errors);
        checkOneMufflerHatch(errors);
        checkHasOutputHatch(errors);
        if (mMufflerHatches.size() == 1) {
            mMufflerHatches.get(0)
                .setController(this);
        }
    }

    @Override
    public boolean supportsPowerPanel() {
        return false;
    }

    public MTEAirFilterBase(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        usesTurbine = false;
    }

    public MTEAirFilterBase(String aName) {
        super(aName);
        usesTurbine = false;
    }

    public abstract long getEUt();

    public String getCasingString() {
        return switch (getCasingMeta()) {
            case 0 -> "Air Filter Turbine Casing";
            case 3 -> "Advanced Air Filter Turbine Casing";
            case 5 -> "Super Air Filter Turbine Casing";
            default -> "fill a ticket on github if you read this";
        };
    }

    public String getPipeString() {
        return switch (getPipeMeta()) {
            case 1 -> "Air Filter Vent Casing";
            case 4 -> "Advanced Air Filter Vent Casing";
            case 6 -> "Super Air Filter Vent Casing";
            default -> "fill a ticket on github if you read this";
        };
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType("Electric Air Filter, EAF")
            .addInfo("Filters pollution received from connected Air Ducts into Liquid Pollution")
            .addInfo("Connects to Air Duct network via its Muffler Hatch (strictly 1 required)")
            .addInfo("Pollution Storage Capacity: 10,000 * 4^(mufflerTier - 1)")
            .addInfo("Base speed: 1,000 pollution -> 10 L Liquid Pollution/s (at LV)")
            .addInfo("Power: Based on energy tier (supports 1 overclock with 2 single-amp hatches)")
            .addInfo("Perfect overclocks: 4x power = 4x pollution processed & liquid produced/s")
            .addInfo("Outputs Liquid Pollution fluid through Fluid Output Hatches")
            .beginStructureBlock(3, 4, 3, true)
            .addController("Front bottom center")
            .addCasing("9-22", getCasingString(), false)
            .addCasing("8", getPipeString(), false)
            .addEnergyHatch("1+", "Any bottom casing", 1)
            .addMaintenanceHatch("1", "Any bottom casing", 1)
            .addMufflerHatch("1", "Any center side casing", 2)
            .addOutputHatch("1+", "Any bottom casing", 1)
            .addAir("Interior of the structure")
            .toolTipFinisher();
        return tt;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        return Textures.BlockIcons.createTextureWithCasing(
            this,
            side,
            aFacing,
            aActive,
            OVERLAY_FRONT_DIESEL_ENGINE,
            OVERLAY_FRONT_DIESEL_ENGINE_GLOW,
            OVERLAY_FRONT_DIESEL_ENGINE_ACTIVE,
            OVERLAY_FRONT_DIESEL_ENGINE_ACTIVE_GLOW);
    }

    @Override
    public ITexture getCasingTexture() {
        return Textures.BlockIcons.getCasingTextureForId(getCasingIndex());
    }

    public abstract float getBonusByTier();

    public abstract int getCasingIndex();

    @Override
    public boolean isCorrectMachinePart(ItemStack aStack) {
        if (aStack == null) return false;
        if (!(aStack.getItem() instanceof MetaGeneratedTool01 tool)) return false;
        if (aStack.getItemDamage() < 170 || aStack.getItemDamage() > 179) return false;

        IToolStats stats = tool.getToolStats(aStack);
        if (stats == null || stats.getSpeedMultiplier() <= 0) return false;

        Materials material = MetaGeneratedTool.getPrimaryMaterial(aStack);
        return material != null && material.mToolSpeed > 0;
    }

    private float getTurbineDamage(ItemStack aStack) {
        if (aStack == null || !(aStack.getItem() instanceof MetaGeneratedTool tool)) {
            return -1;
        }
        return tool.getToolCombatDamage(aStack);
    }

    @Override
    protected IAlignmentLimits getInitialAlignmentLimits() {
        // don't rotate it, it's cursed.
        return (d, r, f) -> d.offsetY == 0 && r.isNotRotated() && !f.isVerticallyFliped();
    }

    public static int calculateMaxPollutionCapacity(int mufflerTier) {
        int clampedTier = Math.max(1, Math.min(15, mufflerTier));
        long cap = 10000L * (1L << (2 * (clampedTier - 1)));
        return (int) Math.min(Integer.MAX_VALUE, cap);
    }

    public int getMaxPollutionCapacity() {
        int mufflerTier = mMufflerHatches.isEmpty() ? 1 : Math.max(1, mMufflerHatches.get(0).mTier);
        return calculateMaxPollutionCapacity(mufflerTier);
    }

    @Override
    public int getScrubbingCapacity() {
        if (!isValidScrubber()) return 0;
        return Math.max(0, getMaxPollutionCapacity() - mStoredPollution);
    }

    @Override
    public int receivePollution(int amount) {
        if (amount <= 0 || !isValidScrubber()) return 0;
        int space = getScrubbingCapacity();
        int toAdd = Math.min(amount, space);
        mStoredPollution += toAdd;
        return toAdd;
    }

    @Override
    public boolean isValidScrubber() {
        return mMachine && getBaseMetaTileEntity() != null && !getBaseMetaTileEntity().isDead();
    }

    @Override
    public int getStoredPollution() {
        return mStoredPollution;
    }

    public void setStoredPollution(int aPollution) {
        this.mStoredPollution = Math.max(0, aPollution);
    }

    @Override
    public int drainStoredPollution() {
        int drained = mStoredPollution;
        mStoredPollution = 0;
        return drained;
    }

    @Override
    public void onBlockDestroyed() {
        super.onBlockDestroyed();
        int buffered = drainStoredPollution();
        if (buffered > 0) {
            IGregTechTileEntity base = getBaseMetaTileEntity();
            if (base != null && !base.isClientSide() && base.getWorld() != null) {
                Pollution.addPollution(base, buffered);
            }
        }
    }

    @NotNull
    @Override
    public CheckRecipeResult checkProcessing() {
        mEfficiencyIncrease = 10000;
        mEfficiency = 10000 - (getIdealStatus() - getRepairStatus()) * 1000;
        if (mEfficiency <= 0) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        long inputVoltage = getMaxInputVoltage();
        byte energyTier = (byte) Math.max(1, GTUtility.getTier(inputVoltage));
        int overclocks = Math.max(0, Math.min(15, energyTier - 1));

        long multiplier = 1L << (2 * overclocks);
        long powerPerTick = TierEU.RECIPE_LV * multiplier;
        int maxProcess = (int) Math.min(Integer.MAX_VALUE, 1000L * multiplier);

        int toProcess = Math.min(mStoredPollution, maxProcess);
        int liquidToProduce = toProcess / 100;
        if (liquidToProduce <= 0) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        FluidStack outputFluid = Materials.LiquidPollution.getFluid(liquidToProduce);
        if (!canOutputAll(new FluidStack[] { outputFluid })) {
            return CheckRecipeResultRegistry.FLUID_OUTPUT_FULL;
        }

        mStoredPollution -= liquidToProduce * 100;
        mOutputFluids = new FluidStack[] { outputFluid };
        mMaxProgresstime = 20;
        mEUt = (int) -Math.min(Integer.MAX_VALUE, powerPerTick);

        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    // Cache the lookup, but return clean copies of the stacks each time
    private ItemStack cleanFilter, dirtyFilter;

    private ItemStack getCleanFilter() {
        if (cleanFilter == null) {
            if (Mods.NewHorizonsCoreMod.isModLoaded()) {
                cleanFilter = GTModHandler.getModItem(Mods.NewHorizonsCoreMod.ID, "AdsorptionFilter", 1, 0);
            }
            if (cleanFilter == null) {
                // fallback for dev environment
                cleanFilter = new ItemStack(Blocks.stone);
            }
        }
        return cleanFilter.copy();
    }

    private ItemStack getDirtyFilter() {
        if (dirtyFilter == null) {
            if (Mods.NewHorizonsCoreMod.isModLoaded()) {
                dirtyFilter = GTModHandler.getModItem(Mods.NewHorizonsCoreMod.ID, "AdsorptionFilterDirty", 1, 0);
            }
            if (dirtyFilter == null) {
                // fallback for dev environment
                dirtyFilter = new ItemStack(Blocks.cobblestone);
            }
        }
        return dirtyFilter.copy();
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("mStoredPollution", mStoredPollution);
        aNBT.setBoolean("isFilterLoaded", isFilterLoaded);
        aNBT.setInteger("filterUsageRemaining", filterUsageRemaining);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mStoredPollution = aNBT.getInteger("mStoredPollution");
        isFilterLoaded = aNBT.getBoolean("isFilterLoaded");
        filterUsageRemaining = aNBT.getInteger("filterUsageRemaining");
    }

    public abstract int getPipeMeta();

    public abstract int getCasingMeta();

    @Override
    public void onPreTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPreTick(aBaseMetaTileEntity, aTick);
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        if (aTick % 200L == 0L) {
            // refresh casing on state change
            int Xpos = aBaseMetaTileEntity.getXCoord() + aBaseMetaTileEntity.getBackFacing().offsetX;
            int Ypos = aBaseMetaTileEntity.getYCoord() + 3;
            int Zpos = aBaseMetaTileEntity.getZCoord() + aBaseMetaTileEntity.getBackFacing().offsetZ;
            try {
                aBaseMetaTileEntity.getWorld()
                    .markBlockRangeForRenderUpdate(Xpos - 1, Ypos, Zpos - 1, Xpos + 1, Ypos, Zpos + 1);
            } catch (Exception ignored) {}
        }
        super.onPostTick(aBaseMetaTileEntity, aTick);
    }

    @Override
    public void onValueUpdate(byte aValue) {
        mFormed = aValue == 1;
        setTurbineOverlay();
    }

    @Override
    public void onRemoval() {
        super.onRemoval();
        if (getBaseMetaTileEntity().isClientSide()) GTUtilityClient.clearTurbineOverlay(overlayTickets);
    }

    @Override
    public byte getUpdateData() {
        return (byte) (mMachine ? 1 : 0);
    }

    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        setTurbineOverlay();
    }

    protected void setTurbineOverlay() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (tile.isServerSide()) return;

        IIconContainer[] tTextures = getBaseMetaTileEntity().isActive() ? TURBINE_NEW_ACTIVE : TURBINE_NEW;

        int[] xyz = new int[3];
        ExtendedFacing ext = getExtendedFacing();
        ext.getWorldOffset(new int[] { 0, -3, 1 }, xyz);
        GTUtilityClient.setTurbineOverlay(
            tile.getWorld(),
            xyz[0] + tile.getXCoord(),
            xyz[1] + tile.getYCoord(),
            xyz[2] + tile.getZCoord(),
            getExtendedFacing().with(ForgeDirection.UP),
            tTextures,
            overlayTickets);
    }

    @Override
    public void onTextureUpdate() {
        setTurbineOverlay();
    }

    @Override
    public boolean onRunningTick(ItemStack aStack) {
        return super.onRunningTick(aStack);
    }

    @Override
    public int getDamageToComponent(ItemStack aStack) {
        return 0;
    }

    @Override
    public String[] getInfoData() {
        long inputVoltage = getMaxInputVoltage();
        byte energyTier = (byte) Math.max(1, GTUtility.getTier(inputVoltage));
        int overclocks = Math.max(0, Math.min(15, energyTier - 1));
        long multiplier = 1L << (2 * overclocks);
        int maxProcess = (int) Math.min(Integer.MAX_VALUE, 1000L * multiplier);
        int maxLiquid = maxProcess / 100;

        return new String[] {
            IGregTechDeviceInformation.encode(
                "GT5U.infodata.progress",
                EnumChatFormatting.GREEN + Integer.toString(mProgresstime / 20) + EnumChatFormatting.RESET,
                EnumChatFormatting.YELLOW + Integer.toString(mMaxProgresstime / 20) + EnumChatFormatting.RESET),
            IGregTechDeviceInformation.encode(
                "GT5U.infodata.energy",
                EnumChatFormatting.GREEN + Long.toString(getBaseMetaTileEntity().getStoredEU())
                    + EnumChatFormatting.RESET,
                EnumChatFormatting.YELLOW + Long.toString(getBaseMetaTileEntity().getEUCapacity())
                    + EnumChatFormatting.RESET),
            IGregTechDeviceInformation.encode(
                "GT5U.infodata.probably_uses",
                EnumChatFormatting.RED + Integer.toString(Math.abs(mEUt)) + EnumChatFormatting.RESET),
            IGregTechDeviceInformation.encode(
                "GT5U.infodata.max_energy_income_tier",
                EnumChatFormatting.YELLOW + Long.toString(getMaxInputVoltage()) + EnumChatFormatting.RESET,
                EnumChatFormatting.YELLOW + VN[GTUtility.getTier(getMaxInputVoltage())] + EnumChatFormatting.RESET),
            IGregTechDeviceInformation.encode(
                "GT5U.infodata.problems_efficiency",
                EnumChatFormatting.RED + Integer.toString(getIdealStatus() - getRepairStatus())
                    + EnumChatFormatting.RESET,
                EnumChatFormatting.YELLOW + Float.toString(mEfficiency / 100.0F) + EnumChatFormatting.RESET + " %"),
            IGregTechDeviceInformation.encode(
                "GT5U.infodata.air_filter.stored_pollution",
                EnumChatFormatting.GREEN + formatNumber(mStoredPollution)
                    + EnumChatFormatting.RESET
                    + " / "
                    + EnumChatFormatting.YELLOW
                    + formatNumber(getMaxPollutionCapacity())
                    + EnumChatFormatting.RESET),
            IGregTechDeviceInformation.encode(
                "GT5U.infodata.air_filter.processing_rate",
                EnumChatFormatting.GREEN + formatNumber(maxProcess)
                    + EnumChatFormatting.RESET
                    + " poll/s -> "
                    + EnumChatFormatting.AQUA
                    + formatNumber(maxLiquid)
                    + " L/s"
                    + EnumChatFormatting.RESET),
            IGregTechDeviceInformation.encode(
                "GT5U.multiblock.recipesDone.fmt",
                EnumChatFormatting.GREEN + formatNumber(recipesDone) + EnumChatFormatting.RESET) };
    }

    @Override
    public boolean supportsSingleRecipeLocking() {
        return false;
    }
}
