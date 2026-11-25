package willow.train.hexie.compat.create.bogey;

import com.simibubi.create.content.equipment.wrench.WrenchItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.bridge.HexieBridges;

final class CreateBogeyWrenchInteract {
    private CreateBogeyWrenchInteract() {}

    static boolean isWrench(ItemStack stack) {
        return stack.getItem() instanceof WrenchItem;
    }

    static ItemInteractionResult open(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand) {
        if (player.isSpectator() || !player.mayBuild() || state.getValue(HexieBogeyBlock.HEAD_MOUNTED))
            return ItemInteractionResult.FAIL;
        if (player instanceof ServerPlayer server && (!(level.getBlockEntity(pos) instanceof HexieBogeyBlockEntity bogey)
                || !HexieBridges.menu().bogeySelectionMenu().open(server, bogey, hand))) return ItemInteractionResult.FAIL;
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}
