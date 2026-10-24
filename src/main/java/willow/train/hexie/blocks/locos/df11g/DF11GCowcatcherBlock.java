package willow.train.hexie.blocks.locos.df11g;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import willow.train.hexie.blocks.carriage.OrdinaryTrainPanelBlock;

public class DF11GCowcatcherBlock extends OrdinaryTrainPanelBlock {
    protected static final VoxelShape ALLAABB = Block.box(-16, 8, -16, 32, 16, 32);

    public DF11GCowcatcherBlock(Properties p_49795_) {
        super(p_49795_);
    }

    public VoxelShape getShape(
            BlockState p_54372_,
            BlockGetter p_54373_,
            BlockPos p_54374_,
            CollisionContext p_54375_) {
        return ALLAABB;
    }
}
