package gregtech.common.items;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import gregtech.api.items.GTGenericItem;
import ic2.api.reactor.IReactor;
import ic2.api.reactor.IReactorComponent;

public class ItemBetavoltaicPlate extends GTGenericItem implements IReactorComponent {

    private final int tier; // 1 for HV (1024 EU/t), 2 for EV (4096 EU/t)

    public ItemBetavoltaicPlate(String aUnlocalized, String aEnglish, int aTier) {
        super(aUnlocalized, aEnglish, "Indestructible");
        this.tier = aTier;
        this.setMaxStackSize(64);
        this.setMaxDamage(0);
    }

    public int getTier() {
        return tier;
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addAdditionalToolTips(List aList, ItemStack aStack, EntityPlayer aPlayer) {
        super.addAdditionalToolTips(aList, aStack, aPlayer);
        if (tier == 1) {
            aList.add("Converts absorbed neutron flux into direct HV electricity");
            aList.add("Max Output: 1,024 EU/t (2A HV) via Dynamo Hatches");
        } else {
            aList.add("Converts absorbed neutron flux into direct EV electricity");
            aList.add("Max Output: 4,096 EU/t (2A EV) via Dynamo Hatches");
        }
        aList.add("Absorbs 100% of incident neutron flux");
        aList.add("Fast neutrons yield 4x electricity vs thermal neutrons");
        aList.add("Excess absorbed energy converts directly into core heat");
    }

    @Override
    public boolean acceptUraniumPulse(IReactor reactor, ItemStack yourStack, ItemStack pulsingStack, int youX, int youY,
        int pulseX, int pulseY, boolean heatrun) {
        // Absorbs pulse completely without reflecting back
        return false;
    }

    @Override
    public boolean canStoreHeat(IReactor aReactor, ItemStack aStack, int x, int y) {
        return false;
    }

    @Override
    public int getMaxHeat(IReactor aReactor, ItemStack aStack, int x, int y) {
        return 0;
    }

    @Override
    public int getCurrentHeat(IReactor aReactor, ItemStack aStack, int x, int y) {
        return 0;
    }

    @Override
    public float influenceExplosion(IReactor aReactor, ItemStack aStack) {
        return -1.0F;
    }

    @Override
    public int alterHeat(IReactor aReactor, ItemStack aStack, int x, int y, int aHeat) {
        return aHeat;
    }

    @Override
    public void processChamber(IReactor aReactor, ItemStack aStack, int x, int y, boolean aHeatRun) {}
}
