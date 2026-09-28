package gregtech.common.tileentities.machines.multi.nuclear;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.casing.Casings.BlackPlutoniumItemPipeCasing;
import static gregtech.api.casing.Casings.ElectrumItemPipeCasing;
import static gregtech.api.casing.Casings.FluxedElectrumItemPipeCasing;
import static gregtech.api.casing.Casings.OsmiumItemPipeCasing;
import static gregtech.api.casing.Casings.PlatinumItemPipeCasing;
import static gregtech.api.casing.Casings.QuantiumItemPipeCasing;
import static gregtech.api.casing.Casings.RadiationProofMachineCasing;
import static gregtech.api.enums.HatchElement.Dynamo;
import static gregtech.api.enums.HatchElement.ExoticDynamo;
import static gregtech.api.enums.HatchElement.Maintenance;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static gregtech.api.util.GTStructureUtility.ofHatchAdder;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizons.modularui.api.math.Color;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.common.widget.DrawableWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;

import gregtech.GTMod;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.SoundResource;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.ICasingTextureProvider;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.GTLog;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gregtech.common.pollution.Pollution;

public class MTENuclearReactor extends MTEEnhancedMultiBlockBase<MTENuclearReactor>
    implements ISurvivalConstructable, ICasingTextureProvider {

    protected static final int CASING_INDEX = 44; // RadiationProofMachineCasing texture
    protected static final String STRUCTURE_5X5 = "5x5";
    protected static final String STRUCTURE_7X7 = "7x7";
    protected static final String STRUCTURE_9X9 = "9x9";

    private static IStructureDefinition<MTENuclearReactor> STRUCTURE_DEFINITION = null;

    private static List<Pair<Block, Integer>> getPipeCasingRepresentatives() {
        return ImmutableList.of(
            Pair.of(ElectrumItemPipeCasing.getBlock(), ElectrumItemPipeCasing.getBlockMeta()),
            Pair.of(PlatinumItemPipeCasing.getBlock(), PlatinumItemPipeCasing.getBlockMeta()),
            Pair.of(OsmiumItemPipeCasing.getBlock(), OsmiumItemPipeCasing.getBlockMeta()),
            Pair.of(QuantiumItemPipeCasing.getBlock(), QuantiumItemPipeCasing.getBlockMeta()),
            Pair.of(FluxedElectrumItemPipeCasing.getBlock(), FluxedElectrumItemPipeCasing.getBlockMeta()),
            Pair.of(BlackPlutoniumItemPipeCasing.getBlock(), BlackPlutoniumItemPipeCasing.getBlockMeta()));
    }

    public int mPipeTier = -1;
    public int gridSize = 0;
    public int coreDimension = 0;
    public INuclearTile[][] mGrid = null;
    private final List<IGregTechTileEntity> mNuclearTiles = new ArrayList<>();

    // Telemetry
    public double mCoreTemp = 20.0;
    public double mAvgTemp = 20.0;
    public int mNeutronsProduced = 0;
    public int mFastAbsorbed = 0;
    public int mThermalAbsorbed = 0;
    public int mEscapedNeutrons = 0;
    public double mEfficiency = 1.0;
    public long mDirectPowerEUt = 0;

    @Nullable
    public static Integer getPipeTierFromBlock(Block block, int meta) {
        if (block == ElectrumItemPipeCasing.getBlock() && meta == ElectrumItemPipeCasing.getBlockMeta())
            return NuclearSimulationEngine.PIPE_TIER_ELECTRUM;
        if (block == PlatinumItemPipeCasing.getBlock() && meta == PlatinumItemPipeCasing.getBlockMeta())
            return NuclearSimulationEngine.PIPE_TIER_PLATINUM;
        if (block == OsmiumItemPipeCasing.getBlock() && meta == OsmiumItemPipeCasing.getBlockMeta())
            return NuclearSimulationEngine.PIPE_TIER_OSMIUM;
        if (block == QuantiumItemPipeCasing.getBlock() && meta == QuantiumItemPipeCasing.getBlockMeta())
            return NuclearSimulationEngine.PIPE_TIER_QUANTIUM;
        if (block == FluxedElectrumItemPipeCasing.getBlock() && meta == FluxedElectrumItemPipeCasing.getBlockMeta())
            return NuclearSimulationEngine.PIPE_TIER_FLUXED_ELECTRUM;
        if (block == BlackPlutoniumItemPipeCasing.getBlock() && meta == BlackPlutoniumItemPipeCasing.getBlockMeta())
            return NuclearSimulationEngine.PIPE_TIER_BLACK_PLUTONIUM;
        return null;
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
        return RadiationProofMachineCasing.getCasingTexture();
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        if (side == aFacing) {
            return new ITexture[] { RadiationProofMachineCasing.getCasingTexture(),
                TextureFactory.of(
                    aActive ? gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_NUCLEAR_REACTOR_ACTIVE
                        : gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_NUCLEAR_REACTOR) };
        }
        return new ITexture[] { RadiationProofMachineCasing.getCasingTexture() };
    }

    public boolean addNuclearTile(IGregTechTileEntity aTileEntity, short aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity mte = aTileEntity.getMetaTileEntity();
        if (mte instanceof INuclearTile) {
            mNuclearTiles.add(aTileEntity);
            return true;
        }
        return false;
    }

    @Override
    public IStructureDefinition<MTENuclearReactor> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<MTENuclearReactor>builder()
                // 5x5 Shape (3x3 internal core grid)
                .addShape(
                    STRUCTURE_5X5,
                    transpose(
                        new String[][] {
                            // Layer -1 (Base)
                            { "ccccc", "ccccc", "ccccc", "ccccc", "ccccc" },
                            // Layer 0 (Controller level)
                            { "cc~cc", "cpppc", "cpppc", "cpppc", "ccccc" },
                            // Layer 1
                            { "ccccc", "cpppc", "cpppc", "cpppc", "ccccc" },
                            // Layer 2
                            { "ccccc", "cpppc", "cpppc", "cpppc", "ccccc" },
                            // Layer 3 (Top Grid)
                            { "ccccc", "cgggc", "cgggc", "cgggc", "ccccc" } }))
                // 7x7 Shape (5x5 internal core grid)
                .addShape(
                    STRUCTURE_7X7,
                    transpose(
                        new String[][] {
                            { "ccccccc", "ccccccc", "ccccccc", "ccccccc", "ccccccc", "ccccccc", "ccccccc" },
                            { "ccc~ccc", "cpppppc", "cpppppc", "cpppppc", "cpppppc", "cpppppc", "ccccccc" },
                            { "ccccccc", "cpppppc", "cpppppc", "cpppppc", "cpppppc", "cpppppc", "ccccccc" },
                            { "ccccccc", "cpppppc", "cpppppc", "cpppppc", "cpppppc", "cpppppc", "ccccccc" },
                            { "ccccccc", "cgggggc", "cgggggc", "cgggggc", "cgggggc", "cgggggc", "ccccccc" } }))
                // 9x9 Shape (7x7 internal core grid)
                .addShape(
                    STRUCTURE_9X9,
                    transpose(
                        new String[][] {
                            { "ccccccccc", "ccccccccc", "ccccccccc", "ccccccccc", "ccccccccc", "ccccccccc", "ccccccccc",
                                "ccccccccc", "ccccccccc" },
                            { "cccc~cccc", "cpppppppc", "cpppppppc", "cpppppppc", "cpppppppc", "cpppppppc", "cpppppppc",
                                "cpppppppc", "ccccccccc" },
                            { "ccccccccc", "cpppppppc", "cpppppppc", "cpppppppc", "cpppppppc", "cpppppppc", "cpppppppc",
                                "cpppppppc", "ccccccccc" },
                            { "ccccccccc", "cpppppppc", "cpppppppc", "cpppppppc", "cpppppppc", "cpppppppc", "cpppppppc",
                                "cpppppppc", "ccccccccc" },
                            { "ccccccccc", "cgggggggc", "cgggggggc", "cgggggggc", "cgggggggc", "cgggggggc", "cgggggggc",
                                "cgggggggc", "ccccccccc" } }))
                .addElement(
                    'c',
                    ofChain(
                        buildHatchAdder(MTENuclearReactor.class).atLeast(Maintenance, Dynamo.or(ExoticDynamo))
                            .casingIndex(CASING_INDEX)
                            .build(),
                        RadiationProofMachineCasing.asElement()))
                .addElement(
                    'p',
                    ofBlocksTiered(
                        MTENuclearReactor::getPipeTierFromBlock,
                        getPipeCasingRepresentatives(),
                        -1,
                        (t, m) -> t.mPipeTier = m,
                        t -> t.mPipeTier))
                .addElement(
                    'g',
                    ofChain(
                        ofHatchAdder(MTENuclearReactor::addNuclearTile, CASING_INDEX, 1),
                        RadiationProofMachineCasing.asElement()))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType("Nuclear Fission Reactor")
            .addInfo("Modern Industrialization-style modular nuclear reactor")
            .addInfo("Simulates discrete fast & thermal neutron transport, scattering, and moderation")
            .addInfo("Supports self-stabilizing negative temperature reactivity feedback")
            .addInfo("Top core grid accepts Nuclear Buses (Items) and Nuclear Hatches (Fluids)")
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
            .addInfo(EnumChatFormatting.RED + "WARNING: Adding water to a dry running hatch causes an explosion!")
            .addInfo(EnumChatFormatting.RED + "WARNING: Core melts down if temperature exceeds casing rating!")
            .beginStructureBlock(5, 5, 5, false)
            .addController("Front center, 2nd layer")
            .addCasing("50+", "Radiation Proof Machine Casings", false)
            .addCasing(
                "27+",
                "Item Pipe Casings (Electrum / Platinum / Osmium / Quantium / Fluxed Electrum / Black Plutonium)",
                false)
            .addOtherStructurePart("Nuclear Bus / Hatch", "Top layer core positions", 1)
            .addMaintenanceHatch("Any outer casing", 1)
            .addDynamoHatch("Any outer casing (Optional for Betavoltaic direct EU)", 1)
            .toolTipFinisher(EnumChatFormatting.AQUA + "GTNH x Modern Industrialization");
        return tt;
    }

    @Override
    public String[] getStructureDescription(ItemStack stackSize) {
        return new String[] { "Modern Industrialization-style Nuclear Fission Reactor",
            "5x5 (3x3 core), 7x7 (5x5 core), or 9x9 (7x7 core)" };
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_5X5, stackSize, hintsOnly, 2, 1, 0);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece(STRUCTURE_5X5, stackSize, 2, 1, 0, elementBudget, env, false, true);
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
                    hatch.getBaseMetaTileEntity().markDirty();
                }
            } else if (te != null && te.getMetaTileEntity() instanceof MTEHatchNuclearBus bus) {
                // Void all fuel in nuclear bus hatches, but PRESERVE other components like reflectors and betavoltaics!
                if (bus.isFuel()) {
                    bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = null;
                    if (bus.getBaseMetaTileEntity() != null) {
                        bus.getBaseMetaTileEntity().markDirty();
                    }
                }
            }
        }

        // Shut down reactor with power loss & powerfail event
        stopMachine(ShutDownReasonRegistry.POWER_LOSS);
        if (GTMod.proxy.powerfailTracker != null) {
            GTMod.proxy.powerfailTracker.createPowerfailEvent(base);
        }
        mEfficiency = 0;

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

        if (checkPiece(STRUCTURE_5X5, 2, 1, 0, errors)) {
            gridSize = 3;
            coreDimension = 5;
        } else if (checkPiece(STRUCTURE_7X7, 3, 1, 0, errors)) {
            gridSize = 5;
            coreDimension = 7;
        } else if (checkPiece(STRUCTURE_9X9, 4, 1, 0, errors)) {
            gridSize = 7;
            coreDimension = 9;
        } else {
            return;
        }

        checkHasMaintenanceHatch(errors);

        // Build 2D grid from matched tiles
        mGrid = new INuclearTile[gridSize][gridSize];
        int cX = aBaseMetaTileEntity.getXCoord();
        int cZ = aBaseMetaTileEntity.getZCoord();
        ForgeDirection facing = aBaseMetaTileEntity.getFrontFacing();

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
            int gy = localZ - 1;

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
    public CheckRecipeResult checkProcessing() {
        if (!mMachine || mGrid == null) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        mMaxProgresstime = 20;
        mEfficiency = 10000;
        mEfficiencyIncrease = 10000;
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
                                        "Coolant injected into dry superheated hatch above boiling threshold ("
                                            + name
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

                NuclearSimulationEngine.SimulationResult res = NuclearSimulationEngine.simulate(mGrid, gridSize, gridSize);
                mCoreTemp = res.maxTemperature;
                mAvgTemp = res.averageTemperature;
                mNeutronsProduced = res.totalNeutronsGenerated;
                mFastAbsorbed = res.fastNeutronsAbsorbed;
                mThermalAbsorbed = res.thermalNeutronsAbsorbed;
                mEscapedNeutrons = res.neutronsEscaped;
                mEfficiency = NuclearSimulationEngine.calculateEfficiency(mAvgTemp);

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
                                    hatch.getBaseMetaTileEntity().markDirty();
                                }
                            } else if (nuclearTile instanceof MTEHatchNuclearBus bus) {
                                bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = null;
                                bus.mInventory[MTEHatchNuclearBus.SLOT_OUTPUT_1] = null;
                                bus.mInventory[MTEHatchNuclearBus.SLOT_OUTPUT_2] = null;
                                if (bus.getBaseMetaTileEntity() != null) {
                                    bus.getBaseMetaTileEntity().markDirty();
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
    }

    @Override
    public void addUIWidgets(ModularWindow.Builder builder, UIBuildContext buildContext) {
        builder.widget(
            new DrawableWidget().setDrawable(GTUITextures.PICTURE_SCREEN_BLACK)
                .setPos(7, 16)
                .setSize(162, 80))
            .widget(
                new TextWidget(
                    "Nuclear Fission Core (" + (coreDimension > 0 ? coreDimension + "x" + coreDimension : "Offline")
                        + ")").setDefaultColor(Color.rgb(0, 255, 128))
                            .setPos(12, 20))
            .widget(
                new TextWidget()
                    .setStringSupplier(
                        () -> String.format(
                            "Peak Temp: %.1f / %.0f °C",
                            mCoreTemp,
                            NuclearSimulationEngine.getMaxOperatingTemperature(mPipeTier)))
                    .setDefaultColor(Color.rgb(255, 200, 0))
                    .setPos(12, 31))
            .widget(
                new TextWidget()
                    .setStringSupplier(
                        () -> String.format("Reactivity: %.1f%%  Flux: %d/s", mEfficiency * 100.0, mNeutronsProduced))
                    .setDefaultColor(Color.rgb(100, 200, 255))
                    .setPos(12, 42))
            .widget(
                new TextWidget()
                    .setStringSupplier(
                        () -> String.format(
                            "Neutrons: %d fast, %d therm, %d esc",
                            mFastAbsorbed,
                            mThermalAbsorbed,
                            mEscapedNeutrons))
                    .setDefaultColor(Color.rgb(200, 200, 200))
                    .setPos(12, 53))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> (mDirectPowerEUt > 0)
                        ? String.format("Pipes: %s | Beta: %d EU/t", getPipeTierName(mPipeTier), mDirectPowerEUt)
                        : "Pipes: " + getPipeTierName(mPipeTier))
                    .setDefaultColor(Color.rgb(180, 220, 180))
                    .setPos(12, 64))
            .widget(new TextWidget().setStringSupplier(() -> {
                double maxTemp = NuclearSimulationEngine.getMaxOperatingTemperature(mPipeTier);
                if (mCoreTemp > maxTemp * 0.85) return "WARNING: THERMAL LIMIT CRITICAL";
                return "Core Stability: NOMINAL";
            })
                .setDefaultColor(
                    mCoreTemp > NuclearSimulationEngine.getMaxOperatingTemperature(mPipeTier) * 0.85
                        ? Color.rgb(255, 50, 50)
                        : Color.rgb(0, 200, 50))
                .setPos(12, 75));
    }
}
