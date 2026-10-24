package willow.train.hexie.creative;

import willow.train.hexie.compat.CreativeTabGroup;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.NotNull;

import willow.train.hexie.init.item.LogoItemInit;

public class DietTab extends CreativeTabGroup {
    public DietTab(String modId) {
        super("item_group_diet");
    }

    @Override
    public @NotNull ItemStack makeIcon() {
        return new ItemStack(LogoItemInit.CA_25T.get());
    }
}
