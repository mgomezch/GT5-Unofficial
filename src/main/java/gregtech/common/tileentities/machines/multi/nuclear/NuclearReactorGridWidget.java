package gregtech.common.tileentities.machines.multi.nuclear;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;

import com.gtnewhorizons.modularui.api.GlStateManager;
import com.gtnewhorizons.modularui.api.drawable.FluidDrawable;
import com.gtnewhorizons.modularui.api.drawable.ItemDrawable;
import com.gtnewhorizons.modularui.api.math.Pos2d;
import com.gtnewhorizons.modularui.api.screen.Cursor;
import com.gtnewhorizons.modularui.api.widget.Interactable;
import com.gtnewhorizons.modularui.api.widget.Widget;
import com.gtnewhorizons.modularui.common.widget.Scrollable;

import codechicken.lib.gui.GuiDraw;
import gregtech.api.gui.modularui.GTUITextures;

public class NuclearReactorGridWidget extends Widget implements Interactable {

    private final MTENuclearReactor reactor;
    private Scrollable parentScrollable;
    private int mZoomIndex = 0;

    public NuclearReactorGridWidget(MTENuclearReactor reactor) {
        this.reactor = reactor;
        setSize(126, 126);
        setTooltipShowUpDelay(0);
        setUpdateTooltipEveryTick(true);
        dynamicTooltip(this::getHoveredTooltip);
    }

    public void setParentScrollable(Scrollable parentScrollable) {
        this.parentScrollable = parentScrollable;
    }

    public int getGridSize() {
        ReactorGridSyncData sync = reactor.getClientGridData();
        return (sync != null && sync.gridSize > 0) ? sync.gridSize : 0;
    }

    public int[] getZoomCellSizes(int N) {
        if (N <= 0) return new int[] { 18 };
        int fit = (N <= 7) ? 18 : Math.max(4, 126 / N);
        TreeSet<Integer> sizes = new TreeSet<>();
        sizes.add(fit);
        if (fit < 14) sizes.add(14);
        if (fit < 18) sizes.add(18);
        sizes.add(24);
        sizes.add(32);
        sizes.add(40);
        return sizes.stream()
            .mapToInt(Integer::intValue)
            .toArray();
    }

    public int getCurrentCellSize() {
        int N = getGridSize();
        int[] sizes = getZoomCellSizes(N);
        int idx = Math.max(0, Math.min(mZoomIndex, sizes.length - 1));
        return sizes[idx];
    }

    public int getZoomPercent() {
        int N = getGridSize();
        if (N <= 0) return 100;
        int fit = (N <= 7) ? 18 : Math.max(4, 126 / N);
        int current = getCurrentCellSize();
        return Math.round(((float) current / (float) fit) * 100.0f);
    }

    public void zoomIn() {
        int[] sizes = getZoomCellSizes(getGridSize());
        if (mZoomIndex < sizes.length - 1) {
            mZoomIndex++;
            updateWidgetSize();
        }
    }

    public void zoomOut() {
        if (mZoomIndex > 0) {
            mZoomIndex--;
            updateWidgetSize();
        }
    }

    public void resetZoom() {
        mZoomIndex = 0;
        updateWidgetSize();
        if (parentScrollable != null) {
            parentScrollable.setHorizontalScrollOffset(0);
            parentScrollable.setVerticalScrollOffset(0);
        }
    }

    public void updateWidgetSize() {
        int N = getGridSize();
        int[] sizes = getZoomCellSizes(N);
        mZoomIndex = Math.max(0, Math.min(mZoomIndex, sizes.length - 1));
        int cellSize = sizes[mZoomIndex];
        int gridPx = N * cellSize;
        int targetW = Math.max(126, gridPx);
        int targetH = Math.max(126, gridPx);
        setSize(targetW, targetH);
        if (parentScrollable != null) {
            parentScrollable.onRebuild();
        }
    }

    @Override
    public void onScreenUpdate() {
        super.onScreenUpdate();
        int targetW = Math.max(126, getGridSize() * getCurrentCellSize());
        int targetH = Math.max(126, getGridSize() * getCurrentCellSize());
        if (getSize().width != targetW || getSize().height != targetH) {
            setSize(targetW, targetH);
            if (parentScrollable != null) {
                parentScrollable.onRebuild();
            }
        }
    }

    @Override
    public ClickResult onClick(int button, boolean isShiftDown) {
        if (parentScrollable != null) {
            return parentScrollable.onClick(button, isShiftDown);
        }
        return ClickResult.IGNORE;
    }

