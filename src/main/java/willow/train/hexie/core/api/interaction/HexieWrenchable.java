package willow.train.hexie.core.api.interaction;

import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public interface HexieWrenchable {
    default InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState rotated = getRotatedBlockState(state, context.getClickedFace());
        if (!rotated.canSurvive(level, pos) || rotated.equals(state)) return InteractionResult.PASS;

        level.setBlock(pos, rotated, Block.UPDATE_ALL);
        playRotateSound(level, pos);
        return InteractionResult.SUCCESS;
    }

    default InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        if (!WrenchHooks.canRemove.test(context)) return InteractionResult.SUCCESS;

        BlockPos pos = context.getClickedPos();
        var player = context.getPlayer();
        if (player != null && !player.isCreative()) {
            Block.getDrops(state, level, pos, level.getBlockEntity(pos), player, context.getItemInHand())
                    .forEach(stack -> player.getInventory().placeItemBackInInventory(stack));
        }
        state.spawnAfterBreak(level, pos, ItemStack.EMPTY, true);
        level.destroyBlock(pos, false);
        playRemoveSound(level, pos);
        return InteractionResult.SUCCESS;
    }

    default BlockState getRotatedBlockState(BlockState state, Direction targetedFace) {
        Direction.Axis axis = targetedFace.getAxis();
        if (axis == Direction.Axis.Y) {
            if (state.hasProperty(BlockStateProperties.HORIZONTAL_AXIS))
                return state.cycle(BlockStateProperties.HORIZONTAL_AXIS);
            if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING))
                return state.setValue(BlockStateProperties.HORIZONTAL_FACING,
                        state.getValue(BlockStateProperties.HORIZONTAL_FACING).getClockWise());
        }
        if (state.hasProperty(BlockStateProperties.AXIS)) {
            Direction facing = Direction.fromAxisAndDirection(state.getValue(BlockStateProperties.AXIS),
                    Direction.AxisDirection.POSITIVE);
            return state.setValue(BlockStateProperties.AXIS, facing.getClockWise(axis).getAxis());
        }
        if (state.hasProperty(BlockStateProperties.FACING)) {
            Direction facing = state.getValue(BlockStateProperties.FACING);
            return state.setValue(BlockStateProperties.FACING, facing.getClockWise(axis));
        }
        return state;
    }

    static void playRotateSound(Level level, BlockPos pos) {
        WrenchHooks.rotateSound.accept(level, pos);
    }

    static void playRemoveSound(Level level, BlockPos pos) {
        WrenchHooks.removeSound.accept(level, pos);
    }

    static void registerWrenchHooks(BiConsumer<Level, BlockPos> rotateSound, BiConsumer<Level, BlockPos> removeSound,
                                   Predicate<UseOnContext> canRemove) {
        WrenchHooks.rotateSound = rotateSound;
        WrenchHooks.removeSound = removeSound;
        WrenchHooks.canRemove = canRemove;
    }
}

final class WrenchHooks {
    static BiConsumer<Level, BlockPos> rotateSound = (level, pos) -> {};
    static BiConsumer<Level, BlockPos> removeSound = (level, pos) -> {};
    static Predicate<UseOnContext> canRemove = context -> true;

    private WrenchHooks() {}
}
