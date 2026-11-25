package willow.train.hexie.world.collision;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.bridge.vehicle.VehicleBridge;
import willow.train.hexie.bridge.world.BlockEntityViews;
import willow.train.hexie.world.blockinteract.CouplerInteraction;
import willow.train.hexie.world.blocks.coupler.AbstractCouplerBlock;
import willow.train.hexie.world.blocks.coupler.JanneyCouplerBlock;
import willow.train.hexie.world.blocks.coupler.ModelCouplerBlock;
import willow.train.hexie.world.blocks.coupler.ScharfenbergCouplerBlock;

public final class CouplerCollision {
    private CouplerCollision() {}

    public static final class ForAbstractCouplerBlock {
        public static VoxelShape getShape(AbstractCouplerBlock block, BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
            VoxelShape shape = block.placedShape(state, level);
            if (block.geometry() == null) return shape;
            float progress = level.getBlockEntity(position) instanceof BlockEntityViews.CouplerBlockEntityView coupler
                    ? coupler.coverProgress(1) : CouplerInteraction.coverProgress(state);
            double floorOffset = level instanceof VehicleBridge.CarriageFloorViewView carriage ? carriage.floorOffset() : 0;
            return new CouplerInteractionShape(shape, state, progress, floorOffset,
                    level.getBlockEntity(position) instanceof BlockEntityViews.CouplerBlockEntityView entity ? entity : null);
        }

        public static VoxelShape getCollisionShape(AbstractCouplerBlock block, BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
            return block.hasCouplerCollision() ? block.placedShape(state, level) : Shapes.empty();
        }

        public static VoxelShape placedShape(AbstractCouplerBlock block, BlockState state, BlockGetter level) {
            // The assembled flag changes only the static mesh; the complete coupler remains selectable.
            VoxelShape shape = block.couplerShape(state);
            return level instanceof VehicleBridge.CarriageFloorViewView carriage ? shape.move(0, -carriage.floorOffset(), 0) : shape;
        }
    }

    public static final class ForJanneyCouplerBlock {
        public static VoxelShape couplerShape(JanneyCouplerBlock block, BlockState state) {
            return block.geometry().shape(block.facing(state), state.getValue(BlockStateProperties.OPEN));
        }
    }

    public static final class ForModelCouplerBlock {
        public static VoxelShape couplerShape(ModelCouplerBlock block, BlockState state) {
            return block.geometry().shape(block.facing(state), state.getValue(BlockStateProperties.OPEN));
        }
    }

    public static final class ForScharfenbergCouplerBlock {
        public static VoxelShape couplerShape(ScharfenbergCouplerBlock block, BlockState state) { return block.geometry().shape(block.facing(state), false); }
    }
}