    @Override
    public void onMouseDragged(int button, long timeSinceLastClick) {
        if (parentScrollable != null) {
            parentScrollable.onMouseDragged(button, timeSinceLastClick);
        }
    }

    @Override
    public boolean onClickReleased(int button) {
        if (parentScrollable != null) {
            return parentScrollable.onClickReleased(button);
        }
        return false;
    }

    @Override
    public boolean onMouseScroll(int direction) {
        if (GuiScreen.isCtrlKeyDown()) {
            if (direction > 0) {
                zoomIn();
            } else {
                zoomOut();
            }
            return true;
        }
        if (parentScrollable != null) {
            return parentScrollable.onMouseScroll(direction);
        }
        return false;
    }

    @Override
    public void draw(float partialTicks) {
        ReactorGridSyncData sync = reactor.getClientGridData();
        if (sync == null || sync.gridSize <= 0) {
            String msg = "Offline / unformed";
            int w = GuiDraw.getStringWidth(msg);
            GuiDraw.drawString(msg, (126 - w) / 2, 58, 0x888888, false);
            return;
        }

        int N = sync.gridSize;
        int cellSize = getCurrentCellSize();
        int gridPx = N * cellSize;
        int offset = (gridPx < 126) ? (126 - gridPx) / 2 : 0;

        for (int gx = 0; gx < N; gx++) {
            for (int gy = 0; gy < N; gy++) {
                int renderGy = (N - 1) - gy;
                int px = offset + gx * cellSize;
                int py = offset + renderGy * cellSize;

                if (NuclearSimulationEngine.isCornerNullCell(gx, gy, N, N)) {
                    continue;
                }

                int idx = gx * N + gy;
                ReactorGridSyncData.ReactorGridCellData cell = (idx < sync.cells.size()) ? sync.cells.get(idx) : null;

                // Draw slot border / background
                if (cellSize == 18) {
                    GTUITextures.SLOT_DARK_GRAY.draw(px, py, 18, 18, partialTicks);
                } else {
                    GuiDraw.drawRect(px, py, cellSize, cellSize, 0xFF373737);
                    GuiDraw.drawRect(px + 1, py + 1, cellSize - 2, cellSize - 2, 0xFF1E1E1E);
                }

                if (cell != null && cell.exists) {
                    int innerSize = Math.max(1, cellSize - 2);
                    // Draw cell contents (Item or Fluid)
                    if (cell.itemStack != null) {
                        GlStateManager.pushMatrix();
                        GlStateManager.translate(px + 1, py + 1, 0);
                        new ItemDrawable(cell.itemStack).draw(0, 0, innerSize, innerSize, partialTicks);
                        GlStateManager.popMatrix();
                    } else if (cell.fluidStack != null) {
                        new FluidDrawable().setFluid(cell.fluidStack)
                            .draw(px + 1, py + 1, innerSize, innerSize, partialTicks);
                    } else if (cell.isFluid) {
                        // Empty coolant hatch: subtle blue tint
                        GuiDraw.drawRect(px + 1, py + 1, innerSize, innerSize, 0x300055AA);
                    }

                    // Mode Shading Overlays
                    if (reactor.mCurrentGuiMode == MTENuclearReactor.GUI_MODE_TEMPERATURE) {
                        double maxTemp = NuclearSimulationEngine.getMaxOperatingTemperature(sync.pipeTier);
                        int color = NuclearColorMaps.getTemperatureColor(cell.temperature, maxTemp);
                        GuiDraw.drawRect(px + 1, py + 1, innerSize, innerSize, color);
                    } else if (reactor.mCurrentGuiMode == MTENuclearReactor.GUI_MODE_NEUTRON_FLUX) {
                        int color = NuclearColorMaps.getNeutronColor(cell.fastFlux + cell.thermalFlux);
                        GuiDraw.drawRect(px + 1, py + 1, innerSize, innerSize, color);
                    } else if (reactor.mCurrentGuiMode == MTENuclearReactor.GUI_MODE_NEUTRON_ABSORPTION) {
                        int color = NuclearColorMaps.getNeutronColor(5.0 * (cell.fastAbsorbed + cell.thermalAbsorbed));
                        GuiDraw.drawRect(px + 1, py + 1, innerSize, innerSize, color);
                    }

                    // Overheating warning flash (> 85% safe temp limit)
                    double maxTemp = NuclearSimulationEngine.getMaxOperatingTemperature(sync.pipeTier);
                    if (cell.temperature > maxTemp * 0.85) {
                        if ((System.currentTimeMillis() / 400) % 2 == 0) {
                            GuiDraw.drawRect(px + 1, py + 1, innerSize, innerSize, 0x60FF0000);
                        }
                    }
                }
            }
        }

        // Slot Hover Highlight
        if ((isHovering() || (parentScrollable != null && parentScrollable.isHovering())) && getContext() != null) {
            Cursor cursor = getContext().getCursor();
            if (cursor != null) {
                Pos2d spos = parentScrollable != null ? parentScrollable.getAbsolutePos() : getAbsolutePos();
                int cx = cursor.getX();
                int cy = cursor.getY();
                int spx = spos.x;
                int spy = spos.y;
                int spw = parentScrollable != null ? parentScrollable.getSize().width : getSize().width;
                int sph = parentScrollable != null ? parentScrollable.getSize().height : getSize().height;
                if (cx >= spx && cx < spx + spw && cy >= spy && cy < spy + sph) {
                    int scrollX = parentScrollable != null ? parentScrollable.getHorizontalScrollOffset() : 0;
                    int scrollY = parentScrollable != null ? parentScrollable.getVerticalScrollOffset() : 0;
                    int mx = cx - spx + scrollX;
                    int my = cy - spy + scrollY;
                    int hx = (mx - offset) / cellSize;
                    int renderHy = (my - offset) / cellSize;
                    int hy = (N - 1) - renderHy;
                    if (hx >= 0 && hx < N && hy >= 0 && hy < N) {
                        if (!NuclearSimulationEngine.isCornerNullCell(hx, hy, N, N)) {
                            int hpx = offset + hx * cellSize;
                            int hpy = offset + renderHy * cellSize;
                            GuiDraw.drawRect(
                                hpx + 1,
                                hpy + 1,
                                Math.max(1, cellSize - 2),
                                Math.max(1, cellSize - 2),
                                0x80FFFFFF);
                        }
                    }
                }
            }
        }
    }

