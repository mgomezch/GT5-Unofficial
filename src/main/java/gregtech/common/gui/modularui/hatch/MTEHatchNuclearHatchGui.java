package gregtech.common.gui.modularui.hatch;

import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;

import net.minecraft.util.EnumChatFormatting;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;

import gregtech.api.modularui2.GTGuiTextures;
import gregtech.api.modularui2.GTWidgetThemes;
import gregtech.api.modularui2.common.CommonWidgets;
import gregtech.common.gui.modularui.hatch.base.MTEHatchBaseGui;
import gregtech.common.tileentities.machines.multi.nuclear.MTEHatchNuclearHatch;

public class MTEHatchNuclearHatchGui extends MTEHatchBaseGui<MTEHatchNuclearHatch> {

    public MTEHatchNuclearHatchGui(MTEHatchNuclearHatch machine) {
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
            "pipeTier",
            new IntSyncValue(() -> machine.mReactorPipeTier, val -> machine.mReactorPipeTier = val));
    }

    @Override
    protected ParentWidget<?> createContentSection(ModularPanel panel, PanelSyncManager syncManager) {
        Flow mainRow = Flow.row()
            .coverChildren()
            .childPadding(4);

        // 1. Stats and Telemetry Screen (width 104, height 54)
        ParentWidget<?> statsScreen = CommonWidgets.createFluidScreen(104, 54);
        Flow textColumn = Flow.column()
            .childPadding(1)
            .crossAxisAlignment(Alignment.CrossAxis.START);

        textColumn.child(
            IKey.dynamic(() -> EnumChatFormatting.AQUA + "STATS")
                .asWidget()
                .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE));

        textColumn.child(IKey.dynamic(() -> {
            if (machine.mInputFluid != null && machine.mReactorPipeTier >= 0
                && MTEHatchNuclearHatch.getRequiredFluidTier(
                    machine.mInputFluid.getFluid()
                        .getName())
                    > machine.mReactorPipeTier) {
                return EnumChatFormatting.RED + "ERR: TIER";
            }
            return EnumChatFormatting.GOLD + String.format("%.0f °C", machine.mTemperature);
        })
            .asWidget()
            .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE));

        textColumn.child(
            IKey.dynamic(
                () -> EnumChatFormatting.AQUA
                    + String.format("F: %d  T: %d", machine.mLastFastFlux, machine.mLastThermalFlux))
                .asWidget()
                .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE));

        textColumn.child(
            IKey.dynamic(
                () -> EnumChatFormatting.GRAY
                    + String.format("Absorbed: %d n/t", machine.mLastFastAbsorbed + machine.mLastThermalAbsorbed))
                .asWidget()
                .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE));

        textColumn.child(IKey.dynamic(() -> {
            if (machine.mLastProducedAmount > 0) {
                return EnumChatFormatting.GREEN + String.format("Out: %d L/t", machine.mLastProducedAmount);
            }
            return EnumChatFormatting.DARK_GRAY + "Out: idle";
        })
            .asWidget()
            .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE));

        statsScreen.child(textColumn);
        mainRow.child(statsScreen);

        // 2. Input Tank Screen (width 50, height 54)
        ParentWidget<?> inputScreen = CommonWidgets.createFluidScreen(50, 54);
        Flow inTextCol = Flow.column()
            .childPadding(1)
            .crossAxisAlignment(Alignment.CrossAxis.START);

        inTextCol.child(
            IKey.dynamic(() -> EnumChatFormatting.GRAY + "In")
                .asWidget()
                .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE));

        inTextCol.child(
            IKey.dynamic(() -> (machine.mInputFluid != null ? formatNumber(machine.mInputFluid.amount) : "0") + "L")
                .asWidget()
                .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE));

        inputScreen.child(inTextCol);
        inputScreen.child(
            new FluidSlot().syncHandler(new FluidSlotSyncHandler(machine.getInputTank()).filter(getFluidSlotFilter()))
                .bottomRel(0)
                .rightRel(0)
                .background(GTGuiTextures.SLOT_FLUID_TANK));
        mainRow.child(inputScreen);

        return super.createContentSection(panel, syncManager).child(mainRow);
    }
}
