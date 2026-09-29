package gregtech.common.tileentities.machines.multi.nuclear;

import static gregtech.api.casing.Casings.NuclearCasing;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.modularui.api.drawable.IDrawable;
import com.gtnewhorizons.modularui.api.drawable.Text;
import com.gtnewhorizons.modularui.api.math.Alignment;
import com.gtnewhorizons.modularui.api.math.Color;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.common.widget.ButtonWidget;
import com.gtnewhorizons.modularui.common.widget.DrawableWidget;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;

import gregtech.api.enums.Textures;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;

public class MTEHatchNuclearControl extends MTEHatch {

    public static final int MODE_TEMP_MIN = 0;
    public static final int MODE_TEMP_MAX = 1;
    public static final int MODE_TEMP_AVG = 2;
    public static final int MODE_FUEL_DURABILITY_MIN = 3;
    public static final int MODE_FUEL_DURABILITY_MAX = 4;
    public static final int MODE_FUEL_DURABILITY_AVG = 5;
    public static final int MODE_COMPONENT_DURABILITY_MIN = 6;
    public static final int MODE_COMPONENT_DURABILITY_MAX = 7;
    public static final int MODE_COMPONENT_DURABILITY_AVG = 8;
    public static final int MODE_COOLANT_LEVEL_MIN = 9;
    public static final int MODE_COOLANT_LEVEL_MAX = 10;
    public static final int MODE_COOLANT_LEVEL_AVG = 11;
    public static final int MODE_COUNT = 12;

    private int mMode = 0;
    private byte mOutputStrength = 0;

    public MTEHatchNuclearControl(int aID, String aName, String aNameRegional, int aTier) {
        super(
            aID,
            aName,
            aNameRegional,
            aTier,
            0,
            new String[] { "Emits Redstone Signals based on Nuclear Reactor conditions",
                "Right-click with screwdriver or use GUI to change mode",
                "Outputs redstone signal strictly from its front face" });
    }

