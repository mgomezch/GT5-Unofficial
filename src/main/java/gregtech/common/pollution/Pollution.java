package gregtech.common.pollution;

import static gregtech.api.objects.XSTR.XSTR_INSTANCE;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.event.world.ChunkWatchEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.gtnewhorizon.gtnhlib.capability.Capabilities;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import gregtech.GTMod;
import gregtech.api.enums.GTValues;
import gregtech.api.hazards.HazardProtection;
import gregtech.api.interfaces.ICleanroom;
import gregtech.api.interfaces.ICleanroomReceiver;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.net.GTPacketPollution;
import gregtech.api.util.GTChunkAssociatedData;
import gregtech.api.util.GTUtility;

// TODO this whole thing should be reworked,
// the global pollution manager should be a
// non static instance in GTProxy
// and all access to it should be non static and via
// GTProxy.proxy.pollutionManager......
public class Pollution {

    private static final Storage STORAGE = new Storage();
    /**
     * Pollution dispersion until effects start: Calculation: ((Limit * 0.01) + 2000) * (4 <- spreading rate)
     * <p>
     * SMOG(500k) 466.7 pollution/sec Poison(750k) 633,3 pollution/sec Dying Plants(1mio) 800 pollution/sec Sour
     * Rain(1.5mio) 1133.3 pollution/sec
     * <p>
     * Pollution producers (pollution/sec) Bronze Boiler(20) Lava Boiler(20) High Pressure Boiler(20) Bronze Blast
     * Furnace(50) Diesel Generator(40/80/160) Gas Turbine(20/40/80) Charcoal Pile(100)
     * <p>
     * Large Diesel Engine(320) Electric Blast Furnace(100) Implosion Compressor(2000) Large Boiler(240) Large Gas
     * Turbine(160) Multi Smelter(100) Pyrolyse Oven(400)
     * <p>
     * Machine Explosion(100,000)
     * <p>
     * Other Random Shit: lots and lots
     * <p>
     * Muffler Hatch Pollution reduction: ** inaccurate ** LV (0%), MV (30%), HV (52%), EV (66%), IV (76%), LuV (84%),
     * ZPM (89%), UV (92%), MAX (95%)
     */
    // chunks left to process in this cycle
    private List<ChunkCoordIntPair> pollutionList = new ArrayList<>();
    // a global list of all chunks with positive pollution
    private final Set<ChunkCoordIntPair> pollutedChunks = new HashSet<>();
    private int operationsPerTick = 0; // how much chunks should be processed in each cycle
    private static final short cycleLen = 1200;
    private final World world;
    private boolean blank = true;
    public static int mPlayerPollution;

    private static final int POLLUTIONPACKET_MINVALUE = 1000;

    private static GT_PollutionEventHandler EVENT_HANDLER;

    public Pollution(World world) {
        this.world = world;

        if (EVENT_HANDLER == null) {
            EVENT_HANDLER = new GT_PollutionEventHandler();
            MinecraftForge.EVENT_BUS.register(EVENT_HANDLER);
        }
    }

    public static void onWorldTick(TickEvent.WorldTickEvent aEvent) { // called from proxy
        // return if pollution disabled
        if (!GTMod.proxy.mPollution) return;
        if (aEvent.phase == TickEvent.Phase.START) return;
        final Pollution pollutionInstance = GTMod.proxy.dimensionWisePollution.get(aEvent.world.provider.dimensionId);
        if (pollutionInstance == null) return;
        pollutionInstance.tickPollutionInWorld((int) (aEvent.world.getTotalWorldTime() % cycleLen));
    }

    public static BlockMatcher standardBlocks;
    public static BlockMatcher liquidBlocks;
    public static BlockMatcher doublePlants;
    public static BlockMatcher crossedSquares;
    public static BlockMatcher blockVine;

