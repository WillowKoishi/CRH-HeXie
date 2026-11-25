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

public class YZTableBlock extends HorizontalBlockBase implements HexieWrenchable {

    private final FacilityCollision.ForYZTableBlock collisionShapes = new FacilityCollision.ForYZTableBlock();

    public YZTableBlock(BlockBehaviour.Properties p_49795_) {
        super(p_49795_);
    }

    @Override
    public VoxelShape getShape(
            BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return collisionShapes.getShape(pState, pLevel, pPos, pContext);
    }
}
