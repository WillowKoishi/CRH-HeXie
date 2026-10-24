package willow.train.hexie.item.editable_panels;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class LacqueredBoardItem extends Item {
    public LacqueredBoardItem(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public void appendHoverText(
            @NotNull ItemStack p_41421_,
            @Nullable TooltipContext p_41422_,
            List<Component> tooltips,
            TooltipFlag p_41424_) {
        tooltips.add(Component.translatable("item.hexie.laquered_board_tooltip"));
        super.appendHoverText(p_41421_, p_41422_, tooltips, p_41424_);
    }
}
