package willow.train.hexie.world.blocks.carriage;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.world.collision.CarriageCollisionShapes;

public class BSP25TAirConditionBlock extends OrdinaryTrainPanelBlock {
    @Override public boolean supportsPassengerDisplay() { return false; }
    public BSP25TAirConditionBlock(BlockBehaviour.Properties p_49795_) {
        super(p_49795_);
    }

    public VoxelShape getShape(
            BlockState p_54372_,
            BlockGetter p_54373_,
            BlockPos p_54374_,
            CollisionContext p_54375_) {
        return CarriageCollisionShapes.ForBSP25TAirConditionBlock.getShape(p_54372_, p_54373_, p_54374_, p_54375_);
    }
}
