package willow.train.hexie.item.editable_panels;

import net.minecraft.ChatFormatting;
import willow.train.hexie.compat.ItemData;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import willow.train.hexie.init.item.EditablePanelItemInit;

import java.util.List;

public class ColoredBrush extends Item {

    public ColoredBrush(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public void appendHoverText(
            @NotNull ItemStack p_41421_,
            @Nullable TooltipContext p_41422_,
            List<Component> tooltips,
            TooltipFlag p_41424_) {
        tooltips.add(
                Component.translatable("item.hexie.colored_brush.tooltip_1")
                        .withStyle(ChatFormatting.RED));
        tooltips.add(
                Component.translatable("item.hexie.colored_brush.tooltip_2")
                        .withStyle(ChatFormatting.GOLD));
        tooltips.add(
                Component.translatable("item.hexie.colored_brush.tooltip_3")
                        .withStyle(ChatFormatting.GREEN));
        tooltips.add(
                Component.translatable("item.hexie.colored_brush.tooltip_4")
                        .withStyle(ChatFormatting.BLUE));
        super.appendHoverText(p_41421_, p_41422_, tooltips, p_41424_);
    }

    @Override
    public ItemStack getDefaultInstance() {
        ItemStack stack = super.getDefaultInstance();
        ItemData.updateTag(stack, data -> data.putInt("durability", 256));
        return stack;
    }

    @Override
    public void onCraftedBy(ItemStack stack, Level pLevel, Player pPlayer) {
        ItemData.updateTag(stack, data -> data.putInt("durability", 256));
        super.onCraftedBy(stack, pLevel, pPlayer);
    }

    public static int getDurability(ItemStack stack) {
        if(!stack.is(EditablePanelItemInit.ColoredBrush.get())) return -1;
        return ItemData.getTag(stack).getInt("durability");
    }

    @Override
    public boolean isBarVisible(ItemStack pStack) {
        if(!pStack.is(EditablePanelItemInit.ColoredBrush.get())) return false;
        return ItemData.getTag(pStack).getInt("durability") < 256;
    }

    @Override
    public int getBarWidth(ItemStack pStack) {
        if(!pStack.is(EditablePanelItemInit.ColoredBrush.get())) return 0;
        return Math.round(((float) ItemData.getTag(pStack).getInt("durability"))/256 * 13f);
    }

    @Override
    public int getBarColor(ItemStack pStack) {
        float percentage = ((float) ItemData.getTag(pStack).getInt("durability"))/256;
        float f = Math.max(0.0F, percentage);
        return Mth.hsvToRgb(f / 3.0F, 1.0F, 1.0F);
    }

    public static ItemStack onEffectiveUse(ItemStack stack) {
        if(!stack.is(EditablePanelItemInit.ColoredBrush.get())) return stack;
        int durability = getDurability(stack);
        if(durability < 2)
            return EditablePanelItemInit.Brush.get().getDefaultInstance();
        ItemData.updateTag(stack, data -> data.putInt("durability", durability - 1));
        return stack;
    }
}
