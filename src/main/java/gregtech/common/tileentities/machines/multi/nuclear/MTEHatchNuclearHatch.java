package gregtech.common.tileentities.machines.multi.nuclear;

import static gregtech.api.enums.Textures.BlockIcons.FLUID_IN_SIGN;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_PIPE_COLORS;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_PIPE_IN;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
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
 * Holds input coolant/fuel and output steam/byproducts.
 * All nuclear physics, boiling, and transmutation logic is processed by MTENuclearReactor.
 */
public class MTEHatchNuclearHatch extends MTEHatch {

    public FluidStack mInputFluid;
    public FluidStack mOutputFluid;
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
    public int mLastProducedAmount = 0;
    public String mLastProducedFluidName = "";
    public int mReactorPipeTier = -1;
    public boolean mWasDry = false;

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
            new String[] { "Tiered Nuclear Fluid Hatch (" + GTValues.VN[aTier] + ")",
                "Holds both Input Coolant / Fuel AND Output Steam / Byproduct",
                "Supports BOTH Fluid Input and Fluid Output from the same block!",
                "Capacity: " + (8000 * (1 << aTier)) + " L per tank",
                "Coolant boiling and transmutation under neutron flux", "Item Pipe Casing determines allowed coolants",
                "Inserting water into a dry running reactor will cause an EXPLOSION!" });
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
        return true;
    }

    // --- Dual Fluid Input/Output from Single Block ---

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
        if (mOutputFluid == null || mOutputFluid.amount <= 0 || maxDrain <= 0) return null;
        int toDrain = Math.min(maxDrain, mOutputFluid.amount);
        FluidStack drained = new FluidStack(mOutputFluid.getFluid(), toDrain);
        if (doDrain) {
            mOutputFluid.amount -= toDrain;
            if (mOutputFluid.amount <= 0) mOutputFluid = null;
            markTileDirty();
        }
        return drained;
    }

    @Override
    public FluidStack drain(ForgeDirection side, FluidStack resource, boolean doDrain) {
        if (resource == null || mOutputFluid == null || !mOutputFluid.isFluidEqual(resource)) return null;
        return drain(side, resource.amount, doDrain);
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
        if (fluid == null || mOutputFluid == null) return false;
        return mOutputFluid.getFluid() == fluid && mOutputFluid.amount > 0;
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection side) {
        return new FluidTankInfo[] { new FluidTankInfo(mInputFluid, mCapacity),
            new FluidTankInfo(mOutputFluid, mCapacity) };
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
        if (mInputFluid != null) aNBT.setTag("mInputFluid", mInputFluid.writeToNBT(new NBTTagCompound()));
        if (mOutputFluid != null) aNBT.setTag("mOutputFluid", mOutputFluid.writeToNBT(new NBTTagCompound()));
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mTemperature = aNBT.getDouble("mTemperature");
        if (mTemperature < 20.0) mTemperature = 20.0;
        mHeatEU = aNBT.getDouble("mHeatEU");
        if (aNBT.hasKey("mReactorPipeTier")) {
            mReactorPipeTier = aNBT.getInteger("mReactorPipeTier");
        }
        if (aNBT.hasKey("mWasDry")) {
            mWasDry = aNBT.getBoolean("mWasDry");
        }
        if (aNBT.hasKey("mInputFluid")) {
            mInputFluid = FluidStack.loadFluidStackFromNBT(aNBT.getCompoundTag("mInputFluid"));
        }
        if (aNBT.hasKey("mOutputFluid")) {
            mOutputFluid = FluidStack.loadFluidStackFromNBT(aNBT.getCompoundTag("mOutputFluid"));
        }
    }

    public void markTileDirty() {
        if (getBaseMetaTileEntity() != null) {
            getBaseMetaTileEntity().markDirty();
        }
    }

    public void addOutputFluid(String fluidName, int amount) {
        if (amount <= 0) return;
        Fluid fluid = FluidRegistry.getFluid(fluidName);
        if (fluid == null && fluidName.startsWith("fluid.")) {
            fluid = FluidRegistry.getFluid(fluidName.substring(6));
        }
        if (fluid == null && !fluidName.startsWith("fluid.")) {
            fluid = FluidRegistry.getFluid("fluid." + fluidName);
        }
        if (fluid == null) fluid = FluidRegistry.getFluid("steam");
        if (fluid == null) return;

        if (mOutputFluid == null) {
            mOutputFluid = new FluidStack(fluid, Math.min(amount, mCapacity));
        } else if (mOutputFluid.getFluid() == fluid) {
            mOutputFluid.amount = Math.min(mCapacity, mOutputFluid.amount + amount);
        }
    }

    @Override
    public void addUIWidgets(ModularWindow.Builder builder, UIBuildContext buildContext) {
        builder.widget(
            new DrawableWidget().setDrawable(GTUITextures.PICTURE_SCREEN_BLACK)
                .setPos(7, 16)
                .setSize(95, 56))
            .widget(
                new TextWidget("Nuclear Hatch (" + GTValues.VN[mTier] + ")").setDefaultColor(Color.rgb(0, 255, 200))
                    .setPos(10, 20))
            .widget(new TextWidget().setStringSupplier(() -> {
                if (mInputFluid != null && mReactorPipeTier >= 0
                    && getRequiredFluidTier(
                        mInputFluid.getFluid()
                            .getName())
                        > mReactorPipeTier) {
                    return "ERR: TIER TOO LOW";
                }
                return String.format("Temp: %.1f °C", mTemperature);
            })
                .setDefaultColor(Color.rgb(255, 200, 0))
                .setPos(10, 32))
            .widget(
                new TextWidget()
                    .setStringSupplier(() -> String.format("In: %dL", mInputFluid != null ? mInputFluid.amount : 0))
                    .setDefaultColor(Color.rgb(100, 220, 255))
                    .setPos(10, 44))
            .widget(
                new TextWidget()
                    .setStringSupplier(() -> String.format("Out: %dL", mOutputFluid != null ? mOutputFluid.amount : 0))
                    .setDefaultColor(Color.rgb(255, 120, 100))
                    .setPos(10, 56))
            .widget(
                new TextWidget("Coolant In").setDefaultColor(0xFFFFFFFF)
                    .setPos(110, 16))
            .widget(new FluidSlotWidget(new FluidStackTank(() -> mInputFluid, f -> {
                mInputFluid = f;
                markDirty();
            }, () -> mCapacity)).setPos(115, 28))
            .widget(
                new TextWidget("Hot Out").setDefaultColor(0xFFFFFFFF)
                    .setPos(148, 16))
            .widget(new FluidSlotWidget(new FluidStackTank(() -> mOutputFluid, f -> {
                mOutputFluid = f;
                markDirty();
            }, () -> mCapacity)).setPos(150, 28));
    }
}
