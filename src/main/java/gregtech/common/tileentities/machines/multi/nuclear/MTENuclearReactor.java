package gregtech.common.tileentities.machines.multi.nuclear;

import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.casing.Casings.NuclearCasing;
import static gregtech.api.enums.HatchElement.Maintenance;
import static gregtech.api.metatileentity.BaseTileEntity.TOOLTIP_DELAY;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static gregtech.api.util.GTStructureUtility.chainItemPipeCasings;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.structurelib.StructureLibAPI;
import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.alignment.enumerable.Rotation;
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
import com.gtnewhorizons.modularui.common.widget.DrawableWidget;
import com.gtnewhorizons.modularui.common.widget.DynamicPositionedColumn;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.Scrollable;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;

import codechicken.lib.gui.GuiDraw;
import goodgenerator.items.GGMaterial;
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
import gregtech.api.items.ItemCoolantCell;
import gregtech.api.items.ItemRadioactiveCell;
import gregtech.api.items.ItemRadioactiveCellIC;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.util.GTLog;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gregtech.common.blocks.ItemMachines;
import gregtech.common.misc.GTStructureChannels;
import gregtech.common.pollution.Pollution;
import ic2.api.reactor.IReactor;
import ic2.api.reactor.IReactorComponent;
import ic2.core.Ic2Items;
import ic2.core.item.reactor.ItemReactorMOX;
import ic2.core.item.reactor.ItemReactorUranium;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.overlay.tooltiprenderers.TTRenderBar;

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
        if (gridSize == 5) return 1;
        if (gridSize == 9) return 2;
        if (gridSize == 13) return 3;
        return 0;
    }

    // Telemetry
    public double mCachedAmbientTemp = NuclearSimulationEngine.DEFAULT_AMBIENT_TEMP;
    public double mCoreTemp = NuclearSimulationEngine.DEFAULT_AMBIENT_TEMP;
    public double mAvgTemp = NuclearSimulationEngine.DEFAULT_AMBIENT_TEMP;
    public boolean mScram = false;
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
    public int mCasing = 0;
    public final List<MTEHatchNuclearControl> mControlHatches = new ArrayList<>();
    public boolean mWasMachineFormed = false;
    public final List<IGregTechTileEntity> mLastFormedNuclearTiles = new ArrayList<>();
    public boolean mWorldSaved = false;

    @Override
    public boolean isDisablingAllowed() {
        return false;
    }

    @Override
    protected IAlignmentLimits getInitialAlignmentLimits() {
        return (d, r, f) -> d.offsetY == 0 && f.isNotFlipped();
    }

    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        if (!mWorldSaved) {
            mWrench = false;
            mScrewdriver = false;
            mSoftMallet = false;
            mHardHammer = false;
            mSolderingTool = false;
            mCrowbar = false;
        }
    }

    public boolean addNuclearControlHatchToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity mte = aTileEntity.getMetaTileEntity();
        if (mte instanceof MTEHatchNuclearControl controlHatch) {
            mControlHatches.add(controlHatch);
            return true;
        }
        return false;
    }

    @Override
    public void clearHatches() {
        super.clearHatches();
        mControlHatches.clear();
    }

    public double getMaintenanceEfficiency() {
        if (!mMachine) return 0.0;
        int ideal = getIdealStatus();
        if (ideal <= 0) return 1.0;
        int repair = getRepairStatus();
        return Math.max(0.0, Math.min(1.0, (double) repair / (double) ideal));
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
                if (mte instanceof MTEHatchNuclearHatch hatch) {
                    int tier = hatch.mTier;
                    if (t.mHatchTier == -1) {
                        t.mHatchTier = tier;
                    } else if (t.mHatchTier != tier) {
                        t.mHatchTierInconsistent = true;
                    }
                    t.mNuclearTiles.add(te);
                    return true;
                } else if (mte instanceof MTEHatchNuclearBus) {
                    t.mNuclearTiles.add(te);
                    return true;
                } else if (mte instanceof MTEHatchNuclearControlRod) {
                    t.mNuclearTiles.add(te);
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean couldBeValid(MTENuclearReactor t, World world, int x, int y, int z, ItemStack trigger) {
            if (world.getTileEntity(x, y, z) instanceof IGregTechTileEntity te) {
                IMetaTileEntity mte = te.getMetaTileEntity();
                return mte instanceof MTEHatchNuclearHatch || mte instanceof MTEHatchNuclearBus
                    || mte instanceof MTEHatchNuclearControlRod;
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
                // Tier 1: 5x5 Footprint, 5x5 Octagonal Chamber (21 cells), Height 5
                .addShape(
                    STRUCTURE_3X3,
                    transpose(
                        new String[][] {
                            // Slice 0 (Top - Nuclear Hatches & Casings)
                            { " ggg ", "ggggg", "ggggg", "ggggg", " ggg " },
                            // Slice 1 (Second from top - All Item Pipe Casings)
                            { " ppp ", "ppppp", "ppppp", "ppppp", " ppp " },
                            // Slice 2 (Third from top - Nuclear Casing Walls, Hollow Interior)
                            { " ccc ", "c   c", "c   c", "c   c", " ccc " },
                            // Slice 3 (Fourth from top / Controller layer - Front Center)
                            { " c~c ", "c   c", "c   c", "c   c", " ccc " },
                            // Slice 4 (Bottom - Nuclear Casings)
                            { " ccc ", "ccccc", "ccccc", "ccccc", " ccc " } }))
                // Tier 2: 9x9 Footprint, 9x9 Octagonal Chamber (69 cells), Height 5
                .addShape(
                    STRUCTURE_5X5,
                    transpose(
                        new String[][] {
                            // Slice 0 (Top - Nuclear Hatches & Casings)
                            { "  ggggg  ", " ggggggg ", "ggggggggg", "ggggggggg", "ggggggggg", "ggggggggg", "ggggggggg",
                                " ggggggg ", "  ggggg  " },
                            // Slice 1 (Second from top - All Item Pipe Casings)
                            { "  ppppp  ", " ppppppp ", "ppppppppp", "ppppppppp", "ppppppppp", "ppppppppp", "ppppppppp",
                                " ppppppp ", "  ppppp  " },
                            // Slice 2 (Third from top - Nuclear Casing Walls, Hollow Interior)
                            { "  ccccc  ", " c     c ", "c       c", "c       c", "c       c", "c       c", "c       c",
                                " c     c ", "  ccccc  " },
                            // Slice 3 (Fourth from top / Controller layer - Front Center)
                            { "  cc~cc  ", " c     c ", "c       c", "c       c", "c       c", "c       c", "c       c",
                                " c     c ", "  ccccc  " },
                            // Slice 4 (Bottom - Nuclear Casings)
                            { "  ccccc  ", " ccccccc ", "ccccccccc", "ccccccccc", "ccccccccc", "ccccccccc", "ccccccccc",
                                " ccccccc ", "  ccccc  " } }))
                // Tier 3: 13x13 Footprint, 13x13 Octagonal Chamber (145 cells), Height 5
                .addShape(
                    STRUCTURE_7X7,
                    transpose(
                        new String[][] {
                            // Slice 0 (Top - Nuclear Hatches & Casings)
                            { "   ggggggg   ", "  ggggggggg  ", " ggggggggggg ", "ggggggggggggg", "ggggggggggggg",
                                "ggggggggggggg", "ggggggggggggg", "ggggggggggggg", "ggggggggggggg", "ggggggggggggg",
                                " ggggggggggg ", "  ggggggggg  ", "   ggggggg   " },
                            // Slice 1 (Second from top - All Item Pipe Casings)
                            { "   ppppppp   ", "  ppppppppp  ", " ppppppppppp ", "ppppppppppppp", "ppppppppppppp",
                                "ppppppppppppp", "ppppppppppppp", "ppppppppppppp", "ppppppppppppp", "ppppppppppppp",
                                " ppppppppppp ", "  ppppppppp  ", "   ppppppp   " },
                            // Slice 2 (Third from top - Nuclear Casing Walls, Hollow Interior)
                            { "   ccccccc   ", "  c       c  ", " c         c ", "c           c", "c           c",
                                "c           c", "c           c", "c           c", "c           c", "c           c",
                                " c         c ", "  c       c  ", "   ccccccc   " },
                            // Slice 3 (Fourth from top / Controller layer - Front Center)
                            { "   ccc~ccc   ", "  c       c  ", " c         c ", "c           c", "c           c",
                                "c           c", "c           c", "c           c", "c           c", "c           c",
                                " c         c ", "  c       c  ", "   ccccccc   " },
                            // Slice 4 (Bottom - Nuclear Casings)
                            { "   ccccccc   ", "  ccccccccc  ", " ccccccccccc ", "ccccccccccccc", "ccccccccccccc",
                                "ccccccccccccc", "ccccccccccccc", "ccccccccccccc", "ccccccccccccc", "ccccccccccccc",
                                " ccccccccccc ", "  ccccccccc  ", "   ccccccc   " } }))
                .addElement(
                    'c',
                    buildHatchAdder(MTENuclearReactor.class).atLeast(Maintenance)
                        .adder(
                            (t, te, index) -> t.addMaintenanceToMachineList(te, index)
                                || t.addOutputToMachineList(te, index)
                                || t.addDynamoToMachineList(te, index)
                                || t.addExoticDynamoToMachineList(te, index)
                                || t.addNuclearControlHatchToMachineList(te, index))
                        .casingIndex(CASING_INDEX)
                        .hint(1)
                        .buildAndChain(StructureUtility.onElementPass(t -> t.mCasing++, NuclearCasing.asElement())))
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
            .addInfo("Height is fixed at 5 blocks for all tiers (octagonal prism chamber)")
            .addInfo("Core chamber features cut-corner null cells with reflecting/absorbing casing walls")
            .addInfo("Wall heat dissipation is uniformly distributed to all active cells via coolant pool")
            .addInfo("Item pipe casings determine operating temperature and allowed coolants:")
            .addInfo(" - Electrum: IC2 coolant -> hot coolant (max 1000 °C)")
            .addInfo(" - Platinum: distilled water -> steam (max 1400 °C)")
            .addInfo(" - Osmium: HP distilled water -> superheated steam (max 1800 °C)")
            .addInfo(" - Quantium: heavy water -> heavy water steam (max 2200 °C)")
            .addInfo(" - Fluxed Electrum: HP heavy water -> HW supercritical steam (max 2600 °C)")
            .addInfo(" - Black Plutonium: all coolants supported (max 3200 °C)")
            .addInfo("Accepts dynamo and multi-amp dynamo hatches for direct betavoltaic EU output")
            .addInfo(" - Betavoltaic cells convert absorbed neutron flux directly to EU (HV 2A, EV 2A)")
            .addInfo("Outputs (depleted items, steam, byproducts, molten cheese) eject to output buses and hatches")
            .addInfo(EnumChatFormatting.RED + "Warning: regular water does not work!")
            .addInfo(EnumChatFormatting.RED + "Warning: overheating hatches void contents!")
            .addInfo(
                EnumChatFormatting.RED
                    + "Warning: insufficient casing tier for HP coolants causes catastrophic explosion!")
            .beginVariableStructureBlock(5, 13, 5, 5, 5, 13, false)
            .addController("Front center, 2nd layer")
            .addCasing("22+", "Nuclear casings", false)
            .addCasing(
                "21+",
                "Item pipe casings (Electrum / Platinum / Osmium / Quantium / Fluxed Electrum / Black Plutonium)",
                false)
            .addOtherStructurePart("Nuclear bus / hatch / control rod hatch", "Top layer octagonal core positions", 1)
            .addOtherStructurePart("Nuclear control hatch", "Any outer casing", 2)
            .addMaintenanceHatch("Any outer casing (exactly 1)", 1)
            .addDynamoHatch("Any outer casing (optional for betavoltaic direct EU, max 1)", 1)
            .addOutputBus("Any outer casing (optional)", 1)
            .addOutputHatch("Any outer casing (optional)", 1)
            .addSubChannel(GTStructureChannels.ITEM_PIPE_CASING)
            .addSubChannel(GTStructureChannels.NUCLEAR_HATCH)
            .toolTipFinisher(EnumChatFormatting.AQUA + "GregTech nuclear power");
        return tt;
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        int tier = stackSize == null ? 1 : stackSize.stackSize;
        if (tier >= 3) {
            buildPiece(STRUCTURE_7X7, stackSize, hintsOnly, 6, 3, 0);
        } else if (tier == 2) {
            buildPiece(STRUCTURE_5X5, stackSize, hintsOnly, 4, 3, 0);
        } else {
            buildPiece(STRUCTURE_3X3, stackSize, hintsOnly, 2, 3, 0);
        }
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        int tier = stackSize == null ? 1 : stackSize.stackSize;
        if (tier >= 3) {
            return survivalBuildPiece(STRUCTURE_7X7, stackSize, 6, 3, 0, elementBudget, env, false, true);
        } else if (tier == 2) {
            return survivalBuildPiece(STRUCTURE_5X5, stackSize, 4, 3, 0, elementBudget, env, false, true);
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

    public void triggerThermalShock(MTEHatchNuclearHatch hatch, String reason) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || !base.isServerSide()) return;

        try {
            GTLog.writeExplosionLog(this, "THERMAL SHOCK: " + reason);
        } catch (Throwable ignored) {}

        // Void the coolant in the affected hatch (flashes into steam)
        if (hatch != null) {
            hatch.mInputFluid = null;
            hatch.mWasDry = true;
            if (hatch.getBaseMetaTileEntity() != null) {
                hatch.getBaseMetaTileEntity()
                    .markDirty();
            }
        }

        // Damage reactor internals: set maximum maintenance issues (drops efficiency to 0%)
        mWrench = false;
        mScrewdriver = false;
        mSoftMallet = false;
        mHardHammer = false;
        mSolderingTool = false;
        mCrowbar = false;
        super.mEfficiency = 0;

        // Issue powerfail notification to player (without stopping machine entity)
        try {
            if (GTMod.proxy != null && GTMod.proxy.powerfailTracker != null) {
                GTMod.proxy.powerfailTracker.createPowerfailEvent(base);
            }
        } catch (Throwable ignored) {}

        try {
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
        } catch (Throwable ignored) {}
    }

    public void triggerDryCoolantShutdown(String reason) {
        triggerThermalShock(null, reason);
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
        clearHatches();
        mNuclearTiles.clear();
        mGrid = null;
        gridSize = 0;
        coreDimension = 0;
        mPipeTier = -1;
        mHatchTier = -1;
        mHatchTierInconsistent = false;
        mCasing = 0;

        if (checkPiece(STRUCTURE_3X3, 2, 3, 0, errors)) {
            gridSize = 5;
            coreDimension = 5;
        } else {
            clearHatches();
            mNuclearTiles.clear();
            mPipeTier = -1;
            mHatchTier = -1;
            mHatchTierInconsistent = false;
            mCasing = 0;
            errors.clear();
            if (checkPiece(STRUCTURE_5X5, 4, 3, 0, errors)) {
                gridSize = 9;
                coreDimension = 9;
            } else {
                clearHatches();
                mNuclearTiles.clear();
                mPipeTier = -1;
                mHatchTier = -1;
                mHatchTierInconsistent = false;
                mCasing = 0;
                errors.clear();
                if (checkPiece(STRUCTURE_7X7, 6, 3, 0, errors)) {
                    gridSize = 13;
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

        checkCasingMin(errors, mCasing, 22);

        if (!errors.isEmpty()) {
            return;
        }

        // Build 2D grid from matched tiles
        mGrid = new INuclearTile[gridSize][gridSize];
        int cX = aBaseMetaTileEntity.getXCoord();
        int cY = aBaseMetaTileEntity.getYCoord();
        int cZ = aBaseMetaTileEntity.getZCoord();
        ExtendedFacing ef = getExtendedFacing();
        boolean isUpsideDown = ef.getRotation() == Rotation.UPSIDE_DOWN;
        int hOffset = gridSize / 2;
        int[] in = new int[3];
        int[] out = new int[3];

        for (IGregTechTileEntity te : mNuclearTiles) {
            in[0] = te.getXCoord() - cX;
            in[1] = te.getYCoord() - cY;
            in[2] = te.getZCoord() - cZ;

            ef.getOffsetABC(in, out);

            int localA = isUpsideDown ? -out[0] : out[0];
            int gx = localA + hOffset;
            int gy = out[2];

            if (gx >= 0 && gx < gridSize && gy >= 0 && gy < gridSize) {
                IMetaTileEntity mte = te.getMetaTileEntity();
                if (mte instanceof MTEHatchNuclearBus bus) {
                    mGrid[gx][gy] = new NuclearGridTile(this, bus, gx, gy);
                } else if (mte instanceof MTEHatchNuclearHatch hatch) {
                    mGrid[gx][gy] = new NuclearGridTile(this, hatch, gx, gy);
                } else if (mte instanceof MTEHatchNuclearControlRod rod) {
                    mGrid[gx][gy] = new NuclearGridTile(this, rod, gx, gy);
                }
            }
        }

        for (IGregTechTileEntity te : mNuclearTiles) {
            if (te != null && te.getMetaTileEntity() instanceof MTEHatchNuclearHatch hatch) {
                hatch.mWasDry = false;
            }
        }

        updateNuclearTilesPipeTier();

        mCachedAmbientTemp = getAmbientTemperature();
        if (mScram) {
            for (IGregTechTileEntity te : mNuclearTiles) {
                if (te != null && te.getMetaTileEntity() instanceof MTEHatchNuclearControlRod rod) {
                    rod.setScram(true);
                }
            }
        }
    }

    public void verifyCasingMin(List<StructureError> errors, int current, int required) {
        checkCasingMin(errors, current, required);
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
        mProgresstime = 0;
        super.mEfficiency = (int) Math.round(getMaintenanceEfficiency() * 10000);
        mEfficiencyIncrease = 0;
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    @Override
    public void checkMaintenance() {
        super.checkMaintenance();
        if (mMachine && getRepairStatus() == getIdealStatus()) {
            IGregTechTileEntity base = getBaseMetaTileEntity();
            if (base != null && base.getLastShutDownReason() == ShutDownReasonRegistry.NO_REPAIR) {
                base.setShutdownStatus(false);
                base.setShutDownReason(ShutDownReasonRegistry.NONE);
                base.enableWorking();
            }
        }
    }

    @Override
    protected void onStructureCheckFinished(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onStructureCheckFinished(aBaseMetaTileEntity);
        if (mMachine) {
            mWasMachineFormed = true;
            mLastFormedNuclearTiles.clear();
            mLastFormedNuclearTiles.addAll(mNuclearTiles);
        } else if (mWasMachineFormed) {
            mWasMachineFormed = false;
            handleStructureBreak();
            mLastFormedNuclearTiles.clear();
        }
    }

    @Override
    public void onRemoval() {
        if (mWasMachineFormed || mMachine) {
            mWasMachineFormed = false;
            handleStructureBreak();
            mLastFormedNuclearTiles.clear();
        }
        super.onRemoval();
    }

    public boolean isAnyTemperatureAbove100() {
        if (mCoreTemp > 100.0 || mAvgTemp > 100.0) {
            return true;
        }
        for (IGregTechTileEntity te : mLastFormedNuclearTiles) {
            if (te != null && !te.isDead()) {
                IMetaTileEntity mte = te.getMetaTileEntity();
                if (mte instanceof MTEHatchNuclearBus bus) {
                    if (bus.mTemperature > 100.0) return true;
                } else if (mte instanceof MTEHatchNuclearHatch hatch) {
                    if (hatch.mTemperature > 100.0) return true;
                } else if (mte instanceof MTEHatchNuclearControlRod rod) {
                    if (rod.mTemperature > 100.0) return true;
                }
            }
        }
        return false;
    }

    public void handleStructureBreak() {
        if (isAnyTemperatureAbove100()) {
            for (IGregTechTileEntity te : mLastFormedNuclearTiles) {
                if (te != null && !te.isDead()) {
                    IMetaTileEntity mte = te.getMetaTileEntity();
                    if (mte instanceof MTEHatchNuclearBus bus) {
                        for (int i = 0; i < bus.mInventory.length; i++) {
                            bus.mInventory[i] = null;
                        }
                        bus.markDirty();
                        if (bus.getBaseMetaTileEntity() != null) {
                            bus.getBaseMetaTileEntity()
                                .markDirty();
                        }
                    } else if (mte instanceof MTEHatchNuclearHatch hatch) {
                        hatch.mInputFluid = null;
                        hatch.markDirty();
                        if (hatch.getBaseMetaTileEntity() != null) {
                            hatch.getBaseMetaTileEntity()
                                .markDirty();
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);

        if (!aBaseMetaTileEntity.isAllowedToWork()) {
            aBaseMetaTileEntity.enableWorking();
        }

        if (aBaseMetaTileEntity.isServerSide() && mMachine) {
            if (mStartUpCheck >= 0) {
                checkMaintenance();
            }

            if (mDirectPowerEUt > 0) {
                addEnergyOutputMultipleDynamos(mDirectPowerEUt, true);
            }

            if (aTick % 100 == 0) {
                mCachedAmbientTemp = getAmbientTemperature();
            }

            if (mGrid != null && (aTick % 20 == 0)) {
                updateNuclearTilesPipeTier();

                double ambient = getAmbientTemperature();

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

                // 2. Check coolant boiling against ambient or dry hatch injection (thermal shock)
                for (IGregTechTileEntity te : mNuclearTiles) {
                    if (te != null && te.getMetaTileEntity() instanceof MTEHatchNuclearHatch hatch) {
                        if (hatch.mInputFluid != null && hatch.mInputFluid.amount > 0) {
                            String name = hatch.mInputFluid.getFluid()
                                .getName()
                                .toLowerCase();
                            double boilingThreshold = NuclearSimulationEngine.getCoolantBoilingThreshold(name);
                            if (ambient >= boilingThreshold) {
                                triggerThermalShock(
                                    hatch,
                                    "Coolant evaporates immediately at ambient temperature (" + name
                                        + ", ambient="
                                        + ambient
                                        + "C >= boiling threshold="
                                        + boilingThreshold
                                        + "C)");
                                return;
                            }
                            if (hatch.mWasDry) {
                                if (hatch.mTemperature > boilingThreshold) {
                                    triggerThermalShock(
                                        hatch,
                                        "Coolant injected into dry superheated hatch above boiling threshold (" + name
                                            + ", temp="
                                            + hatch.mTemperature
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

                double maintEff = getMaintenanceEfficiency();
                NuclearSimulationEngine.SimulationResult res = NuclearSimulationEngine
                    .simulate(mGrid, gridSize, gridSize, maintEff, ambient);
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
                        if (tile instanceof NuclearGridTile gt && gt.isBus()) {
                            directEU += gt.getBus().mDirectEUProduced;
                        }
                    }
                }
                mDirectPowerEUt = (long) Math.round(directEU * maintEff);

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
                    if (te != null) {
                        if (te.getMetaTileEntity() instanceof MTEHatchNuclearHatch hatch) {
                            if (hatch.mTemperature > maxTemp) {
                                hatch.mInputFluid = null;
                                hatch.markTileDirty();
                            }
                        } else if (te.getMetaTileEntity() instanceof MTEHatchNuclearBus bus) {
                            if (bus.mTemperature > maxTemp) {
                                bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = null;
                                bus.markTileDirty();
                            }
                        } else if (te.getMetaTileEntity() instanceof MTEHatchNuclearControlRod rod) {
                            if (rod.mTemperature > maxTemp) {
                                rod.mInventory[MTEHatchNuclearControlRod.SLOT_ROD] = null;
                                rod.markTileDirty();
                            }
                        }
                    }
                }

                // 5. Update coolant tracking and nuclear control hatches
                updateCoolantTracking();
                updateControlHatches();
            }
        } else if (aBaseMetaTileEntity.isServerSide()) {
            for (MTEHatchNuclearControl hatch : mControlHatches) {
                if (hatch != null && hatch.isValid()) {
                    hatch.setOutputRedstone((byte) 0);
                }
            }
        }
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setBoolean("mWorldSaved", true);
        aNBT.setBoolean("mScram", mScram);
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
        mWorldSaved = aNBT.getBoolean("mWorldSaved");
        if (!mWorldSaved) {
            mWrench = false;
            mScrewdriver = false;
            mSoftMallet = false;
            mHardHammer = false;
            mSolderingTool = false;
            mCrowbar = false;
        }
        mScram = aNBT.getBoolean("mScram");
        if (aNBT.hasKey("mCoreTemp")) {
            mCoreTemp = aNBT.getDouble("mCoreTemp");
            mAvgTemp = aNBT.getDouble("mAvgTemp");
        } else {
            mCoreTemp = getAmbientTemperature();
            mAvgTemp = getAmbientTemperature();
        }
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
            this.mScram = data.scram;
            this.mCachedAmbientTemp = data.ambientTemp;
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
        data.scram = this.mScram;
        data.ambientTemp = (float) getAmbientTemperature();

        if (mGrid != null && gridSize > 0) {
            for (int x = 0; x < gridSize; x++) {
                for (int y = 0; y < gridSize; y++) {
                    INuclearTile tile = mGrid[x][y];
                    ReactorGridSyncData.ReactorGridCellData cell = new ReactorGridSyncData.ReactorGridCellData();
                    if (tile instanceof NuclearGridTile gt) {
                        cell.exists = true;
                        cell.temperature = (float) gt.getTemperature();
                        if (gt.isBus()) {
                            MTEHatchNuclearBus bus = gt.getBus();
                            cell.isFluid = false;
                            ItemStack in = bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT];
                            cell.itemStack = (in != null) ? in.copy() : null;
                            cell.fastFlux = bus.mLastFastFlux;
                            cell.thermalFlux = bus.mLastThermalFlux;
                            cell.fastAbsorbed = bus.mLastFastAbsorbed;
                            cell.thermalAbsorbed = bus.mLastThermalAbsorbed;
                            cell.directEU = bus.mDirectEUProduced;
                        } else if (gt.isHatch()) {
                            MTEHatchNuclearHatch hatch = gt.getHatch();
                            cell.isFluid = true;
                            cell.fluidStack = (hatch.mInputFluid != null) ? hatch.mInputFluid.copy() : null;
                            cell.fastFlux = hatch.mLastFastFlux;
                            cell.thermalFlux = hatch.mLastThermalFlux;
                            cell.fastAbsorbed = hatch.mLastFastAbsorbed;
                            cell.thermalAbsorbed = hatch.mLastThermalAbsorbed;
                        } else if (gt.isControlRod()) {
                            MTEHatchNuclearControlRod rod = gt.getControlRod();
                            cell.isFluid = false;
                            ItemStack in = rod.mInventory[MTEHatchNuclearControlRod.SLOT_ROD];
                            cell.itemStack = (in != null) ? in.copy() : null;
                            cell.fastFlux = rod.mLastFastFlux;
                            cell.thermalFlux = rod.mLastThermalFlux;
                            cell.fastAbsorbed = rod.mLastFastAbsorbed;
                            cell.thermalAbsorbed = rod.mLastThermalAbsorbed;
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
                list.add(EnumChatFormatting.WHITE + "Mode: " + EnumChatFormatting.GREEN + "Component view");
                list.add(
                    EnumChatFormatting.GRAY + "Click: switch to " + EnumChatFormatting.GOLD + "temperature overlay");
                list.add(EnumChatFormatting.DARK_GRAY + "Shift-click: cycle through all overlay modes");
                break;
            case GUI_MODE_TEMPERATURE:
                list.add(EnumChatFormatting.WHITE + "Mode: " + EnumChatFormatting.GOLD + "Temperature overlay");
                list.add(EnumChatFormatting.GRAY + "Click: switch to " + EnumChatFormatting.GREEN + "component view");
                list.add(EnumChatFormatting.DARK_GRAY + "Shift-click: cycle through all overlay modes");
                break;
            case GUI_MODE_NEUTRON_FLUX:
                list.add(EnumChatFormatting.WHITE + "Mode: " + EnumChatFormatting.AQUA + "Neutron flux heatmap");
                list.add(EnumChatFormatting.GRAY + "Click: switch to " + EnumChatFormatting.GREEN + "component view");
                list.add(EnumChatFormatting.DARK_GRAY + "Shift-click: cycle through all overlay modes");
                break;
            case GUI_MODE_NEUTRON_ABSORPTION:
                list.add(
                    EnumChatFormatting.WHITE + "Mode: "
                        + EnumChatFormatting.LIGHT_PURPLE
                        + "Neutron absorption heatmap");
                list.add(EnumChatFormatting.GRAY + "Click: switch to " + EnumChatFormatting.GREEN + "component view");
                list.add(EnumChatFormatting.DARK_GRAY + "Shift-click: cycle through all overlay modes");
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

    @Override
    public boolean supportsVoidProtection() {
        return false;
    }

    @Override
    public boolean supportsInputSeparation() {
        return false;
    }

    @Override
    public boolean supportsBatchMode() {
        return false;
    }

    @Override
    public boolean supportsSingleRecipeLocking() {
        return false;
    }

    public void setScram(boolean scram) {
        this.mScram = scram;
        for (IGregTechTileEntity te : mNuclearTiles) {
            if (te != null && te.getMetaTileEntity() instanceof MTEHatchNuclearControlRod rod) {
                rod.setScram(scram);
            }
        }
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null && base.getWorld() != null) {
            World world = base.getWorld();
            int cX = base.getXCoord();
            int cY = base.getYCoord();
            int cZ = base.getZCoord();
            GTUtility.sendSoundToPlayers(
                world,
                scram ? SoundResource.IC2_MACHINES_MACHINE_OVERLOAD : SoundResource.IC2_MACHINES_INTERRUPT_ONE,
                0.6F,
                1.0F,
                cX + 0.5,
                cY + 0.5,
                cZ + 0.5);
            base.markDirty();
        }
    }

    public ButtonWidget createScramButton(IWidgetBuilder<?> builder) {
        ButtonWidget scramButton = new ButtonWidget() {

            @Override
            public void draw(float partialTicks) {
                int x = 0;
                int y = 0;
                int w = getSize().width;
                int h = getSize().height;
                // Outer dark red/black border
                GuiDraw.drawRect(x, y, w, h, 0xFF330000);
                // Background red
                boolean active = mScram;
                int bgColor = isHovering() ? (active ? 0xFFFF3333 : 0xFFDD1111) : (active ? 0xFFCC0000 : 0xFFAA0000);
                GuiDraw.drawRect(x + 1, y + 1, w - 2, h - 2, bgColor);
                // Highlight line at top
                GuiDraw.drawRect(x + 1, y + 1, w - 2, 1, 0x55FFFFFF);
                // Text: SCRAM or SCRAMMED in bold white letters
                String text = active ? (EnumChatFormatting.BOLD + "SCRAMMED") : (EnumChatFormatting.BOLD + "SCRAM");
                int strW = GuiDraw.getStringWidth(text);
                int strH = 8;
                GuiDraw.drawString(text, (w - strW) / 2, (h - strH) / 2, 0xFFFFFFFF, true);
            }
        };
        scramButton.setPos(8, 91)
            .setSize(64, 16);
        scramButton.setPlayClickSound(true);
        scramButton.dynamicTooltip(() -> {
            List<String> tt = new ArrayList<>();
            tt.add(EnumChatFormatting.RED + "" + EnumChatFormatting.BOLD + "Emergency SCRAM");
            tt.add(EnumChatFormatting.GRAY + "Immediately inserts all control rods to 100%.");
            if (mScram) {
                tt.add(EnumChatFormatting.YELLOW + "Status: active (click to reset/disengage)");
            } else {
                tt.add(EnumChatFormatting.GREEN + "Status: disengaged (normal redstone control)");
            }
            return tt;
        });
        scramButton.setUpdateTooltipEveryTick(true);
        scramButton.setOnClick((clickData, widget) -> { setScram(!mScram); });
        return scramButton;
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
        builder.widget(
            new DrawableWidget().setDrawable(GTUITextures.PICTURE_SCREEN_BLACK)
                .setPos(4, 4)
                .setSize(190, 85));
        final SlotWidget inventorySlot = new SlotWidget(inventoryHandler, 1);
        builder.widget(
            inventorySlot.setPos(173, 167)
                .setBackground(GTUITextures.SLOT_DARK_GRAY));

        final DynamicPositionedColumn screenElements = new DynamicPositionedColumn();
        drawTexts(screenElements, inventorySlot);
        builder.widget(
            new Scrollable().setVerticalScroll()
                .widget(screenElements)
                .setPos(10, 7)
                .setSize(182, 79));

        builder.widget(createStructureUpdateButton(builder));

        // Add Reactor Hatches button on the right column
        builder.widget(createReactorGridButton(builder));

        // Add big red SCRAM button
        builder.widget(createScramButton(builder));

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
                        "Core Temp: %.1f / %.0f °C",
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
    }

    public ModularWindow createReactorGridWindow(final EntityPlayer player) {
        final int w = 154;
        final int h = 198;
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

        NuclearReactorGridWidget gridWidget = new NuclearReactorGridWidget(this);
        Scrollable scrollable = new Scrollable().setVerticalScroll()
            .setHorizontalScroll();
        scrollable.widget(gridWidget);
        scrollable.setPos(14, 24)
            .setSize(126, 126);
        gridWidget.setParentScrollable(scrollable);
        builder.widget(scrollable);

        // Zoom Out Button
        ButtonWidget zoomOutBtn = new ButtonWidget() {

            @Override
            public void draw(float partialTicks) {
                super.draw(partialTicks);
                String str = "-";
                int sw = GuiDraw.getStringWidth(str);
                GuiDraw.drawString(str, (getSize().width - sw) / 2, 5, 0xFFFFFF, false);
            }
        };
        zoomOutBtn.setPos(4, 4)
            .setSize(18, 18);
        zoomOutBtn.setBackground(GTUITextures.BUTTON_STANDARD);
        zoomOutBtn.addTooltip("Zoom out");
        zoomOutBtn.setOnClick((clickData, widget) -> gridWidget.zoomOut());
        builder.widget(zoomOutBtn);

        // Zoom In Button
        ButtonWidget zoomInBtn = new ButtonWidget() {

            @Override
            public void draw(float partialTicks) {
                super.draw(partialTicks);
                String str = "+";
                int sw = GuiDraw.getStringWidth(str);
                GuiDraw.drawString(str, (getSize().width - sw) / 2, 5, 0xFFFFFF, false);
            }
        };
        zoomInBtn.setPos(24, 4)
            .setSize(18, 18);
        zoomInBtn.setBackground(GTUITextures.BUTTON_STANDARD);
        zoomInBtn.addTooltip("Zoom in");
        zoomInBtn.setOnClick((clickData, widget) -> gridWidget.zoomIn());
        builder.widget(zoomInBtn);

        // Reset Zoom Button
        ButtonWidget zoomResetBtn = new ButtonWidget() {

            @Override
            public void draw(float partialTicks) {
                super.draw(partialTicks);
                String str = "1:1";
                int sw = GuiDraw.getStringWidth(str);
                GuiDraw.drawString(str, (getSize().width - sw) / 2, 5, 0xFFFFFF, false);
            }
        };
        zoomResetBtn.setPos(44, 4)
            .setSize(22, 18);
        zoomResetBtn.setBackground(GTUITextures.BUTTON_STANDARD);
        zoomResetBtn.dynamicTooltip(() -> {
            List<String> tt = new ArrayList<>();
            tt.add("Reset zoom");
            tt.add(EnumChatFormatting.GRAY + "Current zoom: " + gridWidget.getZoomPercent() + "%");
            return tt;
        });
        zoomResetBtn.setUpdateTooltipEveryTick(true);
        zoomResetBtn.setOnClick((clickData, widget) -> gridWidget.resetZoom());
        builder.widget(zoomResetBtn);

        // Zoom Percentage Label
        builder.widget(
            new TextWidget().setStringSupplier(() -> gridWidget.getZoomPercent() + "%")
                .setDefaultColor(Color.rgb(255, 255, 255))
                .setTextAlignment(Alignment.Center)
                .setSize(40, 10)
                .setPos(68, 8));

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

        // Close Button
        builder.widget(
            ButtonWidget.closeWindowButton(true)
                .setPos(132, 4)
                .setSize(18, 18));

        // Controller Position Indicator (Statically located below the grid in the center-bottom)
        builder.widget(new com.gtnewhorizons.modularui.api.widget.Widget() {

            private final ItemDrawable drawable = new ItemDrawable(ItemList.Machine_Nuclear_Reactor.get(1L));

            @Override
            public void draw(float partialTicks) {
                drawable.draw(0, 0, 16, 16, partialTicks);
            }
        }.setPos(69, 154)
            .setSize(16, 16)
            .addTooltip("Reactor controller (front face)"));

        // Subtitle / Telemetry at bottom
        builder.widget(new TextWidget().setStringSupplier(() -> {
            ReactorGridSyncData sync = getClientGridData();
            if (sync == null || sync.gridSize <= 0) {
                return EnumChatFormatting.RED + "Offline - structure incomplete";
            }
            if (mCurrentGuiMode == GUI_MODE_TEMPERATURE) {
                return String.format(EnumChatFormatting.GOLD + "Max temp: %.1f °C", sync.coreTemp);
            }
            if (mCurrentGuiMode == GUI_MODE_NEUTRON_FLUX) {
                return EnumChatFormatting.AQUA + "Flux: "
                    + NuclearSimulationEngine.formatNeutronFlux(sync.neutronsProduced);
            }
            if (sync.efficiency > 0.0001) {
                return String.format(
                    EnumChatFormatting.DARK_GREEN + "Reactivity: %.1f%%  " + EnumChatFormatting.GOLD + "Max: %.0f°C",
                    sync.efficiency * 100.0,
                    sync.coreTemp);
            }
            return EnumChatFormatting.GRAY + "Status: ready / idle";
        })
            .setTextAlignment(Alignment.Center)
            .setSize(146, 14)
            .setPos(4, 177));

        return builder.build();
    }

    private final ReactorDummy mReactorDummy = new ReactorDummy(this);

    public static double calculateAmbientTemperature(World world, int x, int y, int z) {
        if (world != null) {
            try {
                float bTemp = world.getBiomeGenForCoords(x, z)
                    .getFloatTemperature(x, y, z);
                return (bTemp * 100.0 - 32.0) / 1.8;
            } catch (Exception ignored) {}
        }
        return NuclearSimulationEngine.DEFAULT_AMBIENT_TEMP;
    }

    public double getAmbientTemperature() {
        if (getBaseMetaTileEntity() != null && getBaseMetaTileEntity().getWorld() != null) {
            mCachedAmbientTemp = calculateAmbientTemperature(
                getBaseMetaTileEntity().getWorld(),
                getBaseMetaTileEntity().getXCoord(),
                getBaseMetaTileEntity().getYCoord(),
                getBaseMetaTileEntity().getZCoord());
        }
        return mCachedAmbientTemp;
    }

    public int getRandomNumber(int max) {
        if (getBaseMetaTileEntity() != null) {
            return getBaseMetaTileEntity().getRandomNumber(max);
        }
        return (int) (Math.random() * max);
    }

    public boolean isItemFuel(ItemStack stack) {
        if (stack == null) return false;
        if (stack.getItem() instanceof ItemRadioactiveCell) return true;
        if (stack.getItem() instanceof ItemReactorUranium) return true;
        String name = stack.getUnlocalizedName();
        if (name != null) {
            String lower = name.toLowerCase();
            return lower.contains("uranium") || lower.contains("mox")
                || lower.contains("thorium")
                || lower.contains("plutonium")
                || lower.contains("naquadah")
                || lower.contains("naquadria")
                || lower.contains("tiberium")
                || lower.contains("thecore")
                || lower.contains("fuelrod");
        }
        return false;
    }

    public static boolean isFluidFuel(FluidStack fluid) {
        if (fluid == null || fluid.getFluid() == null) return false;
        String name = fluid.getFluid()
            .getName()
            .toLowerCase();
        if (name.contains("naquadah")) return false; // Avoid overlap with Large Naquadah Reactor
        if (name.contains("thoriumbasedliquidfuel") || (name.contains("thorium") && name.contains("liquidfuel")))
            return true;
        if (name.contains("uraniumbasedliquidfuel") || (name.contains("uranium") && name.contains("liquidfuel")))
            return true;
        if (name.contains("plutoniumbasedliquidfuel") || (name.contains("plutonium") && name.contains("liquidfuel")))
            return true;
        if (name.contains("uraniumhexafluoride")) return true;
        try {
            if (fluid.isFluidEqual(GGMaterial.uraniumBasedLiquidFuel.getFluidOrGas(1))
                || fluid.isFluidEqual(GGMaterial.uraniumBasedLiquidFuelExcited.getFluidOrGas(1))
                || fluid.isFluidEqual(GGMaterial.thoriumBasedLiquidFuel.getFluidOrGas(1))
                || fluid.isFluidEqual(GGMaterial.thoriumBasedLiquidFuelExcited.getFluidOrGas(1))
                || fluid.isFluidEqual(GGMaterial.plutoniumBasedLiquidFuel.getFluidOrGas(1))
                || fluid.isFluidEqual(GGMaterial.plutoniumBasedLiquidFuelExcited.getFluidOrGas(1))) {
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static Fluid getSpentFluid(FluidStack fuel) {
        if (fuel == null || fuel.getFluid() == null) return null;
        String name = fuel.getFluid()
            .getName()
            .toLowerCase();
        if (name.contains("thorium")) {
            try {
                return GGMaterial.thoriumBasedLiquidFuelDepleted.getFluidOrGas(1)
                    .getFluid();
            } catch (Throwable ignored) {}
            Fluid f = FluidRegistry.getFluid("thoriumbasedliquidfueldepleted");
            if (f != null) return f;
            return FluidRegistry.getFluid("fluid.thoriumbasedliquidfueldepleted");
        }
        if (name.contains("uraniumbased") || (name.contains("uranium") && name.contains("liquidfuel"))) {
            try {
                return GGMaterial.uraniumBasedLiquidFuelDepleted.getFluidOrGas(1)
                    .getFluid();
            } catch (Throwable ignored) {}
            Fluid f = FluidRegistry.getFluid("uraniumbasedliquidfueldepleted");
            if (f != null) return f;
            return FluidRegistry.getFluid("fluid.uraniumbasedliquidfueldepleted");
        }
        if (name.contains("plutonium")) {
            try {
                return GGMaterial.plutoniumBasedLiquidFuelDepleted.getFluidOrGas(1)
                    .getFluid();
            } catch (Throwable ignored) {}
            Fluid f = FluidRegistry.getFluid("plutoniumbasedliquidfueldepleted");
            if (f != null) return f;
            return FluidRegistry.getFluid("fluid.plutoniumbasedliquidfueldepleted");
        }
        if (name.contains("uraniumhexafluoride")) {
            Fluid tetra = FluidRegistry.getFluid("uraniumtetrafluoride");
            if (tetra != null) return tetra;
            return FluidRegistry.getFluid("fluid.uraniumtetrafluoride");
        }
        return null;
    }

    public static boolean isCoolantFluid(FluidStack fluid) {
        if (fluid == null || fluid.getFluid() == null) return false;
        String name = fluid.getFluid()
            .getName()
            .toLowerCase();
        return name.contains("water") || name.contains("coolant") || name.contains("sodium") || name.contains("lead");
    }

    public static boolean isItemCoolant(ItemStack stack) {
        if (stack == null) return false;
        if (stack.getItem() instanceof ItemCoolantCell) return true;
        String name = stack.getUnlocalizedName()
            .toLowerCase();
        return name.contains("coolant") || name.contains("heatcapacitor");
    }

    public static double getInsulationDampening(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return 0.0;

        // 1. Naquarite Universal Insulator Foil: rejects 100% heat & radiation
        try {
            if (ItemList.Naquarite_Universal_Insulator_Foil.isStackEqual(stack, false, true)) {
                return 1.0;
            }
        } catch (Throwable ignored) {}

        String unlocalizedName = stack.getUnlocalizedName();
        if (unlocalizedName != null) {
            String lower = unlocalizedName.toLowerCase();
            if (lower.contains("naquarite_universal_insulator_foil")
                || lower.contains("naquariteuniversalinsulatorfoil")
                || (lower.contains("naquarite") && lower.contains("insulator"))) {
                return 1.0;
            }
            if (lower.contains("micainsulatorfoil") || lower.contains("mica_insulator_foil")
                || (lower.contains("mica") && lower.contains("foil"))) {
                return 0.60;
            }
            if (lower.contains("thermalclotht2") || lower.contains("thermal_cloth_t2")
                || (lower.contains("thermalcloth") && (lower.contains("t2") || lower.contains("2")))) {
                return 0.40;
            }
            if (lower.contains("itembasicasteroids") && stack.getItemDamage() == 7) {
                return 0.20;
            }
            if (lower.contains("thermalcloth") || lower.contains("thermal_cloth")) {
                return 0.20;
            }
        }

        try {
            ItemStack gcCloth = gregtech.api.util.GTModHandler
                .getModItem("GalacticraftMars", "item.itemBasicAsteroids", 1, 7);
            if (gcCloth != null && GTUtility.areStacksEqual(stack, gcCloth, false)) {
                return 0.20;
            }
            ItemStack gsClothT2 = gregtech.api.util.GTModHandler.getModItem("GalaxySpace", "item.ThermalClothT2", 1);
            if (gsClothT2 != null && GTUtility.areStacksEqual(stack, gsClothT2, true)) {
                return 0.40;
            }
            ItemStack micaFoil = gregtech.api.util.GTModHandler.getModItem("dreamcraft", "MicaInsulatorFoil", 1);
            if (micaFoil != null && GTUtility.areStacksEqual(stack, micaFoil, true)) {
                return 0.60;
            }
        } catch (Throwable ignored) {}

        return 0.0;
    }

    public static boolean isItemInsulator(ItemStack stack) {
        return getInsulationDampening(stack) > 0.0;
    }

    public static boolean isNaquariteInsulatorFoil(ItemStack stack) {
        return getInsulationDampening(stack) >= 1.0;
    }

    public double getTileInsulationDampening(NuclearGridTile tile) {
        if (tile.isBus()) {
            ItemStack stack = tile.getBus().mInventory[MTEHatchNuclearBus.SLOT_INPUT];
            return getInsulationDampening(stack);
        }
        return 0.0;
    }

    public void updateCoolantTracking() {
        if (mGrid == null) return;
        for (int x = 0; x < gridSize; x++) {
            for (int y = 0; y < gridSize; y++) {
                INuclearTile tile = mGrid[x][y];
                if (tile instanceof NuclearGridTile gt) {
                    if (gt.isHatch()) {
                        MTEHatchNuclearHatch hatch = gt.getHatch();
                        FluidStack fluid = hatch.mInputFluid;
                        if (fluid != null && fluid.amount > 0) {
                            if (isCoolantFluid(fluid) && !isFluidFuel(fluid)) {
                                hatch.mUsedForCooling = true;
                            } else {
                                hatch.mUsedForCooling = false;
                            }
                        } else {
                            hatch.mUsedForCooling = false;
                        }
                    } else if (gt.isBus()) {
                        MTEHatchNuclearBus bus = gt.getBus();
                        ItemStack stack = bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT];
                        if (stack != null) {
                            if (isItemCoolant(stack)) {
                                bus.mUsedForCooling = true;
                            } else {
                                bus.mUsedForCooling = false;
                            }
                        }
                    }
                }
            }
        }
    }

    public void updateControlHatches() {
        for (MTEHatchNuclearControl hatch : mControlHatches) {
            if (hatch != null && hatch.isValid()) {
                byte signal = calculateSignalForMode(hatch.getMode());
                hatch.setOutputRedstone(signal);
            }
        }
    }

    public byte calculateSignalForMode(int mode) {
        if (mGrid == null || gridSize <= 0) return 0;

        switch (mode) {
            case MTEHatchNuclearControl.MODE_TEMP_MIN:
            case MTEHatchNuclearControl.MODE_TEMP_MAX:
            case MTEHatchNuclearControl.MODE_TEMP_AVG: {
                double maxOperatingTemp = NuclearSimulationEngine.getMaxOperatingTemperature(mPipeTier);
                if (maxOperatingTemp <= 0) maxOperatingTemp = 1000.0;
                double minTemp = Double.MAX_VALUE;
                double maxTemp = -Double.MAX_VALUE;
                double sumTemp = 0.0;
                int cellCount = 0;
                for (int x = 0; x < gridSize; x++) {
                    for (int y = 0; y < gridSize; y++) {
                        INuclearTile tile = mGrid[x][y];
                        if (tile != null) {
                            double temp = tile.getTemperature();
                            if (temp < minTemp) minTemp = temp;
                            if (temp > maxTemp) maxTemp = temp;
                            sumTemp += temp;
                            cellCount++;
                        }
                    }
                }
                if (cellCount == 0) return 0;
                double targetTemp = switch (mode) {
                    case MTEHatchNuclearControl.MODE_TEMP_MIN -> minTemp;
                    case MTEHatchNuclearControl.MODE_TEMP_MAX -> maxTemp;
                    case MTEHatchNuclearControl.MODE_TEMP_AVG -> sumTemp / cellCount;
                    default -> 0.0;
                };
                double ratio = Math.max(0.0, Math.min(1.0, targetTemp / maxOperatingTemp));
                return (byte) Math.round(ratio * 15.0);
            }

            case MTEHatchNuclearControl.MODE_FUEL_DURABILITY_MIN:
            case MTEHatchNuclearControl.MODE_FUEL_DURABILITY_MAX:
            case MTEHatchNuclearControl.MODE_FUEL_DURABILITY_AVG: {
                double minFuelDur = Double.MAX_VALUE;
                double maxFuelDur = -Double.MAX_VALUE;
                double sumFuelDur = 0.0;
                int fuelCount = 0;
                for (int x = 0; x < gridSize; x++) {
                    for (int y = 0; y < gridSize; y++) {
                        INuclearTile tile = mGrid[x][y];
                        if (tile instanceof NuclearGridTile gt && gt.isBus()) {
                            MTEHatchNuclearBus bus = gt.getBus();
                            ItemStack stack = bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT];
                            if (stack != null && isItemFuel(stack)) {
                                double dur;
                                if (stack.getItem() instanceof ItemRadioactiveCell radCell) {
                                    int max = radCell.getMaxDamageEx();
                                    int cur = radCell.getDamageOfStack(stack);
                                    dur = max > 0 ? (double) (max - cur) / max : 0.0;
                                } else if (stack.isItemStackDamageable() && stack.getMaxDamage() > 0) {
                                    dur = (double) (stack.getMaxDamage() - stack.getItemDamage())
                                        / stack.getMaxDamage();
                                } else {
                                    dur = 1.0;
                                }
                                dur = Math.max(0.0, Math.min(1.0, dur));
                                if (dur < minFuelDur) minFuelDur = dur;
                                if (dur > maxFuelDur) maxFuelDur = dur;
                                sumFuelDur += dur;
                                fuelCount++;
                            }
                        }
                    }
                }
                if (fuelCount == 0) return 0;
                double targetDur = switch (mode) {
                    case MTEHatchNuclearControl.MODE_FUEL_DURABILITY_MIN -> minFuelDur;
                    case MTEHatchNuclearControl.MODE_FUEL_DURABILITY_MAX -> maxFuelDur;
                    case MTEHatchNuclearControl.MODE_FUEL_DURABILITY_AVG -> sumFuelDur / fuelCount;
                    default -> 0.0;
                };
                return (byte) Math.round(targetDur * 15.0);
            }

            case MTEHatchNuclearControl.MODE_COMPONENT_DURABILITY_MIN:
            case MTEHatchNuclearControl.MODE_COMPONENT_DURABILITY_MAX:
            case MTEHatchNuclearControl.MODE_COMPONENT_DURABILITY_AVG: {
                double minCompDur = Double.MAX_VALUE;
                double maxCompDur = -Double.MAX_VALUE;
                double sumCompDur = 0.0;
                int compCount = 0;
                for (int x = 0; x < gridSize; x++) {
                    for (int y = 0; y < gridSize; y++) {
                        INuclearTile tile = mGrid[x][y];
                        if (tile instanceof NuclearGridTile gt && gt.isBus()) {
                            MTEHatchNuclearBus bus = gt.getBus();
                            ItemStack stack = bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT];
                            if (stack != null && !isItemFuel(stack)
                                && !isItemCoolant(stack)
                                && stack.isItemStackDamageable()
                                && stack.getMaxDamage() > 0) {
                                double dur = (double) (stack.getMaxDamage() - stack.getItemDamage())
                                    / stack.getMaxDamage();
                                dur = Math.max(0.0, Math.min(1.0, dur));
                                if (dur < minCompDur) minCompDur = dur;
                                if (dur > maxCompDur) maxCompDur = dur;
                                sumCompDur += dur;
                                compCount++;
                            }
                        }
                    }
                }
                if (compCount == 0) return 0;
                double targetDur = switch (mode) {
                    case MTEHatchNuclearControl.MODE_COMPONENT_DURABILITY_MIN -> minCompDur;
                    case MTEHatchNuclearControl.MODE_COMPONENT_DURABILITY_MAX -> maxCompDur;
                    case MTEHatchNuclearControl.MODE_COMPONENT_DURABILITY_AVG -> sumCompDur / compCount;
                    default -> 0.0;
                };
                return (byte) Math.round(targetDur * 15.0);
            }

            case MTEHatchNuclearControl.MODE_COOLANT_LEVEL_MIN:
            case MTEHatchNuclearControl.MODE_COOLANT_LEVEL_MAX:
            case MTEHatchNuclearControl.MODE_COOLANT_LEVEL_AVG: {
                double minCoolant = Double.MAX_VALUE;
                double maxCoolant = -Double.MAX_VALUE;
                double sumCoolant = 0.0;
                int coolantCount = 0;
                for (int x = 0; x < gridSize; x++) {
                    for (int y = 0; y < gridSize; y++) {
                        INuclearTile tile = mGrid[x][y];
                        if (tile instanceof NuclearGridTile gt) {
                            if (gt.isHatch()) {
                                MTEHatchNuclearHatch hatch = gt.getHatch();
                                if (hatch.mUsedForCooling) {
                                    double level = 0.0;
                                    if (hatch.mInputFluid != null && hatch.mCapacity > 0) {
                                        level = (double) hatch.mInputFluid.amount / hatch.mCapacity;
                                    }
                                    level = Math.max(0.0, Math.min(1.0, level));
                                    if (level < minCoolant) minCoolant = level;
                                    if (level > maxCoolant) maxCoolant = level;
                                    sumCoolant += level;
                                    coolantCount++;
                                }
                            } else if (gt.isBus()) {
                                MTEHatchNuclearBus bus = gt.getBus();
                                if (bus.mUsedForCooling) {
                                    ItemStack stack = bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT];
                                    double level = 0.0;
                                    if (stack != null && isItemCoolant(stack) && stack.getMaxDamage() > 0) {
                                        level = (double) (stack.getMaxDamage() - stack.getItemDamage())
                                            / stack.getMaxDamage();
                                    }
                                    level = Math.max(0.0, Math.min(1.0, level));
                                    if (level < minCoolant) minCoolant = level;
                                    if (level > maxCoolant) maxCoolant = level;
                                    sumCoolant += level;
                                    coolantCount++;
                                }
                            }
                        }
                    }
                }
                if (coolantCount == 0) return 0;
                double targetLevel = switch (mode) {
                    case MTEHatchNuclearControl.MODE_COOLANT_LEVEL_MIN -> minCoolant;
                    case MTEHatchNuclearControl.MODE_COOLANT_LEVEL_MAX -> maxCoolant;
                    case MTEHatchNuclearControl.MODE_COOLANT_LEVEL_AVG -> sumCoolant / coolantCount;
                    default -> 0.0;
                };
                return (byte) Math.round(targetLevel * 15.0);
            }

            default:
                return 0;
        }
    }

    public boolean isItemBetavoltaic(ItemStack stack) {
        if (stack == null) return false;
        if (stack.getItem() instanceof gregtech.common.items.ItemBetavoltaicPlate) return true;
        if (ItemList.Betavoltaic_Plate_HV.isStackEqual(stack, false, true)) return true;
        if (ItemList.Betavoltaic_Plate_EV.isStackEqual(stack, false, true)) return true;
        String name = stack.getUnlocalizedName()
            .toLowerCase();
        return name.contains("betavoltaic") || name.contains("betacell") || name.contains("neutronovoltaic");
    }

    public int getBetavoltaicTier(ItemStack stack) {
        if (stack == null) return 0;
        if (stack.getItem() instanceof gregtech.common.items.ItemBetavoltaicPlate plate) return plate.getTier();
        if (ItemList.Betavoltaic_Plate_EV.isStackEqual(stack, false, true)) return 2;
        if (ItemList.Betavoltaic_Plate_HV.isStackEqual(stack, false, true)) return 1;
        String name = stack.getUnlocalizedName()
            .toLowerCase();
        if (name.contains("ev") || name.contains("extreme") || name.contains("tier2") || name.contains("t2")) return 2;
        return 1;
    }

    public ItemStack getItemDepletedForm(ItemStack fuel) {
        if (fuel == null) return null;

        // 1. Check if fuel is ItemRadioactiveCellIC with sDepleted
        if (fuel.getItem() instanceof ItemRadioactiveCellIC icCell && icCell.sDepleted != null) {
            return icCell.sDepleted.copy();
        }

        // 2. Check IC2 native fuel items
        if (fuel.getItem() instanceof ItemReactorUranium ic2Uran) {
            boolean isMox = ic2Uran instanceof ItemReactorMOX;
            int cells = ic2Uran.numberOfCells;
            if (isMox) {
                if (cells >= 4 && Ic2Items.reactorDepletedMOXQuad != null)
                    return Ic2Items.reactorDepletedMOXQuad.copy();
                if (cells >= 2 && Ic2Items.reactorDepletedMOXDual != null)
                    return Ic2Items.reactorDepletedMOXDual.copy();
                if (Ic2Items.reactorDepletedMOXSimple != null) return Ic2Items.reactorDepletedMOXSimple.copy();
                return (cells >= 4) ? ItemList.DepletedRodMOX4.get(1L)
                    : (cells >= 2) ? ItemList.DepletedRodMOX2.get(1L) : ItemList.DepletedRodMOX.get(1L);
            } else {
                if (cells >= 4 && Ic2Items.reactorDepletedUraniumQuad != null)
                    return Ic2Items.reactorDepletedUraniumQuad.copy();
                if (cells >= 2 && Ic2Items.reactorDepletedUraniumDual != null)
                    return Ic2Items.reactorDepletedUraniumDual.copy();
                if (Ic2Items.reactorDepletedUraniumSimple != null) return Ic2Items.reactorDepletedUraniumSimple.copy();
                return (cells >= 4) ? ItemList.DepletedRodUranium4.get(1L)
                    : (cells >= 2) ? ItemList.DepletedRodUranium2.get(1L) : ItemList.DepletedRodUranium.get(1L);
            }
        }

        String name = fuel.getUnlocalizedName();
        if (name == null) return null;
        String lower = name.toLowerCase();

        // 3. The Core (RodNaquadah32)
        if (lower.contains("naquadah32") || lower.contains("thecore")
            || (lower.contains("naquadah") && lower.contains("32"))) {
            return ItemList.DepletedRodNaquadah32.get(1L);
        }

        // 4. Excited variants
        if (lower.contains("exciteduranium") || (lower.contains("excited") && lower.contains("uranium"))) {
            if (lower.contains("quad") || lower.contains("4")) return ItemList.DepletedRodExcitedUranium4.get(1L);
            if (lower.contains("dual") || lower.contains("2")) return ItemList.DepletedRodExcitedUranium2.get(1L);
            return ItemList.DepletedRodExcitedUranium.get(1L);
        }
        if (lower.contains("excitedplutonium") || (lower.contains("excited") && lower.contains("plutonium"))) {
            if (lower.contains("quad") || lower.contains("4")) return ItemList.DepletedRodExcitedPlutonium4.get(1L);
            if (lower.contains("dual") || lower.contains("2")) return ItemList.DepletedRodExcitedPlutonium2.get(1L);
            return ItemList.DepletedRodExcitedPlutonium.get(1L);
        }

        // 5. High density variants
        if (lower.contains("highdensityuranium") || (lower.contains("highdensity") && lower.contains("uranium"))) {
            if (lower.contains("quad") || lower.contains("4")) return ItemList.DepletedRodHighDensityUranium4.get(1L);
            if (lower.contains("dual") || lower.contains("2")) return ItemList.DepletedRodHighDensityUranium2.get(1L);
            return ItemList.DepletedRodHighDensityUranium.get(1L);
        }
        if (lower.contains("highdensityplutonium") || (lower.contains("highdensity") && lower.contains("plutonium"))) {
            if (lower.contains("quad") || lower.contains("4")) return ItemList.DepletedRodHighDensityPlutonium4.get(1L);
            if (lower.contains("dual") || lower.contains("2")) return ItemList.DepletedRodHighDensityPlutonium2.get(1L);
            return ItemList.DepletedRodHighDensityPlutonium.get(1L);
        }

        // 6. Naquadria & Tiberium
        if (lower.contains("naquadria")) {
            if (lower.contains("quad") || lower.contains("4")) return ItemList.DepletedRodNaquadria4.get(1L);
            if (lower.contains("dual") || lower.contains("2")) return ItemList.DepletedRodNaquadria2.get(1L);
            return ItemList.DepletedRodNaquadria.get(1L);
        }
        if (lower.contains("tiberium")) {
            if (lower.contains("quad") || lower.contains("4")) return ItemList.DepletedRodTiberium4.get(1L);
            if (lower.contains("dual") || lower.contains("2")) return ItemList.DepletedRodTiberium2.get(1L);
            return ItemList.DepletedRodTiberium.get(1L);
        }

        // 7. Standard fuels: Naquadah, Thorium, MOX, Uranium
        if (lower.contains("naquadah")) {
            if (lower.contains("quad") || lower.contains("4")) return ItemList.DepletedRodNaquadah4.get(1L);
            if (lower.contains("dual") || lower.contains("2")) return ItemList.DepletedRodNaquadah2.get(1L);
            return ItemList.DepletedRodNaquadah.get(1L);
        } else if (lower.contains("mox")) {
            if (lower.contains("quad") || lower.contains("4")) return ItemList.DepletedRodMOX4.get(1L);
            if (lower.contains("dual") || lower.contains("2")) return ItemList.DepletedRodMOX2.get(1L);
            return ItemList.DepletedRodMOX.get(1L);
        } else if (lower.contains("thorium")) {
            if (lower.contains("quad") || lower.contains("4")) return ItemList.DepletedRodThorium4.get(1L);
            if (lower.contains("dual") || lower.contains("2")) return ItemList.DepletedRodThorium2.get(1L);
            return ItemList.DepletedRodThorium.get(1L);
        } else if (lower.contains("uranium")) {
            if (lower.contains("quad") || lower.contains("4")) return ItemList.DepletedRodUranium4.get(1L);
            if (lower.contains("dual") || lower.contains("2")) return ItemList.DepletedRodUranium2.get(1L);
            return ItemList.DepletedRodUranium.get(1L);
        }
        return null;
    }

    public void damageItemComponent(MTEHatchNuclearBus bus, int damage) {
        ItemStack stack = bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT];
        if (stack == null || !stack.isItemStackDamageable()) return;

        int newDamage = stack.getItemDamage() + damage;
        if (newDamage >= stack.getMaxDamage()) {
            ItemStack depleted = getItemDepletedForm(stack);
            bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = null;
            if (depleted != null) {
                this.addOutputPartial(depleted);
            }
        } else {
            stack.setItemDamage(newDamage);
        }
        bus.markTileDirty();
    }

    public double getTileTemperature(NuclearGridTile tile) {
        if (tile.isBus()) return tile.getBus().mTemperature;
        if (tile.isHatch()) return tile.getHatch().mTemperature;
        if (tile.isControlRod()) return tile.getControlRod().mTemperature;
        return getAmbientTemperature();
    }

    public void setTileTemperature(NuclearGridTile tile, double temp) {
        double ambient = getAmbientTemperature();
        if (tile.isBus()) {
            tile.getBus().mTemperature = Math.max(ambient, temp);
        } else if (tile.isHatch()) {
            double minTemp = tile.getHatch()
                .hasWaterCoolant() ? Math.max(0.0, ambient) : ambient;
            tile.getHatch().mTemperature = Math.max(minTemp, temp);
        } else if (tile.isControlRod()) {
            tile.getControlRod().mTemperature = Math.max(ambient, temp);
        }
    }

    public void addTileHeat(NuclearGridTile tile, double heatEU) {
        if (tile.isBus()) {
            tile.getBus().mHeatEU += heatEU;
            tile.getBus().mTemperature += heatEU / NuclearSimulationEngine.EU_PER_DEGREE;
        } else if (tile.isHatch()) {
            tile.getHatch().mHeatEU += heatEU;
            tile.getHatch().mTemperature += heatEU / NuclearSimulationEngine.EU_PER_DEGREE;
        } else if (tile.isControlRod()) {
            tile.getControlRod().mHeatEU += heatEU;
            tile.getControlRod().mTemperature += heatEU / NuclearSimulationEngine.EU_PER_DEGREE;
        }
    }

    public double getTileHeatTransferCoeff(NuclearGridTile tile) {
        if (tile.isBus()) {
            ItemStack stack = tile.getBus().mInventory[MTEHatchNuclearBus.SLOT_INPUT];
            if (stack == null) return 0.02;
            if (isItemInsulator(stack)) return 0.01;
            if (isItemBetavoltaic(stack)) return 0.10;
            String name = stack.getUnlocalizedName()
                .toLowerCase();
            if (name.contains("coolant") || name.contains("vent")) return 0.40;
            if (name.contains("reflector")) return 0.15;
            if (name.contains("fuel") || name.contains("uranium") || name.contains("mox") || name.contains("thorium"))
                return 0.05;
            return 0.03;
        } else if (tile.isHatch()) {
            FluidStack fluid = tile.getHatch().mInputFluid;
            if (fluid == null) return 0.05;
            String name = fluid.getFluid()
                .getName()
                .toLowerCase();
            if (name.contains("water")) return 0.25;
            if (name.contains("coolant")) return 0.50;
            if (name.contains("sodium") || name.contains("lead")) return 0.70;
            return 0.15;
        } else if (tile.isControlRod()) {
            return tile.getControlRod()
                .getHeatTransferCoeff();
        }
        return 0.05;
    }

    public boolean isTileFuel(NuclearGridTile tile) {
        if (tile.isBus()) {
            return isItemFuel(tile.getBus().mInventory[MTEHatchNuclearBus.SLOT_INPUT]);
        } else if (tile.isHatch()) {
            return isFluidFuel(tile.getHatch().mInputFluid);
        }
        return false;
    }

    public int generateTileNeutrons(NuclearGridTile tile, double efficiency) {
        if (tile.isBus()) {
            MTEHatchNuclearBus bus = tile.getBus();
            ItemStack stack = bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT];
            if (!isItemFuel(stack)) {
                bus.mLastNeutronsGenerated = 0;
                return 0;
            }
            int baseNeutrons = 4;
            if (stack.getItem() instanceof ItemRadioactiveCellIC icCell) {
                baseNeutrons = 4 * icCell.numberOfCells;
                if (icCell.sMox) baseNeutrons *= 2;
            } else if (stack.getItem() instanceof ItemReactorUranium ic2Uran) {
                baseNeutrons = 4 * ic2Uran.numberOfCells;
                if (ic2Uran instanceof ItemReactorMOX) baseNeutrons *= 2;
            } else {
                String name = stack.getUnlocalizedName();
                if (name != null) {
                    String lower = name.toLowerCase();
                    if (lower.contains("naquadah32") || lower.contains("thecore")
                        || (lower.contains("naquadah") && lower.contains("32"))) {
                        baseNeutrons = 128;
                    } else if (lower.contains("quad") || lower.contains("4")) {
                        baseNeutrons = 16;
                    } else if (lower.contains("dual") || lower.contains("2")) {
                        baseNeutrons = 8;
                    }
                    if (lower.contains("mox")) baseNeutrons *= 2;
                }
            }
            String name = stack.getUnlocalizedName();
            if (name != null) {
                String lower = name.toLowerCase();
                if (lower.contains("thorium")) baseNeutrons = Math.max(1, baseNeutrons / 2);
                if (lower.contains("naquadah")) baseNeutrons *= 4;
                if (lower.contains("naquadria")) baseNeutrons *= 4;
                if (lower.contains("tiberium")) baseNeutrons *= 2;
            }

            int chainNeutrons = (int) Math
                .round(bus.mLastThermalAbsorbed * NuclearSimulationEngine.thermalFissionMultiplier);
            int produced = (int) Math.round((baseNeutrons + chainNeutrons) * efficiency);
            bus.mLastNeutronsGenerated = produced;
            return produced;
        } else if (tile.isHatch()) {
            MTEHatchNuclearHatch hatch = tile.getHatch();
            FluidStack fluid = hatch.mInputFluid;
            if (!isFluidFuel(fluid) || fluid == null || fluid.amount <= 0) {
                hatch.mLastNeutronsGenerated = 0;
                return 0;
            }
            String name = fluid.getFluid()
                .getName()
                .toLowerCase();
            int baseNeutrons = 8;
            if (name.contains("thorium")) {
                baseNeutrons = name.contains("excited") ? 8 : 4;
            } else if (name.contains("uranium")) {
                baseNeutrons = name.contains("excited") ? 16 : 8;
            } else if (name.contains("plutonium")) {
                baseNeutrons = name.contains("excited") ? 32 : 16;
            } else if (name.contains("uraniumhexafluoride")) {
                baseNeutrons = 12;
            }

            int chainNeutrons = (int) Math
                .round(hatch.mLastThermalAbsorbed * NuclearSimulationEngine.thermalFissionMultiplier);
            int produced = (int) Math.round((baseNeutrons + chainNeutrons) * efficiency);
            hatch.mLastNeutronsGenerated = produced;
            return produced;
        }
        return 0;
    }

    public double getTileAbsorptionProbability(NuclearGridTile tile, NeutronType type) {
        if (tile.isBus()) {
            ItemStack stack = tile.getBus().mInventory[MTEHatchNuclearBus.SLOT_INPUT];
            if (stack == null) return 0.01;
            if (isNaquariteInsulatorFoil(stack)) return 1.0;
            if (isItemInsulator(stack)) return 0.01;
            if (isItemBetavoltaic(stack)) return 1.0;
            String name = stack.getUnlocalizedName()
                .toLowerCase();
            if (name.contains("graphite") || name.contains("carbon") || name.contains("moderator")) {
                return (type == NeutronType.THERMAL) ? 0.009 : 0.002;
            }
            if (name.contains("reflector")) return (type == NeutronType.THERMAL) ? 0.02 : 0.01;
            if (name.contains("boron") || name.contains("cadmium") || name.contains("control")) {
                return (type == NeutronType.THERMAL) ? 0.95 : 0.85;
            }
            if (isItemFuel(stack)) {
                return (type == NeutronType.THERMAL) ? 0.80 : 0.25;
            }
            if (name.contains("coolant")) return 0.05;
            return 0.02;
        } else if (tile.isHatch()) {
            FluidStack fluid = tile.getHatch().mInputFluid;
            if (fluid == null) return 0.01;
            String name = fluid.getFluid()
                .getName()
                .toLowerCase();
            if (name.contains("heavywater")) return (type == NeutronType.THERMAL) ? 0.01 : 0.005;
            if (name.contains("distilledwater")) return (type == NeutronType.THERMAL) ? 0.10 : 0.05;
            if (name.contains("coolant")) return (type == NeutronType.THERMAL) ? 0.12 : 0.03;
            if (name.contains("boron")) return 0.95;
            if (isFluidFuel(fluid)) return (type == NeutronType.THERMAL) ? 0.85 : 0.25;
            return 0.05;
        } else if (tile.isControlRod()) {
            return tile.getControlRod()
                .getAbsorptionProbability(type);
        }
        return 0.01;
    }

    public double getTileScatteringProbability(NuclearGridTile tile, NeutronType type) {
        if (tile.isBus()) {
            ItemStack stack = tile.getBus().mInventory[MTEHatchNuclearBus.SLOT_INPUT];
            if (stack == null) return 0.02;
            if (isNaquariteInsulatorFoil(stack)) return 0.0;
            if (isItemInsulator(stack)) return 0.05;
            if (isItemBetavoltaic(stack)) return 0.0;
            String name = stack.getUnlocalizedName()
                .toLowerCase();
            if (name.contains("graphite") || name.contains("carbon") || name.contains("moderator")) {
                return (type == NeutronType.THERMAL) ? 0.621 : 0.93;
            }
            if (name.contains("reflector")) return (type == NeutronType.THERMAL) ? 0.98 : 0.95;
            if (name.contains("boron") || name.contains("cadmium") || name.contains("control")) {
                return (type == NeutronType.THERMAL) ? 0.05 : 0.10;
            }
            if (name.contains("coolant")) return 0.45;
            if (isItemFuel(stack)) return (type == NeutronType.THERMAL) ? 0.10 : 0.15;
            return 0.05;
        } else if (tile.isHatch()) {
            FluidStack fluid = tile.getHatch().mInputFluid;
            if (fluid == null) return 0.02;
            String name = fluid.getFluid()
                .getName()
                .toLowerCase();
            if (name.contains("heavywater")) return 0.85;
            if (name.contains("distilledwater")) return 0.70;
            if (name.contains("coolant")) return 0.45;
            if (name.contains("sodium")) return 0.20;
            return 0.10;
        } else if (tile.isControlRod()) {
            return tile.getControlRod()
                .getScatteringProbability(type);
        }
        return 0.02;
    }

    public double getTileModerationProbability(NuclearGridTile tile) {
        if (tile.isBus()) {
            ItemStack stack = tile.getBus().mInventory[MTEHatchNuclearBus.SLOT_INPUT];
            if (isItemBetavoltaic(stack)) return 0.0;
            if (stack == null) return 0.05;
            String name = stack.getUnlocalizedName()
                .toLowerCase();
            if (name.contains("graphite") || name.contains("carbon") || name.contains("moderator")) return 0.50;
            if (name.contains("reflector")) return 0.20;
            if (name.contains("coolant")) return 0.40;
            if (isItemFuel(stack)) return 0.10;
            if (name.contains("boron") || name.contains("cadmium") || name.contains("control")) return 0.05;
            return 0.05;
        } else if (tile.isHatch()) {
            FluidStack fluid = tile.getHatch().mInputFluid;
            if (fluid == null) return 0.05;
            String name = fluid.getFluid()
                .getName()
                .toLowerCase();
            if (name.contains("heavywater")) return 0.90;
            if (name.contains("distilledwater")) return 0.80;
            if (name.contains("coolant")) return 0.40;
            if (name.contains("sodium")) return 0.05;
            return 0.20;
        } else if (tile.isControlRod()) {
            return 0.01;
        }
        return 0.05;
    }

    public void onTileNeutronAbsorbed(NuclearGridTile tile, NeutronType type, int count) {
        if (tile.isBus()) {
            MTEHatchNuclearBus bus = tile.getBus();
            if (type == NeutronType.FAST) bus.mFastAbsorbed += count;
            else bus.mThermalAbsorbed += count;
        } else if (tile.isHatch()) {
            MTEHatchNuclearHatch hatch = tile.getHatch();
            if (type == NeutronType.FAST) hatch.mFastAbsorbed += count;
            else hatch.mThermalAbsorbed += count;

            if (type == NeutronType.FAST && hatch.mInputFluid != null && hatch.mInputFluid.amount > 0) {
                String name = hatch.mInputFluid.getFluid()
                    .getName()
                    .toLowerCase();
                boolean isHP = name.contains("highpressure");
                int chance = isHP ? Math.min(100, count * 10) : Math.min(100, count * 5);
                int yield = isHP ? 2 : 1;

                if (name.contains("distilledwater")) {
                    if (getRandomNumber(100) < chance) {
                        hatch.mInputFluid.amount -= 1;
                        if (hatch.mInputFluid.amount <= 0) hatch.mInputFluid = null;
                        Fluid deut = FluidRegistry.getFluid("deuterium");
                        if (deut == null) deut = FluidRegistry.getFluid("fluid.deuterium");
                        if (deut != null) {
                            this.addOutputPartial(new FluidStack(deut, yield));
                        }
                        hatch.markTileDirty();
                    }
                } else if (name.contains("heavywater")) {
                    if (getRandomNumber(100) < chance) {
                        hatch.mInputFluid.amount -= 1;
                        if (hatch.mInputFluid.amount <= 0) hatch.mInputFluid = null;
                        Fluid trit = FluidRegistry.getFluid("tritium");
                        if (trit == null) trit = FluidRegistry.getFluid("fluid.tritium");
                        if (trit != null) {
                            this.addOutputPartial(new FluidStack(trit, yield));
                        }
                        hatch.markTileDirty();
                    }
                }
            }
        } else if (tile.isControlRod()) {
            MTEHatchNuclearControlRod rod = tile.getControlRod();
            if (type == NeutronType.FAST) rod.mFastAbsorbed += count;
            else rod.mThermalAbsorbed += count;
        }
    }

    public void onTileNeutronScattered(NuclearGridTile tile, NeutronType type, int count) {
        if (tile.isBus()) {
            MTEHatchNuclearBus bus = tile.getBus();
            ItemStack stack = bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT];
            if (stack != null && stack.isItemStackDamageable()) {
                String name = stack.getUnlocalizedName()
                    .toLowerCase();
                if (name.contains("reflector")) {
                    if (getRandomNumber(20) == 0) {
                        damageItemComponent(bus, 1);
                    }
                }
            }
        }
    }

    public void addTileNeutronFlux(NuclearGridTile tile, NeutronType type, int count) {
        if (tile.isBus()) {
            if (type == NeutronType.FAST) tile.getBus().mFastFlux += count;
            else tile.getBus().mThermalFlux += count;
        } else if (tile.isHatch()) {
            if (type == NeutronType.FAST) tile.getHatch().mFastFlux += count;
            else tile.getHatch().mThermalFlux += count;
        } else if (tile.isControlRod()) {
            if (type == NeutronType.FAST) tile.getControlRod().mFastFlux += count;
            else tile.getControlRod().mThermalFlux += count;
        }
    }

    public void processTileNuclearTick(NuclearGridTile tile, double efficiency) {
        if (tile.isBus()) {
            processBusNuclearTick(tile.getBus(), tile, efficiency);
        } else if (tile.isHatch()) {
            processHatchNuclearTick(tile.getHatch(), tile, efficiency);
        } else if (tile.isControlRod()) {
            processControlRodNuclearTick(tile.getControlRod(), tile, efficiency);
        }
    }

    public void processControlRodNuclearTick(MTEHatchNuclearControlRod rod, NuclearGridTile tile, double efficiency) {
        rod.mLastFastFlux = rod.mFastFlux;
        rod.mLastThermalFlux = rod.mThermalFlux;
        rod.mLastFastAbsorbed = rod.mFastAbsorbed;
        rod.mLastThermalAbsorbed = rod.mThermalAbsorbed;
        rod.mFastFlux = 0;
        rod.mThermalFlux = 0;
        rod.mFastAbsorbed = 0;
        rod.mThermalAbsorbed = 0;
    }

    public void processBusNuclearTick(MTEHatchNuclearBus bus, NuclearGridTile tile, double efficiency) {
        ItemStack stack = bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT];
        if (stack == null) {
            bus.mLastFastFlux = bus.mFastFlux;
            bus.mLastThermalFlux = bus.mThermalFlux;
            bus.mLastFastAbsorbed = bus.mFastAbsorbed;
            bus.mLastThermalAbsorbed = bus.mThermalAbsorbed;
            bus.mDirectEUProduced = 0;
            bus.mFastFlux = 0;
            bus.mThermalFlux = 0;
            bus.mFastAbsorbed = 0;
            bus.mThermalAbsorbed = 0;
            return;
        }

        // 1. FUEL DEPLETION (Driven by neutron absorption & fission)
        if (isItemFuel(stack)) {
            bus.mDirectEUProduced = 0;
            int damage = bus.mFastAbsorbed * 1 + bus.mThermalAbsorbed * 2 + Math.max(1, bus.mLastNeutronsGenerated / 4);
            if (stack.getItem() instanceof ItemRadioactiveCell radCell) {
                radCell.damageItemStack(stack, damage);
                if (radCell.getDamageOfStack(stack) >= radCell.getMaxDamageEx()) {
                    ItemStack depleted = null;
                    if (radCell instanceof ItemRadioactiveCellIC icCell && icCell.sDepleted != null) {
                        depleted = icCell.sDepleted.copy();
                    } else {
                        depleted = getItemDepletedForm(stack);
                    }
                    bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = null;
                    if (depleted != null) {
                        this.addOutputPartial(depleted);
                    }
                }
                bus.markTileDirty();
            } else if (stack.getItem() instanceof ItemReactorUranium) {
                int curDmg = stack.getItemDamage();
                int maxDmg = stack.getMaxDamage();
                int newDmg = curDmg + damage;
                if (newDmg >= maxDmg) {
                    ItemStack depleted = getItemDepletedForm(stack);
                    bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = null;
                    if (depleted != null) {
                        this.addOutputPartial(depleted);
                    }
                } else {
                    stack.setItemDamage(newDmg);
                }
                bus.markTileDirty();
            } else {
                damageItemComponent(bus, damage);
            }
        }
        // 2. BETAVOLTAIC DIRECT EU GENERATION
        else if (isItemBetavoltaic(stack)) {
            int tier = getBetavoltaicTier(stack);
            long maxEU = (tier >= 2) ? 4096 : 1024;
            double weightedFlux = bus.mFastAbsorbed * 4.0 + bus.mThermalAbsorbed * 1.0;
            double satFlux = 60.0;
            double effFactor = Math.max(0.0, Math.min(1.0, efficiency));
            long genEU = (long) Math.round(maxEU * Math.tanh(weightedFlux / satFlux) * effFactor);
            bus.mDirectEUProduced = genEU;
            double totalEnergy = weightedFlux * 20.0;
            double excessHeat = Math.max(0.0, totalEnergy - genEU);
            if (excessHeat > 0.0) {
                addTileHeat(tile, excessHeat);
            }
        }
        // 3. COOLANT CELL HEAT ABSORPTION (Capacity-based scaling via IReactorComponent)
        else if (stack.getItem() instanceof IReactorComponent comp) {
            mReactorDummy.setCurrentTile(tile);
            if (comp.canStoreHeat(mReactorDummy, stack, 0, 0)) {
                bus.mDirectEUProduced = 0;
                int maxHeat = comp.getMaxHeat(mReactorDummy, stack, 0, 0);
                int curHeat = comp.getCurrentHeat(mReactorDummy, stack, 0, 0);
                double ambient = getAmbientTemperature();
                if (maxHeat > 0 && bus.mTemperature > ambient) {
                    int maxTransferPerTick = Math.max(1, maxHeat / 100);
                    double heatAvailable = (bus.mTemperature - ambient) * NuclearSimulationEngine.EU_PER_DEGREE;
                    double effFactor = Math.max(0.0, Math.min(1.0, efficiency));
                    int heatToTake = (int) Math
                        .round(Math.min(heatAvailable / 25.0, (double) maxTransferPerTick) * effFactor);
                    int room = maxHeat - curHeat;
                    heatToTake = Math.min(heatToTake, room);

                    if (heatToTake > 0) {
                        comp.alterHeat(mReactorDummy, stack, 0, 0, heatToTake);
                        double heatConsumed = heatToTake * 25.0;
                        bus.mTemperature -= (heatConsumed / NuclearSimulationEngine.EU_PER_DEGREE);
                        bus.markTileDirty();
                    }
                }

                // Eject hot/full coolant cells to output buses for freezer re-cooling
                if (comp.getCurrentHeat(mReactorDummy, stack, 0, 0) >= maxHeat) {
                    ItemStack fullCell = stack.copy();
                    bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = null;
                    this.addOutputPartial(fullCell);
                    bus.markTileDirty();
                }
            } else {
                bus.mDirectEUProduced = 0;
            }
        }
        // 4. GENERIC COOLANT/VENT FALLBACK
        else if (stack.getUnlocalizedName()
            .toLowerCase()
            .contains("coolant")) {
                bus.mDirectEUProduced = 0;
                double ambient = getAmbientTemperature();
                if (bus.mTemperature > ambient) {
                    double heatToAbsorb = Math.min(bus.mTemperature - ambient, 100.0)
                        * NuclearSimulationEngine.EU_PER_DEGREE;
                    if (heatToAbsorb > 0) {
                        bus.mTemperature -= (heatToAbsorb / NuclearSimulationEngine.EU_PER_DEGREE);
                        int cellDamage = Math.max(1, (int) (heatToAbsorb / 50.0));
                        damageItemComponent(bus, cellDamage);
                    }
                }
            }
        // 5. EASTER EGG: MOLTEN CHEESE EXTRACTION ABOVE 65°C
        else if (bus.mTemperature > 65.0) {
            bus.mDirectEUProduced = 0;
            if (getBaseMetaTileEntity() != null) {
                long aTick = getBaseMetaTileEntity().getTimer();
                if (aTick != bus.mLastCheeseTick) {
                    bus.mLastCheeseTick = aTick;
                    this.processCheeseExtraction(bus);
                }
            } else {
                this.processCheeseExtraction(bus);
            }
        } else {
            bus.mDirectEUProduced = 0;
        }

        // Reset transient flux counters for next tick's display
        bus.mLastFastFlux = bus.mFastFlux;
        bus.mLastThermalFlux = bus.mThermalFlux;
        bus.mLastFastAbsorbed = bus.mFastAbsorbed;
        bus.mLastThermalAbsorbed = bus.mThermalAbsorbed;
        bus.mFastFlux = 0;
        bus.mThermalFlux = 0;
        bus.mFastAbsorbed = 0;
        bus.mThermalAbsorbed = 0;
    }

    public boolean processCheeseExtraction(MTEHatchNuclearBus bus) {
        if (bus.mTemperature <= 65.0) return false;
        ItemStack stack = bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT];
        if (stack == null || stack.stackSize <= 0) return false;

        GTRecipe recipe = bus.findCheeseExtractionRecipe(stack);
        if (recipe == null) return false;

        FluidStack cheeseOutput = null;
        if (recipe.mFluidOutputs != null) {
            for (FluidStack out : recipe.mFluidOutputs) {
                if (out != null && MTEHatchNuclearBus.isMoltenCheese(out)) {
                    cheeseOutput = out.copy();
                    break;
                }
            }
        }
        if (cheeseOutput == null || cheeseOutput.amount <= 0) return false;

        long totalEU = (long) recipe.mDuration * recipe.mEUt;
        double heatAbsorbed = Math.max(1.0, (double) totalEU);
        double tempDrop = heatAbsorbed / NuclearSimulationEngine.EU_PER_DEGREE;
        bus.mTemperature = Math.max(getAmbientTemperature(), bus.mTemperature - tempDrop);

        int consumeCount = 1;
        if (recipe.mInputs != null && recipe.mInputs.length > 0 && recipe.mInputs[0] != null) {
            consumeCount = Math.max(1, recipe.mInputs[0].stackSize);
        }
        stack.stackSize -= consumeCount;
        if (stack.stackSize <= 0) {
            bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = null;
        }

        this.addOutputPartial(cheeseOutput);

        if (recipe.mOutputs != null) {
            for (ItemStack out : recipe.mOutputs) {
                if (out != null) {
                    this.addOutputPartial(out.copy());
                }
            }
        }

        bus.markTileDirty();
        return true;
    }

    public void processHatchNuclearTick(MTEHatchNuclearHatch hatch, NuclearGridTile tile, double efficiency) {
        hatch.mLastFastFlux = hatch.mFastFlux;
        hatch.mLastThermalFlux = hatch.mThermalFlux;
        hatch.mLastFastAbsorbed = hatch.mFastAbsorbed;
        hatch.mLastThermalAbsorbed = hatch.mThermalAbsorbed;
        hatch.mFastFlux = 0;
        hatch.mThermalFlux = 0;
        hatch.mFastAbsorbed = 0;
        hatch.mThermalAbsorbed = 0;

        if (hatch.mInputFluid == null || hatch.mInputFluid.amount <= 0) return;

        // 1. LIQUID NUCLEAR FUEL PROCESSING (Turns into spent liquid fuel, emits neutrons)
        if (isFluidFuel(hatch.mInputFluid)) {
            FluidStack fluid = hatch.mInputFluid;
            Fluid spentFluid = getSpentFluid(fluid);
            int burn = hatch.mFastAbsorbed * 1 + hatch.mThermalAbsorbed * 2
                + Math.max(1, hatch.mLastNeutronsGenerated / 4);
            int toConsume = Math.max(1, Math.min(fluid.amount, burn));

            if (toConsume > 0) {
                fluid.amount -= toConsume;
                if (fluid.amount <= 0) hatch.mInputFluid = null;
                if (spentFluid != null) {
                    this.addOutputPartial(new FluidStack(spentFluid, toConsume));
                }
                hatch.markTileDirty();
            }
            return;
        }

        String name = hatch.mInputFluid.getFluid()
            .getName()
            .toLowerCase();
        if (name.equals("water")) return; // Regular water is completely disallowed

        int reqTier = MTEHatchNuclearHatch.getRequiredFluidTier(name);
        if (mPipeTier >= 0 && mPipeTier < reqTier) {
            return;
        }

        double minOperatingTemp = 100.0;
        double heatPerMB = NuclearSimulationEngine.coolingHeatPerLiter;
        int steamRatio = 160;
        String outputFluidName = "steam";

        if (name.contains("highpressureheavywater") && !name.contains("steam")) {
            minOperatingTemp = NuclearSimulationEngine.hpWaterBoilingPoint;
            heatPerMB = NuclearSimulationEngine.coolingHeatPerLiter * 4.0;
            steamRatio = 320;
            outputFluidName = "fluid.highpressureheavywatersteam";
        } else if (name.contains("heavywater") && !name.contains("steam")) {
            minOperatingTemp = 100.0;
            heatPerMB = NuclearSimulationEngine.coolingHeatPerLiter;
            steamRatio = 160;
            outputFluidName = "fluid.heavywatersteam";
        } else if (name.contains("highpressuredistilledwater") && !name.contains("steam")) {
            minOperatingTemp = NuclearSimulationEngine.hpWaterBoilingPoint;
            heatPerMB = NuclearSimulationEngine.coolingHeatPerLiter * 2.0;
            steamRatio = 320;
            outputFluidName = "ic2superheatedsteam";
        } else if (name.contains("distilledwater")) {
            minOperatingTemp = 100.0;
            heatPerMB = NuclearSimulationEngine.coolingHeatPerLiter;
            steamRatio = 160;
            outputFluidName = "steam";
        } else if (name.contains("coolant") && !name.contains("hot")) {
            minOperatingTemp = getAmbientTemperature();
            heatPerMB = NuclearSimulationEngine.ic2CoolantHeatPerLiter;
            steamRatio = 1;
            outputFluidName = "ic2hotcoolant";
        } else {
            return;
        }

        hatch.mLastProducedAmount = 0;
        hatch.mLastProducedFluidName = "";
        if (hatch.mTemperature > minOperatingTemp) {
            double deltaT = hatch.mTemperature - minOperatingTemp;
            double heatAvailable = deltaT * NuclearSimulationEngine.EU_PER_DEGREE;
            int maxFluidByHeat = (heatPerMB > 0) ? (int) Math.floor(heatAvailable / heatPerMB)
                : hatch.mInputFluid.amount;

            // Calculate turnover fraction based on deltaT above boiling threshold
            double frac = NuclearSimulationEngine.calculateTurnoverFraction(deltaT);
            double effFactor = Math.max(0.0, Math.min(1.0, efficiency));
            int desiredTurnover = effFactor <= 0.0 ? 0
                : Math.max(1, (int) Math.round(hatch.mCapacity * frac * effFactor));
            int fluidToProcess = Math.min(hatch.mInputFluid.amount, Math.min(desiredTurnover, maxFluidByHeat));

            if (fluidToProcess > 0) {
                int outAmount = fluidToProcess * steamRatio;
                hatch.mInputFluid.amount -= fluidToProcess;
                if (hatch.mInputFluid.amount <= 0) hatch.mInputFluid = null;

                Fluid outFluid = FluidRegistry.getFluid(outputFluidName);
                if (outFluid == null && outputFluidName.startsWith("fluid.")) {
                    outFluid = FluidRegistry.getFluid(outputFluidName.substring(6));
                }
                if (outFluid == null && !outputFluidName.startsWith("fluid.")) {
                    outFluid = FluidRegistry.getFluid("fluid." + outputFluidName);
                }
                if (outFluid == null) {
                    outFluid = FluidRegistry.getFluid("steam");
                }
                if (outFluid != null && outAmount > 0) {
                    this.addOutputPartial(new FluidStack(outFluid, outAmount));
                }

                hatch.mLastProducedAmount = outAmount;
                hatch.mLastProducedFluidName = outputFluidName;
                double heatConsumed = fluidToProcess * heatPerMB;
                hatch.mTemperature = Math
                    .max(minOperatingTemp, hatch.mTemperature - (heatConsumed / NuclearSimulationEngine.EU_PER_DEGREE));
                hatch.markTileDirty();
            }
        }
    }

    public static class NuclearGridTile implements INuclearTile {

        private final MTENuclearReactor reactor;
        private final MTEHatchNuclearBus bus;
        private final MTEHatchNuclearHatch hatch;
        private final MTEHatchNuclearControlRod controlRod;
        private final int gx;
        private final int gy;

        public NuclearGridTile(MTENuclearReactor reactor, MTEHatchNuclearBus bus, int gx, int gy) {
            this.reactor = reactor;
            this.bus = bus;
            this.hatch = null;
            this.controlRod = null;
            this.gx = gx;
            this.gy = gy;
        }

        public NuclearGridTile(MTENuclearReactor reactor, MTEHatchNuclearHatch hatch, int gx, int gy) {
            this.reactor = reactor;
            this.bus = null;
            this.hatch = hatch;
            this.controlRod = null;
            this.gx = gx;
            this.gy = gy;
        }

        public NuclearGridTile(MTENuclearReactor reactor, MTEHatchNuclearControlRod controlRod, int gx, int gy) {
            this.reactor = reactor;
            this.bus = null;
            this.hatch = null;
            this.controlRod = controlRod;
            this.gx = gx;
            this.gy = gy;
        }

        public boolean isBus() {
            return bus != null;
        }

        public boolean isHatch() {
            return hatch != null;
        }

        public boolean isControlRod() {
            return controlRod != null;
        }

        public MTEHatchNuclearBus getBus() {
            return bus;
        }

        public MTEHatchNuclearHatch getHatch() {
            return hatch;
        }

        public MTEHatchNuclearControlRod getControlRod() {
            return controlRod;
        }

        public int getGx() {
            return gx;
        }

        public int getGy() {
            return gy;
        }

        @Override
        public double getTemperature() {
            return reactor.getTileTemperature(this);
        }

        @Override
        public void setTemperature(double temp) {
            reactor.setTileTemperature(this, temp);
        }

        @Override
        public void addHeat(double heatEU) {
            reactor.addTileHeat(this, heatEU);
        }

        @Override
        public double getHeatTransferCoeff() {
            return reactor.getTileHeatTransferCoeff(this);
        }

        @Override
        public boolean isFuel() {
            return reactor.isTileFuel(this);
        }

        @Override
        public int generateNeutrons(double efficiency) {
            return reactor.generateTileNeutrons(this, efficiency);
        }

        @Override
        public double getAbsorptionProbability(NeutronType type) {
            return reactor.getTileAbsorptionProbability(this, type);
        }

        @Override
        public double getScatteringProbability(NeutronType type) {
            return reactor.getTileScatteringProbability(this, type);
        }

        @Override
        public double getModerationProbability() {
            return reactor.getTileModerationProbability(this);
        }

        @Override
        public void onNeutronAbsorbed(NeutronType type, int count) {
            reactor.onTileNeutronAbsorbed(this, type, count);
        }

        @Override
        public void onNeutronScattered(NeutronType type, int count) {
            reactor.onTileNeutronScattered(this, type, count);
        }

        @Override
        public void addNeutronFlux(NeutronType type, int count) {
            reactor.addTileNeutronFlux(this, type, count);
        }

        @Override
        public void nuclearTick(double efficiency) {
            reactor.processTileNuclearTick(this, efficiency);
        }

        @Override
        public double getInsulationDampening() {
            return reactor.getTileInsulationDampening(this);
        }
    }

    @Override
    public void getExtraWailaNBT(EntityPlayerMP playerMP, TileEntity tileEntity, NBTTagCompound tag, World world, int x,
        int y, int z) {
        tag.setDouble("coreTemp", mCoreTemp);
        double maxTemp = NuclearSimulationEngine.getMaxOperatingTemperature(mPipeTier);
        tag.setDouble("maxTemp", maxTemp);
        tag.setLong("euOutput", mDirectPowerEUt);
        tag.setInteger("coolantRate", mOutputCoolantRate);
        tag.setString("coolantName", mOutputCoolantName != null ? mOutputCoolantName : "");
        tag.setFloat("reactivity", (float) mReactivity);
        tag.setInteger("neutronsProduced", mNeutronsProduced);
        tag.setInteger("fastAbsorbed", mFastAbsorbed);
        tag.setInteger("thermalAbsorbed", mThermalAbsorbed);
        tag.setInteger("escapedNeutrons", mEscapedNeutrons);

        int fuelCount = 0;
        int coolantHatchCount = 0;
        int controlRodCount = 0;
        for (IGregTechTileEntity te : mNuclearTiles) {
            if (te != null) {
                IMetaTileEntity mte = te.getMetaTileEntity();
                if (mte instanceof MTEHatchNuclearBus) {
                    fuelCount++;
                } else if (mte instanceof MTEHatchNuclearHatch) {
                    coolantHatchCount++;
                } else if (mte instanceof MTEHatchNuclearControlRod) {
                    controlRodCount++;
                }
            }
        }
        tag.setInteger("fuelCount", fuelCount);
        tag.setInteger("coolantHatchCount", coolantHatchCount);
        tag.setInteger("controlRodCount", controlRodCount);
        tag.setInteger("totalCells", mNuclearTiles.size());
    }

    @Override
    public void getExtraWailaBody(ItemStack itemStack, List<String> list, NBTTagCompound tag,
        IWailaDataAccessor accessor, IWailaConfigHandler config) {
        double temp = tag.getDouble("coreTemp");
        double maxTemp = tag.getDouble("maxTemp");
        if (maxTemp <= 0.0) {
            maxTemp = 2500.0;
        }
        double ratio = Math.max(0.0, Math.min(1.0, temp / maxTemp));

        int topColor;
        int bottomColor;
        if (ratio >= 0.90) {
            topColor = 0xFFFF3333;
            bottomColor = 0xFF880000;
        } else if (ratio >= 0.75) {
            topColor = 0xFFFF9900;
            bottomColor = 0xFFCC5500;
        } else if (ratio >= 0.50) {
            topColor = 0xFFFFD700;
            bottomColor = 0xFFB8860B;
        } else {
            topColor = 0xFF00E676;
            bottomColor = 0xFF007A33;
        }
        String tempText = String.format("Core Temp: %,.1f / %,.0f °C", temp, maxTemp);
        list.add(TTRenderBar.create(tempText, topColor, bottomColor, ratio));

        long euOutput = tag.getLong("euOutput");
        int coolantRate = tag.getInteger("coolantRate");
        String coolantName = tag.getString("coolantName");

        if (euOutput > 0) {
            list.add(
                EnumChatFormatting.GREEN + "EU Output: "
                    + EnumChatFormatting.WHITE
                    + String.format("+%,d EU/t", euOutput));
        }
        if (coolantRate > 0 && !coolantName.isEmpty()) {
            list.add(
                EnumChatFormatting.AQUA + "Coolant Output: "
                    + EnumChatFormatting.WHITE
                    + String.format("%,d L/s %s", coolantRate, coolantName));
        }
        if (euOutput == 0 && coolantRate == 0) {
            list.add(EnumChatFormatting.GRAY + "Output: " + EnumChatFormatting.DARK_GRAY + "0 EU/t | 0 L/s");
        }

        float reactivity = tag.getFloat("reactivity");
        int flux = tag.getInteger("neutronsProduced");
        list.add(
            EnumChatFormatting.YELLOW + "Reactivity: "
                + EnumChatFormatting.WHITE
                + String.format("%.1f%%", reactivity * 100.0f)
                + EnumChatFormatting.GRAY
                + " | "
                + EnumChatFormatting.YELLOW
                + "Flux: "
                + EnumChatFormatting.WHITE
                + NuclearSimulationEngine.formatNeutronFlux(flux));

        int fast = tag.getInteger("fastAbsorbed");
        int therm = tag.getInteger("thermalAbsorbed");
        int esc = tag.getInteger("escapedNeutrons");
        if (flux > 0 || fast > 0 || therm > 0 || esc > 0) {
            list.add(
                EnumChatFormatting.GRAY + String.format("Neutrons: %,d fast, %,d therm, %,d esc", fast, therm, esc));
        }

        int fuelCount = tag.getInteger("fuelCount");
        int coolantCount = tag.getInteger("coolantHatchCount");
        int totalCells = tag.getInteger("totalCells");
        if (totalCells > 0) {
            list.add(
                EnumChatFormatting.GRAY
                    + String.format("Grid Cells: %d Fuel, %d Coolant / %d Total", fuelCount, coolantCount, totalCells));
        }
    }

    @Override
    public void getWailaBody(ItemStack itemStack, List<String> currentTip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        final NBTTagCompound tag = accessor.getNBTData();
        String efficiency = EnumChatFormatting.RESET + StatCollector
            .translateToLocalFormatted("GT5U.waila.multiblock.status.efficiency", tag.getFloat("efficiency"));
        if (tag.getBoolean("hasProblems")) {
            currentTip.add(
                EnumChatFormatting.RED + StatCollector.translateToLocal("GT5U.waila.multiblock.status.has_problem")
                    + efficiency);
        } else if (!tag.getBoolean("incompleteStructure")) {
            currentTip.add(
                EnumChatFormatting.GREEN + StatCollector.translateToLocal("GT5U.waila.multiblock.status.running_fine")
                    + efficiency);
        }
        try {
            if (gregtech.GTMod.proxy != null && gregtech.GTMod.proxy.wailaAverageNS && tag.hasKey("averageNS")) {
                int tAverageTime = tag.getInteger("averageNS");
                currentTip.add(
                    StatCollector.translateToLocalFormatted(
                        "GT5U.waila.multiblock.status.cpu_load",
                        formatNumber((long) tAverageTime)));
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z) {
        try {
            super.getWailaNBTData(player, tile, tag, world, x, y, z);
        } catch (Throwable ignored) {}
        tag.setInteger("progress", 0);
        tag.setInteger("maxProgress", 0);
    }

    @Override
    public void getExtraInfoData(List<String> info) {
        if (!mMachine) return;
        double maxTemp = NuclearSimulationEngine.getMaxOperatingTemperature(mPipeTier);
        info.add(String.format("Core Temp: %,.1f / %,.0f °C", mCoreTemp, maxTemp));
        if (mDirectPowerEUt > 0) {
            info.add(String.format("EU Output: +%,d EU/t", mDirectPowerEUt));
        }
        if (mOutputCoolantRate > 0 && mOutputCoolantName != null && !mOutputCoolantName.isEmpty()) {
            info.add(String.format("Coolant Output: %,d L/s %s", mOutputCoolantRate, mOutputCoolantName));
        }
        info.add(String.format("Reactivity: %.1f%%", mReactivity * 100.0));
        info.add("Flux: " + NuclearSimulationEngine.formatNeutronFlux(mNeutronsProduced));
        info.add(
            String.format("Neutrons: %,d fast, %,d therm, %,d esc", mFastAbsorbed, mThermalAbsorbed, mEscapedNeutrons));
        int fuelCount = 0;
        int coolantHatchCount = 0;
        int controlRodCount = 0;
        for (IGregTechTileEntity te : mNuclearTiles) {
            if (te != null) {
                IMetaTileEntity mte = te.getMetaTileEntity();
                if (mte instanceof MTEHatchNuclearBus) {
                    fuelCount++;
                } else if (mte instanceof MTEHatchNuclearHatch) {
                    coolantHatchCount++;
                } else if (mte instanceof MTEHatchNuclearControlRod) {
                    controlRodCount++;
                }
            }
        }
        if (!mNuclearTiles.isEmpty()) {
            info.add(
                String.format(
                    "Grid Cells: %d Fuel, %d Coolant, %d Control / %d Total",
                    fuelCount,
                    coolantHatchCount,
                    controlRodCount,
                    mNuclearTiles.size()));
        }
    }

    public static class ReactorDummy implements IReactor {

        private final MTENuclearReactor reactor;
        private NuclearGridTile currentTile;

        public ReactorDummy(MTENuclearReactor reactor) {
            this.reactor = reactor;
        }

        public void setCurrentTile(NuclearGridTile tile) {
            this.currentTile = tile;
        }

        @Override
        public ChunkCoordinates getPosition() {
            if (reactor.getBaseMetaTileEntity() == null) return new ChunkCoordinates(0, 0, 0);
            return new ChunkCoordinates(
                reactor.getBaseMetaTileEntity()
                    .getXCoord(),
                reactor.getBaseMetaTileEntity()
                    .getYCoord(),
                reactor.getBaseMetaTileEntity()
                    .getZCoord());
        }

        @Override
        public World getWorld() {
            return reactor.getBaseMetaTileEntity() != null ? reactor.getBaseMetaTileEntity()
                .getWorld() : null;
        }

        @Override
        public int getHeat() {
            return currentTile != null ? (int) currentTile.getTemperature() : (int) reactor.mAvgTemp;
        }

        @Override
        public void setHeat(int heat) {
            if (currentTile != null) {
                currentTile.setTemperature(heat);
            }
        }

        @Override
        public int addHeat(int amount) {
            if (currentTile != null) {
                currentTile.addHeat(amount * NuclearSimulationEngine.EU_PER_DEGREE);
                return (int) currentTile.getTemperature();
            }
            return 0;
        }

        @Override
        public int getMaxHeat() {
            return 10000;
        }

        @Override
        public void setMaxHeat(int maxHeat) {}

        @Override
        public void addEmitHeat(int heat) {}

        @Override
        public float getHeatEffectModifier() {
            return 1.0f;
        }

        @Override
        public void setHeatEffectModifier(float modifier) {}

        @Override
        public float getReactorEnergyOutput() {
            return 0;
        }

        @Override
        public double getReactorEUEnergyOutput() {
            return 0;
        }

        @Override
        public float addOutput(float energy) {
            return energy;
        }

        @Override
        public ItemStack getItemAt(int x, int y) {
            if (currentTile != null && currentTile.isBus()) {
                return currentTile.getBus().mInventory[MTEHatchNuclearBus.SLOT_INPUT];
            }
            return null;
        }

        @Override
        public void setItemAt(int x, int y, ItemStack item) {
            if (currentTile != null && currentTile.isBus()) {
                currentTile.getBus().mInventory[MTEHatchNuclearBus.SLOT_INPUT] = item;
            }
        }

        @Override
        public void explode() {}

        @Override
        public int getTickRate() {
            return 20;
        }

        @Override
        public boolean produceEnergy() {
            return true;
        }

        @Override
        public void setRedstoneSignal(boolean redstone) {}

        @Override
        public boolean isFluidCooled() {
            return false;
        }
    }
}
