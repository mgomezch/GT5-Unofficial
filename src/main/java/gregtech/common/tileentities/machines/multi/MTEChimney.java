package gregtech.common.tileentities.machines.multi;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.isAir;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlockAnyMeta;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.Maintenance;
import static gregtech.api.enums.HatchElement.Muffler;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_MULTI_SMELTER;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_MULTI_SMELTER_ACTIVE;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_MULTI_SMELTER_ACTIVE_GLOW;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_MULTI_SMELTER_GLOW;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import javax.annotation.Nonnull;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.ICasingTextureProvider;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatchMuffler;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.pollution.Pollution;

public class MTEChimney extends MTEEnhancedMultiBlockBase<MTEChimney>
    implements ISurvivalConstructable, ICasingTextureProvider {

    public static final int CASING_INDEX = 10;
    public static final int CHIMNEY_HEIGHT = 25;
    public static final int POLLUTION_PER_5_SEC = 500;

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final IStructureDefinition<MTEChimney> STRUCTURE_DEFINITION = StructureDefinition
        .<MTEChimney>builder()
        .addShape(STRUCTURE_PIECE_MAIN, buildStructureShape())
        .addElement(
            'm',
            buildHatchAdder(MTEChimney.class).atLeast(Maintenance)
                .casingIndex(CASING_INDEX)
                .hint(1)
                .buildAndChain(Blocks.brick_block, 0))
        .addElement('M', ofChain(Muffler.newAny(CASING_INDEX, 2), isAir()))
        .addElement('b', ofBlock(Blocks.brick_block, 0))
        .addElement('-', isAir())
        .addElement('F', ofChain(ofBlockAnyMeta(Blocks.furnace), ofBlockAnyMeta(Blocks.lit_furnace)))
        .build();

    private static String[][] buildStructureShape() {
        String[][] shape = new String[CHIMNEY_HEIGHT][];
        // Layer 0 is top layer (y = 24 relative to controller)
        shape[0] = new String[] { "mmm", "mMm", "mmm" };
        // Middle 23 layers (y = 1 to 23 relative to controller)
        for (int i = 1; i <= CHIMNEY_HEIGHT - 2; i++) {
            shape[i] = new String[] { "bbb", "b-b", "bbb" };
        }
        // Layer 24 is bottom layer (y = 0 relative to controller): front is row 0, center is row 1
        shape[CHIMNEY_HEIGHT - 1] = new String[] { "bFb", "b~b", "bbb" };
        return transpose(shape);
    }

    public MTEChimney(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTEChimney(String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEChimney(this.mName);
    }

    @Override
    public IStructureDefinition<MTEChimney> getStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType("Chimney")
            .addInfo("Draws pollution from the chunk and vents it high into the atmosphere.")
            .addInfo(
                "When the furnace at the bottom front is lit, removes up to " + POLLUTION_PER_5_SEC
                    + " pollution every 5 seconds")
            .addInfo("and re-adds it at the height of its highest block (height 25).")
            .addInfo("Optionally accepts a Muffler Hatch at the top center to reduce pollution output")
            .addInfo("while maintaining the full atmospheric height effect.")
            .addSeparator()
            .beginStructureBlock(3, CHIMNEY_HEIGHT, 3, false)
            .addController("Bottom center")
            .addOtherStructurePart("Furnace", "Bottom front center (facing outward)")
            .addStructureInfo("Walls: 3x3 hollow tower of Brick Blocks (height 25)")
            .addMaintenanceHatch("Top layer outer ring (optional, max 1)")
            .addMufflerHatch("Top center (optional, max 1)")
            .toolTipFinisher(EnumChatFormatting.GOLD + "GT New Horizons");
        return tt;
    }

    @Override
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        if (!checkPiece(STRUCTURE_PIECE_MAIN, 1, CHIMNEY_HEIGHT - 1, 1)) {
            return false;
        }
        return mMaintenanceHatches.size() <= 1 && mMufflerHatches.size() <= 1;
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, 1, CHIMNEY_HEIGHT - 1, 1);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece(
            STRUCTURE_PIECE_MAIN,
            stackSize,
            1,
            CHIMNEY_HEIGHT - 1,
            1,
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
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        if (aBaseMetaTileEntity.isServerSide()) {
            if (mMachine) {
                GTUtility.sendChatToPlayer(
                    aPlayer,
                    "Chimney is formed and "
                        + (aBaseMetaTileEntity.isActive() ? "running (Furnace is lit)." : "idle (Furnace is unlit)."));
            } else {
                GTUtility.sendChatToPlayer(aPlayer, "Chimney structure incomplete.");
            }
        }
        return true;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);

        if (aBaseMetaTileEntity.isServerSide()) {
            if (this.mMachine) {
                ForgeDirection front = aBaseMetaTileEntity.getFrontFacing();
                int fx = aBaseMetaTileEntity.getXCoord() + front.offsetX;
                int fy = aBaseMetaTileEntity.getYCoord();
                int fz = aBaseMetaTileEntity.getZCoord() + front.offsetZ;

                World world = aBaseMetaTileEntity.getWorld();
                Block block = world.getBlock(fx, fy, fz);
                TileEntity te = world.getTileEntity(fx, fy, fz);

                boolean isLit = block == Blocks.lit_furnace
                    || (te instanceof TileEntityFurnace && ((TileEntityFurnace) te).isBurning());

                if (isLit) {
                    aBaseMetaTileEntity.setActive(true);

                    // Every 5 seconds (100 ticks)
                    if (aTick % 100 == 0) {
                        checkMaintenance();
                        if (getRepairStatus() == getIdealStatus() || !shouldCheckMaintenance()) {
                            int chunkX = aBaseMetaTileEntity.getXCoord() >> 4;
                            int chunkZ = aBaseMetaTileEntity.getZCoord() >> 4;
                            int currentPollution = Pollution.getPollution(world, chunkX, chunkZ);

                            if (currentPollution > 0) {
                                int amountToRemove = Math.min(currentPollution, POLLUTION_PER_5_SEC);
                                int outputAmount = amountToRemove;

                                if (!mMufflerHatches.isEmpty()) {
                                    MTEHatchMuffler muffler = mMufflerHatches.get(0);
                                    if (muffler.isValid()) {
                                        outputAmount = muffler.calculatePollutionReduction(amountToRemove);
                                        muffler.getBaseMetaTileEntity()
                                            .setActive(true);
                                    }
                                }

                                double topHeight = aBaseMetaTileEntity.getYCoord() + (CHIMNEY_HEIGHT - 1.0);
                                Pollution.movePollutionHeight(
                                    world,
                                    chunkX,
                                    chunkZ,
                                    amountToRemove,
                                    outputAmount,
                                    topHeight);
                            }
                        }
                    }
                } else {
                    aBaseMetaTileEntity.setActive(false);
                }
            } else {
                aBaseMetaTileEntity.setActive(false);
            }
        } else if (aBaseMetaTileEntity.isClientSide()) {
            if (aBaseMetaTileEntity.isActive() && mMufflerHatches.isEmpty()) {
                // Emit smoke particles out of the open top center
                int cx = aBaseMetaTileEntity.getXCoord();
                int cy = aBaseMetaTileEntity.getYCoord() + CHIMNEY_HEIGHT;
                int cz = aBaseMetaTileEntity.getZCoord();
                World world = aBaseMetaTileEntity.getWorld();
                world.spawnParticle("largesmoke", cx + 0.5, cy + 0.1, cz + 0.5, 0.0, 0.15, 0.0);
            }
        }
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int aColorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            if (aActive) {
                return new ITexture[] { TextureFactory.of(Blocks.brick_block),
                    TextureFactory.of(OVERLAY_FRONT_MULTI_SMELTER_ACTIVE), TextureFactory.builder()
                        .addIcon(OVERLAY_FRONT_MULTI_SMELTER_ACTIVE_GLOW)
                        .glow()
                        .build() };
            }
            return new ITexture[] { TextureFactory.of(Blocks.brick_block),
                TextureFactory.of(OVERLAY_FRONT_MULTI_SMELTER), TextureFactory.builder()
                    .addIcon(OVERLAY_FRONT_MULTI_SMELTER_GLOW)
                    .glow()
                    .build() };
        }
        return new ITexture[] { TextureFactory.of(Blocks.brick_block) };
    }

    @Override
    public ITexture getCasingTexture() {
        return TextureFactory.of(Blocks.brick_block);
    }

    public int getCasingTextureIndex() {
        return CASING_INDEX;
    }
}
