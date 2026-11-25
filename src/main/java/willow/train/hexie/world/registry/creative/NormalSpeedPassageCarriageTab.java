package willow.train.hexie.world.registry.creative;

import willow.train.hexie.core.util.general.CreativeTabGroup;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.NotNull;

import willow.train.hexie.world.registry.items.LogoItemInit;

public class NormalSpeedPassageCarriageTab extends CreativeTabGroup {
    public NormalSpeedPassageCarriageTab(String modId) {
        super("item_group_normal_speed_passage_carriage");
    }

    @Override
    public @NotNull ItemStack makeIcon() {
        return new ItemStack(LogoItemInit.SERIES_25_LOGOS.get());
    }
}
