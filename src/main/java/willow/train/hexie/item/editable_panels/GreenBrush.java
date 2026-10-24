package willow.train.hexie.item.editable_panels;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GreenBrush extends Item {

    public GreenBrush(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public void appendHoverText(
            @NotNull ItemStack p_41421_,
            @Nullable TooltipContext p_41422_,
            List<Component> tooltips,
            TooltipFlag p_41424_) {
        tooltips.add(
                Component.translatable("item.hexie.green_brush.tooltip")
                        .withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.ITALIC));
        super.appendHoverText(p_41421_, p_41422_, tooltips, p_41424_);
    }
}