    public static void onPostInitClient() {
        if (PollutionConfig.pollution) {
            standardBlocks = new BlockMatcher();
            liquidBlocks = new BlockMatcher();
            doublePlants = new BlockMatcher();
            crossedSquares = new BlockMatcher();
            blockVine = new BlockMatcher();
            standardBlocks.updateClassList(PollutionConfig.renderStandardBlock);
            liquidBlocks.updateClassList(PollutionConfig.renderBlockLiquid);
            doublePlants.updateClassList(PollutionConfig.renderBlockDoublePlant);
            crossedSquares.updateClassList(PollutionConfig.renderCrossedSquares);
            blockVine.updateClassList(PollutionConfig.renderblockVine);
            MinecraftForge.EVENT_BUS.register(standardBlocks);
            MinecraftForge.EVENT_BUS.register(liquidBlocks);
            MinecraftForge.EVENT_BUS.register(doublePlants);
            MinecraftForge.EVENT_BUS.register(crossedSquares);
            MinecraftForge.EVENT_BUS.register(blockVine);
            MinecraftForge.EVENT_BUS.register(new PollutionTooltip());
        }
    }

    private void tickPollutionInWorld(int aTickID) { // called from method above
        // gen data set
        if (aTickID == 0 || blank) {
            // make a snapshot of what to work on
            pollutionList = new ArrayList<>(pollutedChunks);
            // set operations per tick
            if (!pollutionList.isEmpty()) operationsPerTick = Math.max(1, pollutionList.size() / cycleLen);
            else operationsPerTick = 0; // SANity
            blank = false;
        }

        for (int chunksProcessed = 0; chunksProcessed < operationsPerTick; chunksProcessed++) {
            if (pollutionList.isEmpty()) break; // no more stuff to do
            ChunkCoordIntPair actualPos = pollutionList.remove(pollutionList.size() - 1); // faster
            // get pollution
            ChunkData currentData = STORAGE.get(world, actualPos);
            int tPollution = currentData.getAmount();
            float tHeight = currentData.getHeight();
            // remove some
            tPollution = (int) (0.9945f * tPollution);
            if (tPollution == 0) {
                tHeight = Float.NaN;
            }

            if (tPollution > 400000) { // Spread Pollution

                ChunkCoordIntPair[] tNeighbors = new ChunkCoordIntPair[4]; // array is faster
                tNeighbors[0] = (new ChunkCoordIntPair(actualPos.chunkXPos + 1, actualPos.chunkZPos));
                tNeighbors[1] = (new ChunkCoordIntPair(actualPos.chunkXPos - 1, actualPos.chunkZPos));
                tNeighbors[2] = (new ChunkCoordIntPair(actualPos.chunkXPos, actualPos.chunkZPos + 1));
                tNeighbors[3] = (new ChunkCoordIntPair(actualPos.chunkXPos, actualPos.chunkZPos - 1));
                for (ChunkCoordIntPair neighborPosition : tNeighbors) {
                    ChunkData neighbor = STORAGE.get(world, neighborPosition);
                    int neighborPollution = neighbor.getAmount();
                    if (neighborPollution * 6 < tPollution * 5) { // MATHEMATICS...
                        int tDiff = tPollution - neighborPollution;
                        tDiff = tDiff / 20;
                        if (tDiff > 0) {
                            final float propHeight = tHeight;
                            final int amountToProp = tDiff;
                            tPollution -= tDiff;
                            mutatePollution(world, neighborPosition.chunkXPos, neighborPosition.chunkZPos, d -> {
                                if (Float.isNaN(propHeight)) {
                                    d.changeAmount(amountToProp);
                                } else {
                                    d.addPollution(amountToProp, propHeight);
                                }
                            }, pollutedChunks);
                        }
                    }
                }

                // Create Pollution effects
                // Smog filter TODO
                if (tPollution > GTMod.proxy.mPollutionSmogLimit) {
                    AxisAlignedBB chunk = AxisAlignedBB.getBoundingBox(
                        actualPos.chunkXPos << 4,
                        0,
                        actualPos.chunkZPos << 4,
                        (actualPos.chunkXPos << 4) + 16,
                        256,
                        (actualPos.chunkZPos << 4) + 16);
                    List<EntityLivingBase> tEntitys = world.getEntitiesWithinAABB(EntityLivingBase.class, chunk);
                    for (EntityLivingBase tEnt : tEntitys) {
                        if (tEnt instanceof EntityPlayerMP && ((EntityPlayerMP) tEnt).capabilities.isCreativeMode)
                            continue;
                        if (XSTR_INSTANCE.nextFloat() > getPollutionEffectFactor(tEnt.posY, tHeight)) continue;
                        if (!(HazardProtection.isWearingFullGasHazmat(tEnt))) {
                            switch (XSTR_INSTANCE.nextInt(3)) {
                                case 1 -> tEnt.addPotionEffect(
                                    new PotionEffect(
                                        Potion.weakness.id,
                                        Math.min(tPollution / 1000, 1000),
                                        tPollution / 400000));
                                case 2 -> tEnt.addPotionEffect(
                                    new PotionEffect(
                                        Potion.moveSlowdown.id,
                                        Math.min(tPollution / 1000, 1000),
                                        tPollution / 400000));
                                default -> tEnt.addPotionEffect(
                                    new PotionEffect(
                                        Potion.digSlowdown.id,
                                        Math.min(tPollution / 1000, 1000),
                                        tPollution / 400000));
                            }
                        }
                    }

                    // Poison effects
                    if (tPollution > GTMod.proxy.mPollutionPoisonLimit) {
                        for (EntityLivingBase tEnt : tEntitys) {
                            if (tEnt instanceof EntityPlayerMP && ((EntityPlayerMP) tEnt).capabilities.isCreativeMode)
                                continue;
                            if (XSTR_INSTANCE.nextFloat() > getPollutionEffectFactor(tEnt.posY, tHeight)) continue;
                            if (!HazardProtection.isWearingFullGasHazmat(tEnt)) {
                                switch (XSTR_INSTANCE.nextInt(4)) {
                                    case 1 -> tEnt.addPotionEffect(
                                        new PotionEffect(Potion.confusion.id, Math.min(tPollution / 2000, 1000), 1));
                                    case 2 -> tEnt.addPotionEffect(
                                        new PotionEffect(
                                            Potion.poison.id,
                                            Math.min(tPollution / 4000, 1000),
                                            tPollution / 500000));
                                    case 3 -> tEnt.addPotionEffect(
                                        new PotionEffect(Potion.blindness.id, Math.min(tPollution / 2000, 1000), 1));
                                    default -> tEnt
                                        .addPotionEffect(new PotionEffect(Potion.hunger.id, tPollution / 500000));
                                }
                            }
                        }

                        // killing plants
                        if (tPollution > GTMod.proxy.mPollutionVegetationLimit) {
                            int f = 20;
                            for (; f < (tPollution / 25000); f++) {
                                int x = (actualPos.chunkXPos << 4) + XSTR_INSTANCE.nextInt(16);
                                int y = 60 + (-f + XSTR_INSTANCE.nextInt(f * 2 + 1));
                                int z = (actualPos.chunkZPos << 4) + XSTR_INSTANCE.nextInt(16);
                                if (XSTR_INSTANCE.nextFloat() <= getPollutionEffectFactor(y, tHeight)) {
                                    damageBlock(world, x, y, z, tPollution > GTMod.proxy.mPollutionSourRainLimit);
                                }
                            }
                        }
                    }
                }
            }
            // Write new pollution to Hashmap !!!
            final float finalHeight = tHeight;
            final int finalPollution = tPollution;
            mutatePollution(
                world,
                actualPos.chunkXPos,
                actualPos.chunkZPos,
                c -> c.set(finalPollution, finalHeight),
                pollutedChunks);

            // Send new value to players nearby
            if (tPollution > POLLUTIONPACKET_MINVALUE) {
                NetworkRegistry.TargetPoint point = new NetworkRegistry.TargetPoint(
                    world.provider.dimensionId,
                    (actualPos.chunkXPos << 4),
                    64,
                    (actualPos.chunkZPos << 4),
                    256);
                GTValues.NW.sendToAllAround(new GTPacketPollution(actualPos, tPollution), point);
            }
        }
    }

