package willow.train.hexie.world.blocks.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.world.blocks.carriage.PanelBlock25Side;
import willow.train.hexie.world.collision.FacilityCollision;

public class GreenInsulationFenceBlock extends PanelBlock25Side {
    @Override public boolean supportsPassengerDisplay() { return false; }
    protected int type = 0;

    public GreenInsulationFenceBlock(BlockBehaviour.Properties pProperties, int tType) {
        super(pProperties);
        type = tType;
    }

    private final FacilityCollision.ForGreenInsulationFenceBlock collisionShapes = new FacilityCollision.ForGreenInsulationFenceBlock();

    @Override
    public VoxelShape getShape(
            BlockState p_54372_,
            BlockGetter p_54373_,
            BlockPos p_54374_,
            CollisionContext p_54375_) {
        return collisionShapes.getShape(this, p_54372_, p_54373_, p_54374_, p_54375_, () -> super.getShape(p_54372_, p_54373_, p_54374_, p_54375_));
    }

    public final int variantType() { return type; }
}
