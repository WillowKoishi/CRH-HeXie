package willow.train.hexie.world.blocks.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.world.blocks.base.HorizontalBlockBase;
import willow.train.hexie.world.collision.FacilityCollision;

/** The shared thin-panel behaviour; thickness is a model definition in sixteenths. */
public class ThinPanelBlock extends HorizontalBlockBase {
    private final FacilityCollision.ForThinPanelBlock collisionShapes;
    public ThinPanelBlock(BlockBehaviour.Properties properties,double thickness) {
        super(properties);
        collisionShapes = new FacilityCollision.ForThinPanelBlock(thickness);
    }
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        return collisionShapes.getShape(state, level, pos, context);
    }
}