    private void setChunkPollution(ChunkCoordIntPair coord, int pollution) {
        mutatePollution(world, coord.chunkXPos, coord.chunkZPos, c -> c.setAmount(pollution), pollutedChunks);
    }

    private static void damageBlock(World world, int x, int y, int z, boolean sourRain) {
        if (world.isRemote) return;
        Block tBlock = world.getBlock(x, y, z);
        int tMeta = world.getBlockMetadata(x, y, z);
        if (tBlock == Blocks.air || tBlock == Blocks.stone || tBlock == Blocks.sand || tBlock == Blocks.deadbush)
            return;

        if (tBlock == Blocks.leaves || tBlock == Blocks.leaves2 || tBlock.getMaterial() == Material.leaves)
            world.setBlockToAir(x, y, z);
        if (tBlock == Blocks.reeds) {
            tBlock.dropBlockAsItem(world, x, y, z, tMeta, 0);
            world.setBlockToAir(x, y, z);
        }
        if (tBlock == Blocks.tallgrass) world.setBlock(x, y, z, Blocks.deadbush);
        if (tBlock == Blocks.vine) {
            tBlock.dropBlockAsItem(world, x, y, z, tMeta, 0);
            world.setBlockToAir(x, y, z);
        }
        if (tBlock == Blocks.waterlily || tBlock == Blocks.wheat
            || tBlock == Blocks.cactus
            || tBlock.getMaterial() == Material.cactus
            || tBlock == Blocks.melon_block
            || tBlock == Blocks.melon_stem) {
            tBlock.dropBlockAsItem(world, x, y, z, tMeta, 0);
            world.setBlockToAir(x, y, z);
        }
        if (tBlock == Blocks.red_flower || tBlock == Blocks.yellow_flower
            || tBlock == Blocks.carrots
            || tBlock == Blocks.potatoes
            || tBlock == Blocks.pumpkin
            || tBlock == Blocks.pumpkin_stem) {
            tBlock.dropBlockAsItem(world, x, y, z, tMeta, 0);
            world.setBlockToAir(x, y, z);
        }
        if (tBlock == Blocks.sapling || tBlock.getMaterial() == Material.plants)
            world.setBlock(x, y, z, Blocks.deadbush);
        if (tBlock == Blocks.cocoa) {
            tBlock.dropBlockAsItem(world, x, y, z, tMeta, 0);
            world.setBlockToAir(x, y, z);
        }
        if (tBlock == Blocks.mossy_cobblestone) world.setBlock(x, y, z, Blocks.cobblestone);
        if (tBlock == Blocks.grass || tBlock.getMaterial() == Material.grass) world.setBlock(x, y, z, Blocks.dirt);
        if (tBlock == Blocks.farmland || tBlock == Blocks.dirt) {
            world.setBlock(x, y, z, Blocks.sand);
        }

        if (sourRain && world.isRaining()
            && (tBlock == Blocks.gravel || tBlock == Blocks.cobblestone)
            && world.getBlock(x, y + 1, z) == Blocks.air
            && world.canBlockSeeTheSky(x, y, z)) {
            if (tBlock == Blocks.cobblestone) {
                world.setBlock(x, y, z, Blocks.gravel);
            } else {
                world.setBlock(x, y, z, Blocks.sand);
            }
        }
    }