    public MTEHatchNuclearControl(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, 0, aDescription, aTextures);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEHatchNuclearControl(mName, mTier, mDescriptionArray, mTextures);
    }

    public static String getModeName(int mode) {
        return switch (mode) {
            case MODE_TEMP_MIN -> "Temperature (Min)";
            case MODE_TEMP_MAX -> "Temperature (Max)";
            case MODE_TEMP_AVG -> "Temperature (Avg)";
            case MODE_FUEL_DURABILITY_MIN -> "Fuel Durability (Min)";
            case MODE_FUEL_DURABILITY_MAX -> "Fuel Durability (Max)";
            case MODE_FUEL_DURABILITY_AVG -> "Fuel Durability (Avg)";
            case MODE_COMPONENT_DURABILITY_MIN -> "Component Durability (Min)";
            case MODE_COMPONENT_DURABILITY_MAX -> "Component Durability (Max)";
            case MODE_COMPONENT_DURABILITY_AVG -> "Component Durability (Avg)";
            case MODE_COOLANT_LEVEL_MIN -> "Coolant Level (Min)";
            case MODE_COOLANT_LEVEL_MAX -> "Coolant Level (Max)";
            case MODE_COOLANT_LEVEL_AVG -> "Coolant Level (Avg)";
            default -> "Unknown";
        };
    }

    public int getMode() {
        return mMode;
    }

    public void setMode(int mode) {
        if (mode < 0) {
            mode = (mode % MODE_COUNT + MODE_COUNT) % MODE_COUNT;
        } else {
            mode = mode % MODE_COUNT;
        }
        this.mMode = mode;
        if (getBaseMetaTileEntity() != null) {
            getBaseMetaTileEntity().markDirty();
        }
    }

    public byte getOutputStrength() {
        return mOutputStrength;
    }

    public void setOutputRedstone(byte signal) {
        this.mOutputStrength = (byte) Math.max(0, Math.min(15, signal));
        IGregTechTileEntity te = getBaseMetaTileEntity();
        if (te != null) {
            ForgeDirection facing = te.getFrontFacing();
            for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
                te.setOutputRedstoneSignal(side, side == facing ? mOutputStrength : (byte) 0);
            }
        }
    }

    public void setOutputStrengthDirect(byte signal) {
        this.mOutputStrength = signal;
    }

    @Override
    public boolean doesEmptyContainers() {
        return false;
    }

    @Override
    public boolean doesFillContainers() {
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
        return false;
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection aSide,
        ItemStack aStack) {
        return false;
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection aSide,
        ItemStack aStack) {
        return false;
    }

    @Override
    public boolean allowGeneralRedstoneOutput() {
        return true;
    }

    @Override
    public boolean isFacingValid(ForgeDirection facing) {
        return true;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        ForgeDirection facing = aBaseMetaTileEntity.getFrontFacing();
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            aBaseMetaTileEntity.setOutputRedstoneSignal(side, side == facing ? mOutputStrength : (byte) 0);
        }
    }

    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer aPlayer, float aX, float aY, float aZ,
        ItemStack aTool) {
        setMode((mMode + 1) % MODE_COUNT);
        GTUtility.sendChatToPlayer(aPlayer, "Control Hatch: " + getModeName(mMode));
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        openGui(aPlayer);
        return true;
    }

    @Override
    protected boolean useMui2() {
        return false;
    }

    @Override
    public void addUIWidgets(ModularWindow.Builder builder, UIBuildContext buildContext) {
        builder.widget(
            new DrawableWidget().setDrawable(GTUITextures.PICTURE_SCREEN_BLACK)
                .setPos(7, 16)
                .setSize(162, 56))
            .widget(
                new TextWidget("Nuclear Control Hatch").setDefaultColor(Color.rgb(0, 255, 128))
                    .setPos(12, 20))
            .widget(
                new TextWidget().setStringSupplier(() -> "Mode: " + getModeName(mMode))
                    .setDefaultColor(Color.rgb(100, 200, 255))
                    .setPos(12, 33))
            .widget(
                new TextWidget().setStringSupplier(() -> String.format("Output Signal: %d / 15", mOutputStrength))
                    .setDefaultColor(Color.rgb(255, 80, 80))
                    .setPos(12, 46))
            .widget(
                new ButtonWidget().setOnClick((clickData, widget) -> setMode(mMode - 1))
                    .setBackground(() -> new IDrawable[] { GTUITextures.BUTTON_STANDARD })
                    .addTooltip("Previous Mode")
                    .setPos(12, 57)
                    .setSize(14, 12))
            .widget(
                new TextWidget(Text.localised("<")).setTextAlignment(Alignment.Center)
                    .setPos(12, 59)
                    .setSize(14, 10))
            .widget(
                new ButtonWidget().setOnClick((clickData, widget) -> setMode(mMode + 1))
                    .setBackground(() -> new IDrawable[] { GTUITextures.BUTTON_STANDARD })
                    .addTooltip("Next Mode")
                    .setPos(151, 57)
                    .setSize(14, 12))
            .widget(
                new TextWidget(Text.localised(">")).setTextAlignment(Alignment.Center)
                    .setPos(151, 59)
                    .setSize(14, 10))
            .widget(new FakeSyncWidget.IntegerSyncer(this::getMode, this::setMode))
            .widget(new FakeSyncWidget.ByteSyncer(this::getOutputStrength, this::setOutputStrengthDirect));
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("mMode", mMode);
        aNBT.setByte("mOutputStrength", mOutputStrength);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mMode = aNBT.getInteger("mMode");
        mOutputStrength = aNBT.getByte("mOutputStrength");
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        ITexture base = NuclearCasing.getCasingTexture();
        return new ITexture[] { base, TextureFactory.of(Textures.BlockIcons.OVERLAY_HATCH_SPLITTER_REDSTONE),
            TextureFactory.builder()
                .addIcon(Textures.BlockIcons.OVERLAY_HATCH_SPLITTER_REDSTONE_GLOW)
                .glow()
                .build() };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        ITexture base = NuclearCasing.getCasingTexture();
        return new ITexture[] { base, TextureFactory.of(Textures.BlockIcons.OVERLAY_HATCH_SPLITTER_REDSTONE) };
    }
}
