package willow.train.hexietrain;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * 橙色状态播报，发给列车拥有者（放置方块的玩家）。
 */
public final class TrainMessages {

    private TrainMessages() {}

    public static void notifyOwner(ServerLevel level, UUID ownerId, String text) {
        HexieTrain.LOGGER.info("[HexieTrain][notice] {}", text);
        if (level == null || ownerId == null) return;
        Player player = level.getPlayerByUUID(ownerId);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component.literal(text).withStyle(ChatFormatting.GOLD), false);
        }
    }
}