    public static double getPollutionEffectFactor(double targetY, float pollutionHeight) {
        if (Float.isNaN(pollutionHeight)) return 1.0;
        double deltaY = Math.abs(targetY - pollutionHeight);
        return Math.pow(0.2, deltaY / 50.0);
    }

    private static Pollution getPollutionManager(World world) {
        return GTMod.proxy.dimensionWisePollution
            .computeIfAbsent(world.provider.dimensionId, i -> new Pollution(world));
    }

    /** @see #addPollution(TileEntity, int) */
    public static void addPollution(IGregTechTileEntity te, int aPollution) {
        addPollution((TileEntity) te, aPollution);
    }

    /**
     * Also pollutes cleanroom if {@code te} is an instance of {@link ICleanroomReceiver}.
     *
     * @see #addPollution(World, int, int, int)
     */
    public static void addPollution(TileEntity te, int aPollution) {
        if (!GTMod.proxy.mPollution || aPollution == 0 || te.getWorldObj().isRemote) return;

        if (aPollution > 0) {
            ICleanroomReceiver receiver = Capabilities.getCapability(te, ICleanroomReceiver.class);
            if (receiver != null) {
                ICleanroom cleanroom = receiver.getCleanroom();
                if (cleanroom != null && cleanroom.isValidCleanroom()) {
                    cleanroom.pollute();
                }
            }
        }

        addPollution(te.getWorldObj(), te.xCoord >> 4, te.zCoord >> 4, aPollution, te.yCoord);
    }

    /** @see #addPollution(World, int, int, int) */
    public static void addPollution(Chunk ch, int aPollution) {
        addPollution(ch.worldObj, ch.xPosition, ch.zPosition, aPollution);
    }

    public static void addPollution(Chunk ch, int aPollution, double height) {
        addPollution(ch.worldObj, ch.xPosition, ch.zPosition, aPollution, height);
    }

