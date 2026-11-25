package willow.train.hexie.world.blocks.locos;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.world.blockinteract.LocoBlockInteract;
import willow.train.hexie.world.blocks.carriage.TrainDoorBlock;
import willow.train.hexie.world.collision.DirectionalShape;
import willow.train.hexie.world.collision.LocoCollision;
import willow.train.hexie.world.placement.LocoPlacement;

/** Two-block locomotive door, with a fixed frame and a leaf hinged beside it. */
public final class SS7DDoorBlock extends TrainDoorBlock {

    public SS7DDoorBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return LocoPlacement.ForSS7DDoorBlock.getStateForPlacement(context, () -> super.getStateForPlacement(context));
    }

    @Override public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return LocoBlockInteract.ForSS7DDoorBlock.useItemOn(this, stack, state, level, pos, player, hand, hit, () -> super.useItemOn(stack, state, level, pos, player, hand, hit));
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return LocoCollision.ForSS7DDoorBlock.getShape(state, world, pos, context);
    }

    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return LocoCollision.ForSS7DDoorBlock.getCollisionShape(this, state, world, pos, context);
    }

    public static DirectionalShape[][][] createShapes() {
        return LocoCollision.ForSS7DDoorBlock.createShapes();
    }

    public static VoxelShape mirrorX(VoxelShape shape) {
        return LocoCollision.ForSS7DDoorBlock.mirrorX(shape);
    }
}
