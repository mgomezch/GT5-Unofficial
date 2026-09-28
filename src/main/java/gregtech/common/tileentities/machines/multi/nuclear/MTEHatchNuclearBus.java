package gregtech.common.tileentities.machines.multi.nuclear;

import static gregtech.api.enums.Textures.BlockIcons.ITEM_IN_SIGN;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_PIPE_COLORS;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_PIPE_IN;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;
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
import gregtech.api.items.ItemRadioactiveCell;
import gregtech.api.items.ItemRadioactiveCellIC;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;
import ic2.api.reactor.IReactor;
import ic2.api.reactor.IReactorComponent;

public class MTEHatchNuclearBus extends MTEHatch implements INuclearTile {

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT_1 = 1;
    public static final int SLOT_OUTPUT_2 = 2;

    public double mTemperature = 20.0;
    public double mHeatEU = 0.0;
    public int mFastFlux = 0;
    public int mThermalFlux = 0;
    public int mFastAbsorbed = 0;
    public int mThermalAbsorbed = 0;
    public int mLastNeutronsGenerated = 0;
    public long mDirectEUProduced = 0;

    private final IReactor mDummyReactor = new DummyReactor(this);

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
        ItemStack stack = mInventory[SLOT_INPUT];
        if (stack == null) return 0.02;
        if (isBetavoltaic()) return 0.10;
        String name = stack.getUnlocalizedName()
            .toLowerCase();
        if (name.contains("coolant") || name.contains("vent")) return 0.40;
        if (name.contains("reflector")) return 0.15;
        if (name.contains("fuel") || name.contains("uranium") || name.contains("mox") || name.contains("thorium"))
            return 0.05;
        return 0.03;
    }

    public boolean isBetavoltaic() {
        ItemStack stack = mInventory[SLOT_INPUT];
        if (stack == null) return false;
        String name = stack.getUnlocalizedName()
            .toLowerCase();
        return name.contains("betavoltaic") || name.contains("betacell") || name.contains("neutronovoltaic");
    }

    public int getBetavoltaicTier() {
        ItemStack stack = mInventory[SLOT_INPUT];
        if (stack == null) return 0;
        String name = stack.getUnlocalizedName()
            .toLowerCase();
        if (name.contains("ev") || name.contains("extreme") || name.contains("tier2") || name.contains("t2")) return 2;
        return 1; // HV default
    }

    @Override
    public boolean isFuel() {
        ItemStack stack = mInventory[SLOT_INPUT];
        if (stack == null) return false;
        if (stack.getItem() instanceof ItemRadioactiveCell) return true;
        String name = stack.getUnlocalizedName()
            .toLowerCase();
        return name.contains("uranium") || name.contains("mox")
            || name.contains("thorium")
            || name.contains("plutonium")
            || name.contains("naquadah")
            || name.contains("fuelrod");
    }

    @Override
    public int generateNeutrons(double efficiency) {
        if (!isFuel()) {
            mLastNeutronsGenerated = 0;
            return 0;
        }
        ItemStack stack = mInventory[SLOT_INPUT];
        int baseNeutrons = 4;
        if (stack.getItem() instanceof ItemRadioactiveCellIC icCell) {
            baseNeutrons = 4 * icCell.numberOfCells;
            if (icCell.sMox) baseNeutrons *= 2;
        } else {
            String name = stack.getUnlocalizedName()
                .toLowerCase();
            if (name.contains("dual")) baseNeutrons = 8;
            else if (name.contains("quad")) baseNeutrons = 16;
            if (name.contains("mox")) baseNeutrons *= 2;
        }
        String name = stack.getUnlocalizedName()
            .toLowerCase();
        if (name.contains("naquadah")) baseNeutrons *= 4;

        int produced = (int) Math.round(baseNeutrons * efficiency);
        mLastNeutronsGenerated = produced;
        return produced;
    }

    @Override
    public double getAbsorptionProbability(NeutronType type) {
        if (isBetavoltaic()) {
            return 1.0;
        }
        ItemStack stack = mInventory[SLOT_INPUT];
        if (stack == null) return 0.01;
        String name = stack.getUnlocalizedName()
            .toLowerCase();
        if (name.contains("reflector")) return 0.02;
        if (name.contains("boron") || name.contains("cadmium") || name.contains("control")) {
            return (type == NeutronType.THERMAL) ? 0.95 : 0.85;
        }
        if (isFuel()) {
            return (type == NeutronType.THERMAL) ? 0.80 : 0.25;
        }
        if (name.contains("coolant")) return 0.05;
        return 0.02;
    }

    @Override
    public double getScatteringProbability(NeutronType type) {
        if (isBetavoltaic()) {
            return 0.0;
        }
        ItemStack stack = mInventory[SLOT_INPUT];
        if (stack == null) return 0.02;
        String name = stack.getUnlocalizedName()
            .toLowerCase();
        if (name.contains("reflector")) return 0.95;
        if (name.contains("coolant")) return 0.30;
        if (isFuel()) return 0.15;
        return 0.05;
    }

    @Override
    public double getModerationProbability() {
        if (isBetavoltaic()) {
            return 0.0;
        }
        ItemStack stack = mInventory[SLOT_INPUT];
        if (stack == null) return 0.05;
        String name = stack.getUnlocalizedName()
            .toLowerCase();
        if (name.contains("reflector")) return 0.65;
        if (name.contains("coolant")) return 0.40;
        return 0.10;
    }

    @Override
    public void onNeutronAbsorbed(NeutronType type, int count) {
        if (type == NeutronType.FAST) mFastAbsorbed += count;
        else mThermalAbsorbed += count;

        ItemStack stack = mInventory[SLOT_INPUT];
        if (stack != null && isFuel()) {
            // Fission chain reaction bonus: absorbing thermal neutrons produces additional heat
            if (type == NeutronType.THERMAL) {
                addHeat(count * 20.0);
            }
        }
    }

    @Override
    public void onNeutronScattered(NeutronType type, int count) {
        ItemStack stack = mInventory[SLOT_INPUT];
        if (stack != null && stack.isItemStackDamageable()) {
            String name = stack.getUnlocalizedName()
                .toLowerCase();
            if (name.contains("reflector")) {
                // Wear down reflector very slowly
                if (getRandomNumber(20) == 0) {
                    damageComponent(1);
                }
            }
        }
    }

    @Override
    public void addNeutronFlux(NeutronType type, int count) {
        if (type == NeutronType.FAST) mFastFlux += count;
        else mThermalFlux += count;
    }

    @Override
    public void nuclearTick(double efficiency) {
        ItemStack stack = mInventory[SLOT_INPUT];
        if (stack == null) {
            mDirectEUProduced = 0;
            mFastFlux = 0;
            mThermalFlux = 0;
            mFastAbsorbed = 0;
            mThermalAbsorbed = 0;
            return;
        }

        // 1. FUEL DEPLETION (Driven by neutron absorption & fission)
        if (isFuel()) {
            mDirectEUProduced = 0;
            int damage = mFastAbsorbed * 1 + mThermalAbsorbed * 2 + Math.max(1, mLastNeutronsGenerated / 4);
            if (stack.getItem() instanceof ItemRadioactiveCell radCell) {
                radCell.damageItemStack(stack, damage);
                if (radCell.getDamageOfStack(stack) >= radCell.getMaxDamageEx()) {
                    ItemStack depleted = null;
                    if (radCell instanceof ItemRadioactiveCellIC icCell && icCell.sDepleted != null) {
                        depleted = icCell.sDepleted.copy();
                    } else {
                        depleted = getDepletedForm(stack);
                    }
                    mInventory[SLOT_INPUT] = null;
                    ejectToOutput(depleted);
                }
                markTileDirty();
            } else {
                damageComponent(damage);
            }
        }
        // 2. BETAVOLTAIC DIRECT EU GENERATION
        else if (isBetavoltaic()) {
            int tier = getBetavoltaicTier();
            long maxEU = (tier >= 2) ? 4096 : 1024;
            double weightedFlux = mFastAbsorbed * 4.0 + mThermalAbsorbed * 1.0;
            double satFlux = 60.0;
            long genEU = (long) Math.round(maxEU * Math.tanh(weightedFlux / satFlux));
            mDirectEUProduced = genEU;
            double totalEnergy = weightedFlux * 20.0;
            double excessHeat = Math.max(0.0, totalEnergy - genEU);
            if (excessHeat > 0.0) {
                addHeat(excessHeat);
            }
        }
        // 3. COOLANT CELL HEAT ABSORPTION (Capacity-based scaling via IReactorComponent)
        else if (stack.getItem() instanceof IReactorComponent comp && comp.canStoreHeat(mDummyReactor, stack, 0, 0)) {
            mDirectEUProduced = 0;
            int maxHeat = comp.getMaxHeat(mDummyReactor, stack, 0, 0);
            int curHeat = comp.getCurrentHeat(mDummyReactor, stack, 0, 0);
            if (maxHeat > 0 && mTemperature > 50.0) {
                int maxTransferPerTick = Math.max(1, maxHeat / 100);
                double heatAvailable = (mTemperature - 50.0) * NuclearSimulationEngine.EU_PER_DEGREE;
                int heatToTake = (int) Math.min(heatAvailable / 25.0, (double) maxTransferPerTick);
                int room = maxHeat - curHeat;
                heatToTake = Math.min(heatToTake, room);

                if (heatToTake > 0) {
                    comp.alterHeat(mDummyReactor, stack, 0, 0, heatToTake);
                    double heatConsumed = heatToTake * 25.0;
                    mTemperature -= (heatConsumed / NuclearSimulationEngine.EU_PER_DEGREE);
                    markTileDirty();
                }
            }

            // Eject hot/full coolant cells to output slots for freezer re-cooling
            if (comp.getCurrentHeat(mDummyReactor, stack, 0, 0) >= maxHeat) {
                ItemStack fullCell = stack.copy();
                mInventory[SLOT_INPUT] = null;
                ejectToOutput(fullCell);
                markTileDirty();
            }
        }
        // 4. GENERIC COOLANT/VENT FALLBACK
        else {
            mDirectEUProduced = 0;
            String name = stack.getUnlocalizedName()
                .toLowerCase();
            if (name.contains("coolant") && mTemperature > 50.0) {
                // Coolant cell absorbs heat from this cell
                double heatToAbsorb = Math.min(mTemperature - 50.0, 100.0) * NuclearSimulationEngine.EU_PER_DEGREE;
                if (heatToAbsorb > 0) {
                    mTemperature -= (heatToAbsorb / NuclearSimulationEngine.EU_PER_DEGREE);
                    int cellDamage = Math.max(1, (int) (heatToAbsorb / 50.0));
                    damageComponent(cellDamage);
                }
            }
        }

        // Reset transient flux counters for next tick's display
        mFastFlux = 0;
        mThermalFlux = 0;
        mFastAbsorbed = 0;
        mThermalAbsorbed = 0;
    }

    private void damageComponent(int damage) {
        ItemStack stack = mInventory[SLOT_INPUT];
        if (stack == null || !stack.isItemStackDamageable()) return;

        int newDamage = stack.getItemDamage() + damage;
        if (newDamage >= stack.getMaxDamage()) {
            ItemStack depleted = getDepletedForm(stack);
            mInventory[SLOT_INPUT] = null;
            ejectToOutput(depleted);
        } else {
            stack.setItemDamage(newDamage);
        }
        markTileDirty();
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

    private boolean ejectToOutput(ItemStack stack) {
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

    private ItemStack getDepletedForm(ItemStack fuel) {
        if (fuel == null) return null;
        String name = fuel.getUnlocalizedName()
            .toLowerCase();
        if (name.contains("uranium")) {
            if (name.contains("quad") || name.contains("4"))
                return gregtech.api.enums.ItemList.DepletedRodUranium4.get(1L);
            if (name.contains("dual") || name.contains("2"))
                return gregtech.api.enums.ItemList.DepletedRodUranium2.get(1L);
            return gregtech.api.enums.ItemList.DepletedRodUranium.get(1L);
        } else if (name.contains("mox")) {
            if (name.contains("quad") || name.contains("4")) return gregtech.api.enums.ItemList.DepletedRodMOX4.get(1L);
            if (name.contains("dual") || name.contains("2")) return gregtech.api.enums.ItemList.DepletedRodMOX2.get(1L);
            return gregtech.api.enums.ItemList.DepletedRodMOX.get(1L);
        } else if (name.contains("thorium")) {
            if (name.contains("quad") || name.contains("4"))
                return gregtech.api.enums.ItemList.DepletedRodThorium4.get(1L);
            if (name.contains("dual") || name.contains("2"))
                return gregtech.api.enums.ItemList.DepletedRodThorium2.get(1L);
            return gregtech.api.enums.ItemList.DepletedRodThorium.get(1L);
        } else if (name.contains("naquadah")) {
            if (name.contains("quad") || name.contains("4"))
                return gregtech.api.enums.ItemList.DepletedRodNaquadah4.get(1L);
            if (name.contains("dual") || name.contains("2"))
                return gregtech.api.enums.ItemList.DepletedRodNaquadah2.get(1L);
            return gregtech.api.enums.ItemList.DepletedRodNaquadah.get(1L);
        }
        return null;
    }

    private static class DummyReactor implements IReactor {

        private final MTEHatchNuclearBus bus;

        DummyReactor(MTEHatchNuclearBus bus) {
            this.bus = bus;
        }

        @Override
        public ChunkCoordinates getPosition() {
            if (bus.getBaseMetaTileEntity() == null) return new ChunkCoordinates(0, 0, 0);
            return new ChunkCoordinates(
                bus.getBaseMetaTileEntity()
                    .getXCoord(),
                bus.getBaseMetaTileEntity()
                    .getYCoord(),
                bus.getBaseMetaTileEntity()
                    .getZCoord());
        }

        @Override
        public World getWorld() {
            return bus.getBaseMetaTileEntity() != null ? bus.getBaseMetaTileEntity()
                .getWorld() : null;
        }

        @Override
        public int getHeat() {
            return (int) bus.mTemperature;
        }

        @Override
        public void setHeat(int heat) {
            bus.mTemperature = heat;
        }

        @Override
        public int addHeat(int amount) {
            bus.mTemperature += amount;
            return (int) bus.mTemperature;
        }

        @Override
        public int getMaxHeat() {
            return 10000;
        }

        @Override
        public void setMaxHeat(int maxHeat) {}

        @Override
        public void addEmitHeat(int heat) {}

        @Override
        public float getHeatEffectModifier() {
            return 1.0f;
        }

        @Override
        public void setHeatEffectModifier(float modifier) {}

        @Override
        public float getReactorEnergyOutput() {
            return 0;
        }

        @Override
        public double getReactorEUEnergyOutput() {
            return 0;
        }

        @Override
        public float addOutput(float energy) {
            return energy;
        }

        @Override
        public ItemStack getItemAt(int x, int y) {
            return bus.mInventory[SLOT_INPUT];
        }

        @Override
        public void setItemAt(int x, int y, ItemStack item) {
            bus.mInventory[SLOT_INPUT] = item;
        }

        @Override
        public void explode() {}

        @Override
        public int getTickRate() {
            return 20;
        }

        @Override
        public boolean produceEnergy() {
            return true;
        }

        @Override
        public void setRedstoneSignal(boolean redstone) {}

        @Override
        public boolean isFluidCooled() {
            return false;
        }
    }

    @Override
    public void addUIWidgets(ModularWindow.Builder builder, UIBuildContext buildContext) {
        builder.widget(
            new DrawableWidget().setDrawable(GTUITextures.PICTURE_SCREEN_BLACK)
                .setPos(7, 16)
                .setSize(100, 56))
            .widget(
                new TextWidget("Nuclear Core Bus").setDefaultColor(Color.rgb(0, 255, 128))
                    .setPos(10, 20))
            .widget(
                new TextWidget().setStringSupplier(() -> String.format("Temp: %.1f °C", mTemperature))
                    .setDefaultColor(Color.rgb(255, 200, 0))
                    .setPos(10, 32))
            .widget(
                new TextWidget().setStringSupplier(() -> String.format("Neutrons: %d/t", mLastNeutronsGenerated))
                    .setDefaultColor(Color.rgb(100, 200, 255))
                    .setPos(10, 44))
            .widget(
                new TextWidget("Input Rod").setDefaultColor(0xFFFFFFFF)
                    .setPos(115, 16))
            .widget(
                new SlotWidget(inventoryHandler, SLOT_INPUT)
                    .setBackground(getGUITextureSet().getItemSlot(), GTUITextures.OVERLAY_SLOT_IN)
                    .setPos(120, 28))
            .widget(
                new TextWidget("Outputs").setDefaultColor(0xFFFFFFFF)
                    .setPos(148, 16))
            .widget(
                new SlotWidget(inventoryHandler, SLOT_OUTPUT_1).setAccess(true, false)
                    .setBackground(getGUITextureSet().getItemSlot(), GTUITextures.OVERLAY_SLOT_OUT)
                    .setPos(145, 28))
            .widget(
                new SlotWidget(inventoryHandler, SLOT_OUTPUT_2).setAccess(true, false)
                    .setBackground(getGUITextureSet().getItemSlot(), GTUITextures.OVERLAY_SLOT_OUT)
                    .setPos(145, 48));
    }
}