    /**
     * Add some pollution to given chunk. Can pass in negative to remove pollution. Will clamp the final pollution
     * number to 0 if it would be changed into negative.
     *
     * @param w          world to modify. do nothing if it's a client world
     * @param chunkX     chunk coordinate X, i.e. blockX >> 4
     * @param chunkZ     chunk coordinate Z, i.e. blockZ >> 4
     * @param aPollution desired delta. Positive means the pollution in chunk would go higher.
     */
    public static void addPollution(World w, int chunkX, int chunkZ, int aPollution) {
        if (!GTMod.proxy.mPollution || aPollution == 0 || w.isRemote) return;
        ChunkData current = STORAGE.get(w, chunkX, chunkZ);
        double height = current.hasHeight() ? current.getHeight() : 64.0;
        addPollution(w, chunkX, chunkZ, aPollution, height);
    }

    public static void addPollution(World w, int chunkX, int chunkZ, int aPollution, double height) {
        if (!GTMod.proxy.mPollution || aPollution == 0 || w.isRemote) return;
        mutatePollution(w, chunkX, chunkZ, d -> d.addPollution(aPollution, height), null);
    }

    /**
     * Moves pollution from current height to a new emission height (such as a chimney stack).
     * If a muffler hatch is present, outputAmount can be smaller than amountToRemove,
     * permanently reducing pollution while preserving the height impact as if all amountToRemove was vented.
     */
    public static void movePollutionHeight(World world, int chunkX, int chunkZ, int amountToRemove, int outputAmount,
        double newEmissionHeight) {
        if (!GTMod.proxy.mPollution || world.isRemote) return;
        mutatePollution(world, chunkX, chunkZ, d -> {
            int current = d.getAmount();
            if (current <= 0) return;
            int removed = Math.min(current, amountToRemove);
            float oldHeight = d.getHeight();
            int remaining = current - removed;
            if (remaining <= 0 || Float.isNaN(oldHeight)) {
                d.set(outputAmount, (float) newEmissionHeight);
            } else {
                long virtualTotal = (long) remaining + removed;
                double weightedHeight = ((double) remaining * oldHeight + (double) removed * newEmissionHeight)
                    / (double) virtualTotal;
                int newTotal = GTUtility.safeInt((long) remaining + outputAmount, 0);
                d.set(newTotal, (float) weightedHeight);
            }
        }, null);
    }

    private static void mutatePollution(World world, int x, int z, Consumer<ChunkData> mutator,
        @Nullable Set<ChunkCoordIntPair> chunks) {
        ChunkData data = STORAGE.get(world, x, z);
        boolean hadPollution = data.getAmount() > 0;
        mutator.accept(data);
        boolean hasPollution = data.getAmount() > 0;
        if (hasPollution != hadPollution) {
            if (chunks == null) chunks = getPollutionManager(world).pollutedChunks;
            if (hasPollution) chunks.add(new ChunkCoordIntPair(x, z));
            else chunks.remove(new ChunkCoordIntPair(x, z));
        }
    }

    /** @see #getPollution(World, int, int) */
    public static int getPollution(IGregTechTileEntity te) {
        return getPollution(te.getWorld(), te.getXCoord() >> 4, te.getZCoord() >> 4);
    }

    /** @see #getPollution(World, int, int) */
    public static int getPollution(Chunk ch) {
        return getPollution(ch.worldObj, ch.xPosition, ch.zPosition);
    }

    /**
     * Get the pollution in specified chunk
     *
     * @param w      world to look in. can be a client world, but that limits the knowledge to what server side send us
     * @param chunkX chunk coordinate X, i.e. blockX >> 4
     * @param chunkZ chunk coordinate Z, i.e. blockZ >> 4
     * @return pollution amount. may be 0 if pollution is disabled, or if it's a client world and server did not send us
     *         info about this chunk
     */
    public static int getPollution(World w, int chunkX, int chunkZ) {
        if (!GTMod.proxy.mPollution) return 0;
        if (w.isRemote) {
            // it really should be querying the client side stuff instead
            return GTMod.clientProxy().mPollutionRenderer.getKnownPollution(chunkX << 4, chunkZ << 4);
        }
        return STORAGE.get(w, chunkX, chunkZ)
            .getAmount();
    }

    public static float getPollutionHeight(World w, int chunkX, int chunkZ) {
        if (!GTMod.proxy.mPollution || w.isRemote) return Float.NaN;
        return STORAGE.get(w, chunkX, chunkZ)
            .getHeight();
    }

