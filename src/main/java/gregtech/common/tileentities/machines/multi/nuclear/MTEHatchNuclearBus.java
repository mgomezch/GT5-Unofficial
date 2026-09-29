package gregtech.common.tileentities.machines.multi.nuclear;

import static gregtech.api.enums.Textures.BlockIcons.ITEM_IN_SIGN;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_PIPE_COLORS;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_PIPE_IN;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.modularui.api.math.Color;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.common.widget.DrawableWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;

import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;

/**
 * Dumb item container hatch for the modular nuclear reactor.
 * Holds 1 input slot (slot 0) and 2 output slots (slots 1 & 2).
 * All nuclear physics, depletion, and energy generation logic is processed by MTENuclearReactor.
 */
public class MTEHatchNuclearBus extends MTEHatch {

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT_1 = 1;
    public static final int SLOT_OUTPUT_2 = 2;

    public double mTemperature = 20.0;
    public double mHeatEU = 0.0;
    public int mFastFlux = 0;
    public int mThermalFlux = 0;
    public int mFastAbsorbed = 0;
    public int mThermalAbsorbed = 0;
    public int mLastFastFlux = 0;
    public int mLastThermalFlux = 0;
    public int mLastFastAbsorbed = 0;
    public int mLastThermalAbsorbed = 0;
    public int mLastNeutronsGenerated = 0;
    public long mDirectEUProduced = 0;

    public MTEHatchNuclearBus(int aID, String aName, String aNameRegional, int aTier) {
        super(
            aID,
            aName,
            aNameRegional,
            aTier,
            3,
            new String[] { "Nuclear Core Bus for Items", "Holds Fuel Rods, Reflectors, Coolant Cells, or Control Rods",
                "Supports BOTH Input and Output from the same block!", "Slot 0: Active Core Component (Input)",
                "Slots 1 & 2: Depleted / Extracted Items (Output)" });
    }

