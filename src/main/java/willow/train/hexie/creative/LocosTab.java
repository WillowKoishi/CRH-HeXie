package willow.train.hexie.creative;

import willow.train.hexie.compat.CreativeTabGroup;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.NotNull;

import willow.train.hexie.init.item.LogoItemInit;

public class LocosTab extends CreativeTabGroup {
    public LocosTab(String modId) {
        super("item_locos");
    }

    @Override
    public @NotNull ItemStack makeIcon() {
        return new ItemStack(LogoItemInit.SERIES_25_LOGOS.get());
    }
}
