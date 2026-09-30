package gregtech.common.tileentities.machines.multi.nuclear;

import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_PIPE_COLORS;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_PIPE_IN;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.oredict.OreDictionary;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.gtnewhorizons.modularui.api.math.Color;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.common.widget.DrawableWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;

import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.Textures;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.objects.ItemData;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.common.gui.modularui.hatch.MTEHatchNuclearControlRodGui;

/**
 * Nuclear Control Rod Hatch.
 * Placed on the top layer of the Nuclear Reactor alongside nuclear buses and hatches.
 * Holds exactly 1 Long Rod of Silver, Boron, Cadmium, Indium, or Hafnium.
 * Incoming redstone signal (0..15) determines how far the control rod is inserted (0% to 100%).
 * Absorbs neutrons and converts them into heat in the reactor core.
 */
public class MTEHatchNuclearControlRod extends MTEHatch {

    public static final int SLOT_ROD = 0;

    public enum ControlRodType {

        NONE("None", 0.0, 0.0, 0.02),
        SILVER("Silver", 0.50, 0.20, 0.20),
        BORON("Boron", 0.70, 0.35, 0.08),
        CADMIUM("Cadmium", 0.85, 0.50, 0.12),
        INDIUM("Indium", 0.95, 0.65, 0.14),
        HAFNIUM("Hafnium", 0.99, 0.80, 0.16);

        public final String displayName;
        public final double maxThermalAbsorption;
        public final double maxFastAbsorption;
        public final double heatTransferCoeff;

        ControlRodType(String displayName, double maxThermalAbsorption, double maxFastAbsorption,
            double heatTransferCoeff) {
            this.displayName = displayName;
            this.maxThermalAbsorption = maxThermalAbsorption;
            this.maxFastAbsorption = maxFastAbsorption;
            this.heatTransferCoeff = heatTransferCoeff;
        }
    }

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

    public MTEHatchNuclearControlRod(int aID, String aName, String aNameRegional, int aTier) {
        super(
            aID,
            aName,
            aNameRegional,
            aTier,
            1,
            new String[] { "Nuclear Core Control Rod Hatch",
                "Holds Long Rods of Silver, Boron, Cadmium, Indium, or Hafnium",
                "Controlled by external Redstone signal (0..15)",
                "0 signal = 0% insertion (retracted, zero absorption)",
                "15 signal = 100% insertion (full absorption)" });
    }

    public MTEHatchNuclearControlRod(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, 1, aDescription, aTextures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEHatchNuclearControlRod(mName, mTier, mDescriptionArray, mTextures);
    }

    public static ControlRodType getRodType(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return ControlRodType.NONE;

        try {
            ItemData itemData = GTOreDictUnificator.getItemData(stack);
            if (itemData != null && itemData.mPrefix == OrePrefixes.stickLong && itemData.mMaterial != null) {
                Materials mat = itemData.mMaterial.mMaterial;
                if (mat == Materials.Silver) return ControlRodType.SILVER;
                if (mat == Materials.Boron) return ControlRodType.BORON;
                if (mat == Materials.Cadmium) return ControlRodType.CADMIUM;
                if (mat == Materials.Indium) return ControlRodType.INDIUM;
                if (mat != null && "Hafnium".equalsIgnoreCase(mat.mName)) return ControlRodType.HAFNIUM;
            }
        } catch (Throwable ignored) {}

        try {
            int[] oreIDs = OreDictionary.getOreIDs(stack);
            for (int id : oreIDs) {
                String name = OreDictionary.getOreName(id);
                if ("stickLongSilver".equals(name)) return ControlRodType.SILVER;
                if ("stickLongBoron".equals(name)) return ControlRodType.BORON;
                if ("stickLongCadmium".equals(name)) return ControlRodType.CADMIUM;
                if ("stickLongIndium".equals(name)) return ControlRodType.INDIUM;
                if ("stickLongHafnium".equals(name)) return ControlRodType.HAFNIUM;
            }
        } catch (Throwable ignored) {}

        try {
            String unloc = stack.getUnlocalizedName();
            if (unloc != null) {
                unloc = unloc.toLowerCase();
                if (unloc.contains("sticklong") || unloc.contains("rodlong") || unloc.contains("longrod")) {
                    if (unloc.contains("hafnium")) return ControlRodType.HAFNIUM;
                    if (unloc.contains("indium")) return ControlRodType.INDIUM;
                    if (unloc.contains("cadmium")) return ControlRodType.CADMIUM;
                    if (unloc.contains("boron")) return ControlRodType.BORON;
                    if (unloc.contains("silver")) return ControlRodType.SILVER;
                }
            }
        } catch (Throwable ignored) {}

        return ControlRodType.NONE;
    }

    public static boolean isControlRod(ItemStack stack) {
        return getRodType(stack) != ControlRodType.NONE;
    }

    public boolean mScram = false;

    public void setScram(boolean scram) {
        this.mScram = scram;
        markTileDirty();
    }

    public double getAmbientTemperature() {
        if (getBaseMetaTileEntity() != null && getBaseMetaTileEntity().getWorld() != null) {
            return MTENuclearReactor.calculateAmbientTemperature(
                getBaseMetaTileEntity().getWorld(),
                getBaseMetaTileEntity().getXCoord(),
                getBaseMetaTileEntity().getYCoord(),
                getBaseMetaTileEntity().getZCoord());
        }
        return NuclearSimulationEngine.DEFAULT_AMBIENT_TEMP;
    }

