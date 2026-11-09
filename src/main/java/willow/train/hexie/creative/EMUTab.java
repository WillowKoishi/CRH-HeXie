package willow.train.hexie.creative;

import net.minecraft.world.item.ItemStack;
import willow.train.hexie.compat.CreativeTabGroup;
import willow.train.hexie.init.item.LogoItemInit;

public final class EMUTab extends CreativeTabGroup {
    public EMUTab() {
        super("item_group_emu");
    }

    @Override
    public ItemStack makeIcon() {
        return new ItemStack(LogoItemInit.CRH_LOGO.get());
    }
}
