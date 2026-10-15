package gregtech.api.metatileentity.implementations;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import gregtech.GTMod;
import gregtech.api.enums.Dyes;
import gregtech.api.enums.HarvestTool;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaPipeEntity;
import gregtech.api.render.TextureFactory;
import gregtech.common.covers.Cover;
import gregtech.common.pollution.AirDuctNetwork;
import gregtech.common.pollution.IPollutionScrubber;

@IMetaTileEntity.SkipGenerateDescription
public class MTEAirDuct extends MetaPipeEntity {

    private AirDuctNetwork network;

    public MTEAirDuct(int aID, String aName, String aNameRegional) {
        super(aID, aName, 0, false);
    }

    public MTEAirDuct(String aName) {
        super(aName, 0);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEAirDuct(mName);
    }

    @Override
    public float getThickness() {
        return 0.5f;
    }

    @Override
    public float getCollisionThickness() {
        return getThickness();
    }

    @Override
    public byte getTileEntityBaseType() {
        return HarvestTool.WrenchPipeLevel1.toTileEntityBaseType();
    }

    @Override
    public boolean getGT6StyleConnection() {
        return GTMod.proxy.gt6Pipe;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, int aConnections,
        int colorIndex, boolean aConnected, boolean aRedstone) {
        return new ITexture[] { TextureFactory.of(
            Materials.Steel.mIconSet.mTextures[OrePrefixes.pipeMedium.getTextureIndex()],
            Dyes.getModulation(colorIndex, Materials.Steel.mRGBa)) };
    }

    @Override
    public String[] getDescription() {
        return new String[] {
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("gt.blockmachines.pipe.airduct.desc.0"),
            EnumChatFormatting.GOLD + StatCollector.translateToLocal("gt.blockmachines.pipe.airduct.desc.1"),
            EnumChatFormatting.AQUA + StatCollector.translateToLocal("gt.blockmachines.pipe.airduct.desc.2") };
    }

    @Override
    public boolean canConnect(ForgeDirection side, TileEntity tileEntity) {
        if (tileEntity == null) return false;
        if (tileEntity instanceof IGregTechTileEntity gtTile) {
            IMetaTileEntity meta = gtTile.getMetaTileEntity();
            if (meta == null) return false;
            if (meta instanceof MTEAirDuct) return true;
            if (meta instanceof MTEHatchMuffler) return true;
            if (meta instanceof IPollutionScrubber) return true;
        }
        return false;
    }

    @Override
    public boolean letsIn(Cover cover) {
        return false;
    }

    @Override
    public boolean letsOut(Cover cover) {
        return false;
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity iGregTechTileEntity, int i, ForgeDirection side,
        ItemStack itemStack) {
        return false;
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity iGregTechTileEntity, int i, ForgeDirection side,
        ItemStack itemStack) {
        return false;
    }

    @Override
    public boolean isLiquidInput(ForgeDirection side) {
        return false;
    }

    @Override
    public boolean isLiquidOutput(ForgeDirection side) {
        return false;
    }

    public AirDuctNetwork getNetwork() {
        if (network != null && !network.isInvalid()) {
            return network;
        }
        rebuildNetwork();
        return network;
    }

    public void setNetwork(AirDuctNetwork net) {
        this.network = net;
    }

    public void rebuildNetwork() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || base.isClientSide()) return;

        AirDuctNetwork newNet = new AirDuctNetwork();
        Set<MTEAirDuct> visited = new HashSet<>();
        Queue<MTEAirDuct> queue = new ArrayDeque<>();

        queue.add(this);
        visited.add(this);

        while (!queue.isEmpty()) {
            MTEAirDuct current = queue.poll();
            newNet.addDuct(current);
            current.network = newNet;

            IGregTechTileEntity currentBase = current.getBaseMetaTileEntity();
            if (currentBase == null) continue;

            for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
                if (!current.isConnectedAtSide(side)) continue;

                IGregTechTileEntity adjTile = currentBase.getIGregTechTileEntityAtSide(side);
                if (adjTile == null) continue;
                IMetaTileEntity adjMeta = adjTile.getMetaTileEntity();

                if (adjMeta instanceof MTEAirDuct neighborDuct) {
                    if (neighborDuct.isConnectedAtSide(side.getOpposite()) && visited.add(neighborDuct)) {
                        queue.add(neighborDuct);
                    }
                } else if (adjMeta instanceof IPollutionScrubber directScrubber) {
                    newNet.addScrubber(directScrubber);
                } else if (adjMeta instanceof MTEHatchMuffler muffler) {
                    IPollutionScrubber scrubber = muffler.getScrubber();
                    if (scrubber != null) {
                        newNet.addScrubber(scrubber);
                    }
                }
            }
        }
        this.network = newNet;
    }

    @Override
    public boolean renderInside(ForgeDirection side) {
        return false;
    }

    public boolean isValidDuct() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        return base != null && !base.isDead();
    }

    @Override
    public void onRemoval() {
        if (network != null) {
            network.invalidate();
            network = null;
        }
        super.onRemoval();
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity.isServerSide() && mCheckConnections) {
            checkConnections();
            if (network != null) {
                network.invalidate();
                network = null;
            }
        }
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        if (network != null && !network.isInvalid() && network.getAnchorDuct() == this) {
            aNBT.setBoolean("isNetworkAnchor", true);
            NBTTagList scrubberList = new NBTTagList();
            for (IPollutionScrubber scrubber : network.getScrubbers()) {
                if (scrubber.isValidScrubber() && scrubber.getBaseMetaTileEntity() != null) {
                    IGregTechTileEntity base = scrubber.getBaseMetaTileEntity();
                    NBTTagCompound sTag = new NBTTagCompound();
                    sTag.setInteger("x", base.getXCoord());
                    sTag.setInteger("y", base.getYCoord());
                    sTag.setInteger("z", base.getZCoord());
                    scrubberList.appendTag(sTag);
                }
            }
            aNBT.setTag("connectedScrubbers", scrubberList);
        }
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {}
}
