package gregtech.common.tileentities.machines.multi.nuclear;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.casing.Casings.NuclearCasing;
import static gregtech.api.enums.HatchElement.Maintenance;
import static gregtech.api.metatileentity.BaseTileEntity.TOOLTIP_DELAY;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static gregtech.api.util.GTStructureUtility.chainItemPipeCasings;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.structurelib.StructureLibAPI;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.IStructureElement.BlocksToPlace;
import com.gtnewhorizon.structurelib.structure.IStructureElement.PlaceResult;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizon.structurelib.util.ItemStackPredicate;
import com.gtnewhorizons.modularui.api.drawable.IDrawable;
import com.gtnewhorizons.modularui.api.drawable.ItemDrawable;
import com.gtnewhorizons.modularui.api.math.Alignment;
import com.gtnewhorizons.modularui.api.math.Color;
import com.gtnewhorizons.modularui.api.math.Pos2d;
import com.gtnewhorizons.modularui.api.math.Size;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.api.widget.IWidgetBuilder;
import com.gtnewhorizons.modularui.common.widget.ButtonWidget;
import com.gtnewhorizons.modularui.common.widget.DynamicPositionedColumn;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;

import gregtech.GTMod;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.SoundResource;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.ICasingTextureProvider;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.interfaces.tileentity.ITurnable;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.util.GTLog;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gregtech.common.blocks.ItemMachines;
import gregtech.common.misc.GTStructureChannels;
import gregtech.common.pollution.Pollution;

