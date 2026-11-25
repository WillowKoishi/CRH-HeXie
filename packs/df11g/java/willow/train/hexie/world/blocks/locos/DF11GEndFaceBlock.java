package willow.train.hexie.world.blocks.locos;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.world.blocks.carriage.OrdinaryTrainPanelBlock;
import willow.train.hexie.world.collision.LocoCollision;

public class DF11GEndFaceBlock extends OrdinaryTrainPanelBlock {
    @Override public boolean supportsPassengerDisplay() { return false; }

    public DF11GEndFaceBlock(BlockBehaviour.Properties p_49795_) {
        super(p_49795_);
    }

    @Override
    public VoxelShape getCollisionShape(
            BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return LocoCollision.ForDF11GEndFaceBlock.getCollisionShape(pState, pLevel, pPos, pContext);
    }

    @Override
    public VoxelShape getShape(
            BlockState pState, BlockGetter p_54373_, BlockPos p_54374_, CollisionContext p_54375_) {
        return LocoCollision.ForDF11GEndFaceBlock.getShape(pState, p_54373_, p_54374_, p_54375_);
    }
}
