package willow.train.hexie.sync.c2s.signs;

import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.player.Player;
import willow.train.hexie.bridge.ui.MenuBridge.BlockEntityEditMenuView;

/** Validate the complete payload before invoking this single edit commit. */
final class SignEditSubmission {
    static final int MAX_TEXT_LENGTH = 384;

    private SignEditSubmission() {}

    static <T> void commit(Player player, BlockPos pos, Class<T> type,
                                                  Consumer<T> edit) {
        commit(player, pos, -1, type, edit);
    }

    static <T> void commit(Player player, BlockPos pos, int menuId, Class<T> type, Consumer<T> edit) {
        if (!(player instanceof ServerPlayer) || !(player.containerMenu instanceof BlockEntityEditMenuView menu)
                || !menu.stillValid(player) || !type.isInstance(menu.editTarget())
                || (menu.contentOnly() || menuId >= 0) && menuId != menu.asMenu().containerId
                || !menu.editTarget().getBlockPos().equals(pos)) return;
        var target = menu.editTarget();
        edit.accept(type.cast(target));
        if (menu.contentOnly()) {
            menu.commitEdit(player);
            return;
        }
        sync(target);
    }

    static void sync(BlockEntity target) {
        target.setChanged();
        if (target.getLevel() instanceof ServerLevel level) level.getChunkSource().blockChanged(target.getBlockPos());
    }
}