    public byte getRedstoneSignal() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null) return 0;
        return base.getStrongestRedstone();
    }

    public double getInsertionRatio() {
        if (mScram) return 1.0;
        byte rs = getRedstoneSignal();
        return Math.max(0.0, Math.min(1.0, (double) rs / 15.0));
    }

    public int getInsertionPercent() {
        return (int) Math.round(getInsertionRatio() * 100.0);
    }

    public double getAbsorptionProbability(NeutronType type) {
        ControlRodType rod = getRodType(mInventory[SLOT_ROD]);
        if (rod == ControlRodType.NONE) return 0.01;
        double ratio = getInsertionRatio();
        double max = (type == NeutronType.THERMAL) ? rod.maxThermalAbsorption : rod.maxFastAbsorption;
        return Math.max(0.01, ratio * max);
    }

    public double getScatteringProbability(NeutronType type) {
        ControlRodType rod = getRodType(mInventory[SLOT_ROD]);
        if (rod == ControlRodType.NONE) return 0.02;
        double ratio = getInsertionRatio();
        return Math.max(0.01, 0.05 * (1.0 - ratio));
    }

    public double getHeatTransferCoeff() {
        ControlRodType rod = getRodType(mInventory[SLOT_ROD]);
        if (rod == ControlRodType.NONE) return 0.02;
        return rod.heatTransferCoeff;
    }

    @Override
    public int getInventoryStackLimit() {
        return 1;
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
        return aIndex == SLOT_ROD;
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection aSide,
        ItemStack aStack) {
        return aIndex == SLOT_ROD && isControlRod(aStack) && (mInventory[SLOT_ROD] == null);
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection aSide,
        ItemStack aStack) {
        return aIndex == SLOT_ROD;
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setDouble("mTemperature", mTemperature);
        aNBT.setDouble("mHeatEU", mHeatEU);
        aNBT.setBoolean("mScram", mScram);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        if (aNBT.hasKey("mTemperature")) {
            mTemperature = aNBT.getDouble("mTemperature");
        } else {
            mTemperature = getAmbientTemperature();
        }
        mHeatEU = aNBT.getDouble("mHeatEU");
        mScram = aNBT.getBoolean("mScram");
    }

    public void markTileDirty() {
        if (getBaseMetaTileEntity() != null) {
            getBaseMetaTileEntity().markDirty();
        }
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        byte color = getBaseMetaTileEntity().getColorization();
        ITexture coloredOverlay = TextureFactory.of(OVERLAY_PIPE_COLORS[color + 1]);
        return new ITexture[] { aBaseTexture, TextureFactory.of(OVERLAY_PIPE_IN), coloredOverlay,
            TextureFactory.of(Textures.BlockIcons.OVERLAY_HATCH_HEAT_SENSOR_GLOW) };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        byte color = getBaseMetaTileEntity().getColorization();
        ITexture coloredOverlay = TextureFactory.of(OVERLAY_PIPE_COLORS[color + 1]);
        return new ITexture[] { aBaseTexture, TextureFactory.of(OVERLAY_PIPE_IN), coloredOverlay,
            TextureFactory.of(Textures.BlockIcons.OVERLAY_HATCH_HEAT_SENSOR) };
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
    public ModularPanel buildUI(com.cleanroommc.modularui.factory.PosGuiData data, PanelSyncManager syncManager,
        UISettings uiSettings) {
        return new MTEHatchNuclearControlRodGui(this).build(data, syncManager, uiSettings);
    }

    @Override
    public void addUIWidgets(ModularWindow.Builder builder, UIBuildContext buildContext) {
        builder.widget(
            new DrawableWidget().setDrawable(GTUITextures.PICTURE_SCREEN_BLACK)
                .setPos(7, 16)
                .setSize(96, 56))
            .widget(
                new TextWidget("Control Rod Hatch").setDefaultColor(Color.rgb(180, 100, 255))
                    .setPos(10, 20))
            .widget(
                new TextWidget().setStringSupplier(() -> String.format("Temp: %.1f °C", mTemperature))
                    .setDefaultColor(Color.rgb(255, 200, 0))
                    .setPos(10, 31))
            .widget(
                new TextWidget()
                    .setStringSupplier(
                        () -> String.format("Insert: %d%% (RS: %d)", getInsertionPercent(), getRedstoneSignal()))
                    .setDefaultColor(Color.rgb(100, 255, 200))
                    .setPos(10, 42))
            .widget(
                new TextWidget().setStringSupplier(() -> "Rod: " + getRodType(mInventory[SLOT_ROD]).displayName)
                    .setDefaultColor(Color.rgb(200, 200, 255))
                    .setPos(10, 53))
            .widget(
                new TextWidget("Rod").setDefaultColor(0xFFFFFFFF)
                    .setPos(125, 16))
            .widget(
                new SlotWidget(inventoryHandler, SLOT_ROD)
                    .setBackground(getGUITextureSet().getItemSlot(), GTUITextures.OVERLAY_SLOT_IN)
                    .setPos(122, 28));
    }
}
