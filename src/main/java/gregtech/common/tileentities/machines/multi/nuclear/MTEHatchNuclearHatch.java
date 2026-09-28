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

public class MTEHatchNuclearHatch extends MTEHatch implements INuclearTile {

    public FluidStack mInputFluid;
    public FluidStack mOutputFluid;
    public final int mCapacity;

    public double mTemperature = 20.0;
    public double mHeatEU = 0.0;
    public int mFastFlux = 0;
    public int mThermalFlux = 0;
    public int mFastAbsorbed = 0;
    public int mThermalAbsorbed = 0;
    public int mReactorPipeTier = -1;
    public boolean mWasDry = false;

    public int getReactorPipeTier() {
        return mReactorPipeTier;
    }

    public void setReactorPipeTier(int aTier) {
        this.mReactorPipeTier = aTier;
    }

    public static int getRequiredFluidTier(String fluidName) {
        if (fluidName == null) return 999;
        String name = fluidName.toLowerCase();
        if (name.equals("water")) return 999; // Regular water is completely disallowed
        if (name.contains("coolant") && !name.contains("hot")) return NuclearSimulationEngine.PIPE_TIER_ELECTRUM;
        if (name.contains("distilledwater") && !name.contains("highpressure"))
            return NuclearSimulationEngine.PIPE_TIER_PLATINUM;
        if (name.contains("highpressuredistilledwater")) return NuclearSimulationEngine.PIPE_TIER_OSMIUM;
        if (name.contains("heavywater") && !name.contains("highpressure"))
            return NuclearSimulationEngine.PIPE_TIER_QUANTIUM;
        if (name.contains("highpressureheavywater")) return NuclearSimulationEngine.PIPE_TIER_FLUXED_ELECTRUM;
        return 999;
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
        if (mReactorPipeTier >= 0 && getRequiredFluidTier(name) > mReactorPipeTier) {
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
        if (mReactorPipeTier >= 0 && getRequiredFluidTier(name) > mReactorPipeTier) {
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

    // --- INuclearTile Implementation ---

    @Override
    public double getTemperature() {
        return mTemperature;
    }

    @Override
    public void setTemperature(double temp) {
        this.mTemperature = Math.max(20.0, temp);
    }

    @Override
    public void addHeat(double heatEU) {
        this.mHeatEU += heatEU;
        this.mTemperature += heatEU / NuclearSimulationEngine.EU_PER_DEGREE;
    }

    @Override
    public double getHeatTransferCoeff() {
        if (mInputFluid == null) return 0.05;
        String name = mInputFluid.getFluid()
            .getName()
            .toLowerCase();
        if (name.contains("water")) return 0.25;
        if (name.contains("coolant")) return 0.50;
        if (name.contains("sodium") || name.contains("lead")) return 0.70;
        return 0.15;
    }

    @Override
    public boolean isFuel() {
        if (mInputFluid == null) return false;
        String name = mInputFluid.getFluid()
            .getName()
            .toLowerCase();
        return name.contains("thorium") || name.contains("uranium") || name.contains("naquadah");
    }

    @Override
    public int generateNeutrons(double efficiency) {
        if (!isFuel() || mInputFluid == null || mInputFluid.amount <= 0) return 0;
        int produced = (int) Math.round(8 * efficiency);
        mInputFluid.amount -= Math.max(1, produced / 4);
        if (mInputFluid.amount <= 0) mInputFluid = null;
        return produced;
    }

    @Override
    public double getAbsorptionProbability(NeutronType type) {
        if (mInputFluid == null) return 0.01;
        String name = mInputFluid.getFluid()
            .getName()
            .toLowerCase();
        if (name.contains("heavywater")) return (type == NeutronType.THERMAL) ? 0.01 : 0.005;
        if (name.contains("distilledwater")) return (type == NeutronType.THERMAL) ? 0.10 : 0.05;
        if (name.contains("coolant")) return (type == NeutronType.THERMAL) ? 0.12 : 0.03;
        if (name.contains("boron")) return 0.95;
        if (isFuel()) return (type == NeutronType.THERMAL) ? 0.85 : 0.25;
        return 0.05;
    }

    @Override
    public double getScatteringProbability(NeutronType type) {
        if (mInputFluid == null) return 0.02;
        String name = mInputFluid.getFluid()
            .getName()
            .toLowerCase();
        if (name.contains("heavywater")) return 0.85;
        if (name.contains("distilledwater")) return 0.70;
        if (name.contains("coolant")) return 0.45;
        if (name.contains("sodium")) return 0.20;
        return 0.10;
    }

    @Override
    public double getModerationProbability() {
        if (mInputFluid == null) return 0.05;
        String name = mInputFluid.getFluid()
            .getName()
            .toLowerCase();
        if (name.contains("heavywater")) return 0.90;
        if (name.contains("distilledwater")) return 0.80;
        if (name.contains("coolant")) return 0.40;
        if (name.contains("sodium")) return 0.05; // fast reactor coolant
        return 0.20;
    }

    @Override
    public void onNeutronAbsorbed(NeutronType type, int count) {
        if (type == NeutronType.FAST) mFastAbsorbed += count;
        else mThermalAbsorbed += count;

        // Neutron capture transmutation on fast neutron absorption
        if (type == NeutronType.FAST && mInputFluid != null && mInputFluid.amount > 0) {
            String name = mInputFluid.getFluid()
                .getName()
                .toLowerCase();
            if (name.contains("distilledwater")) {
                if (getRandomNumber(100) < Math.min(100, count * 5)) {
                    mInputFluid.amount -= 1;
                    if (mInputFluid.amount <= 0) mInputFluid = null;
                    addOutputFluid("deuterium", 1);
                    markTileDirty();
                }
            } else if (name.contains("heavywater")) {
                if (getRandomNumber(100) < Math.min(100, count * 5)) {
                    mInputFluid.amount -= 1;
                    if (mInputFluid.amount <= 0) mInputFluid = null;
                    addOutputFluid("tritium", 1);
                    markTileDirty();
                }
            }
        }
    }

    @Override
    public void onNeutronScattered(NeutronType type, int count) {}

    @Override
    public void addNeutronFlux(NeutronType type, int count) {
        if (type == NeutronType.FAST) mFastFlux += count;
        else mThermalFlux += count;
    }

    @Override
    public void nuclearTick(double efficiency) {
        mFastFlux = 0;
        mThermalFlux = 0;
        mFastAbsorbed = 0;
        mThermalAbsorbed = 0;

        if (mInputFluid == null || mInputFluid.amount <= 0) return;

        String name = mInputFluid.getFluid()
            .getName()
            .toLowerCase();
        if (name.equals("water")) return; // Regular water is completely disallowed

        int reqTier = getRequiredFluidTier(name);
        if (mReactorPipeTier >= 0 && mReactorPipeTier < reqTier) {
            return;
        }

        double boilingPoint = 100.0;
        double heatPerMB = 160.0;
        int steamRatio = 160;
        String outputFluidName = "steam";

        if (name.contains("highpressureheavywater") && !name.contains("steam")) {
            boilingPoint = NuclearSimulationEngine.hpWaterBoilingPoint;
            heatPerMB = 640.0;
            steamRatio = 160;
            outputFluidName = "fluid.highpressureheavywatersteam";
        } else if (name.contains("heavywater") && !name.contains("steam")) {
            boilingPoint = 100.0;
            heatPerMB = 160.0;
            steamRatio = 160;
            outputFluidName = "fluid.heavywatersteam";
        } else if (name.contains("highpressuredistilledwater") && !name.contains("steam")) {
            boilingPoint = NuclearSimulationEngine.hpWaterBoilingPoint;
            heatPerMB = 320.0;
            steamRatio = 160;
            outputFluidName = "ic2superheatedsteam";
        } else if (name.contains("distilledwater")) {
            boilingPoint = 100.0;
            heatPerMB = 160.0;
            steamRatio = 160;
            outputFluidName = "steam";
        } else if (name.contains("coolant") && !name.contains("hot")) {
            boilingPoint = 100.0;
            heatPerMB = 100.0;
            steamRatio = 1;
            outputFluidName = "ic2hotcoolant";
        } else {
            return;
        }

        // Coolant boiling phase transition
        if (mTemperature > boilingPoint) {
            double heatAvailable = (mTemperature - boilingPoint) * NuclearSimulationEngine.EU_PER_DEGREE;
            int maxFluidByHeat = (int) (heatAvailable / heatPerMB);
            int fluidToBoil = Math.min(mInputFluid.amount, maxFluidByHeat);

            // Cap boiling rate by hatch tier
            int maxRate = 100 * (1 << mTier);
            fluidToBoil = Math.min(fluidToBoil, maxRate);

            if (fluidToBoil > 0) {
                int steamAmount = fluidToBoil * steamRatio;
                int space = mCapacity - (mOutputFluid != null ? mOutputFluid.amount : 0);
                if (steamAmount > space) {
                    fluidToBoil = space / steamRatio;
                    steamAmount = fluidToBoil * steamRatio;
                }

                if (fluidToBoil > 0 && steamAmount > 0) {
                    mInputFluid.amount -= fluidToBoil;
                    if (mInputFluid.amount <= 0) mInputFluid = null;

                    addOutputFluid(outputFluidName, steamAmount);
                    double heatConsumed = fluidToBoil * heatPerMB;
                    mTemperature -= (heatConsumed / NuclearSimulationEngine.EU_PER_DEGREE);
                    markTileDirty();
                }
            }
        }
    }

    private int getRandomNumber(int max) {
        if (getBaseMetaTileEntity() != null) {
            return getBaseMetaTileEntity().getRandomNumber(max);
        }
        return (int) (Math.random() * max);
    }

    private void markTileDirty() {
        if (getBaseMetaTileEntity() != null) {
            getBaseMetaTileEntity().markDirty();
        }
    }

    private void addOutputFluid(String fluidName, int amount) {
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
