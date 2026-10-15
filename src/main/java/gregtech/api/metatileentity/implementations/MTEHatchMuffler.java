package gregtech.api.metatileentity.implementations;

import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_MUFFLER;
import static gregtech.api.objects.XSTR.XSTR_INSTANCE;

import java.lang.ref.WeakReference;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.GTMod;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ParticleFX;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTSplit;
import gregtech.api.util.WorldSpawnedEventBuilder;
import gregtech.api.util.tooltip.TooltipHelper;
import gregtech.common.pollution.AirDuctNetwork;
import gregtech.common.pollution.IPollutionScrubber;
import gregtech.common.pollution.Pollution;
import gregtech.common.tileentities.machines.multi.MTEAirFilterBase;

@SuppressWarnings("unused") // Unused API is expected within scope
@IMetaTileEntity.SkipGenerateDescription
@IMetaTileEntity.SkipGenerateName
public class MTEHatchMuffler extends MTEHatch {

    public MTEHatchMuffler(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier, 0, "");
    }

    public MTEHatchMuffler(int aID, String aName, String aNameRegional, int aTier, int aInvSlotCount,
        String[] aDescription, ITexture... aTextures) {
        super(aID, aName, aNameRegional, aTier, aInvSlotCount, aDescription, aTextures);
    }

    public MTEHatchMuffler(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        this(aName, aTier, 0, aDescription, aTextures);
    }

    public MTEHatchMuffler(String aName, int aTier, int aInvSlotCount, String[] aDescription,
        ITexture[][][] aTextures) {
        super(aName, aTier, aInvSlotCount, aDescription, aTextures);
    }

    @Override
    public String getLocalName() {
        if (!hasOwnLocalName()) return super.getLocalName();
        return StatCollector.translateToLocalFormatted("gt.blockmachines.hatch.muffler.name", GTValues.VN[mTier]);
    }

    @Override
    public String[] getDescription() {
        String[] description = GTSplit
            .splitLocalizedFormatted("gt.blockmachines.muffler.desc", String.valueOf(calculatePollutionReduction(100)));
        return mTier > 1 ? TooltipHelper.pollutionDisabledTooltip(description) : description;
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture, TextureFactory.of(OVERLAY_MUFFLER) };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture, TextureFactory.of(OVERLAY_MUFFLER) };
    }

    @Override
    public boolean isValidSlot(int aIndex) {
        return false;
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        return false;
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        return false;
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEHatchMuffler(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public boolean needsClientTick() {
        return true;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity.isClientSide() && this.getBaseMetaTileEntity()
            .isActive()) {
            pollutionParticles(
                this.getBaseMetaTileEntity()
                    .getWorld(),
                ParticleFX.LARGE_SMOKE.toString());
        }
        if (aBaseMetaTileEntity.isServerSide() && aTick % 100 == 0) {
            aBaseMetaTileEntity.tryDisableTicking();
        }
    }

    @Override
    public boolean isFacingValid(ForgeDirection facing) {
        return true;
    }

    @SideOnly(Side.CLIENT)
    public void pollutionParticles(World aWorld, String name) {
        boolean chk1, chk2, chk3;
        float ran1 = XSTR_INSTANCE.nextFloat(), ran2, ran3;
        chk1 = ran1 * 100 < calculatePollutionReduction(100);
        if (Pollution.getPollution(getBaseMetaTileEntity()) >= GTMod.proxy.mPollutionSmogLimit) {
            ran2 = XSTR_INSTANCE.nextFloat();
            ran3 = XSTR_INSTANCE.nextFloat();
            chk2 = ran2 * 100 < calculatePollutionReduction(100);
            chk3 = ran3 * 100 < calculatePollutionReduction(100);
            if (!(chk1 || chk2 || chk3)) return;
        } else {
            if (!chk1) return;
            ran2 = ran3 = 0.0F;
            chk2 = chk3 = false;
        }

        final IGregTechTileEntity aMuffler = this.getBaseMetaTileEntity();
        final ForgeDirection aDir = aMuffler.getFrontFacing();
        final float xPos = aDir.offsetX * 0.76F + aMuffler.getXCoord() + 0.25F;
        final float yPos = aDir.offsetY * 0.76F + aMuffler.getYCoord() + 0.25F;
        final float zPos = aDir.offsetZ * 0.76F + aMuffler.getZCoord() + 0.25F;

        final float ySpd = aDir.offsetY * 0.1F + 0.2F + 0.1F * XSTR_INSTANCE.nextFloat();
        final float xSpd;
        final float zSpd;

        if (aDir.offsetY == -1) {
            final float temp = XSTR_INSTANCE.nextFloat() * 2 * (float) Math.PI;
            xSpd = (float) Math.sin(temp) * 0.1F;
            zSpd = (float) Math.cos(temp) * 0.1F;
        } else {
            xSpd = aDir.offsetX * (0.1F + 0.2F * XSTR_INSTANCE.nextFloat());
            zSpd = aDir.offsetZ * (0.1F + 0.2F * XSTR_INSTANCE.nextFloat());
        }

        final WorldSpawnedEventBuilder.ParticleEventBuilder events = new WorldSpawnedEventBuilder.ParticleEventBuilder()
            .setIdentifier(name)
            .setWorld(aWorld)
            .setMotion(xSpd, ySpd, zSpd);

        if (chk1) {
            events
                .setPosition(
                    xPos + ran1 * 0.5F,
                    yPos + XSTR_INSTANCE.nextFloat() * 0.5F,
                    zPos + XSTR_INSTANCE.nextFloat() * 0.5F)
                .run();
        }
        if (chk2) {
            events
                .setPosition(
                    xPos + ran2 * 0.5F,
                    yPos + XSTR_INSTANCE.nextFloat() * 0.5F,
                    zPos + XSTR_INSTANCE.nextFloat() * 0.5F)
                .run();
        }
        if (chk3) {
            events
                .setPosition(
                    xPos + ran3 * 0.5F,
                    yPos + XSTR_INSTANCE.nextFloat() * 0.5F,
                    zPos + XSTR_INSTANCE.nextFloat() * 0.5F)
                .run();
        }
    }

    public int calculatePollutionReduction(int aPollution) {
        if (mTier < 2) {
            return aPollution;
        }
        return (int) ((float) aPollution * ((100F - 12.5F * ((float) mTier - 1F)) / 100F));
    }

    protected WeakReference<MTEMultiBlockBase> mController;
    protected WeakReference<MTEMultiBlockBase> mLastController;
    protected int mLastControllerX, mLastControllerY, mLastControllerZ;
    protected boolean mHasLastController = false;

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        if (mHasLastController) {
            aNBT.setBoolean("mHasLastController", true);
            aNBT.setInteger("mLastControllerX", mLastControllerX);
            aNBT.setInteger("mLastControllerY", mLastControllerY);
            aNBT.setInteger("mLastControllerZ", mLastControllerZ);
        }
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mHasLastController = aNBT.getBoolean("mHasLastController");
        if (mHasLastController) {
            mLastControllerX = aNBT.getInteger("mLastControllerX");
            mLastControllerY = aNBT.getInteger("mLastControllerY");
            mLastControllerZ = aNBT.getInteger("mLastControllerZ");
        }
    }

    public void setController(MTEMultiBlockBase controller) {
        this.mController = controller == null ? null : new WeakReference<>(controller);
        if (controller != null) {
            this.mLastController = this.mController;
            IGregTechTileEntity cBase = controller.getBaseMetaTileEntity();
            if (cBase != null) {
                this.mLastControllerX = cBase.getXCoord();
                this.mLastControllerY = cBase.getYCoord();
                this.mLastControllerZ = cBase.getZCoord();
                this.mHasLastController = true;
            }
        }
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null && !base.isClientSide()) {
            for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
                IGregTechTileEntity adj = base.getIGregTechTileEntityAtSide(side);
                if (adj != null && adj.getMetaTileEntity() instanceof MTEAirDuct duct) {
                    AirDuctNetwork net = duct.getNetwork();
                    if (net != null) {
                        net.invalidate();
                    }
                }
            }
        }
    }

    public MTEMultiBlockBase getController() {
        return mController == null ? null : mController.get();
    }

    public IPollutionScrubber getScrubber() {
        MTEMultiBlockBase controller = getController();
        if (controller == null && mLastController != null) {
            controller = mLastController.get();
        }
        if (controller == null && mHasLastController) {
            IGregTechTileEntity base = getBaseMetaTileEntity();
            if (base != null && base.getWorld() != null) {
                TileEntity te = base.getWorld()
                    .getTileEntity(mLastControllerX, mLastControllerY, mLastControllerZ);
                if (te instanceof IGregTechTileEntity gtTE
                    && gtTE.getMetaTileEntity() instanceof MTEMultiBlockBase multi) {
                    controller = multi;
                    mLastController = new WeakReference<>(multi);
                }
            }
        }
        if (controller instanceof IPollutionScrubber scrubber) {
            return scrubber;
        }
        return null;
    }

    @Override
    public void onBlockDestroyed() {
        super.onBlockDestroyed();
        IPollutionScrubber scrubber = getScrubber();
        if (scrubber != null) {
            int buffered = scrubber.drainStoredPollution();
            if (buffered > 0) {
                IGregTechTileEntity base = getBaseMetaTileEntity();
                if (base != null && !base.isClientSide() && base.getWorld() != null) {
                    Pollution.addPollution(base, buffered);
                }
            }
        }
    }

    public AirDuctNetwork getConnectedAirDuctNetwork() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null) return null;
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            IGregTechTileEntity adj = base.getIGregTechTileEntityAtSide(side);
            if (adj != null && adj.getMetaTileEntity() instanceof MTEAirDuct duct) {
                AirDuctNetwork net = duct.getNetwork();
                if (net != null && !net.isInvalid()) {
                    return net;
                }
            }
        }
        return null;
    }

    public boolean isFrontFacingConnectedToAirDuct() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null) return false;
        IGregTechTileEntity adj = base.getIGregTechTileEntityAtSide(base.getFrontFacing());
        return adj != null && adj.getMetaTileEntity() instanceof MTEAirDuct;
    }

    /**
     * @param mte             The multi-block controller's {@link MetaTileEntity} MetaTileEntity is passed so newer
     *                        muffler hatches can do wacky things with the multis
     * @param pollutionAmount How much pollution to output. Reduced by muffler efficiency.
     * @return pollution success
     */
    public boolean polluteEnvironment(MetaTileEntity mte, int pollutionAmount) {
        if (mte instanceof MTEAirFilterBase) {
            return true;
        }

        int remaining = pollutionAmount;
        AirDuctNetwork network = getConnectedAirDuctNetwork();
        if (network != null) {
            int accepted = network.routePollution(pollutionAmount);
            remaining -= accepted;
        }

        if (remaining <= 0) {
            return true;
        }

        if (getBaseMetaTileEntity().getAirAtSide(getBaseMetaTileEntity().getFrontFacing())
            || isFrontFacingConnectedToAirDuct()) {
            Pollution.addPollution(getBaseMetaTileEntity(), calculatePollutionReduction(remaining));
            return true;
        }
        return false;
    }
}
