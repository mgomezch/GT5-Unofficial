package gregtech.common.gui.modularui.hatch;

import net.minecraft.util.EnumChatFormatting;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;

import gregtech.api.modularui2.GTGuiTextures;
import gregtech.api.modularui2.GTWidgetThemes;
import gregtech.api.modularui2.common.CommonWidgets;
import gregtech.common.gui.modularui.hatch.base.MTEHatchBaseGui;
import gregtech.common.tileentities.machines.multi.nuclear.MTEHatchNuclearBus;

public class MTEHatchNuclearBusGui extends MTEHatchBaseGui<MTEHatchNuclearBus> {

    public MTEHatchNuclearBusGui(MTEHatchNuclearBus machine) {
        super(machine);
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        syncManager.syncValue(
            "temperature",
            new DoubleSyncValue(() -> machine.mTemperature, val -> machine.mTemperature = val));
        syncManager
            .syncValue("fastFlux", new IntSyncValue(() -> machine.mLastFastFlux, val -> machine.mLastFastFlux = val));
        syncManager.syncValue(
            "thermalFlux",
            new IntSyncValue(() -> machine.mLastThermalFlux, val -> machine.mLastThermalFlux = val));
        syncManager.syncValue(
            "neutronsGen",
            new IntSyncValue(() -> machine.mLastNeutronsGenerated, val -> machine.mLastNeutronsGenerated = val));
    }

    @Override
    protected ParentWidget<?> createContentSection(ModularPanel panel, PanelSyncManager syncManager) {
        Flow mainRow = Flow.row()
            .coverChildren()
            .childPadding(6);

        // Status and Telemetry Screen (width 118, height 54)
        ParentWidget<?> statsScreen = CommonWidgets.createFluidScreen(118, 54);
        Flow textColumn = Flow.column()
            .childPadding(1)
            .crossAxisAlignment(Alignment.CrossAxis.START);

        textColumn.child(
            IKey.dynamic(() -> EnumChatFormatting.GREEN + "Nuclear core bus")
                .asWidget()
                .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE));

        textColumn.child(
            IKey.dynamic(() -> EnumChatFormatting.GOLD + String.format("Temp: %.1f °C", machine.mTemperature))
                .asWidget()
                .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE));

        textColumn.child(
            IKey.dynamic(() -> EnumChatFormatting.AQUA + String.format("Fast: %d n/t", machine.mLastFastFlux))
                .asWidget()
                .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE));

        textColumn.child(
            IKey.dynamic(() -> EnumChatFormatting.BLUE + String.format("Thermal: %d n/t", machine.mLastThermalFlux))
                .asWidget()
                .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE));

        textColumn.child(IKey.dynamic(() -> {
            net.minecraft.item.ItemStack stack = machine.mInventory[MTEHatchNuclearBus.SLOT_INPUT];
            double damp = gregtech.common.tileentities.machines.multi.nuclear.MTENuclearReactor
                .getInsulationDampening(stack);
            if (damp >= 1.0) {
                return EnumChatFormatting.DARK_PURPLE + "Insulation: 100% (universal)";
            } else if (damp > 0.0) {
                return EnumChatFormatting.LIGHT_PURPLE + String.format("Insulation: %.0f%%", damp * 100.0);
            }
            return EnumChatFormatting.GRAY + String.format("Neutrons: %d/t", machine.mLastNeutronsGenerated);
        })
            .asWidget()
            .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE));

        statsScreen.child(textColumn);
        mainRow.child(statsScreen);

        // Input Slot Column (Slot 0 - active rod / component)
        Flow inputCol = Flow.column()
            .coverChildren()
            .childPadding(1)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER);

        inputCol.child(
            IKey.dynamic(() -> EnumChatFormatting.WHITE + "In")
                .asWidget()
                .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE));

        inputCol.child(
            new ItemSlot()
                .slot(new ModularSlot(machine.inventoryHandler, MTEHatchNuclearBus.SLOT_INPUT).singletonSlotGroup())
                .backgroundOverlay(GTGuiTextures.OVERLAY_SLOT_IN_STANDARD));

        mainRow.child(inputCol);

        return super.createContentSection(panel, syncManager).child(mainRow);
    }
}
