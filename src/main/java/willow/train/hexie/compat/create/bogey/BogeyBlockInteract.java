package willow.train.hexie.compat.create.bogey;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class BogeyBlockInteract {
    private BogeyBlockInteract() {}

    public static ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit, Supplier<ItemInteractionResult> baseUseItemOn) {
        // Handle the top before Create's useItemOn cycles the style on the server.
        if (!player.isShiftKeyDown() && hit.getDirection() == Direction.UP
                && CreateBogeyWrenchInteract.isWrench(stack)) {
            return CreateBogeyWrenchInteract.open(state, level, pos, player, hand);
        }
        return baseUseItemOn.get();
    }
}
