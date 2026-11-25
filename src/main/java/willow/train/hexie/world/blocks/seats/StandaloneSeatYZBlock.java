package willow.train.hexie.world.blocks.seats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.bridge.world.WorldBlockBridge.SeatYZBlockView;
import willow.train.hexie.core.util.seat.SeatCollision;

public final class StandaloneSeatYZBlock extends StandaloneSeatBlock implements SeatYZBlockView {
    private static final VoxelShape PLAYER_COLLISION = box(0, 0, 0, 16, 3, 16);
    private static final VoxelShape COLLISION = box(0, 0, 0, 16, 6, 16);

    public StandaloneSeatYZBlock(Properties properties) {
        super(properties);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z
                ? SeatCollision.ForSeatYZBlock.snShape() : SeatCollision.ForSeatYZBlock.weShape();
    }

    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return context instanceof EntityCollisionContext entity && entity.getEntity() instanceof Player
                ? PLAYER_COLLISION : COLLISION;
    }

    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return PassengerSeatInteraction.use(state, level, pos, player, hit, () -> player);
    }
}
