package willow.train.hexie.world.blocks.locos;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.world.blocks.carriage.TrainDoorBlock;
import willow.train.hexie.world.collision.LocoCollision;

public class HXD3DCabDoorBlock extends TrainDoorBlock {
    public HXD3DCabDoorBlock(BlockBehaviour.Properties pProperties) {
        super(pProperties);
    }

    @Override
    public VoxelShape getShape(
            BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return LocoCollision.ForHXD3DCabDoorBlock.getShape(pState, pLevel, pPos, pContext);
    }

    @Override
    public VoxelShape getCollisionShape(
            BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return LocoCollision.ForHXD3DCabDoorBlock.getCollisionShape(pState, pLevel, pPos, pContext);
    }
}
