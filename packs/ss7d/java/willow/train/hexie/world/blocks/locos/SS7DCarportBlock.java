package willow.train.hexie.world.blocks.locos;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.bridge.world.CarriageBlockBridge;
import willow.train.hexie.world.blocks.base.HorizontalBlockBase;
import willow.train.hexie.world.collision.LocoCollision;
import willow.train.hexie.world.placement.LocoPlacement;

/** SS7D roof segments use the authored north side as their outward direction. */
public class SS7DCarportBlock extends HorizontalBlockBase implements CarriageBlockBridge.SS7DCarportBlockView {

    private final LocoCollision.ForSS7DCarportBlock collisionShapes;

    public SS7DCarportBlock(BlockBehaviour.Properties properties, boolean side) {
        super(properties);
        collisionShapes = new LocoCollision.ForSS7DCarportBlock(side);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return LocoPlacement.ForSS7DCarportBlock.getStateForPlacement(context, () -> super.getStateForPlacement(context));
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return collisionShapes.getShape(state, level, pos, context);
    }
}
