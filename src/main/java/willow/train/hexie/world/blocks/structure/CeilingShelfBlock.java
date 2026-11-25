package willow.train.hexie.world.blocks.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.core.api.interaction.HexieWrenchable;
import willow.train.hexie.world.blocks.base.HorizontalBlockBase;
import willow.train.hexie.world.collision.FacilityCollision;

public class CeilingShelfBlock extends HorizontalBlockBase implements HexieWrenchable {

    // public static VoxelShape BASE = Block.box(0, 1, 0, 16, 2, 16);

    public CeilingShelfBlock(BlockBehaviour.Properties p_49795_) {
        super(p_49795_);
    }

    @Override
    public VoxelShape getShape(
            BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return FacilityCollision.ForCeilingShelfBlock.getShape(pState, pLevel, pPos, pContext);
    }
}
