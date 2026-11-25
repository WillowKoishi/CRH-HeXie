package willow.train.hexie.world.blocks.carriage;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.world.collision.CarriageCollisionShapes;

public class TrainSealedWindowBlock extends PanelBlock25Side {

    public TrainSealedWindowBlock(BlockBehaviour.Properties p_49795_) {
        super(p_49795_);
    } // 8,0,6,19,32,8

    // "bottom"

    public VoxelShape getShape(
            BlockState p_54372_,
            BlockGetter p_54373_,
            BlockPos p_54374_,
            CollisionContext p_54375_) {
        return CarriageCollisionShapes.ForTrainSealedWindowBlock.getShape(p_54372_, p_54373_, p_54374_, p_54375_);
    }
}
