package willow.train.hexie.blocks.locos.ss7d;

import java.util.LinkedHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.blocks.base.DirectionalShape;
import willow.train.hexie.blocks.carriage.TrainDoorBlock;
import willow.train.hexie.blocks.parts.PartInstallation;
import willow.train.hexie.item.FloorSwitcherItem;

/** Two-block locomotive door, with a fixed frame and a leaf hinged beside it. */
public final class SS7DDoorBlock extends TrainDoorBlock {
    private static final DirectionalShape[][][] SHAPES = createShapes();

    public SS7DDoorBlock(Properties properties) {
        super(properties);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) return null;
        Direction right = state.getValue(FACING).getClockWise();
        BlockPos pos = context.getClickedPos();
        var hit = context.getClickLocation();
        double side = (hit.x - pos.getX() - .5) * right.getStepX()
                + (hit.z - pos.getZ() - .5) * right.getStepZ();
        // This property selects a mirror of the complete door, including the
        // frame and handrails. The unmirrored model's hinge is on the right.
        return state.setValue(HINGE, side < 0 ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT);
    }

    @Override public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof FloorSwitcherItem) || player.isShiftKeyDown())
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        if (player.isSpectator() || !player.mayBuild()) return ItemInteractionResult.FAIL;

        BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        BlockPos upper = lower.above();
        BlockState bottom = level.getBlockState(lower);
        BlockState top = level.getBlockState(upper);
        if (!bottom.is(this) || !top.is(this) || bottom.getValue(HALF) != DoubleBlockHalf.LOWER
                || top.getValue(HALF) != DoubleBlockHalf.UPPER
                || !bottom.setValue(HALF, DoubleBlockHalf.UPPER).equals(top)) return ItemInteractionResult.FAIL;

        var previous = new LinkedHashMap<BlockPos, BlockState>();
        previous.put(lower, bottom);
        previous.put(upper, top);
        var next = new LinkedHashMap<BlockPos, BlockState>();
        next.put(lower, bottom.cycle(HINGE));
        next.put(upper, top.cycle(HINGE));
        boolean changed = PartInstallation.replace(level, previous, next, at -> false,
                at -> level.mayInteract(player, at) && player.mayUseItemAt(at, hit.getDirection(), stack));
        return changed ? ItemInteractionResult.sidedSuccess(level.isClientSide) : ItemInteractionResult.FAIL;
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        int mirrored = state.getValue(HINGE) == DoorHingeSide.RIGHT ? 1 : 0;
        int open = state.getValue(OPEN) ? 1 : 0;
        int upper = state.getValue(HALF) == DoubleBlockHalf.UPPER ? 1 : 0;
        return SHAPES[mirrored][open][upper].get(state.getValue(FACING));
    }

    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        // The authored doorway is narrower than a player's bounding box. Keep the
        // outline for interaction, but leave both cells passable when open.
        return state.getValue(OPEN) ? Shapes.empty() : getShape(state, world, pos, context);
    }

    private static DirectionalShape[][][] createShapes() {
        var shapes = new DirectionalShape[2][2][2];
        for (int mirrored = 0; mirrored < 2; mirrored++) {
            for (int upper = 0; upper < 2; upper++) {
                VoxelShape closed = Block.box(0, 0, -1, 16, 16, 1);
                VoxelShape open = Shapes.or(Block.box(10, 0, -1, 16, 16, 1),
                        upper == 0 ? Block.box(0, 0, -1, 10, 2, 1) : Block.box(0, 14, -1, 10, 16, 1),
                        upper == 0 ? Block.box(9.5, 2, -9.5, 11.5, 16, 0.5)
                                : Block.box(9.5, 0, -9.5, 11.5, 14, 0.5));
                if (mirrored == 1) open = mirrorX(open);
                shapes[mirrored][0][upper] = new DirectionalShape(closed);
                shapes[mirrored][1][upper] = new DirectionalShape(open);
            }
        }
        return shapes;
    }

    private static VoxelShape mirrorX(VoxelShape shape) {
        VoxelShape result = Shapes.empty();
        for (var box : shape.toAabbs())
            result = Shapes.or(result, Shapes.box(1 - box.maxX, box.minY, box.minZ,
                    1 - box.minX, box.maxY, box.maxZ));
        return result;
    }
}