    public static float getPollutionHeight(Chunk ch) {
        return getPollutionHeight(ch.worldObj, ch.xPosition, ch.zPosition);
    }

    public static float getPollutionHeight(IGregTechTileEntity te) {
        return getPollutionHeight(te.getWorld(), te.getXCoord() >> 4, te.getZCoord() >> 4);
    }

    public static boolean hasPollutionHeight(Chunk ch) {
        if (!GTMod.proxy.mPollution || ch.worldObj.isRemote) return false;
        return STORAGE.isCreated(ch.worldObj, ch.getChunkCoordIntPair()) && STORAGE.get(ch)
            .hasHeight();
    }

    public static boolean hasPollutionHeight(World w, int chunkX, int chunkZ) {
        if (!GTMod.proxy.mPollution || w.isRemote) return false;
        return STORAGE.isCreated(w, chunkX, chunkZ) && STORAGE.get(w, chunkX, chunkZ)
            .hasHeight();
    }

    public static boolean hasPollution(Chunk ch) {
        if (!GTMod.proxy.mPollution) return false;
        return STORAGE.isCreated(ch.worldObj, ch.getChunkCoordIntPair()) && STORAGE.get(ch)
            .getAmount() > 0;
    }

    /**
     * Calculates the at-height adjusted pollution gibbl score.
     * Takes the target Y, chunk pollution, and pollution height.
     * The pollution number is multiplied by an exponential decay factor:
     * factor = 0.5^(|targetY - pollutionHeight| / 30.0).
     */
    public static double getAtHeightAdjustedPollution(double targetY, int chunkPollution, float pollutionHeight) {
        if (chunkPollution <= 0) return 0.0;
        if (Float.isNaN(pollutionHeight)) return chunkPollution;
        double deltaY = Math.abs(targetY - pollutionHeight);
        double factor = Math.pow(0.5, deltaY / 30.0);
        return chunkPollution * factor;
    }

    /**
     * Calculates the at-height adjusted pollution gibbl score at the location of a TileEntity.
     */
    public static double getAtHeightAdjustedPollution(IGregTechTileEntity te) {
        if (te == null) return 0.0;
        World w = te.getWorld();
        if (w == null) return 0.0;
        int chunkX = te.getXCoord() >> 4;
        int chunkZ = te.getZCoord() >> 4;
        int pollution = getPollution(w, chunkX, chunkZ);
        if (pollution <= 0) return 0.0;
        float height = getPollutionHeight(w, chunkX, chunkZ);
        return getAtHeightAdjustedPollution(te.getYCoord(), pollution, height);
    }

    /**
     * Calculates the multiblock machine efficiency penalty in GregTech efficiency points (where 10000 = 100%):
     * - Decreases by 1% (100 points) for every 5000 adjusted at-height gibbl for the first 10% (up to 50000 gibbl)
     * - Decreases by 1% (100 points) for every 10000 gibbl after 50000 gibbl
     */
    public static int getPollutionEfficiencyPenalty(double adjustedGibbl) {
        if (adjustedGibbl <= 0.0) return 0;
        double penaltyPercent;
        if (adjustedGibbl <= 50000.0) {
            penaltyPercent = adjustedGibbl / 5000.0;
        } else {
            penaltyPercent = 10.0 + ((adjustedGibbl - 50000.0) / 10000.0);
        }
        return Math.max(0, (int) Math.round(penaltyPercent * 100.0));
    }

    /**
     * Calculates the multiplier for maintenance issue frequency.
     * Every 2000 adjusted at-height gibbl doubles the frequency:
     * multiplier = 2^(adjustedGibbl / 2000.0).
     */
    public static double getMaintenanceFrequencyMultiplier(double adjustedGibbl) {
        if (adjustedGibbl <= 0.0) return 1.0;
        return Math.pow(2.0, adjustedGibbl / 2000.0);
    }

    public static void migrate(ChunkDataEvent.Load e) {
        addPollution(
            e.getChunk(),
            e.getData()
                .getInteger("GTPOLLUTION"));
    }

    public static class GT_PollutionEventHandler {

        @SubscribeEvent
        public void chunkWatch(ChunkWatchEvent.Watch event) {
            if (!GTMod.proxy.mPollution) return;
            World world = event.player.worldObj;
            if (STORAGE.isCreated(world, event.chunk)) {
                int pollution = STORAGE.get(world, event.chunk)
                    .getAmount();
                if (pollution > POLLUTIONPACKET_MINVALUE)
                    GTValues.NW.sendToPlayer(new GTPacketPollution(event.chunk, pollution), event.player);
            }
        }

