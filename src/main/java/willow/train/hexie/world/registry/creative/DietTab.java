package willow.train.hexie.world.registry.creative;

import willow.train.hexie.core.util.general.CreativeTabGroup;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.NotNull;

import willow.train.hexie.world.registry.items.LogoItemInit;

public class DietTab extends CreativeTabGroup {
    public DietTab(String modId) {
        super("item_group_diet");
    }

    @Override
    public @NotNull ItemStack makeIcon() {
        return new ItemStack(LogoItemInit.CA_25T.get());
    }
}