public class MTENuclearReactor extends MTEEnhancedMultiBlockBase<MTENuclearReactor>
    implements ISurvivalConstructable, ICasingTextureProvider {

    protected static final int CASING_INDEX = NuclearCasing.getTextureId();
    public static final String STRUCTURE_TIER_1 = "tier_1";
    public static final String STRUCTURE_TIER_2 = "tier_2";
    public static final String STRUCTURE_TIER_3 = "tier_3";
    @Deprecated
    protected static final String STRUCTURE_3X3 = STRUCTURE_TIER_1;
    @Deprecated
    protected static final String STRUCTURE_5X5 = STRUCTURE_TIER_2;
    @Deprecated
    protected static final String STRUCTURE_7X7 = STRUCTURE_TIER_3;

    private static IStructureDefinition<MTENuclearReactor> STRUCTURE_DEFINITION = null;

    public int mPipeTier = -1;
    public int gridSize = 0;
    public int coreDimension = 0;
    public INuclearTile[][] mGrid = null;
    public final List<IGregTechTileEntity> mNuclearTiles = new ArrayList<>();

    public int getReactorTier() {
        if (gridSize == 3) return 1;
        if (gridSize == 7) return 2;
        if (gridSize == 11) return 3;
        return 0;
    }

    // Telemetry
    public double mCoreTemp = 20.0;
    public double mAvgTemp = 20.0;
    public int mNeutronsProduced = 0;
    public int mFastAbsorbed = 0;
    public int mThermalAbsorbed = 0;
    public int mEscapedNeutrons = 0;
    public double mReactivity = 1.0;
    public long mDirectPowerEUt = 0;
    public long mWallNeutronAccumulator = 0;
    public int mWallMaintenanceTimer = 0;
    public int mOutputCoolantRate = 0;
    public String mOutputCoolantName = "";
    public static final int GUI_MODE_COMPONENTS = 0;
    public static final int GUI_MODE_TEMPERATURE = 1;
    public static final int GUI_MODE_NEUTRON_FLUX = 2;
    public static final int GUI_MODE_NEUTRON_ABSORPTION = 3;
    public int mCurrentGuiMode = GUI_MODE_COMPONENTS;
    public ReactorGridSyncData mClientGridData = null;

    public int mHatchTier = -1;
    public boolean mHatchTierInconsistent = false;

    public double getMaintenanceEfficiency() {
        if (!mMachine) return 0.0;
        int issues = Math.max(0, getIdealStatus() - getRepairStatus());
        return Math.max(0.0, 1.0 - (issues * 0.10));
    }

    public void causeNewMaintenanceIssue() {
        List<Integer> working = new ArrayList<>();
        if (mWrench) working.add(0);
        if (mScrewdriver) working.add(1);
        if (mSoftMallet) working.add(2);
        if (mHardHammer) working.add(3);
        if (mSolderingTool) working.add(4);
        if (mCrowbar) working.add(5);
        if (!working.isEmpty()) {
            int pick = working.get(getBaseMetaTileEntity().getRandomNumber(working.size()));
            switch (pick) {
                case 0 -> mWrench = false;
                case 1 -> mScrewdriver = false;
                case 2 -> mSoftMallet = false;
                case 3 -> mHardHammer = false;
                case 4 -> mSolderingTool = false;
                case 5 -> mCrowbar = false;
            }
            if (getBaseMetaTileEntity() != null) {
                getBaseMetaTileEntity().markDirty();
            }
        }
    }

    public static ItemStack getNuclearHatchStack(int tier) {
        return switch (tier) {
            case 1 -> ItemList.Hatch_Nuclear_LV.get(1);
            case 2 -> ItemList.Hatch_Nuclear_MV.get(1);
            case 3 -> ItemList.Hatch_Nuclear_HV.get(1);
            case 4 -> ItemList.Hatch_Nuclear_EV.get(1);
            case 5 -> ItemList.Hatch_Nuclear_IV.get(1);
            case 6 -> ItemList.Hatch_Nuclear_LuV.get(1);
            case 7 -> ItemList.Hatch_Nuclear_ZPM.get(1);
            case 8 -> ItemList.Hatch_Nuclear_UV.get(1);
            case 9 -> ItemList.Hatch_Nuclear_UHV.get(1);
            default -> null;
        };
    }

    public static class NuclearHatchElement implements IStructureElement<MTENuclearReactor> {

        @Override
        public boolean check(MTENuclearReactor t, World world, int x, int y, int z) {
            if (world.getTileEntity(x, y, z) instanceof IGregTechTileEntity te) {
                IMetaTileEntity mte = te.getMetaTileEntity();
                if (mte instanceof INuclearTile) {
                    if (mte instanceof MTEHatchNuclearHatch hatch) {
                        int tier = hatch.mTier;
                        if (t.mHatchTier == -1) {
                            t.mHatchTier = tier;
                        } else if (t.mHatchTier != tier) {
                            t.mHatchTierInconsistent = true;
                        }
                    }
                    t.mNuclearTiles.add(te);
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean couldBeValid(MTENuclearReactor t, World world, int x, int y, int z, ItemStack trigger) {
            if (world.getTileEntity(x, y, z) instanceof IGregTechTileEntity te) {
                return te.getMetaTileEntity() instanceof INuclearTile;
            }
            return world.getBlock(x, y, z) == GregTechAPI.sBlockMachines;
        }

        @Override
        public boolean spawnHint(MTENuclearReactor t, World world, int x, int y, int z, ItemStack trigger) {
            StructureLibAPI.hintParticle(world, x, y, z, GregTechAPI.sBlockMachines, 0);
            return true;
        }

        @Override
        public boolean placeBlock(MTENuclearReactor t, World world, int x, int y, int z, ItemStack trigger) {
            int tier = GTStructureChannels.NUCLEAR_HATCH.getValueClamped(trigger, 1, 9);
            ItemStack stack = getNuclearHatchStack(tier);
            if (stack == null) return false;
            if (stack.getItem() instanceof ItemMachines itemMachines) {
                boolean success = itemMachines
                    .placeBlockAt(stack, null, world, x, y, z, ForgeDirection.UP.ordinal(), 0.5f, 0.5f, 0.5f, 0);
                if (success && world.getTileEntity(x, y, z) instanceof ITurnable turnable) {
                    turnable.setFrontFacing(ForgeDirection.UP);
                }
                return success;
            }
            return false;
        }

        @Override
        public PlaceResult survivalPlaceBlock(MTENuclearReactor t, World world, int x, int y, int z, ItemStack trigger,
            AutoPlaceEnvironment env) {
            if (check(t, world, x, y, z)) return PlaceResult.SKIP;
            if (!StructureLibAPI.isBlockTriviallyReplaceable(world, x, y, z, env.getActor())) {
                return PlaceResult.REJECT;
            }
            int tier = GTStructureChannels.NUCLEAR_HATCH.getValueClamped(trigger, 1, 9);
            ItemStack stack = getNuclearHatchStack(tier);
            if (stack == null) return PlaceResult.REJECT;

            PlaceResult result = StructureUtility.survivalPlaceBlock(
                stack,
                ItemStackPredicate.NBTMode.EXACT,
                null,
                false,
                world,
                x,
                y,
                z,
                env.getSource(),
                env.getActor(),
                env.getChatter());
            if (result == PlaceResult.ACCEPT && world.getTileEntity(x, y, z) instanceof ITurnable turnable) {
                turnable.setFrontFacing(ForgeDirection.UP);
            }
            return result;
        }

        @Override
        public BlocksToPlace getBlocksToPlace(MTENuclearReactor t, World world, int x, int y, int z, ItemStack trigger,
            AutoPlaceEnvironment env) {
            int tier = GTStructureChannels.NUCLEAR_HATCH.getValueClamped(trigger, 1, 9);
            ItemStack stack = getNuclearHatchStack(tier);
            return stack != null ? BlocksToPlace.create(stack) : BlocksToPlace.createEmpty();
        }
    }

    public int getPipeTier() {
        return mPipeTier;
    }

    public void setPipeTier(int aPipeTier) {
        this.mPipeTier = aPipeTier;
    }

    public static String getPipeTierName(int tier) {
        return NuclearSimulationEngine.getPipeTierName(tier);
    }

    public MTENuclearReactor(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTENuclearReactor(String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTENuclearReactor(this.mName);
    }

    @Override
    public ITexture getCasingTexture() {
        return NuclearCasing.getCasingTexture();
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        if (side == aFacing) {
            return new ITexture[] { NuclearCasing.getCasingTexture(),
                TextureFactory.of(
                    aActive ? gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_FISSION_REACTOR_ACTIVE
                        : gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_FISSION_REACTOR) };
        }
        return new ITexture[] { NuclearCasing.getCasingTexture() };
    }

    @Override
    public IStructureDefinition<MTENuclearReactor> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<MTENuclearReactor>builder()
                // Tier 1: 5x5 Footprint, 3x3 Octagonal Core (5 cells), Height 5
                .addShape(
                    STRUCTURE_3X3,
                    transpose(
                        new String[][] {
                            // Slice 0 (Top - Nuclear Hatches & Casings)
                            { " ccc ", "ccgcc", "cgggc", "ccgcc", " ccc " },
                            // Slice 1 (Upper Pipe Casings)
                            { " ccc ", "ccpcc", "cpppc", "ccpcc", " ccc " },
                            // Slice 2 (Middle Pipe Casings)
                            { " ccc ", "ccpcc", "cpppc", "ccpcc", " ccc " },
                            // Slice 3 (Controller layer - Front Center)
                            { " c~c ", "ccpcc", "cpppc", "ccpcc", " ccc " },
                            // Slice 4 (Bottom - Nuclear Casings)
                            { " ccc ", "ccccc", "ccccc", "ccccc", " ccc " } }))
                // Tier 2: 9x9 Footprint, 7x7 Octagonal Core (45 cells), Height 5
                .addShape(
                    STRUCTURE_5X5,
                    transpose(
                        new String[][] {
                            // Slice 0 (Top - Nuclear Hatches & Casings)
                            { "  ccccc  ", " cgggggc ", "cgggggggc", "cgggggggc", "cgggggggc", "cgggggggc", "cgggggggc",
                                " cgggggc ", "  ccccc  " },
                            // Slice 1 (Upper Pipe Casings)
                            { "  ccccc  ", " cpppppc ", "cpppppppc", "cpppppppc", "cpppppppc", "cpppppppc", "cpppppppc",
                                " cpppppc ", "  ccccc  " },
                            // Slice 2 (Middle Pipe Casings)
                            { "  ccccc  ", " cpppppc ", "cpppppppc", "cpppppppc", "cpppppppc", "cpppppppc", "cpppppppc",
                                " cpppppc ", "  ccccc  " },
                            // Slice 3 (Controller layer - Front Center)
                            { "  cc~cc  ", " cpppppc ", "cpppppppc", "cpppppppc", "cpppppppc", "cpppppppc", "cpppppppc",
                                " cpppppc ", "  ccccc  " },
                            // Slice 4 (Bottom - Nuclear Casings)
                            { "  ccccc  ", " ccccccc ", "ccccccccc", "ccccccccc", "ccccccccc", "ccccccccc", "ccccccccc",
                                " ccccccc ", "  ccccc  " } }))
                // Tier 3: 13x13 Footprint, 11x11 Octagonal Core (109 cells), Height 5
                .addShape(
                    STRUCTURE_7X7,
                    transpose(
                        new String[][] {
                            // Slice 0 (Top - Nuclear Hatches & Casings)
                            { "   ccccccc   ", "  cgggggggc  ", " cgggggggggc ", "cgggggggggggc", "cgggggggggggc",
                                "cgggggggggggc", "cgggggggggggc", "cgggggggggggc", "cgggggggggggc", "cgggggggggggc",
                                " cgggggggggc ", "  cgggggggc  ", "   ccccccc   " },
                            // Slice 1 (Upper Pipe Casings)
                            { "   ccccccc   ", "  cpppppppc  ", " cpppppppppc ", "cpppppppppppc", "cpppppppppppc",
                                "cpppppppppppc", "cpppppppppppc", "cpppppppppppc", "cpppppppppppc", "cpppppppppppc",
                                " cpppppppppc ", "  cpppppppc  ", "   ccccccc   " },
                            // Slice 2 (Middle Pipe Casings)
                            { "   ccccccc   ", "  cpppppppc  ", " cpppppppppc ", "cpppppppppppc", "cpppppppppppc",
                                "cpppppppppppc", "cpppppppppppc", "cpppppppppppc", "cpppppppppppc", "cpppppppppppc",
                                " cpppppppppc ", "  cpppppppc  ", "   ccccccc   " },
                            // Slice 3 (Controller layer - Front Center)
                            { "   ccc~ccc   ", "  cpppppppc  ", " cpppppppppc ", "cpppppppppppc", "cpppppppppppc",
                                "cpppppppppppc", "cpppppppppppc", "cpppppppppppc", "cpppppppppppc", "cpppppppppppc",
                                " cpppppppppc ", "  cpppppppc  ", "   ccccccc   " },
                            // Slice 4 (Bottom - Nuclear Casings)
                            { "   ccccccc   ", "  ccccccccc  ", " ccccccccccc ", "ccccccccccccc", "ccccccccccccc",
                                "ccccccccccccc", "ccccccccccccc", "ccccccccccccc", "ccccccccccccc", "ccccccccccccc",
                                " ccccccccccc ", "  ccccccccc  ", "   ccccccc   " } }))
                .addElement(
                    'c',
                    ofChain(
                        buildHatchAdder(MTENuclearReactor.class).atLeast(Maintenance)
                            .adder(
                                (t, te, index) -> t.addMaintenanceToMachineList(te, index)
                                    || t.addDynamoToMachineList(te, index)
                                    || t.addExoticDynamoToMachineList(te, index))
                            .casingIndex(CASING_INDEX)
                            .hint(1)
                            .build(),
                        NuclearCasing.asElement()))
                .addElement('p', chainItemPipeCasings(-1, (t, casingTier) -> {
                    if (casingTier < 3) {
                        t.mPipeTier = -1;
                    } else {
                        t.mPipeTier = casingTier - 3;
                    }
                }, t -> t.mPipeTier == -1 ? -1 : t.mPipeTier + 3))
                .addElement('g', GTStructureChannels.NUCLEAR_HATCH.use(new NuclearHatchElement()))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType("Nuclear Fission Reactor")
            .addInfo("Modular nuclear reactor simulating discrete neutron transport and moderation")
            .addInfo("Supports self-stabilizing negative temperature reactivity feedback")
            .addInfo("Height is fixed at 5 blocks for all tiers (Octagonal prism chamber)")
            .addInfo("Core chamber features cut-corner null cells with reflecting/absorbing casing walls")
            .addInfo("Wall heat dissipation is uniformly distributed to all active cells via coolant pool")
            .addInfo("Item Pipe Casings determine operating temperature and allowed coolants:")
            .addInfo(" - Electrum: IC2 Coolant -> Hot Coolant (Max 1000 °C)")
            .addInfo(" - Platinum: Distilled Water -> Steam (Max 1400 °C)")
            .addInfo(" - Osmium: HP Distilled Water -> Superheated Steam (Max 1800 °C)")
            .addInfo(" - Quantium: Heavy Water -> Heavy Water Steam (Max 2200 °C)")
            .addInfo(" - Fluxed Electrum: HP Heavy Water -> HW Supercritical Steam (Max 2600 °C)")
            .addInfo(" - Black Plutonium: All coolants supported (Max 3200 °C)")
            .addInfo("Accepts Dynamo and Multi-Amp Dynamo Hatches for direct Betavoltaic EU output")
            .addInfo(" - Betavoltaic Cells convert absorbed neutron flux directly to EU (HV 2A, EV 2A)")
            .addInfo(EnumChatFormatting.RED + "WARNING: Regular water does not work!")
            .addInfo(EnumChatFormatting.RED + "WARNING: Overheating hatches void contents!")
            .addInfo(
                EnumChatFormatting.RED
                    + "WARNING: Insufficient casing tier for HP coolants causes catastrophic explosion!")
            .beginVariableStructureBlock(5, 13, 5, 5, 5, 13, false)
            .addController("Front center, 2nd layer")
            .addCasing("44+", "Nuclear Casings", false)
            .addCasing(
                "15+",
                "Item Pipe Casings (Electrum / Platinum / Osmium / Quantium / Fluxed Electrum / Black Plutonium)",
                false)
            .addOtherStructurePart("Nuclear Bus / Hatch", "Top layer octagonal core positions", 1)
            .addMaintenanceHatch("Any outer casing (Exactly 1)", 1)
            .addDynamoHatch("Any outer casing (Optional for Betavoltaic direct EU, max 1)", 1)
            .addSubChannel(GTStructureChannels.ITEM_PIPE_CASING)
            .addSubChannel(GTStructureChannels.NUCLEAR_HATCH)
            .toolTipFinisher(EnumChatFormatting.AQUA + "GregTech Nuclear Power");
        return tt;
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        int tier = stackSize.stackSize;
        if (tier == 2) {
            buildPiece(STRUCTURE_5X5, stackSize, hintsOnly, 4, 3, 0);
        } else if (tier == 3) {
            buildPiece(STRUCTURE_7X7, stackSize, hintsOnly, 6, 3, 0);
        } else {
            buildPiece(STRUCTURE_3X3, stackSize, hintsOnly, 2, 3, 0);
        }
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        int tier = stackSize.stackSize;
        if (tier == 2) {
            return survivalBuildPiece(STRUCTURE_5X5, stackSize, 4, 3, 0, elementBudget, env, false, true);
        } else if (tier == 3) {
            return survivalBuildPiece(STRUCTURE_7X7, stackSize, 6, 3, 0, elementBudget, env, false, true);
        } else {
            return survivalBuildPiece(STRUCTURE_3X3, stackSize, 2, 3, 0, elementBudget, env, false, true);
        }
    }

    public void updateNuclearTilesPipeTier() {
        for (IGregTechTileEntity te : mNuclearTiles) {
            if (te != null && te.getMetaTileEntity() instanceof MTEHatchNuclearHatch hatch) {
                hatch.setReactorPipeTier(mPipeTier);
            }
        }
    }

    public void explodeReactor(boolean highPressure, String reason) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || !base.isServerSide()) return;

        GTLog.writeExplosionLog(this, reason);
        World world = base.getWorld();
        int cX = base.getXCoord();
        int cY = base.getYCoord();
        int cZ = base.getZCoord();

        // High pressure causes twice the explosion radius (18.0F vs 9.0F from large boilers)
        float strength = highPressure ? 18.0F : 9.0F;

        Pollution.addPollution(base, GTMod.proxy.mPollutionOnExplosion * (highPressure ? 4 : 2));

        for (IGregTechTileEntity te : mNuclearTiles) {
            if (te != null && !te.isDead()) {
                world.setBlock(te.getXCoord(), te.getYCoord(), te.getZCoord(), Blocks.air);
            }
        }

        world.setBlock(cX, cY, cZ, Blocks.air);

        GTUtility.sendSoundToPlayers(
            world,
            SoundResource.IC2_MACHINES_MACHINE_OVERLOAD,
            1.0F,
            -1,
            cX + 0.5,
            cY + 0.5,
            cZ + 0.5);

        if (GregTechAPI.sMachineExplosions) {
            world.createExplosion(null, cX + 0.5, cY + 0.5, cZ + 0.5, strength, true);
        }
    }

    public void triggerDryCoolantShutdown(String reason) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || !base.isServerSide()) return;

        GTLog.writeExplosionLog(this, "DRY COOLANT POWERFAIL: " + reason);

        // Void all coolant in fluid hatches
        for (IGregTechTileEntity te : mNuclearTiles) {
            if (te != null && te.getMetaTileEntity() instanceof MTEHatchNuclearHatch hatch) {
                hatch.mInputFluid = null;
                hatch.mOutputFluid = null;
                hatch.mWasDry = true;
                if (hatch.getBaseMetaTileEntity() != null) {
                    hatch.getBaseMetaTileEntity()
                        .markDirty();
                }
            } else if (te != null && te.getMetaTileEntity() instanceof MTEHatchNuclearBus bus) {
                // Void all fuel in nuclear bus hatches, but PRESERVE other components like reflectors and betavoltaics!
                if (bus.isFuel()) {
                    bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = null;
                    if (bus.getBaseMetaTileEntity() != null) {
                        bus.getBaseMetaTileEntity()
                            .markDirty();
                    }
                }
            }
        }

        // Shut down reactor with power loss & powerfail event
        stopMachine(ShutDownReasonRegistry.POWER_LOSS);
        if (GTMod.proxy.powerfailTracker != null) {
            GTMod.proxy.powerfailTracker.createPowerfailEvent(base);
        }
        super.mEfficiency = 0;
        mReactivity = 0.0;
        mWallNeutronAccumulator = 0;
        mWallMaintenanceTimer = 0;
        mOutputCoolantRate = 0;
        mOutputCoolantName = "";

        World world = base.getWorld();
        int cX = base.getXCoord();
        int cY = base.getYCoord();
        int cZ = base.getZCoord();
        GTUtility.sendSoundToPlayers(
            world,
            SoundResource.IC2_MACHINES_MACHINE_OVERLOAD,
            0.8F,
            0.5F,
            cX + 0.5,
            cY + 0.5,
            cZ + 0.5);
    }

    @Override
    public void onMachineBlockUpdate() {
        super.onMachineBlockUpdate();
        if (!mMachine) {
            mWallNeutronAccumulator = 0;
            mWallMaintenanceTimer = 0;
            mOutputCoolantRate = 0;
            mOutputCoolantName = "";
            for (IGregTechTileEntity te : mNuclearTiles) {
                if (te != null && te.getMetaTileEntity() instanceof MTEHatchNuclearHatch hatch) {
                    hatch.mWasDry = false;
                }
            }
        }
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        mNuclearTiles.clear();
        mGrid = null;
        gridSize = 0;
        coreDimension = 0;
        mPipeTier = -1;
        mHatchTier = -1;
        mHatchTierInconsistent = false;

        if (checkPiece(STRUCTURE_3X3, 2, 3, 0, errors)) {
            gridSize = 3;
            coreDimension = 5;
        } else {
            mNuclearTiles.clear();
            mPipeTier = -1;
            mHatchTier = -1;
            mHatchTierInconsistent = false;
            errors.clear();
            if (checkPiece(STRUCTURE_5X5, 4, 3, 0, errors)) {
                gridSize = 7;
                coreDimension = 9;
            } else {
                mNuclearTiles.clear();
                mPipeTier = -1;
                mHatchTier = -1;
                mHatchTierInconsistent = false;
                errors.clear();
                if (checkPiece(STRUCTURE_7X7, 6, 3, 0, errors)) {
                    gridSize = 11;
                    coreDimension = 13;
                } else {
                    return;
                }
            }
        }

        checkOneMaintenanceHatch(errors);

        int totalDynamos = mDynamoHatches.size() + mExoticDynamoHatches.size();
        if (totalDynamos > 1) {
            errors.add(StructureErrors.tooManyHatches(ItemList.Hatch_Dynamo_HV.get(1), 1));
        }

        if (mPipeTier < 0) {
            errors.add(StructureErrors.of("GT5U.gui.text.structure_error.invalid_pipe_tier"));
        }

        if (mHatchTierInconsistent) {
            errors.add(StructureErrors.of("GT5U.gui.text.structure_error.inconsistent_nuclear_hatch_tier"));
        }

        if (!errors.isEmpty()) {
            return;
        }

        // Build 2D grid from matched tiles
        mGrid = new INuclearTile[gridSize][gridSize];
        int cX = aBaseMetaTileEntity.getXCoord();
        int cZ = aBaseMetaTileEntity.getZCoord();
        ForgeDirection facing = aBaseMetaTileEntity.getFrontFacing();

        int wallThickness = (coreDimension - gridSize) / 2;

        for (IGregTechTileEntity te : mNuclearTiles) {
            int dx = te.getXCoord() - cX;
            int dz = te.getZCoord() - cZ;

            int localX, localZ;
            if (facing == ForgeDirection.NORTH) {
                localX = dx;
                localZ = dz;
            } else if (facing == ForgeDirection.SOUTH) {
                localX = -dx;
                localZ = -dz;
            } else if (facing == ForgeDirection.EAST) {
                localX = -dz;
                localZ = dx;
            } else { // WEST
                localX = dz;
                localZ = -dx;
            }

            int gx = localX + (gridSize / 2);
            int gy = localZ - wallThickness;

            if (gx >= 0 && gx < gridSize && gy >= 0 && gy < gridSize) {
                if (te.getMetaTileEntity() instanceof INuclearTile nt) {
                    mGrid[gx][gy] = nt;
                }
            }
        }

        for (IGregTechTileEntity te : mNuclearTiles) {
            if (te != null && te.getMetaTileEntity() instanceof MTEHatchNuclearHatch hatch) {
                hatch.mWasDry = false;
            }
        }

        updateNuclearTilesPipeTier();
    }

    @Override
    public boolean showRecipeTextInGUI() {
        return false;
    }

    @Override
    public boolean shouldDisplayCheckRecipeResult() {
        return false;
    }

    @Override
    public CheckRecipeResult checkProcessing() {
        if (!mMachine || mGrid == null) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        mMaxProgresstime = 20;
        super.mEfficiency = (int) Math.round(getMaintenanceEfficiency() * 10000);
        mEfficiencyIncrease = 0;
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);

        if (aBaseMetaTileEntity.isServerSide() && mMachine) {
            if (mDirectPowerEUt > 0) {
                addEnergyOutputMultipleDynamos(mDirectPowerEUt, true);
            }

            if (mGrid != null && (aTick % 20 == 0)) {
                updateNuclearTilesPipeTier();

                // 1. Check for high-pressure coolant in insufficient casing tier -> EXPLODE!
                for (IGregTechTileEntity te : mNuclearTiles) {
                    if (te != null && te.getMetaTileEntity() instanceof MTEHatchNuclearHatch hatch) {
                        if (hatch.mInputFluid != null && hatch.mInputFluid.amount > 0) {
                            String name = hatch.mInputFluid.getFluid()
                                .getName()
                                .toLowerCase();
                            if (name.contains("highpressure")) {
                                int reqTier = MTEHatchNuclearHatch.getRequiredFluidTier(name);
                                if (mPipeTier < reqTier) {
                                    explodeReactor(
                                        true,
                                        "Catastrophic overpressure explosion: " + name
                                            + " requires "
                                            + NuclearSimulationEngine.getPipeTierVoltageName(reqTier)
                                            + " ("
                                            + NuclearSimulationEngine.getPipeTierName(reqTier)
                                            + ") casing or higher, but reactor only has "
                                            + NuclearSimulationEngine.getPipeTierVoltageName(mPipeTier)
                                            + " ("
                                            + NuclearSimulationEngine.getPipeTierName(mPipeTier)
                                            + ")");
                                    return;
                                }
                            }
                        }
                    }
                }

                // 2. Check dry hatch coolant injection (thermal shock) -> DRY COOLANT SHUTDOWN (no explosion)
                for (IGregTechTileEntity te : mNuclearTiles) {
                    if (te != null && te.getMetaTileEntity() instanceof MTEHatchNuclearHatch hatch) {
                        if (hatch.mInputFluid != null && hatch.mInputFluid.amount > 0) {
                            String name = hatch.mInputFluid.getFluid()
                                .getName()
                                .toLowerCase();
                            if (hatch.mWasDry) {
                                double boilingThreshold = NuclearSimulationEngine.getCoolantBoilingThreshold(name);
                                if (hatch.getTemperature() > boilingThreshold) {
                                    triggerDryCoolantShutdown(
                                        "Coolant injected into dry superheated hatch above boiling threshold (" + name
                                            + ", temp="
                                            + hatch.getTemperature()
                                            + "C > threshold="
                                            + boilingThreshold
                                            + "C)");
                                    return;
                                }
                                hatch.mWasDry = false;
                            }
                        } else {
                            // Hatch has zero coolant: controller remembers that
                            hatch.mWasDry = true;
                        }
                    }
                }

                // 3. Check loss-of-coolant: if active reactor has coolant hatches and all of them are dry
                boolean hasFuel = false;
                boolean hasCoolantHatches = false;
                boolean allCoolantDry = true;
                for (IGregTechTileEntity te : mNuclearTiles) {
                    if (te != null) {
                        if (te.getMetaTileEntity() instanceof MTEHatchNuclearBus bus && bus.isFuel()) {
                            hasFuel = true;
                        } else if (te.getMetaTileEntity() instanceof MTEHatchNuclearHatch hatch) {
                            hasCoolantHatches = true;
                            if (hatch.mInputFluid != null && hatch.mInputFluid.amount > 0) {
                                allCoolantDry = false;
                            }
                        }
                    }
                }
                if (hasFuel && hasCoolantHatches && allCoolantDry) {
                    triggerDryCoolantShutdown("Loss of Coolant: All coolant hatches depleted on active reactor");
                    return;
                }

                double maintEff = getMaintenanceEfficiency();
                NuclearSimulationEngine.SimulationResult res = NuclearSimulationEngine
                    .simulate(mGrid, gridSize, gridSize, maintEff);
                mCoreTemp = res.maxTemperature;
                mAvgTemp = res.averageTemperature;
                mNeutronsProduced = res.totalNeutronsGenerated;
                mFastAbsorbed = res.fastNeutronsAbsorbed;
                mThermalAbsorbed = res.thermalNeutronsAbsorbed;
                mEscapedNeutrons = res.neutronsEscaped;
                mReactivity = res.averageReactivity;

                // Accumulate wall neutron impacts for custom maintenance mechanic
                int wallHits = res.wallNeutronsReflected + res.wallNeutronsAbsorbed;
                mWallNeutronAccumulator += wallHits;
                mWallMaintenanceTimer += 20;

                // Once per minute (1200 ticks = 60s), check maintenance issue probability: min(0.1, N/10000)
                if (mWallMaintenanceTimer >= 1200) {
                    mWallMaintenanceTimer = 0;
                    long N = mWallNeutronAccumulator;
                    mWallNeutronAccumulator = 0;
                    double prob = Math.min(0.1, (double) N / 10000.0);
                    if (prob > 0.0 && (aBaseMetaTileEntity.getRandomNumber(1000000) / 1000000.0) < prob) {
                        causeNewMaintenanceIssue();
                    }
                }

                // Sum direct EU from betavoltaic cells across the grid
                long directEU = 0;
                for (int x = 0; x < gridSize; x++) {
                    for (int y = 0; y < gridSize; y++) {
                        INuclearTile tile = mGrid[x][y];
                        if (tile instanceof MTEHatchNuclearBus bus) {
                            directEU += bus.mDirectEUProduced;
                        }
                    }
                }
                mDirectPowerEUt = directEU;

                // Sum output coolant production across all coolant hatches
                int totalCoolantProduced = 0;
                String coolantName = "";
                for (IGregTechTileEntity te : mNuclearTiles) {
                    if (te != null && te.getMetaTileEntity() instanceof MTEHatchNuclearHatch hatch) {
                        if (hatch.mLastProducedAmount > 0) {
                            totalCoolantProduced += hatch.mLastProducedAmount;
                            if (coolantName.isEmpty() && hatch.mLastProducedFluidName != null
                                && !hatch.mLastProducedFluidName.isEmpty()) {
                                Fluid f = FluidRegistry.getFluid(hatch.mLastProducedFluidName);
                                if (f != null) {
                                    coolantName = f.getLocalizedName(new FluidStack(f, 1000));
                                } else {
                                    coolantName = hatch.mLastProducedFluidName;
                                }
                            }
                        }
                    }
                }
                mOutputCoolantRate = totalCoolantProduced;
                mOutputCoolantName = coolantName;
                super.mEfficiency = (int) Math.round(maintEff * 10000);

                // 4. Check casing-dependent maximum operating temperature:
                // Overheating hatches void items and fluids inside, but do NOT explode!
                double maxTemp = NuclearSimulationEngine.getMaxOperatingTemperature(mPipeTier);
                for (IGregTechTileEntity te : mNuclearTiles) {
                    if (te != null && te.getMetaTileEntity() instanceof INuclearTile nuclearTile) {
                        if (nuclearTile.getTemperature() > maxTemp) {
                            if (nuclearTile instanceof MTEHatchNuclearHatch hatch) {
                                hatch.mInputFluid = null;
                                hatch.mOutputFluid = null;
                                if (hatch.getBaseMetaTileEntity() != null) {
                                    hatch.getBaseMetaTileEntity()
                                        .markDirty();
                                }
                            } else if (nuclearTile instanceof MTEHatchNuclearBus bus) {
                                bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = null;
                                bus.mInventory[MTEHatchNuclearBus.SLOT_OUTPUT_1] = null;
                                bus.mInventory[MTEHatchNuclearBus.SLOT_OUTPUT_2] = null;
                                if (bus.getBaseMetaTileEntity() != null) {
                                    bus.getBaseMetaTileEntity()
                                        .markDirty();
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setDouble("mCoreTemp", mCoreTemp);
        aNBT.setDouble("mAvgTemp", mAvgTemp);
        aNBT.setInteger("gridSize", gridSize);
        aNBT.setInteger("coreDimension", coreDimension);
        aNBT.setInteger("mPipeTier", mPipeTier);
        aNBT.setLong("mDirectPowerEUt", mDirectPowerEUt);
        aNBT.setLong("mWallNeutronAccumulator", mWallNeutronAccumulator);
        aNBT.setInteger("mWallMaintenanceTimer", mWallMaintenanceTimer);
        aNBT.setInteger("mOutputCoolantRate", mOutputCoolantRate);
        aNBT.setString("mOutputCoolantName", mOutputCoolantName != null ? mOutputCoolantName : "");
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mCoreTemp = aNBT.getDouble("mCoreTemp");
        mAvgTemp = aNBT.getDouble("mAvgTemp");
        gridSize = aNBT.getInteger("gridSize");
        coreDimension = aNBT.getInteger("coreDimension");
        mPipeTier = aNBT.getInteger("mPipeTier");
        mDirectPowerEUt = aNBT.getLong("mDirectPowerEUt");
        mWallNeutronAccumulator = aNBT.getLong("mWallNeutronAccumulator");
        mWallMaintenanceTimer = aNBT.getInteger("mWallMaintenanceTimer");
        mOutputCoolantRate = aNBT.getInteger("mOutputCoolantRate");
        mOutputCoolantName = aNBT.getString("mOutputCoolantName");
    }

    public ReactorGridSyncData getClientGridData() {
        if (mClientGridData != null) return mClientGridData;
        if (mGrid != null && gridSize > 0) return collectGridSyncData();
        return null;
    }

    public void applyGridSyncData(ReactorGridSyncData data) {
        this.mClientGridData = data;
        if (data != null) {
            if (data.gridSize > 0) this.mMachine = true;
            this.gridSize = data.gridSize;
            this.coreDimension = data.coreDimension;
            this.mCoreTemp = data.coreTemp;
            this.mAvgTemp = data.avgTemp;
            this.mReactivity = data.efficiency;
            this.mPipeTier = data.pipeTier;
            this.mDirectPowerEUt = data.directPowerEUt;
            this.mNeutronsProduced = data.neutronsProduced;
            this.mFastAbsorbed = data.fastAbsorbed;
            this.mThermalAbsorbed = data.thermalAbsorbed;
            this.mEscapedNeutrons = data.escapedNeutrons;
            this.mOutputCoolantRate = data.outputCoolantRate;
            this.mOutputCoolantName = data.outputCoolantName;
        }
    }

    public ReactorGridSyncData collectGridSyncData() {
        ReactorGridSyncData data = new ReactorGridSyncData();
        data.gridSize = this.gridSize;
        data.coreDimension = this.coreDimension;
        data.coreTemp = (float) this.mCoreTemp;
        data.avgTemp = (float) this.mAvgTemp;
        data.efficiency = this.mReactivity;
        data.pipeTier = this.mPipeTier;
        data.directPowerEUt = this.mDirectPowerEUt;
        data.neutronsProduced = this.mNeutronsProduced;
        data.fastAbsorbed = this.mFastAbsorbed;
        data.thermalAbsorbed = this.mThermalAbsorbed;
        data.escapedNeutrons = this.mEscapedNeutrons;
        data.outputCoolantRate = this.mOutputCoolantRate;
        data.outputCoolantName = this.mOutputCoolantName;

        if (mGrid != null && gridSize > 0) {
            for (int x = 0; x < gridSize; x++) {
                for (int y = 0; y < gridSize; y++) {
                    INuclearTile tile = mGrid[x][y];
                    ReactorGridSyncData.ReactorGridCellData cell = new ReactorGridSyncData.ReactorGridCellData();
                    if (tile != null) {
                        cell.exists = true;
                        cell.temperature = (float) tile.getTemperature();
                        if (tile instanceof MTEHatchNuclearBus bus) {
                            cell.isFluid = false;
                            ItemStack in = bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT];
                            cell.itemStack = (in != null) ? in.copy() : null;
                            cell.fastFlux = bus.mLastFastFlux;
                            cell.thermalFlux = bus.mLastThermalFlux;
                            cell.fastAbsorbed = bus.mLastFastAbsorbed;
                            cell.thermalAbsorbed = bus.mLastThermalAbsorbed;
                            cell.directEU = bus.mDirectEUProduced;
                        } else if (tile instanceof MTEHatchNuclearHatch hatch) {
                            cell.isFluid = true;
                            cell.fluidStack = (hatch.mInputFluid != null) ? hatch.mInputFluid.copy() : null;
                            cell.fastFlux = hatch.mLastFastFlux;
                            cell.thermalFlux = hatch.mLastThermalFlux;
                            cell.fastAbsorbed = hatch.mLastFastAbsorbed;
                            cell.thermalAbsorbed = hatch.mLastThermalAbsorbed;
                        }
                    }
                    data.cells.add(cell);
                }
            }
        }
        return data;
    }

    public List<String> getModeButtonTooltip() {
        List<String> list = new ArrayList<>();
        switch (mCurrentGuiMode) {
            case GUI_MODE_COMPONENTS:
                list.add(EnumChatFormatting.WHITE + "Mode: " + EnumChatFormatting.GREEN + "Component View");
                list.add(
                    EnumChatFormatting.GRAY + "Click: Switch to " + EnumChatFormatting.GOLD + "Temperature Overlay");
                list.add(EnumChatFormatting.DARK_GRAY + "Shift-Click: Cycle through all overlay modes");
                break;
            case GUI_MODE_TEMPERATURE:
                list.add(EnumChatFormatting.WHITE + "Mode: " + EnumChatFormatting.GOLD + "Temperature Overlay");
                list.add(EnumChatFormatting.GRAY + "Click: Switch to " + EnumChatFormatting.GREEN + "Component View");
                list.add(EnumChatFormatting.DARK_GRAY + "Shift-Click: Cycle through all overlay modes");
                break;
            case GUI_MODE_NEUTRON_FLUX:
                list.add(EnumChatFormatting.WHITE + "Mode: " + EnumChatFormatting.AQUA + "Neutron Flux Heatmap");
                list.add(EnumChatFormatting.GRAY + "Click: Switch to " + EnumChatFormatting.GREEN + "Component View");
                list.add(EnumChatFormatting.DARK_GRAY + "Shift-Click: Cycle through all overlay modes");
                break;
            case GUI_MODE_NEUTRON_ABSORPTION:
                list.add(
                    EnumChatFormatting.WHITE + "Mode: "
                        + EnumChatFormatting.LIGHT_PURPLE
                        + "Neutron Absorption Heatmap");
                list.add(EnumChatFormatting.GRAY + "Click: Switch to " + EnumChatFormatting.GREEN + "Component View");
                list.add(EnumChatFormatting.DARK_GRAY + "Shift-Click: Cycle through all overlay modes");
                break;
        }
        return list;
    }

    @Override
    protected boolean useMui2() {
        return false;
    }

    @Override
    public boolean supportsPowerPanel() {
        return false;
    }

    public static final int REACTOR_GRID_WINDOW_ID = 20;

    public ButtonWidget createReactorGridButton(IWidgetBuilder<?> builder) {
        ButtonWidget button = (ButtonWidget) ButtonWidget.openSyncedWindowButton(REACTOR_GRID_WINDOW_ID)
            .setPlayClickSound(true)
            .setBackground(
                () -> new IDrawable[] { GTUITextures.BUTTON_STANDARD, new ItemDrawable(ItemList.RodUranium.get(1L)) })
            .setPos(174, 91)
            .setSize(16, 16);
        button.addTooltip(StatCollector.translateToLocal("GT5U.gui.button.reactor_hatches"))
            .setTooltipShowUpDelay(TOOLTIP_DELAY);
        return button;
    }

    @Override
    public void addUIWidgets(ModularWindow.Builder builder, UIBuildContext buildContext) {
        super.addUIWidgets(builder, buildContext);

        // Add Reactor Hatches button on the right column
        builder.widget(createReactorGridButton(builder));

        // Register synced window for the Reactor Hatches view
        buildContext.addSyncedWindow(REACTOR_GRID_WINDOW_ID, this::createReactorGridWindow);

        // Network syncer for reactor grid & telemetry
        builder.widget(
            new FakeSyncWidget<>(
                this::collectGridSyncData,
                this::applyGridSyncData,
                ReactorGridSyncData::writeToBuffer,
                ReactorGridSyncData::readFromBuffer));
    }

    @Override
    protected void drawTexts(DynamicPositionedColumn screenElements, SlotWidget inventorySlot) {
        super.drawTexts(screenElements, inventorySlot);

        screenElements.widget(
            new TextWidget()
                .setStringSupplier(
                    () -> String.format(
                        "Max Temp: %.1f / %.0f °C",
                        mCoreTemp,
                        NuclearSimulationEngine.getMaxOperatingTemperature(mPipeTier)))
                .setDefaultColor(Color.rgb(255, 200, 0))
                .setTextAlignment(Alignment.CenterLeft)
                .setEnabled(widget -> mMachine));
        screenElements.widget(
            new TextWidget().setStringSupplier(() -> String.format("Avg Reactivity: %.1f%%", mReactivity * 100.0))
                .setDefaultColor(Color.rgb(100, 200, 255))
                .setTextAlignment(Alignment.CenterLeft)
                .setEnabled(widget -> mMachine));
        screenElements.widget(
            new TextWidget()
                .setStringSupplier(() -> "Flux: " + NuclearSimulationEngine.formatNeutronFlux(mNeutronsProduced))
                .setDefaultColor(Color.rgb(100, 220, 255))
                .setTextAlignment(Alignment.CenterLeft)
                .setEnabled(widget -> mMachine));
        screenElements.widget(
            new TextWidget().setStringSupplier(
                () -> String
                    .format("Neutrons: %d fast, %d therm, %d esc", mFastAbsorbed, mThermalAbsorbed, mEscapedNeutrons))
                .setDefaultColor(Color.rgb(200, 200, 200))
                .setTextAlignment(Alignment.CenterLeft)
                .setEnabled(widget -> mMachine));
        screenElements.widget(
            new TextWidget()
                .setStringSupplier(
                    () -> (mOutputCoolantRate > 0 && mOutputCoolantName != null && !mOutputCoolantName.isEmpty())
                        ? String.format("Coolant Output: %,d L/s %s", mOutputCoolantRate, mOutputCoolantName)
                        : "Coolant Output: 0 L/s")
                .setDefaultColor(Color.rgb(100, 220, 255))
                .setTextAlignment(Alignment.CenterLeft)
                .setEnabled(widget -> mMachine));
        screenElements.widget(
            new TextWidget()
                .setStringSupplier(
                    () -> String.format("EU Output: %d EU/t", mDirectPowerEUt))
                .setDefaultColor(Color.rgb(180, 220, 180))
                .setTextAlignment(Alignment.CenterLeft)
                .setEnabled(widget -> mMachine));
    }

    public ModularWindow createReactorGridWindow(final EntityPlayer player) {
        final int w = 154;
        final int h = 180;
        final int parentW = getGUIWidth();
        final int parentH = getGUIHeight();

        ModularWindow.Builder builder = ModularWindow.builder(w, h);
        builder.setBackground(GTUITextures.BACKGROUND_SINGLEBLOCK_DEFAULT);
        builder.setGuiTint(getGUIColorization());
        builder.setDraggable(true);
        builder.setPos((size, window) -> {
            Pos2d mainPos = Alignment.Center.getAlignedPos(size, new Size(parentW, parentH));
            int x = (int) mainPos.getX() - w - 2;
            if (x < 2) {
                x = 2;
            }
            return new Pos2d(x, Math.max(10, (int) mainPos.getY()));
        });

        // Title
        builder.widget(
            new TextWidget().setStringSupplier(() -> "Core Hatches")
                .setDefaultColor(Color.rgb(40, 40, 40))
                .setTextAlignment(Alignment.CenterLeft)
                .setSize(90, 14)
                .setPos(10, 9));

        // Close Button
        builder.widget(
            ButtonWidget.closeWindowButton(true)
                .setPos(132, 4)
                .setSize(18, 18));

        // Mode Toggle Button
        ButtonWidget modeButton = new ButtonWidget() {

            @Override
            public void draw(float partialTicks) {
                super.draw(partialTicks);
                if (mCurrentGuiMode == GUI_MODE_COMPONENTS) {
                    new ItemDrawable(ItemList.RodUranium.get(1L)).draw(1, 1, 16, 16, partialTicks);
                } else if (mCurrentGuiMode == GUI_MODE_TEMPERATURE) {
                    new ItemDrawable(new ItemStack(Items.fire_charge)).draw(1, 1, 16, 16, partialTicks);
                } else if (mCurrentGuiMode == GUI_MODE_NEUTRON_FLUX) {
                    new ItemDrawable(new ItemStack(Items.nether_star)).draw(1, 1, 16, 16, partialTicks);
                } else {
                    new ItemDrawable(new ItemStack(Blocks.iron_bars)).draw(1, 1, 16, 16, partialTicks);
                }
            }
        };
        modeButton.setPos(110, 4)
            .setSize(18, 18);
        modeButton.setBackground(GTUITextures.BUTTON_STANDARD);
        modeButton.setUpdateTooltipEveryTick(true);
        modeButton.dynamicTooltip(this::getModeButtonTooltip);
        modeButton.setOnClick((clickData, widget) -> {
            if (clickData.shift) {
                mCurrentGuiMode = (mCurrentGuiMode + 1) % 4;
            } else {
                mCurrentGuiMode = (mCurrentGuiMode == GUI_MODE_COMPONENTS) ? GUI_MODE_TEMPERATURE : GUI_MODE_COMPONENTS;
            }
        });
        builder.widget(modeButton);

        // Core Grid Visualization
        builder.widget(new NuclearReactorGridWidget(this).setPos(14, 26));

        // Subtitle / Telemetry at bottom
        builder.widget(new TextWidget().setStringSupplier(() -> {
            ReactorGridSyncData sync = getClientGridData();
            if (sync == null || sync.gridSize <= 0) {
                return EnumChatFormatting.RED + "Offline - Structure Incomplete";
            }
            if (mCurrentGuiMode == GUI_MODE_TEMPERATURE) {
                return String.format(EnumChatFormatting.GOLD + "Max Temp: %.1f °C", sync.coreTemp);
            }
            if (mCurrentGuiMode == GUI_MODE_NEUTRON_FLUX) {
                return EnumChatFormatting.AQUA + "Flux: "
                    + NuclearSimulationEngine.formatNeutronFlux(sync.neutronsProduced);
            }
            if (sync.efficiency > 0.0001) {
                return String.format(
                    EnumChatFormatting.DARK_GREEN + "Avg Reactivity: %.1f %%  "
                        + EnumChatFormatting.GOLD
                        + "Max: %.0f°C",
                    sync.efficiency * 100.0,
                    sync.coreTemp);
            }
            return EnumChatFormatting.GRAY + "Status: Ready / Idle";
        })
            .setTextAlignment(Alignment.CenterLeft)
            .setSize(140, 14)
            .setPos(8, 158));

        return builder.build();
    }
}
