package willow.train.hexie.network.c2s.signs;

import java.util.function.Consumer;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import willow.train.hexie.menu.BlockEntityEditMenu;

/** Validate the complete payload before invoking this single edit commit. */
final class SignEditSubmission {
    static final int MAX_TEXT_LENGTH = 384;

    private SignEditSubmission() {}

    static <T> void commit(Player player, BlockPos pos, Class<T> type,
                                                  Consumer<T> edit) {
        commit(player, pos, -1, type, edit);
    }

    static <T> void commit(Player player, BlockPos pos, int menuId, Class<T> type, Consumer<T> edit) {
        if (!(player instanceof ServerPlayer) || !(player.containerMenu instanceof BlockEntityEditMenu<?> menu)
                || !menu.stillValid(player) || !type.isInstance(menu.editTarget())
                || (menu.contentOnly() || menuId >= 0) && menuId != menu.containerId
                || !menu.editTarget().getBlockPos().equals(pos)) return;
        var target = menu.editTarget();
        edit.accept(type.cast(target));
        if (menu.contentOnly()) {
            menu.commitEdit(player);
            return;
        }
        target.setChanged();
        if (target instanceof SmartBlockEntity smart) smart.sendData();
        else player.level().sendBlockUpdated(pos, target.getBlockState(), target.getBlockState(), 3);
    }
}