    public List<String> getHoveredTooltip() {
        List<String> list = new ArrayList<>();
        if ((!isHovering() && (parentScrollable == null || !parentScrollable.isHovering())) || getContext() == null) {
            return list;
        }
        ReactorGridSyncData sync = reactor.getClientGridData();
        if (sync == null || sync.gridSize <= 0) return list;

        Cursor cursor = getContext().getCursor();
        if (cursor == null) return list;

        Pos2d spos = parentScrollable != null ? parentScrollable.getAbsolutePos() : getAbsolutePos();
        int cx = cursor.getX();
        int cy = cursor.getY();
        int spx = spos.x;
        int spy = spos.y;
        int spw = parentScrollable != null ? parentScrollable.getSize().width : getSize().width;
        int sph = parentScrollable != null ? parentScrollable.getSize().height : getSize().height;
        if (cx < spx || cx >= spx + spw || cy < spy || cy >= spy + sph) {
            return list;
        }

        int N = sync.gridSize;
        int cellSize = getCurrentCellSize();
        int gridPx = N * cellSize;
        int offset = (gridPx < 126) ? (126 - gridPx) / 2 : 0;

        int scrollX = parentScrollable != null ? parentScrollable.getHorizontalScrollOffset() : 0;
        int scrollY = parentScrollable != null ? parentScrollable.getVerticalScrollOffset() : 0;
        int mx = cx - spx + scrollX;
        int my = cy - spy + scrollY;
        int hx = (mx - offset) / cellSize;
        int renderHy = (my - offset) / cellSize;
        int hy = (N - 1) - renderHy;
        if (hx < 0 || hx >= N || hy < 0 || hy >= N) return list;

        if (NuclearSimulationEngine.isCornerNullCell(hx, hy, N, N)) {
            return list;
        }

        int idx = hx * N + hy;
        if (idx < 0 || idx >= sync.cells.size()) return list;
        ReactorGridSyncData.ReactorGridCellData cell = sync.cells.get(idx);
        if (cell == null || !cell.exists) {
            list.add(EnumChatFormatting.GRAY + "Empty core position (" + hx + ", " + hy + ")");
            return list;
        }

        double maxTemp = NuclearSimulationEngine.getMaxOperatingTemperature(sync.pipeTier);

        // 1. Component Identity
        if (cell.itemStack != null) {
            list.add(EnumChatFormatting.WHITE + cell.itemStack.getDisplayName());
            if (cell.itemStack.stackSize > 1) {
                list.add(EnumChatFormatting.GRAY + "Amount: " + cell.itemStack.stackSize);
            }
        } else if (cell.fluidStack != null) {
            list.add(EnumChatFormatting.AQUA + cell.fluidStack.getLocalizedName());
            if (MTENuclearReactor.isFluidFuel(cell.fluidStack)) {
                list.add(EnumChatFormatting.GOLD + "Nuclear liquid fuel");
            }
            list.add(EnumChatFormatting.GRAY + String.format("Amount: %,d L", cell.fluidStack.amount));
        } else if (cell.isFluid) {
            list.add(EnumChatFormatting.GRAY + "Empty nuclear fluid hatch");
        } else {
            list.add(EnumChatFormatting.GRAY + "Empty nuclear component bus");
        }

        // 2. Position
        list.add(EnumChatFormatting.DARK_GRAY + "Position: (" + hx + ", " + hy + ")");

        // 3. Thermal Telemetry
        String tempColor;
        if (cell.temperature > maxTemp) {
            tempColor = EnumChatFormatting.DARK_RED.toString();
        } else if (cell.temperature > maxTemp * 0.85) {
            tempColor = EnumChatFormatting.RED.toString();
        } else if (cell.temperature > maxTemp * 0.5) {
            tempColor = EnumChatFormatting.YELLOW.toString();
        } else {
            tempColor = EnumChatFormatting.GREEN.toString();
        }

        list.add(
            EnumChatFormatting.GRAY + "Temperature: "
                + tempColor
                + String.format("%.1f °C", cell.temperature)
                + EnumChatFormatting.DARK_GRAY
                + String.format(" (safe limit: %.0f °C)", maxTemp));

        if (cell.temperature > maxTemp) {
            list.add(EnumChatFormatting.RED + "" + EnumChatFormatting.BOLD + "Status: melting point exceeded!");
        } else if (cell.temperature > maxTemp * 0.85) {
            list.add(EnumChatFormatting.RED + "Status: thermal warning - high temperature");
        } else if (cell.temperature > maxTemp * 0.5) {
            list.add(EnumChatFormatting.YELLOW + "Status: operating - elevated temperature");
        } else {
            list.add(EnumChatFormatting.GREEN + "Status: nominal operation");
        }

        // 4. Neutron Telemetry
        int totalFlux = cell.fastFlux + cell.thermalFlux;
        if (totalFlux > 0) {
            list.add(
                EnumChatFormatting.GRAY + "Neutron flux: "
                    + EnumChatFormatting.AQUA
                    + String.format("%,d /s", totalFlux)
                    + EnumChatFormatting.DARK_GRAY
                    + String.format(" (%d fast, %d thermal)", cell.fastFlux, cell.thermalFlux));
        }

        int totalAbs = cell.fastAbsorbed + cell.thermalAbsorbed;
        if (totalAbs > 0) {
            list.add(
                EnumChatFormatting.GRAY + "Neutrons absorbed: "
                    + EnumChatFormatting.LIGHT_PURPLE
                    + String.format("%,d /s", totalAbs)
                    + EnumChatFormatting.DARK_GRAY
                    + String.format(" (%d fast, %d thermal)", cell.fastAbsorbed, cell.thermalAbsorbed));
        }

        // 5. Betavoltaic Power
        if (cell.directEU > 0) {
            list.add(
                EnumChatFormatting.AQUA + "Betavoltaic power: "
                    + EnumChatFormatting.GREEN
                    + "+"
                    + String.format("%,d", cell.directEU)
                    + " EU/t");
        }

        // 6. Mode Context Telemetry
        if (reactor.mCurrentGuiMode == MTENuclearReactor.GUI_MODE_TEMPERATURE) {
            float delta = cell.temperature - sync.avgTemp;
            String deltaSign = delta >= 0 ? "+" : "";
            list.add(EnumChatFormatting.DARK_GRAY + String.format("Core temp delta: %s%.1f °C", deltaSign, delta));
        } else if (reactor.mCurrentGuiMode == MTENuclearReactor.GUI_MODE_NEUTRON_FLUX) {
            if (sync.neutronsProduced > 0 && totalFlux > 0) {
                double share = ((double) totalFlux / sync.neutronsProduced) * 100.0;
                list.add(EnumChatFormatting.DARK_GRAY + String.format("Core flux share: %.1f%%", share));
            }
        } else if (reactor.mCurrentGuiMode == MTENuclearReactor.GUI_MODE_NEUTRON_ABSORPTION) {
            int totalCoreAbs = sync.fastAbsorbed + sync.thermalAbsorbed;
            if (totalCoreAbs > 0 && totalAbs > 0) {
                double share = ((double) totalAbs / totalCoreAbs) * 100.0;
                list.add(EnumChatFormatting.DARK_GRAY + String.format("Core absorption share: %.1f%%", share));
            }
        }

        return list;
    }
}
