package gregtech.common.tileentities.machines.multi.nuclear;

import static gregtech.api.enums.Textures.BlockIcons.FLUID_IN_SIGN;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_PIPE_COLORS;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_PIPE_IN;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;

import com.cleanroommc.modularui.utils.fluid.FluidStackTank;
import com.gtnewhorizons.modularui.api.math.Color;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.common.widget.DrawableWidget;
import com.gtnewhorizons.modularui.common.widget.FluidSlotWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;

import gregtech.api.enums.GTValues;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.render.TextureFactory;

/**
 * Dumb fluid container hatch for the modular nuclear reactor.
 * Holds input coolant or liquid nuclear fuel.
 * All nuclear physics, boiling, and transmutation logic is processed by MTENuclearReactor.
 */
public class MTEHatchNuclearHatch extends MTEHatch {

    public FluidStack mInputFluid;
    public final int mCapacity;

    public double mTemperature = NuclearSimulationEngine.DEFAULT_AMBIENT_TEMP;
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
    public int mLastProducedAmount = 0;
    public String mLastProducedFluidName = "";
    public int mReactorPipeTier = -1;
    public boolean mWasDry = false;
    public boolean mUsedForCooling = false;

    public int getReactorPipeTier() {
        return mReactorPipeTier;
    }

    public void setReactorPipeTier(int aTier) {
        this.mReactorPipeTier = aTier;
    }

    public static int getRequiredFluidTier(String fluidName) {
        return NuclearSimulationEngine.getRequiredFluidTier(fluidName);
    }

    public MTEHatchNuclearHatch(int aID, String aName, String aNameRegional, int aTier) {
        super(
            aID,
            aName,
            aNameRegional,
            aTier,
            0,
            new String[] { "Tiered nuclear fluid hatch (" + GTValues.VN[aTier] + ")",
                "Holds input coolant or liquid nuclear fuel", "Input-only core fluid hatch",
                "All outputs (steam, hot coolant, byproducts, spent fuel) eject to reactor output hatches",
                "Capacity: " + (8000 * (1 << aTier)) + " L", "Coolant boiling and transmutation under neutron flux",
                "Item pipe casing determines allowed coolants",
                "Inserting water into a dry running reactor will cause an explosion!" });
        this.mCapacity = 8000 * (1 << aTier);
    }

    public MTEHatchNuclearHatch(String aName, int aTier, int aCapacity, String[] aDescription,
        ITexture[][][] aTextures) {
        super(aName, aTier, 0, aDescription, aTextures);
        this.mCapacity = aCapacity;
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEHatchNuclearHatch(mName, mTier, mCapacity, mDescriptionArray, mTextures);
    }

    @Override
    public int getCapacity() {
        return mCapacity;
    }

    @Override
    public int getRealCapacity() {
        return mCapacity;
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
        return true;
    }

    @Override
    public boolean canTankBeEmptied() {
        return false;
    }

    // --- Input-Only Fluid Hatch ---

    @Override
    public int fill(ForgeDirection side, FluidStack resource, boolean doFill) {
        if (resource == null || resource.amount <= 0) return 0;
        String name = resource.getFluid()
            .getName()
            .toLowerCase();
        if (name.equals("water")) return 0; // Regular water is completely disallowed
        // Allow high-pressure fluids to enter so reactor detects them and explodes if casing is insufficient
        if (mReactorPipeTier >= 0 && !name.contains("highpressure") && getRequiredFluidTier(name) > mReactorPipeTier) {
            return 0;
        }

        if (mInputFluid == null) {
            int toFill = Math.min(resource.amount, mCapacity);
            if (doFill) {
                mInputFluid = new FluidStack(resource.getFluid(), toFill);
                markTileDirty();
            }
            return toFill;
        } else if (mInputFluid.isFluidEqual(resource)) {
            int space = mCapacity - mInputFluid.amount;
            int toFill = Math.min(resource.amount, space);
            if (doFill && toFill > 0) {
                mInputFluid.amount += toFill;
                markTileDirty();
            }
            return toFill;
        }
        return 0;
    }

    @Override
    public FluidStack drain(ForgeDirection side, int maxDrain, boolean doDrain) {
        return null;
    }

    @Override
    public FluidStack drain(ForgeDirection side, FluidStack resource, boolean doDrain) {
        return null;
    }

    @Override
    public boolean canFill(ForgeDirection side, Fluid fluid) {
        if (fluid == null) return false;
        String name = fluid.getName()
            .toLowerCase();
        if (name.equals("water")) return false; // Regular water is completely disallowed
        if (mReactorPipeTier >= 0 && !name.contains("highpressure") && getRequiredFluidTier(name) > mReactorPipeTier) {
            return false;
        }
        return mInputFluid == null || (mInputFluid.getFluid() == fluid && mInputFluid.amount < mCapacity);
    }

