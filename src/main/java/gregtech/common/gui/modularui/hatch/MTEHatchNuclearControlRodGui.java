package gregtech.common.gui.modularui.hatch;

import net.minecraft.item.ItemStack;
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
import gregtech.common.tileentities.machines.multi.nuclear.MTEHatchNuclearControlRod;

public class MTEHatchNuclearControlRodGui extends MTEHatchBaseGui<MTEHatchNuclearControlRod> {

    public MTEHatchNuclearControlRodGui(MTEHatchNuclearControlRod machine) {
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
            "fastAbsorbed",
            new IntSyncValue(() -> machine.mLastFastAbsorbed, val -> machine.mLastFastAbsorbed = val));
        syncManager.syncValue(
            "thermalAbsorbed",
            new IntSyncValue(() -> machine.mLastThermalAbsorbed, val -> machine.mLastThermalAbsorbed = val));
        syncManager.syncValue(
            "scram",
            new com.cleanroommc.modularui.value.sync.BooleanSyncValue(
                () -> machine.mScram,
                val -> machine.mScram = val));
    }

    @Override
    protected ParentWidget<?> createContentSection(ModularPanel panel, PanelSyncManager syncManager) {
        Flow mainRow = Flow.row()
            .coverChildren()
            .childPadding(4);

        // Status and Telemetry Screen (width 110, height 54)
        ParentWidget<?> statsScreen = CommonWidgets.createFluidScreen(110, 54);
        Flow textColumn = Flow.column()
            .childPadding(1)
            .crossAxisAlignment(Alignment.CrossAxis.START);

        textColumn.child(
            IKey.dynamic(() -> EnumChatFormatting.LIGHT_PURPLE + "Control rod hatch")
                .asWidget()
                .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE));

        textColumn.child(
            IKey.dynamic(() -> EnumChatFormatting.GOLD + String.format("Temp: %.1f °C", machine.mTemperature))
                .asWidget()
                .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE));

        textColumn.child(IKey.dynamic(() -> {
            if (machine.mScram) {
                return EnumChatFormatting.RED + "Insert: 100% (SCRAMMED)";
            }
            return EnumChatFormatting.GREEN
                + String.format("Insert: %d%% (RS: %d)", machine.getInsertionPercent(), machine.getRedstoneSignal());
        })
            .asWidget()
            .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE));

        textColumn.child(IKey.dynamic(() -> {
            ItemStack rod = machine.mInventory[MTEHatchNuclearControlRod.SLOT_ROD];
            String rodName = MTEHatchNuclearControlRod.getRodType(rod).displayName;
            return EnumChatFormatting.AQUA + "Rod: " + rodName;
        })
            .asWidget()
            .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE));

        textColumn.child(
            IKey.dynamic(
                () -> EnumChatFormatting.GRAY
                    + String.format("Absorbed: %d n/t", machine.mLastFastAbsorbed + machine.mLastThermalAbsorbed))
                .asWidget()
                .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE));

        statsScreen.child(textColumn);
        mainRow.child(statsScreen);

        // Control Rod Slot Column
        Flow slotCol = Flow.column()
            .coverChildren()
            .childPadding(1)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER);

        slotCol.child(
            IKey.dynamic(() -> EnumChatFormatting.WHITE + "Rod")
                .asWidget()
                .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE));

        slotCol.child(
            new ItemSlot()
                .slot(
                    new ModularSlot(machine.inventoryHandler, MTEHatchNuclearControlRod.SLOT_ROD).singletonSlotGroup())
                .backgroundOverlay(GTGuiTextures.OVERLAY_SLOT_IN_STANDARD));

        mainRow.child(slotCol);

        return mainRow;
    }
}
