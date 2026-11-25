package willow.train.hexie.world.registry.creative;

import willow.train.hexie.core.util.general.CreativeTabGroup;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.NotNull;

import willow.train.hexie.world.registry.blocks.BlockInit;

public class MainTab extends CreativeTabGroup {
    public MainTab(String modId) {
        super("item_group_main");
    }

    @Override
    public @NotNull ItemStack makeIcon() {
        return new ItemStack(BlockInit.CR_LOGO.get());
    }
}
