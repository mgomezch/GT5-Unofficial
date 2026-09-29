package gregtech.common.tileentities.machines.multi.nuclear;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.EnumChatFormatting;
import codechicken.lib.gui.GuiDraw;
import com.gtnewhorizons.modularui.api.drawable.FluidDrawable;
import com.gtnewhorizons.modularui.api.drawable.ItemDrawable;
import com.gtnewhorizons.modularui.api.screen.Cursor;
import com.gtnewhorizons.modularui.api.widget.Widget;
import gregtech.api.gui.modularui.GTUITextures;

public class NuclearReactorGridWidget extends Widget {

    private final MTENuclearReactor reactor;

    public NuclearReactorGridWidget(MTENuclearReactor reactor) {
        this.reactor = reactor;
        setSize(126, 126);
        setUpdateTooltipEveryTick(true);
        dynamicTooltip(this::getHoveredTooltip);
    }

    @Override
    public void draw(float partialTicks) {
        ReactorGridSyncData sync = reactor.getClientGridData();
        if (sync == null || sync.gridSize <= 0) {
            String msg = "Offline / Unformed";
            int w = GuiDraw.getStringWidth(msg);
            GuiDraw.drawString(msg, (126 - w) / 2, 58, 0x888888, false);
            return;
        }

        int N = sync.gridSize;
        int offset = (126 - N * 18) / 2;

        for (int gx = 0; gx < N; gx++) {
            for (int gy = 0; gy < N; gy++) {
                int px = offset + gx * 18;
                int py = offset + gy * 18;
                int idx = gx * N + gy;
                ReactorGridSyncData.ReactorGridCellData cell = (idx < sync.cells.size()) ? sync.cells.get(idx) : null;

                // Draw slot border / background
                GTUITextures.SLOT_DARK_GRAY.draw(px, py, 18, 18, partialTicks);

                if (cell != null && cell.exists) {
                    // Draw cell contents (Item or Fluid)
                    if (cell.itemStack != null) {
                        new ItemDrawable(cell.itemStack).draw(px + 1, py + 1, 16, 16, partialTicks);
                    } else if (cell.fluidStack != null) {
                        new FluidDrawable().setFluid(cell.fluidStack).draw(px + 1, py + 1, 16, 16, partialTicks);
                    } else if (cell.isFluid) {
                        // Empty coolant hatch: subtle blue tint
                        GuiDraw.drawRect(px + 1, py + 1, 16, 16, 0x300055AA);
                    }

                    // Mode Shading Overlays
                    if (reactor.mCurrentGuiMode == MTENuclearReactor.GUI_MODE_TEMPERATURE) {
                        double maxTemp = NuclearSimulationEngine.getMaxOperatingTemperature(sync.pipeTier);
                        int color = NuclearColorMaps.getTemperatureColor(cell.temperature, maxTemp);
                        GuiDraw.drawRect(px + 1, py + 1, 16, 16, color);
                    } else if (reactor.mCurrentGuiMode == MTENuclearReactor.GUI_MODE_NEUTRON_FLUX) {
                        int color = NuclearColorMaps.getNeutronColor(cell.fastFlux + cell.thermalFlux);
                        GuiDraw.drawRect(px + 1, py + 1, 16, 16, color);
                    } else if (reactor.mCurrentGuiMode == MTENuclearReactor.GUI_MODE_NEUTRON_ABSORPTION) {
                        int color = NuclearColorMaps.getNeutronColor(5.0 * (cell.fastAbsorbed + cell.thermalAbsorbed));
                        GuiDraw.drawRect(px + 1, py + 1, 16, 16, color);
                    }

                    // Overheating warning flash (> 85% safe temp limit)
                    double maxTemp = NuclearSimulationEngine.getMaxOperatingTemperature(sync.pipeTier);
                    if (cell.temperature > maxTemp * 0.85) {
                        if ((System.currentTimeMillis() / 400) % 2 == 0) {
                            GuiDraw.drawRect(px + 1, py + 1, 16, 16, 0x60FF0000);
                        }
                    }
                }
            }
        }

        // Slot Hover Highlight
        if (isHovering() && getContext() != null) {
            Cursor cursor = getContext().getCursor();
            if (cursor != null) {
                int mx = cursor.getX() - getPos().x;
                int my = cursor.getY() - getPos().y;
                int hx = (mx - offset) / 18;
                int hy = (my - offset) / 18;
                if (hx >= 0 && hx < N && hy >= 0 && hy < N) {
                    int hpx = offset + hx * 18;
                    int hpy = offset + hy * 18;
                    GuiDraw.drawRect(hpx + 1, hpy + 1, 16, 16, 0x80FFFFFF);
                }
            }
        }
    }

