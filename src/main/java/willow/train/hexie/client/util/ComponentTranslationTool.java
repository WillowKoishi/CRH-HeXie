package willow.train.hexie.client.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;


public class ComponentTranslationTool {

    public static final Component HELP_WITH_LSHIFT_COMPONENT = Component.translatable("tooltip.hexie.abc.binding.explain.un_1").withStyle(ChatFormatting.GRAY)
            .append(Component.literal("「LShift」").withStyle(ChatFormatting.GOLD))
            .append(Component.translatable("tooltip.hexie.abc.binding.explain.un_2").withStyle(ChatFormatting.GRAY));

    // 向某玩家展示

    public static void showSuccess(Player pPlayer, boolean b, Component... components) {
        Component cx = Component.literal("✔ ").withStyle(ChatFormatting.GREEN);
        for(Component c : components) {
            cx = cx.copy().append(c).withStyle(ChatFormatting.GREEN);
        }
        pPlayer.displayClientMessage(cx, b);
    }

    public static void showError(Player pPlayer, boolean b, Component... components) {
        Component cx = Component.literal("❌ ").withStyle(ChatFormatting.RED);
        for(Component c : components) {
            cx = cx.copy().append(c).withStyle(ChatFormatting.RED);
        }
        pPlayer.displayClientMessage(cx, b);
    }

}
