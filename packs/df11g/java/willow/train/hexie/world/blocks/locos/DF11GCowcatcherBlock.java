package willow.train.hexie.world.blocks.locos;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.world.blocks.carriage.OrdinaryTrainPanelBlock;
import willow.train.hexie.world.collision.LocoCollision;

public class DF11GCowcatcherBlock extends OrdinaryTrainPanelBlock {
    @Override public boolean supportsPassengerDisplay() { return false; }

    public DF11GCowcatcherBlock(BlockBehaviour.Properties p_49795_) {
        super(p_49795_);
    }

    public VoxelShape getShape(
            BlockState p_54372_,
            BlockGetter p_54373_,
            BlockPos p_54374_,
            CollisionContext p_54375_) {
        return LocoCollision.ForDF11GCowcatcherBlock.getShape(p_54372_, p_54373_, p_54374_, p_54375_);
    }
}