    public MTEHatchNuclearBus(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, 3, aDescription, aTextures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEHatchNuclearBus(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public boolean doesFillContainers() {
        return false;
    }

    @Override
    public boolean doesEmptyContainers() {
        return false;
    }

    @Override
    public boolean canTankBeFilled() {
        return false;
    }

    @Override
    public boolean canTankBeEmptied() {
        return false;
    }

    @Override
    public boolean isValidSlot(int aIndex) {
        return aIndex >= 0 && aIndex <= 2;
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection aSide,
        ItemStack aStack) {
        // Automation can ONLY insert fresh components into slot 0
        return aIndex == SLOT_INPUT;
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection aSide,
        ItemStack aStack) {
        // Automation can extract products from output slots 1 & 2
        return aIndex == SLOT_OUTPUT_1 || aIndex == SLOT_OUTPUT_2;
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        byte color = getBaseMetaTileEntity().getColorization();
        ITexture coloredOverlay = TextureFactory.of(OVERLAY_PIPE_COLORS[color + 1]);
        return new ITexture[] { aBaseTexture, TextureFactory.of(OVERLAY_PIPE_IN), coloredOverlay,
            TextureFactory.of(ITEM_IN_SIGN) };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        byte color = getBaseMetaTileEntity().getColorization();
        ITexture coloredOverlay = TextureFactory.of(OVERLAY_PIPE_COLORS[color + 1]);
        return new ITexture[] { aBaseTexture, TextureFactory.of(OVERLAY_PIPE_IN), coloredOverlay,
            TextureFactory.of(ITEM_IN_SIGN) };
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setDouble("mTemperature", mTemperature);
        aNBT.setDouble("mHeatEU", mHeatEU);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mTemperature = aNBT.getDouble("mTemperature");
        if (mTemperature < 20.0) mTemperature = 20.0;
        mHeatEU = aNBT.getDouble("mHeatEU");
    }

    public void markTileDirty() {
        if (getBaseMetaTileEntity() != null) {
            getBaseMetaTileEntity().markDirty();
        }
    }

    public boolean ejectToOutput(ItemStack stack) {
        if (stack == null) return true;
        if (mInventory[SLOT_OUTPUT_1] == null) {
            mInventory[SLOT_OUTPUT_1] = stack;
            return true;
        } else if (GTUtility.areStacksEqual(mInventory[SLOT_OUTPUT_1], stack)
            && mInventory[SLOT_OUTPUT_1].stackSize + stack.stackSize <= mInventory[SLOT_OUTPUT_1].getMaxStackSize()) {
                mInventory[SLOT_OUTPUT_1].stackSize += stack.stackSize;
                return true;
            } else if (mInventory[SLOT_OUTPUT_2] == null) {
                mInventory[SLOT_OUTPUT_2] = stack;
                return true;
            } else if (GTUtility.areStacksEqual(mInventory[SLOT_OUTPUT_2], stack)
                && mInventory[SLOT_OUTPUT_2].stackSize + stack.stackSize
                    <= mInventory[SLOT_OUTPUT_2].getMaxStackSize()) {
                        mInventory[SLOT_OUTPUT_2].stackSize += stack.stackSize;
                        return true;
                    }
        return false;
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity,
        net.minecraft.entity.player.EntityPlayer aPlayer) {
        openGui(aPlayer);
        return true;
    }

    @Override
    protected boolean useMui2() {
        return true;
    }

    @Override
    public com.cleanroommc.modularui.screen.ModularPanel buildUI(com.cleanroommc.modularui.factory.PosGuiData data,
        com.cleanroommc.modularui.value.sync.PanelSyncManager syncManager,
        com.cleanroommc.modularui.screen.UISettings uiSettings) {
        return new gregtech.common.gui.modularui.hatch.MTEHatchNuclearBusGui(this).build(data, syncManager, uiSettings);
    }

    @Override
    public void addUIWidgets(ModularWindow.Builder builder, UIBuildContext buildContext) {
        builder.widget(
            new DrawableWidget().setDrawable(GTUITextures.PICTURE_SCREEN_BLACK)
                .setPos(7, 16)
                .setSize(96, 56))
            .widget(
                new TextWidget("Nuclear Core Bus").setDefaultColor(Color.rgb(0, 255, 128))
                    .setPos(10, 20))
            .widget(
                new TextWidget().setStringSupplier(() -> String.format("Temp: %.1f °C", mTemperature))
                    .setDefaultColor(Color.rgb(255, 200, 0))
                    .setPos(10, 31))
            .widget(
                new TextWidget().setStringSupplier(() -> String.format("Fast: %d n/t", mLastFastFlux))
                    .setDefaultColor(Color.rgb(100, 200, 255))
                    .setPos(10, 42))
            .widget(
                new TextWidget().setStringSupplier(() -> String.format("Thrm: %d n/t", mLastThermalFlux))
                    .setDefaultColor(Color.rgb(150, 180, 255))
                    .setPos(10, 53))
            .widget(
                new TextWidget("In").setDefaultColor(0xFFFFFFFF)
                    .setPos(115, 16))
            .widget(
                new SlotWidget(inventoryHandler, SLOT_INPUT)
                    .setBackground(getGUITextureSet().getItemSlot(), GTUITextures.OVERLAY_SLOT_IN)
                    .setPos(112, 28))
            .widget(
                new TextWidget("Out").setDefaultColor(0xFFFFFFFF)
                    .setPos(145, 16))
            .widget(
                new SlotWidget(inventoryHandler, SLOT_OUTPUT_1).setAccess(true, false)
                    .setBackground(getGUITextureSet().getItemSlot(), GTUITextures.OVERLAY_SLOT_OUT)
                    .setPos(142, 28))
            .widget(
                new SlotWidget(inventoryHandler, SLOT_OUTPUT_2).setAccess(true, false)
                    .setBackground(getGUITextureSet().getItemSlot(), GTUITextures.OVERLAY_SLOT_OUT)
                    .setPos(142, 48));
    }
}