    @Override
    public boolean canDrain(ForgeDirection side, Fluid fluid) {
        return false;
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection side) {
        return new FluidTankInfo[] { new FluidTankInfo(mInputFluid, mCapacity) };
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        byte color = getBaseMetaTileEntity().getColorization();
        ITexture coloredOverlay = TextureFactory.of(OVERLAY_PIPE_COLORS[color + 1]);
        return new ITexture[] { aBaseTexture, TextureFactory.of(OVERLAY_PIPE_IN), coloredOverlay,
            TextureFactory.of(FLUID_IN_SIGN) };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        byte color = getBaseMetaTileEntity().getColorization();
        ITexture coloredOverlay = TextureFactory.of(OVERLAY_PIPE_COLORS[color + 1]);
        return new ITexture[] { aBaseTexture, TextureFactory.of(OVERLAY_PIPE_IN), coloredOverlay,
            TextureFactory.of(FLUID_IN_SIGN) };
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setDouble("mTemperature", mTemperature);
        aNBT.setDouble("mHeatEU", mHeatEU);
        aNBT.setInteger("mReactorPipeTier", mReactorPipeTier);
        aNBT.setBoolean("mWasDry", mWasDry);
        aNBT.setBoolean("mUsedForCooling", mUsedForCooling);
        if (mInputFluid != null) aNBT.setTag("mInputFluid", mInputFluid.writeToNBT(new NBTTagCompound()));
    }

    public boolean hasWaterCoolant() {
        if (mInputFluid != null && mInputFluid.getFluid() != null) {
            String name = mInputFluid.getFluid()
                .getName()
                .toLowerCase();
            return name.contains("water") && !name.contains("steam");
        }
        return false;
    }

    public double getAmbientTemperature() {
        double ambient = NuclearSimulationEngine.DEFAULT_AMBIENT_TEMP;
        if (getBaseMetaTileEntity() != null && getBaseMetaTileEntity().getWorld() != null) {
            ambient = MTENuclearReactor.calculateAmbientTemperature(
                getBaseMetaTileEntity().getWorld(),
                getBaseMetaTileEntity().getXCoord(),
                getBaseMetaTileEntity().getYCoord(),
                getBaseMetaTileEntity().getZCoord());
        }
        if (hasWaterCoolant()) {
            return Math.max(0.0, ambient);
        }
        return ambient;
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
        if (aNBT.hasKey("mReactorPipeTier")) {
            mReactorPipeTier = aNBT.getInteger("mReactorPipeTier");
        }
        if (aNBT.hasKey("mWasDry")) {
            mWasDry = aNBT.getBoolean("mWasDry");
        }
        if (aNBT.hasKey("mUsedForCooling")) {
            mUsedForCooling = aNBT.getBoolean("mUsedForCooling");
        }
        if (aNBT.hasKey("mInputFluid")) {
            mInputFluid = FluidStack.loadFluidStackFromNBT(aNBT.getCompoundTag("mInputFluid"));
        }
    }

    public void markTileDirty() {
        if (getBaseMetaTileEntity() != null) {
            getBaseMetaTileEntity().markDirty();
        }
    }

    public boolean isFluidInputAllowed(FluidStack aFluid) {
        if (aFluid == null || aFluid.getFluid() == null) return false;
        String name = aFluid.getFluid()
            .getName()
            .toLowerCase();
        if (name.equals("water")) return false;
        if (mReactorPipeTier >= 0 && !name.contains("highpressure") && getRequiredFluidTier(name) > mReactorPipeTier) {
            return false;
        }
        return true;
    }

    public com.cleanroommc.modularui.utils.fluid.FluidStackTank getInputTank() {
        return new FluidStackTank(() -> mInputFluid, f -> {
            mInputFluid = f;
            markTileDirty();
        }, () -> mCapacity);
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
        return new gregtech.common.gui.modularui.hatch.MTEHatchNuclearHatchGui(this)
            .build(data, syncManager, uiSettings);
    }

    @Override
    public void addUIWidgets(ModularWindow.Builder builder, UIBuildContext buildContext) {
        builder.widget(
            new DrawableWidget().setDrawable(GTUITextures.PICTURE_SCREEN_BLACK)
                .setPos(7, 16)
                .setSize(96, 56))
            .widget(
                new TextWidget("Stats").setDefaultColor(Color.rgb(0, 255, 200))
                    .setPos(10, 20))
            .widget(new TextWidget().setStringSupplier(() -> {
                if (mInputFluid != null && mReactorPipeTier >= 0
                    && getRequiredFluidTier(
                        mInputFluid.getFluid()
                            .getName())
                        > mReactorPipeTier) {
                    return "ERR: TIER";
                }
                return String.format("%.0f °C", mTemperature);
            })
                .setDefaultColor(Color.rgb(255, 200, 0))
                .setPos(10, 31))
            .widget(
                new TextWidget().setStringSupplier(() -> String.format("F: %d", mLastFastFlux))
                    .setDefaultColor(Color.rgb(100, 220, 255))
                    .setPos(10, 42))
            .widget(
                new TextWidget().setStringSupplier(() -> String.format("T: %d", mLastThermalFlux))
                    .setDefaultColor(Color.rgb(150, 180, 255))
                    .setPos(10, 53))
            // Coolant In Tank
            .widget(
                new DrawableWidget().setDrawable(GTUITextures.PICTURE_SCREEN_BLACK)
                    .setPos(107, 16)
                    .setSize(48, 56))
            .widget(
                new TextWidget("In").setDefaultColor(0xFFFFFFFF)
                    .setPos(109, 19))
            .widget(
                new TextWidget().setStringSupplier(() -> (mInputFluid != null ? mInputFluid.amount : 0) + "L")
                    .setDefaultColor(Color.rgb(180, 180, 180))
                    .setPos(109, 30))
            .widget(new FluidSlotWidget(getInputTank()).setPos(121, 42));
    }
}
