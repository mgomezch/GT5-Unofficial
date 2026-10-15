package gregtech.common.tileentities.machines.multi;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.isAir;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.Muffler;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_MULTI_SMELTER;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_MULTI_SMELTER_ACTIVE;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_MULTI_SMELTER_ACTIVE_GLOW;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_MULTI_SMELTER_GLOW;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidHandler;
import net.minecraftforge.oredict.OreDictionary;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElementCheckOnly;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Mods;
import gregtech.api.enums.TierEU;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.ICasingTextureProvider;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchMaintenance;
import gregtech.api.metatileentity.implementations.MTEHatchMuffler;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gregtech.common.pollution.Pollution;
import gregtech.common.render.IMTERenderer;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTEHatchCustomFluidBase;

public class MTEIndustrialSmokeStack extends MTEEnhancedMultiBlockBase<MTEIndustrialSmokeStack>
    implements ISurvivalConstructable, ICasingTextureProvider, IMTERenderer {

    public static final int CASING_INDEX = 10;
    private static final String STRUCTURE_PIECE_MAIN = "main";

    private final int mTier;
    private int accumulatedPollution = 0;
    private boolean isSteamPowered = false;

    protected final List<MTEHatch> mSteamHatches = new ArrayList<>();

    // Cached IC2 WindSim reflection field
    private static Field ic2WindDirectionField;
    private static boolean ic2WindReflectionInitialized = false;

    private static IStructureDefinition<MTEIndustrialSmokeStack> STRUCTURE_DEFINITION_T1;
    private static IStructureDefinition<MTEIndustrialSmokeStack> STRUCTURE_DEFINITION_T2;
    private static IStructureDefinition<MTEIndustrialSmokeStack> STRUCTURE_DEFINITION_T3;

    public MTEIndustrialSmokeStack(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional);
        this.mTier = aTier;
    }

    public MTEIndustrialSmokeStack(String aName, int aTier) {
        super(aName);
        this.mTier = aTier;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEIndustrialSmokeStack(this.mName, this.mTier);
    }

    public int getTier() {
        return mTier;
    }

    public int getStructuralHeight() {
        return mTier * 30;
    }

    public int getBaseWidth() {
        return 1 + 2 * mTier;
    }

    public int getHorizontalOffset() {
        return mTier;
    }

    public int getVerticalOffset() {
        return getStructuralHeight() - 1;
    }

    public int getBaseMaxPollutionPerCycle() {
        return switch (mTier) {
            case 1 -> 2000;
            case 2 -> 8000;
            case 3 -> 32000;
            default -> 2000;
        };
    }

    public int getMaxPollutionPerCycle() {
        int amps = isSteamPowered ? 1 : Math.max(1, mEnergyHatches.size());
        return getBaseMaxPollutionPerCycle() * amps;
    }

    public long getVoltage() {
        return switch (mTier) {
            case 1 -> TierEU.RECIPE_LV;
            case 2 -> TierEU.RECIPE_MV;
            case 3 -> TierEU.RECIPE_HV;
            default -> TierEU.RECIPE_LV;
        };
    }

    @Override
    public IStructureDefinition<MTEIndustrialSmokeStack> getStructureDefinition() {
        return switch (mTier) {
            case 1 -> {
                if (STRUCTURE_DEFINITION_T1 == null) {
                    STRUCTURE_DEFINITION_T1 = buildDefinition(buildStructureShapeT1());
                }
                yield STRUCTURE_DEFINITION_T1;
            }
            case 2 -> {
                if (STRUCTURE_DEFINITION_T2 == null) {
                    STRUCTURE_DEFINITION_T2 = buildDefinition(buildStructureShapeT2());
                }
                yield STRUCTURE_DEFINITION_T2;
            }
            case 3 -> {
                if (STRUCTURE_DEFINITION_T3 == null) {
                    STRUCTURE_DEFINITION_T3 = buildDefinition(buildStructureShapeT3());
                }
                yield STRUCTURE_DEFINITION_T3;
            }
            default -> null;
        };
    }

    private static IStructureDefinition<MTEIndustrialSmokeStack> buildDefinition(String[][] shape) {
        return StructureDefinition.<MTEIndustrialSmokeStack>builder()
            .addShape(STRUCTURE_PIECE_MAIN, shape)
            .addElement(
                'b',
                ofChain(
                    ofBlock(GregTechAPI.sBlockConcretes, 0),
                    (IStructureElementCheckOnly<MTEIndustrialSmokeStack>) (mte, world, x, y, z) -> isConcreteBlock(
                        world,
                        x,
                        y,
                        z,
                        world.getBlock(x, y, z),
                        world.getBlockMetadata(x, y, z))))
            .addElement(
                'h',
                ofChain(
                    buildHatchAdder(MTEIndustrialSmokeStack.class).adder(MTEIndustrialSmokeStack::addBottomHatch)
                        .casingIndex(CASING_INDEX)
                        .hint(1)
                        .build(),
                    ofBlock(GregTechAPI.sBlockConcretes, 0),
                    (IStructureElementCheckOnly<MTEIndustrialSmokeStack>) (mte, world, x, y, z) -> isConcreteBlock(
                        world,
                        x,
                        y,
                        z,
                        world.getBlock(x, y, z),
                        world.getBlockMetadata(x, y, z))))
            .addElement('M', ofChain(Muffler.newAny(CASING_INDEX, 2), isAir()))
            .addElement('-', isAir())
            .addElement('.', isAir())
            .build();
    }

    public static boolean isConcreteBlock(World world, int x, int y, int z, Block block, int meta) {
        if (block == null || block == Blocks.air) return false;

        // 1. GregTech concrete blocks
        if (block == GregTechAPI.sBlockConcretes) return true;
        if (block == GregTechAPI.sBlockCasings9 && meta == 3) return true;
        if (block == GregTechAPI.sBlockReinforced && meta == 13) return true;

        // 2. Chisel / general name matching
        String unlocalized = block.getUnlocalizedName();
        if (unlocalized != null && unlocalized.toLowerCase()
            .contains("concrete")) return true;

        // 3. ArchitectureCraft shape check
        if (Mods.ArchitectureCraft.isModLoaded()) {
            try {
                TileEntity te = world.getTileEntity(x, y, z);
                if (te != null && te.getClass()
                    .getName()
                    .contains("TileShape")) {
                    TileEntity shapeTe = te;
                    Class<?> shapeClass = shapeTe.getClass();
                    Field baseField = shapeClass.getField("baseBlockState");
                    Object blockState = baseField.get(shapeTe);
                    if (blockState != null) {
                        Class<?> bsClass = blockState.getClass();
                        Block baseBlock = (Block) bsClass.getMethod("getBlock")
                            .invoke(blockState);
                        if (baseBlock == GregTechAPI.sBlockConcretes || baseBlock == GregTechAPI.sBlockCasings9
                            || (baseBlock.getUnlocalizedName() != null && baseBlock.getUnlocalizedName()
                                .toLowerCase()
                                .contains("concrete"))) {
                            return true;
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 4. OreDictionary check
        try {
            int[] ids = OreDictionary.getOreIDs(new ItemStack(block, 1, meta));
            for (int id : ids) {
                String name = OreDictionary.getOreName(id);
                if ("stoneConcrete".equals(name) || "blockConcrete".equals(name)) return true;
            }
        } catch (Throwable ignored) {}

        return false;
    }

    public boolean addBottomHatch(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity mte = aTileEntity.getMetaTileEntity();
        if (mte == null) return false;

        if (mte instanceof MTEHatchEnergy energyHatch) {
            if (energyHatch.mTier == this.mTier && energyHatch.maxAmperesIn() == 1) {
                energyHatch.updateTexture(aBaseCasingIndex);
                energyHatch.updateCraftingIcon(this.getMachineCraftingIcon());
                return mEnergyHatches.add(energyHatch);
            }
            return false;
        }

        if (isSteamHatch(mte)) {
            if (mte instanceof MTEHatch hatch) {
                hatch.updateTexture(aBaseCasingIndex);
                hatch.updateCraftingIcon(this.getMachineCraftingIcon());
                return mSteamHatches.add(hatch);
            }
            return false;
        }

        if (mte instanceof MTEHatchMaintenance maintenanceHatch) {
            maintenanceHatch.updateTexture(aBaseCasingIndex);
            maintenanceHatch.updateCraftingIcon(this.getMachineCraftingIcon());
            return mMaintenanceHatches.add(maintenanceHatch);
        }

        return false;
    }

    private static boolean isSteamHatch(IMetaTileEntity mte) {
        if (mte instanceof MTEHatchCustomFluidBase fluidHatch) {
            return Materials.Steam.mGas.equals(fluidHatch.mLockedFluid);
        }
        if (mte instanceof MTEHatchInput inputHatch) {
            FluidStack f = inputHatch.getFluid();
            return f != null && Materials.Steam.mGas.equals(f.getFluid());
        }
        return false;
    }

    private static String[][] buildStructureShapeT1() {
        String[][] shape = new String[30][];
        shape[0] = new String[] { "bbb", "bMb", "bbb" };
        for (int i = 1; i <= 27; i++) {
            shape[i] = new String[] { "bbb", "b-b", "bbb" };
        }
        shape[28] = new String[] { "hhh", "h-h", "hhh" };
        shape[29] = new String[] { "h~h", "h-h", "hhh" };
        return transpose(shape);
    }

    private static String[][] buildStructureShapeT2() {
        String[][] shape = new String[60][];
        shape[0] = new String[] { ".....", ".bbb.", ".bMb.", ".bbb.", "....." };
        for (int i = 1; i <= 29; i++) {
            shape[i] = new String[] { ".....", ".bbb.", ".b-b.", ".bbb.", "....." };
        }
        for (int i = 30; i <= 57; i++) {
            shape[i] = new String[] { "bbbbb", "b---b", "b---b", "b---b", "bbbbb" };
        }
        shape[58] = new String[] { "hhhhh", "h---h", "h---h", "h---h", "hhhhh" };
        shape[59] = new String[] { "hh~hh", "h---h", "h---h", "h---h", "hhhhh" };
        return transpose(shape);
    }

    private static String[][] buildStructureShapeT3() {
        String[][] shape = new String[90][];
        shape[0] = new String[] { ".......", ".......", "..bbb..", "..bMb..", "..bbb..", ".......", "......." };
        for (int i = 1; i <= 29; i++) {
            shape[i] = new String[] { ".......", ".......", "..bbb..", "..b-b..", "..bbb..", ".......", "......." };
        }
        for (int i = 30; i <= 59; i++) {
            shape[i] = new String[] { ".......", ".bbbbb.", ".b---b.", ".b---b.", ".b---b.", ".bbbbb.", "......." };
        }
        for (int i = 60; i <= 87; i++) {
            shape[i] = new String[] { "bbbbbbb", "b-----b", "b-----b", "b-----b", "b-----b", "b-----b", "bbbbbbb" };
        }
        shape[88] = new String[] { "hhhhhhh", "h-----h", "h-----h", "h-----h", "h-----h", "h-----h", "hhhhhhh" };
        shape[89] = new String[] { "hhh~hhh", "h-----h", "h-----h", "h-----h", "h-----h", "h-----h", "hhhhhhh" };
        return transpose(shape);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType("Industrial Smoke Stack T" + mTier)
            .addInfo("Draws pollution across a multi-chunk radius and vents it into the high atmosphere.")
            .addInfo(
                "Radius: " + mTier
                    + " chunk(s) Manhattan distance (up to "
                    + ((2 * mTier * (mTier + 1)) + 1)
                    + " chunks).")
            .addInfo("Cleaning efficiency decreases linearly with Manhattan distance (100% at center to 50% at edge).")
            .addInfo("Efficiency decreases exponentially with vertical distance (50% at half stack height).")
            .addInfo(
                "Base throughput: up to " + getBaseMaxPollutionPerCycle()
                    + " pollution every 5 seconds (1A on-tier power).")
            .addInfo(
                "Supplying 4 amps on-tier power grants 4x throughput (" + (getBaseMaxPollutionPerCycle() * 4)
                    + " pollution/cycle).")
            .addInfo(
                "Accumulates soot up to 100x max cycle pollution; on power failure, dumps ALL accumulated soot at ground level!")
            .addSeparator()
            .beginStructureBlock(getBaseWidth(), getStructuralHeight(), getBaseWidth(), false)
            .addController("Front bottom center")
            .addStructureInfo(
                "Walls: Concrete blocks (Tier " + mTier + " height: " + getStructuralHeight() + " blocks)")
            .addStructureInfo("Also accepts any Chisel concrete variant or ArchitectureCraft concrete shape")
            .addEnergyHatch(
                "Bottom 2 layers: 1 to 4 single-amp " + (mTier == 1 ? "LV" : mTier == 2 ? "MV" : "HV")
                    + " Energy Hatches, OR")
            .addOtherStructurePart("Steam Hatch", "Bottom 2 layers: up to 8 Steam Hatches (produces white steam plume)")
            .addStructureInfo("Cannot mix Steam and Energy Hatches")
            .addMaintenanceHatch("Bottom 2 layers (optional, max 1)")
            .addMufflerHatch("Top center (optional, max 1)")
            .toolTipFinisher(EnumChatFormatting.GOLD + "GT New Horizons");
        return tt;
    }

    @Override
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        mUpdate = 300; // Structure check once every 15 seconds
        mSteamHatches.clear();

        if (!checkPiece(STRUCTURE_PIECE_MAIN, getHorizontalOffset(), getVerticalOffset(), 0)) {
            return false;
        }

        if (mMaintenanceHatches.size() > 1 || mMufflerHatches.size() > 1) {
            return false;
        }

        boolean hasSteam = !mSteamHatches.isEmpty();
        boolean hasEnergy = !mEnergyHatches.isEmpty();

        if (hasSteam == hasEnergy) {
            return false;
        }

        if (hasSteam) {
            if (mSteamHatches.size() > 8) return false;
            this.isSteamPowered = true;
        } else {
            if (mEnergyHatches.size() > 4) return false;
            for (MTEHatchEnergy h : mEnergyHatches) {
                if (h.mTier != this.mTier || h.maxAmperesIn() != 1) return false;
            }
            this.isSteamPowered = false;
        }

        return true;
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, getHorizontalOffset(), getVerticalOffset(), 0);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece(
            STRUCTURE_PIECE_MAIN,
            stackSize,
            getHorizontalOffset(),
            getVerticalOffset(),
            0,
            elementBudget,
            env,
            false,
            true);
    }

    @Override
    public boolean shouldCheckMaintenance() {
        return !mMaintenanceHatches.isEmpty();
    }

    @Nonnull
    @Override
    public CheckRecipeResult checkProcessing() {
        return CheckRecipeResultRegistry.NO_RECIPE;
    }

    @Override
    public int getMaxEfficiency(ItemStack aStack) {
        return 10000;
    }

    @Override
    public int getPollutionPerTick(ItemStack aStack) {
        return 0;
    }

    @Override
    public int getDamageToComponent(ItemStack aStack) {
        return 0;
    }

    @Override
    public boolean explodesOnComponentBreak(ItemStack aStack) {
        return false;
    }

    @Override
    public boolean isCorrectMachinePart(ItemStack aStack) {
        return true;
    }

    @Override
    public boolean needsClientTick() {
        return true;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);

        if (aBaseMetaTileEntity.isServerSide()) {
            if (this.mMachine) {
                boolean powerOk;
                if (isSteamPowered) {
                    int amps = Math.min(mSteamHatches.size(), 4);
                    int steamPerTick = (int) (amps * getVoltage() * 2);
                    powerOk = drainSteam(steamPerTick);
                } else {
                    int amps = mEnergyHatches.size();
                    long euPerTick = (long) amps * getVoltage();
                    powerOk = drainEnergyInput(euPerTick);
                }

                if (!powerOk) {
                    if (aBaseMetaTileEntity.isActive()) {
                        // Power failure! Instantly eject all accumulated pollution at ground level
                        if (accumulatedPollution > 0) {
                            int chunkX = aBaseMetaTileEntity.getXCoord() >> 4;
                            int chunkZ = aBaseMetaTileEntity.getZCoord() >> 4;
                            Pollution.addPollution(
                                aBaseMetaTileEntity.getWorld(),
                                chunkX,
                                chunkZ,
                                accumulatedPollution,
                                aBaseMetaTileEntity.getYCoord());
                            accumulatedPollution = 0;
                        }
                        stopMachine(ShutDownReasonRegistry.POWER_LOSS);
                        aBaseMetaTileEntity.setActive(false);
                    }
                } else {
                    aBaseMetaTileEntity.setActive(true);

                    // Every 5 seconds (100 ticks): process pollution cycle
                    if (aTick % 100 == 0) {
                        checkMaintenance();
                        if (getRepairStatus() == getIdealStatus() || !shouldCheckMaintenance()) {
                            processPollutionCycle(aBaseMetaTileEntity);
                        }
                    }
                }
            } else {
                aBaseMetaTileEntity.setActive(false);
            }
        }
    }

    private boolean drainSteam(int aSteam) {
        if (aSteam <= 0) return true;
        int available = 0;
        for (MTEHatch hatch : mSteamHatches) {
            if (!hatch.isValid()) continue;
            IFluidHandler fh = (IFluidHandler) hatch.getBaseMetaTileEntity();
            if (fh != null) {
                FluidStack drained = fh
                    .drain(ForgeDirection.UNKNOWN, new FluidStack(Materials.Steam.mGas, aSteam), false);
                if (drained != null && drained.isFluidEqual(Materials.Steam.getGas(1))) {
                    available += drained.amount;
                    if (available >= aSteam) break;
                }
            }
        }
        if (available < aSteam) return false;

        int toDrain = aSteam;
        for (MTEHatch hatch : mSteamHatches) {
            if (!hatch.isValid()) continue;
            IFluidHandler fh = (IFluidHandler) hatch.getBaseMetaTileEntity();
            if (fh != null) {
                FluidStack drained = fh
                    .drain(ForgeDirection.UNKNOWN, new FluidStack(Materials.Steam.mGas, toDrain), true);
                if (drained != null && drained.isFluidEqual(Materials.Steam.getGas(1))) {
                    toDrain -= drained.amount;
                    if (toDrain <= 0) break;
                }
            }
        }
        return true;
    }

    private void processPollutionCycle(IGregTechTileEntity aBaseMetaTileEntity) {
        World world = aBaseMetaTileEntity.getWorld();
        int centerChunkX = aBaseMetaTileEntity.getXCoord() >> 4;
        int centerChunkZ = aBaseMetaTileEntity.getZCoord() >> 4;
        double controllerY = aBaseMetaTileEntity.getYCoord();

        int cycleMax = getMaxPollutionPerCycle();
        int remainingBudget = cycleMax;
        int displacedThisCycle = 0;
        int maxAccumulation = 100 * cycleMax;

        double halfHeight = getStructuralHeight() / 2.0;
        double topHeight = controllerY + getStructuralHeight() - 1.0;

        for (int dx = -mTier; dx <= mTier; dx++) {
            for (int dz = -mTier; dz <= mTier; dz++) {
                int manhattanDist = Math.abs(dx) + Math.abs(dz);
                if (manhattanDist > mTier) continue;

                int targetChunkX = centerChunkX + dx;
                int targetChunkZ = centerChunkZ + dz;

                int currentPollution = Pollution.getPollution(world, targetChunkX, targetChunkZ);
                if (currentPollution <= 0) continue;

                double distEff = 1.0 - 0.5 * ((double) manhattanDist / (double) mTier);
                float pHeight = Pollution.getPollutionHeight(world, targetChunkX, targetChunkZ);
                double vertFactor = Float.isNaN(pHeight) ? 1.0
                    : Math.pow(0.5, Math.abs(controllerY - pHeight) / halfHeight);

                int available = (int) Math.round(currentPollution * vertFactor * distEff);
                if (available <= 0) continue;

                int amountToTake = Math.min(available, remainingBudget);
                if (amountToTake <= 0) break;

                int outputAmount = amountToTake;
                if (!mMufflerHatches.isEmpty()) {
                    MTEHatchMuffler muffler = mMufflerHatches.get(0);
                    if (muffler.isValid()) {
                        outputAmount = muffler.calculatePollutionReduction(amountToTake);
                        muffler.getBaseMetaTileEntity()
                            .setActive(true);
                    }
                }

                Pollution.movePollutionHeight(world, targetChunkX, targetChunkZ, amountToTake, outputAmount, topHeight);
                displacedThisCycle += amountToTake;
                remainingBudget -= amountToTake;

                if (remainingBudget <= 0) break;
            }
            if (remainingBudget <= 0) break;
        }

        accumulatedPollution = Math.min(maxAccumulation, accumulatedPollution + displacedThisCycle);
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        if (aBaseMetaTileEntity.isServerSide()) {
            if (mMachine) {
                String powerType = isSteamPowered ? "Steam" : "Electric";
                String status = aBaseMetaTileEntity.isActive() ? "Running (" + powerType + ")" : "Idle / Power loss";
                GTUtility.sendChatToPlayer(aPlayer, "Industrial Smoke Stack T" + mTier + ": " + status);
                GTUtility.sendChatToPlayer(
                    aPlayer,
                    "Accumulated soot: " + accumulatedPollution + " / " + (100 * getMaxPollutionPerCycle()));
            } else {
                GTUtility.sendChatToPlayer(aPlayer, "Industrial Smoke Stack T" + mTier + ": Structure incomplete.");
            }
        }
        return true;
    }

    @Override
    public String[] getInfoData() {
        return new String[] { EnumChatFormatting.BLUE + "Industrial Smoke Stack T" + mTier + EnumChatFormatting.RESET,
            "Status: " + (getBaseMetaTileEntity().isActive() ? EnumChatFormatting.GREEN + "Active"
                : EnumChatFormatting.RED + "Inactive") + EnumChatFormatting.RESET,
            "Power Source: " + (isSteamPowered ? "Steam" : "Electricity"), "Manhattan Radius: " + mTier + " chunk(s)",
            "Accumulated Soot: " + accumulatedPollution + " / " + (100 * getMaxPollutionPerCycle()),
            "Cycle Max Throughput: " + getMaxPollutionPerCycle() };
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("accumulatedPollution", accumulatedPollution);
        aNBT.setBoolean("isSteamPowered", isSteamPowered);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        accumulatedPollution = aNBT.getInteger("accumulatedPollution");
        isSteamPowered = aNBT.getBoolean("isSteamPowered");
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int aColorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            if (aActive) {
                return new ITexture[] { TextureFactory.of(GregTechAPI.sBlockConcretes, 0),
                    TextureFactory.of(OVERLAY_FRONT_MULTI_SMELTER_ACTIVE), TextureFactory.builder()
                        .addIcon(OVERLAY_FRONT_MULTI_SMELTER_ACTIVE_GLOW)
                        .glow()
                        .build() };
            }
            return new ITexture[] { TextureFactory.of(GregTechAPI.sBlockConcretes, 0),
                TextureFactory.of(OVERLAY_FRONT_MULTI_SMELTER), TextureFactory.builder()
                    .addIcon(OVERLAY_FRONT_MULTI_SMELTER_GLOW)
                    .glow()
                    .build() };
        }
        return new ITexture[] { TextureFactory.of(GregTechAPI.sBlockConcretes, 0) };
    }

    @Override
    public ITexture getCasingTexture() {
        return TextureFactory.of(GregTechAPI.sBlockConcretes, 0);
    }

    // --- IMTERenderer (Visual Smoke Effect) ---

    @Override
    public AxisAlignedBB getRenderBoundingBox(int x, int y, int z) {
        int h = getStructuralHeight();
        return AxisAlignedBB.getBoundingBox(x - 16, y, z - 16, x + 16, y + h + 10, z + 16);
    }

    @Override
    public double getMaxRenderDistanceSquared() {
        return 65536.0D; // 256 blocks visibility for massive smoke stacks
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderTESR(double x, double y, double z, float timeSinceLastTick) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || !base.isActive()) return;

        World world = base.getWorld();
        if (world == null) return;

        ForgeDirection front = base.getFrontFacing();
        ForgeDirection back = front.getOpposite();

        double centerX = x + back.offsetX * mTier + 0.5;
        double centerY = y + getStructuralHeight() + 0.2;
        double centerZ = z + back.offsetZ * mTier + 0.5;

        // Query IC2 WindSim
        double windSpeed = 10.0;
        double windAngle = 0.0;
        try {
            ic2.core.WorldData wd = ic2.core.WorldData.get(world);
            if (wd != null && wd.windSim != null) {
                windSpeed = wd.windSim.getWindAt(base.getYCoord() + getStructuralHeight());
                if (!ic2WindReflectionInitialized) {
                    try {
                        ic2WindDirectionField = ic2.core.WindSim.class.getDeclaredField("windDirection");
                        ic2WindDirectionField.setAccessible(true);
                    } catch (Throwable ignored) {}
                    ic2WindReflectionInitialized = true;
                }
                if (ic2WindDirectionField != null) {
                    int degrees = ic2WindDirectionField.getInt(wd.windSim);
                    windAngle = Math.toRadians(degrees);
                }
            }
        } catch (Throwable e) {
            windAngle = ((world.getTotalWorldTime() / 200.0) % (2 * Math.PI));
        }

        double windFactor = Math.min(3.0, windSpeed / 10.0);
        double driftX = Math.cos(windAngle) * windFactor;
        double driftZ = Math.sin(windAngle) * windFactor;

        float red = isSteamPowered ? 0.95f : 0.12f;
        float green = isSteamPowered ? 0.95f : 0.12f;
        float blue = isSteamPowered ? 1.0f : 0.12f;
        float maxAlpha = isSteamPowered ? 0.35f : 0.50f;

        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);

        Tessellator tess = Tessellator.instance;
        double time = world.getTotalWorldTime() + timeSinceLastTick;

        // Render 8 overlapping floating circular smoke puffs
        for (int i = 0; i < 8; i++) {
            double phase = ((time * 0.035 + i * 0.125) % 1.0);
            double puffY = centerY + phase * 4.5;
            double puffX = centerX + phase * driftX * 3.0;
            double puffZ = centerZ + phase * driftZ * 3.0;
            double radius = 0.8 + phase * 1.2;
            float alpha = (float) (Math.sin(phase * Math.PI) * maxAlpha);

            tess.startDrawing(GL11.GL_TRIANGLE_FAN);
            tess.setColorRGBA_F(red, green, blue, alpha);
            tess.addVertex(puffX, puffY, puffZ);

            tess.setColorRGBA_F(red, green, blue, 0.0f);
            for (int k = 0; k <= 16; k++) {
                double angle = k * (2.0 * Math.PI / 16.0);
                double vx = puffX + Math.cos(angle) * radius;
                double vz = puffZ + Math.sin(angle) * radius;
                tess.addVertex(vx, puffY, vz);
            }
            tess.draw();
        }

        GL11.glDepthMask(true);
        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }
}