        @SubscribeEvent
        public void onWorldLoad(WorldEvent.Load e) {
            // super class loads everything lazily. We force it to load them all.
            if (!e.world.isRemote) STORAGE.loadAll(e.world);
        }

        @SubscribeEvent
        public void onWorldUnload(WorldEvent.Unload e) {
            GTMod.proxy.dimensionWisePollution.remove(e.world.provider.dimensionId);
        }
    }

    @ParametersAreNonnullByDefault
    private static final class Storage extends GTChunkAssociatedData<ChunkData> {

        private Storage() {
            super("Pollution", ChunkData.class, 64, (byte) 1, false);
        }

        @Override
        protected void writeElement(DataOutput output, ChunkData element, World world, int chunkX, int chunkZ)
            throws IOException {
            output.writeInt(element.getAmount());
            output.writeFloat(element.getHeight());
        }

        @Override
        protected ChunkData readElement(DataInput input, int version, World world, int chunkX, int chunkZ)
            throws IOException {
            if (version == 0) {
                int amount = input.readInt();
                ChunkData data = new ChunkData(amount, Float.NaN);
                if (data.getAmount() > 0)
                    getPollutionManager(world).pollutedChunks.add(new ChunkCoordIntPair(chunkX, chunkZ));
                return data;
            } else if (version == 1) {
                int amount = input.readInt();
                float height = input.readFloat();
                ChunkData data = new ChunkData(amount, height);
                if (data.getAmount() > 0)
                    getPollutionManager(world).pollutedChunks.add(new ChunkCoordIntPair(chunkX, chunkZ));
                return data;
            }
            throw new IOException("Region file corrupted");
        }

        @Override
        protected ChunkData createElement(World world, int chunkX, int chunkZ) {
            return new ChunkData();
        }

        @Override
        public void loadAll(World w) {
            super.loadAll(w);
        }

        public boolean isCreated(World world, ChunkCoordIntPair coord) {
            return isCreated(world.provider.dimensionId, coord.chunkXPos, coord.chunkZPos);
        }

        public boolean isCreated(World world, int chunkX, int chunkZ) {
            return isCreated(world.provider.dimensionId, chunkX, chunkZ);
        }
    }

    private static final class ChunkData implements GTChunkAssociatedData.IData {

        public int amount;
        public float height;

        private ChunkData() {
            this(0, Float.NaN);
        }

        private ChunkData(int amount) {
            this(amount, Float.NaN);
        }

        private ChunkData(int amount, float height) {
            this.amount = Math.max(0, amount);
            this.height = this.amount > 0 ? height : Float.NaN;
        }

        /**
         * Current pollution amount.
         */
        public int getAmount() {
            return amount;
        }

        public float getHeight() {
            return height;
        }

        public boolean hasHeight() {
            return !Float.isNaN(height);
        }

        public void setAmount(int amount) {
            this.amount = Math.max(amount, 0);
            if (this.amount == 0) {
                this.height = Float.NaN;
            }
        }

        public void set(int amount, float height) {
            this.amount = Math.max(amount, 0);
            this.height = this.amount > 0 ? height : Float.NaN;
        }

        public void changeAmount(int delta) {
            this.amount = Math.max(GTUtility.safeInt((long) amount + delta, 0), 0);
            if (this.amount == 0) {
                this.height = Float.NaN;
            }
        }

        public void addPollution(int addedAmount, double emissionHeight) {
            if (addedAmount <= 0) {
                changeAmount(addedAmount);
                return;
            }
            if (this.amount <= 0 || Float.isNaN(this.height)) {
                this.amount = GTUtility.safeInt((long) Math.max(0, this.amount) + addedAmount, 0);
                this.height = (float) emissionHeight;
            } else {
                long totalPollution = (long) this.amount + addedAmount;
                double newHeight = ((double) this.amount * this.height + (double) addedAmount * emissionHeight)
                    / (double) totalPollution;
                this.amount = GTUtility.safeInt(totalPollution, 0);
                this.height = (float) newHeight;
            }
        }

        @Override
        public boolean isSameAsDefault() {
            return amount == 0;
        }
    }
}
