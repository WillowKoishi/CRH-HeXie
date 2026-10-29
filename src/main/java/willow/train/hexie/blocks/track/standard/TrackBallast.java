package willow.train.hexie.blocks.track.standard;

import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackBlockEntity;
import com.simibubi.create.content.trains.track.TrackTargetingBlockItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.init.track.TrackMaterialInit;

public final class TrackBallast {
    private TrackBallast() {}

    public static boolean enabled(BlockState state) {
        return state.getBlock() instanceof TrackBlock track && track.getMaterial() == TrackMaterialInit.STANDARD;
    }

    public static boolean canPaint(Player player, ItemStack stack) {
        return !player.isShiftKeyDown() && player.mayBuild() && !player.isSpectator()
                && stack.getItem() instanceof BlockItem item
                && !(item instanceof TrackTargetingBlockItem) && !(item.getBlock() instanceof TrackBlock);
    }

    /** A curve's two copies receive the same material; only the primary copy is rendered. */
    public static void paint(TrackBlockEntity track, BlockPos target, BlockState texture) {
        ((BallastData) track).hexie$setBallast(target, texture);
        if (target != null && track.getLevel().getBlockEntity(target) instanceof TrackBlockEntity other)
            ((BallastData) other).hexie$setBallast(track.getBlockPos(), texture);
    }
}
