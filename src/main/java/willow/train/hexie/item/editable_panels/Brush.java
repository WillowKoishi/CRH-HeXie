package willow.train.hexie.item.editable_panels;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import willow.train.hexie.blocks.signs.PanelAttachmentRemoval;

import java.util.List;

public class Brush extends Item {
    public Brush(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public void appendHoverText(
            @NotNull ItemStack p_41421_,
            @Nullable TooltipContext p_41422_,
            List<Component> tooltips,
            TooltipFlag p_41424_) {
        tooltips.add(Component.translatable("item.hexie.brush.tooltip"));
        super.appendHoverText(p_41421_, p_41422_, tooltips, p_41424_);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return PanelAttachmentRemoval.remove(context);
    }
}
