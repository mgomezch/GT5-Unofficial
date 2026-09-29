package gregtech.common.tileentities.machines.multi.nuclear;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.modularui.common.internal.network.NetworkUtils;

import cpw.mods.fml.common.network.ByteBufUtils;

public class ReactorGridSyncData {

    public int gridSize = 0;
    public int coreDimension = 0;
    public float coreTemp = 20.0f;
    public float avgTemp = 20.0f;
    public double efficiency = 0.0;
    public int pipeTier = 0;
    public long directPowerEUt = 0;
    public int neutronsProduced = 0;
    public int fastAbsorbed = 0;
    public int thermalAbsorbed = 0;
    public int escapedNeutrons = 0;
    public int outputCoolantRate = 0;
    public String outputCoolantName = "";
    public List<ReactorGridCellData> cells = new ArrayList<>();

    public static class ReactorGridCellData {

        public boolean exists = false;
        public boolean isFluid = false;
        public ItemStack itemStack = null;
        public FluidStack fluidStack = null;
        public float temperature = 20.0f;
        public int fastFlux = 0;
        public int thermalFlux = 0;
        public int fastAbsorbed = 0;
        public int thermalAbsorbed = 0;
        public long directEU = 0;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ReactorGridCellData that = (ReactorGridCellData) o;
            if (exists != that.exists || isFluid != that.isFluid) return false;
            if (Math.abs(temperature - that.temperature) > 0.5f) return false;
            if (fastFlux != that.fastFlux || thermalFlux != that.thermalFlux) return false;
            if (fastAbsorbed != that.fastAbsorbed || thermalAbsorbed != that.thermalAbsorbed) return false;
            if (directEU != that.directEU) return false;
            if (!ItemStack.areItemStacksEqual(itemStack, that.itemStack)) return false;
            if (fluidStack == null ? that.fluidStack != null : !fluidStack.isFluidStackIdentical(that.fluidStack))
                return false;
            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hash(exists, isFluid, (int) temperature, fastFlux, thermalFlux);
        }
    }

    public static void writeToBuffer(PacketBuffer buf, ReactorGridSyncData data) {
        if (data == null) {
            buf.writeByte(0);
            return;
        }
        buf.writeByte(data.gridSize);
        buf.writeByte(data.coreDimension);
        buf.writeFloat(data.coreTemp);
        buf.writeFloat(data.avgTemp);
        buf.writeFloat((float) data.efficiency);
        buf.writeByte(data.pipeTier);
        buf.writeLong(data.directPowerEUt);
        buf.writeVarIntToBuffer(data.neutronsProduced);
        buf.writeVarIntToBuffer(data.fastAbsorbed);
        buf.writeVarIntToBuffer(data.thermalAbsorbed);
        buf.writeVarIntToBuffer(data.escapedNeutrons);
        buf.writeVarIntToBuffer(data.outputCoolantRate);
        ByteBufUtils.writeUTF8String(buf, data.outputCoolantName != null ? data.outputCoolantName : "");

        buf.writeVarIntToBuffer(data.cells.size());
        for (ReactorGridCellData cell : data.cells) {
            int mask = 0;
            if (cell.exists) mask |= 1;
            if (cell.isFluid) mask |= 2;
            if (cell.itemStack != null) mask |= 4;
            if (cell.fluidStack != null) mask |= 8;
            buf.writeByte(mask);
            if (cell.exists) {
                buf.writeFloat(cell.temperature);
                buf.writeVarIntToBuffer(cell.fastFlux);
                buf.writeVarIntToBuffer(cell.thermalFlux);
                buf.writeVarIntToBuffer(cell.fastAbsorbed);
                buf.writeVarIntToBuffer(cell.thermalAbsorbed);
                buf.writeLong(cell.directEU);
                if (cell.itemStack != null) {
                    NetworkUtils.writeItemStack(buf, cell.itemStack);
                }
                if (cell.fluidStack != null) {
                    buf.writeVarIntToBuffer(cell.fluidStack.getFluidID());
                    buf.writeVarIntToBuffer(cell.fluidStack.amount);
                }
            }
        }
    }

    public static ReactorGridSyncData readFromBuffer(PacketBuffer buf) {
        ReactorGridSyncData data = new ReactorGridSyncData();
        data.gridSize = buf.readByte();
        if (data.gridSize == 0) return data;

        data.coreDimension = buf.readByte();
        data.coreTemp = buf.readFloat();
        data.avgTemp = buf.readFloat();
        data.efficiency = buf.readFloat();
        data.pipeTier = buf.readByte();
        data.directPowerEUt = buf.readLong();
        data.neutronsProduced = buf.readVarIntFromBuffer();
        data.fastAbsorbed = buf.readVarIntFromBuffer();
        data.thermalAbsorbed = buf.readVarIntFromBuffer();
        data.escapedNeutrons = buf.readVarIntFromBuffer();
        data.outputCoolantRate = buf.readVarIntFromBuffer();
        data.outputCoolantName = ByteBufUtils.readUTF8String(buf);

        int cellCount = buf.readVarIntFromBuffer();
        for (int i = 0; i < cellCount; i++) {
            ReactorGridCellData cell = new ReactorGridCellData();
            int mask = buf.readByte();
            cell.exists = (mask & 1) != 0;
            cell.isFluid = (mask & 2) != 0;
            boolean hasItem = (mask & 4) != 0;
            boolean hasFluid = (mask & 8) != 0;
            if (cell.exists) {
                cell.temperature = buf.readFloat();
                cell.fastFlux = buf.readVarIntFromBuffer();
                cell.thermalFlux = buf.readVarIntFromBuffer();
                cell.fastAbsorbed = buf.readVarIntFromBuffer();
                cell.thermalAbsorbed = buf.readVarIntFromBuffer();
                cell.directEU = buf.readLong();
                if (hasItem) {
                    cell.itemStack = NetworkUtils.readItemStack(buf);
                }
                if (hasFluid) {
                    int fluidId = buf.readVarIntFromBuffer();
                    int amount = buf.readVarIntFromBuffer();
                    Fluid fluid = FluidRegistry.getFluid(fluidId);
                    if (fluid != null) {
                        cell.fluidStack = new FluidStack(fluid, amount);
                    }
                }
            }
            data.cells.add(cell);
        }
        return data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReactorGridSyncData that = (ReactorGridSyncData) o;
        if (gridSize != that.gridSize || pipeTier != that.pipeTier || directPowerEUt != that.directPowerEUt)
            return false;
        if (Math.abs(coreTemp - that.coreTemp) > 0.5f) return false;
        if (Math.abs(avgTemp - that.avgTemp) > 0.5f) return false;
        if (Math.abs(efficiency - that.efficiency) > 0.005) return false;
        if (cells.size() != that.cells.size()) return false;
        for (int i = 0; i < cells.size(); i++) {
            if (!cells.get(i)
                .equals(that.cells.get(i))) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(gridSize, (int) coreTemp, pipeTier, cells.size());
    }
}
