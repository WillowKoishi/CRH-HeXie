package willow.train.hexie.blocks.track.standard;

import com.simibubi.create.content.trains.track.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.init.track.TrackMaterialInit;

public final class TrackBallast {
    private TrackBallast() {}
    public static boolean enabled(BlockState state) {
        return state.getBlock() instanceof TrackBlock track && track.getMaterial() == TrackMaterialInit.STANDARD;
    }
    public static boolean canPaint(Player player, ItemStack stack) {
        return fillState(player, stack) != null;
    }

    /** Both straight and curved track validate exactly the block state whose material they store. */
    public static BlockState fillState(Player player, ItemStack stack) {
        if (!player.mayBuild() || player.isSpectator() || player.isShiftKeyDown()
                || !(stack.getItem() instanceof BlockItem item) || item instanceof TrackTargetingBlockItem
                || item.getBlock() instanceof TrackBlock) return null;
        var state = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY)
                .apply(item.getBlock().defaultBlockState());
        var world = EmptyBlockGetter.INSTANCE;
        return state.getRenderShape() == RenderShape.MODEL && state.isCollisionShapeFullBlock(world, BlockPos.ZERO)
                && Block.isShapeFullBlock(state.getShape(world, BlockPos.ZERO)) ? state : null;
    }
    /** Shared by normal tracks and the optional flexible placement variant. */
    public static boolean apply(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player) {
        if (!enabled(state)) return false;
        var texture = fillState(player, stack);
        if (texture == null) return false;
        if (!level.isClientSide && level.mayInteract(player, pos)) {
            // Ordinary straight tracks allocate their data storage only when first painted.
            if (!state.getValue(TrackBlock.HAS_BE))
                level.setBlockAndUpdate(pos, state.setValue(TrackBlock.HAS_BE, true));
            if (level.getBlockEntity(pos) instanceof TrackBlockEntity track) {
                paint(track, null, texture);
                for (var curve : track.getConnections().values())
                    if (curve.getMaterial() == TrackMaterialInit.STANDARD) paint(track, curve.getKey(), texture);
            }
        }
        return true;
    }

    public static void paint(TrackBlockEntity track, BlockPos target, BlockState state) {
        ((BallastData) track).hexie$setBallast(target, state);
        if (target != null && track.getLevel().hasChunkAt(target)
                && track.getLevel().getBlockEntity(target) instanceof TrackBlockEntity other)
            ((BallastData) other).hexie$setBallast(track.getBlockPos(), state);
    }
}