    public List<String> getHoveredTooltip() {
        List<String> list = new ArrayList<>();
        if (!isHovering() || getContext() == null) return list;
        ReactorGridSyncData sync = reactor.getClientGridData();
        if (sync == null || sync.gridSize <= 0) return list;

        int N = sync.gridSize;
        int offset = (126 - N * 18) / 2;
        Cursor cursor = getContext().getCursor();
        if (cursor == null) return list;

        int mx = cursor.getX() - getPos().x;
        int my = cursor.getY() - getPos().y;
        int hx = (mx - offset) / 18;
        int hy = (my - offset) / 18;
        if (hx < 0 || hx >= N || hy < 0 || hy >= N) return list;

        int idx = hx * N + hy;
        if (idx < 0 || idx >= sync.cells.size()) return list;
        ReactorGridSyncData.ReactorGridCellData cell = sync.cells.get(idx);
        if (cell == null || !cell.exists) {
            list.add(EnumChatFormatting.GRAY + "Empty Core Position (" + hx + ", " + hy + ")");
            return list;
        }

        double maxTemp = NuclearSimulationEngine.getMaxOperatingTemperature(sync.pipeTier);

        if (reactor.mCurrentGuiMode == MTENuclearReactor.GUI_MODE_TEMPERATURE) {
            list.add(EnumChatFormatting.GOLD + "Temperature: " + EnumChatFormatting.YELLOW + String.format("%.1f °C", cell.temperature));
            if (cell.itemStack != null) {
                list.add(EnumChatFormatting.WHITE + cell.itemStack.getDisplayName());
            } else if (cell.fluidStack != null) {
                list.add(EnumChatFormatting.AQUA + cell.fluidStack.getLocalizedName() + " (" + String.format("%,d", cell.fluidStack.amount) + " L)");
            } else if (cell.isFluid) {
                list.add(EnumChatFormatting.DARK_GRAY + "Empty Coolant Hatch");
            } else {
                list.add(EnumChatFormatting.DARK_GRAY + "Empty Component Bus");
            }
            list.add(EnumChatFormatting.DARK_GRAY + String.format("Max Safe Temp: %.0f °C", maxTemp));
            if (cell.temperature > maxTemp * 0.85) {
                list.add(EnumChatFormatting.RED + "WARNING: THERMAL LIMIT APPROACHING");
            } else {
                list.add(EnumChatFormatting.GREEN + "Thermal Stability: Nominal");
            }
            list.add(EnumChatFormatting.GRAY + String.format("Neutrons: %d fast/s, %d thermal/s", cell.fastFlux, cell.thermalFlux));
        } else {
            // Component mode
            if (cell.itemStack != null) {
                list.add(EnumChatFormatting.WHITE + cell.itemStack.getDisplayName());
                if (cell.itemStack.stackSize > 1) {
                    list.add(EnumChatFormatting.GRAY + "Amount: " + cell.itemStack.stackSize);
                }
                if (cell.directEU > 0) {
                    list.add(EnumChatFormatting.AQUA + "Betavoltaic Power: " + EnumChatFormatting.GREEN + "+" + cell.directEU + " EU/t");
                }
            } else if (cell.fluidStack != null) {
                list.add(EnumChatFormatting.AQUA + cell.fluidStack.getLocalizedName());
                list.add(EnumChatFormatting.GRAY + String.format("Amount: %,d L", cell.fluidStack.amount));
            } else if (cell.isFluid) {
                list.add(EnumChatFormatting.GRAY + "Empty Nuclear Coolant Hatch");
            } else {
                list.add(EnumChatFormatting.GRAY + "Empty Nuclear Component Bus");
            }

            String tempColor = (cell.temperature > maxTemp * 0.85) ? EnumChatFormatting.RED.toString()
                : (cell.temperature > maxTemp * 0.5) ? EnumChatFormatting.YELLOW.toString()
                : EnumChatFormatting.GREEN.toString();
            list.add(EnumChatFormatting.GRAY + "Temperature: " + tempColor + String.format("%.1f °C", cell.temperature));

            if (cell.fastFlux > 0 || cell.thermalFlux > 0) {
                list.add(EnumChatFormatting.DARK_GRAY + String.format("Flux: %d fast, %d thermal", cell.fastFlux, cell.thermalFlux));
            }
            if (cell.fastAbsorbed > 0 || cell.thermalAbsorbed > 0) {
                list.add(EnumChatFormatting.DARK_GRAY + String.format("Absorbed: %d fast, %d thermal", cell.fastAbsorbed, cell.thermalAbsorbed));
            }
        }
        return list;
    }
}
