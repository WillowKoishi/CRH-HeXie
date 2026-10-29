package willow.train.hexie.blocks.track.standard;

import com.simibubi.create.content.trains.track.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.init.track.TrackMaterialInit;

public final class TrackBallast {
    private TrackBallast() {}
    public static boolean enabled(BlockState state) {
        return state.getBlock() instanceof TrackBlock track && track.getMaterial() == TrackMaterialInit.STANDARD;
    }
    public static boolean canPaint(Player player, ItemStack stack) {
        return player.mayBuild() && !player.isSpectator() && !player.isShiftKeyDown()
                && stack.getItem() instanceof BlockItem item && !(item instanceof TrackTargetingBlockItem)
                && !(item.getBlock() instanceof TrackBlock);
    }
    public static void paint(TrackBlockEntity track, BlockPos target, BlockState state) {
        ((BallastData) track).hexie$setBallast(target, state);
        if (target != null && track.getLevel().hasChunkAt(target)
                && track.getLevel().getBlockEntity(target) instanceof TrackBlockEntity other)
            ((BallastData) other).hexie$setBallast(track.getBlockPos(), state);
    }
}
