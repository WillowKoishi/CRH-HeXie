package willow.train.hexie.compat.create.track;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class TrackCollision {
    private TrackCollision() {}

    public static final class ForStandardTrackBlock {
        public static VoxelShape getShape(StandardTrackBlock block, BlockState state, BlockGetter world,
                BlockPos pos, CollisionContext context, Supplier<VoxelShape> baseGetShape) {
            return baseGetShape.get().move(0,
                    StandardTrackBlock.heightIncrease(block.getMaterial()), 0);
        }

        public static VoxelShape getInteractionShape(StandardTrackBlock block, BlockState state,
                BlockGetter world, BlockPos pos, Supplier<VoxelShape> baseGetInteractionShape) {
            return baseGetInteractionShape.get().move(0,
                    StandardTrackBlock.heightIncrease(block.getMaterial()), 0);
        }

        public static VoxelShape getCollisionShape(StandardTrackBlock block, BlockState state, BlockGetter world,
                BlockPos pos, CollisionContext context, Supplier<VoxelShape> baseGetCollisionShape) {
            return baseGetCollisionShape.get().move(0,
                    StandardTrackBlock.heightIncrease(block.getMaterial()), 0);
        }
    }
}
