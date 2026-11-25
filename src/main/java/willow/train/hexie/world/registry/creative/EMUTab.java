package willow.train.hexie.world.registry.creative;

import net.minecraft.world.item.ItemStack;
import willow.train.hexie.core.util.general.CreativeTabGroup;
import willow.train.hexie.world.registry.items.LogoItemInit;

public final class EMUTab extends CreativeTabGroup {
    public EMUTab() {
        super("item_group_emu");
    }

    @Override
    public ItemStack makeIcon() {
        return new ItemStack(LogoItemInit.CRH_LOGO.get());
    }
}
