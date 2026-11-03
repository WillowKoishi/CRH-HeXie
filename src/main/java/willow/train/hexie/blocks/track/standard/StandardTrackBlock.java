package willow.train.hexie.blocks.track.standard;

import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackMaterial;
import com.simibubi.create.content.trains.track.BezierTrackPointLocation;
import com.simibubi.create.content.trains.track.TrackTargetingBehaviour.RenderedTrackOverlayType;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.transform.Affine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import willow.train.hexie.init.track.TrackBlockInit;
import willow.train.hexie.init.track.TrackMaterialInit;

public class StandardTrackBlock extends TrackBlock {

    public StandardTrackBlock(Properties properties, TrackMaterial material) {
        super(properties, material);
    }

    @Override
    public net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,
            BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
            net.minecraft.world.phys.BlockHitResult hit) {
        return TrackBallast.apply(stack, state, level, pos, player)
                ? net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide)
                : super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    public <Self extends Affine<Self>> PartialModel prepareTrackOverlay(Affine<Self> transform,
            BlockGetter world, BlockPos pos, BlockState state, BezierTrackPointLocation curvePoint,
            Direction.AxisDirection direction, RenderedTrackOverlayType type) {
        // Apply before Create's slope/curve rotations so the offset remains vertical.
        transform.translate(0, (float) TrackMaterialInit.RAIL_HEIGHT_INCREASE, 0);
        return super.prepareTrackOverlay(transform, world, pos, state, curvePoint, direction, type);
    }

    @Override
    public PartialModel prepareAssemblyOverlay(BlockGetter world, BlockPos pos, BlockState state,
            Direction direction, PoseStack pose) {
        pose.translate(0, TrackMaterialInit.RAIL_HEIGHT_INCREASE, 0);
        return super.prepareAssemblyOverlay(world, pos, state, direction, pose);
    }

    @Override
    public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, BlockGetter world,
            BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        return super.getShape(state, world, pos, context).move(0,
                willow.train.hexie.init.track.TrackMaterialInit.RAIL_HEIGHT_INCREASE, 0);
    }

    @Override
    public net.minecraft.world.phys.shapes.VoxelShape getInteractionShape(BlockState state,
            BlockGetter world, BlockPos pos) {
        return super.getInteractionShape(state, world, pos).move(0,
                willow.train.hexie.init.track.TrackMaterialInit.RAIL_HEIGHT_INCREASE, 0);
    }

    @Override
    public net.minecraft.world.phys.shapes.VoxelShape getCollisionShape(BlockState state, BlockGetter world,
            BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        return super.getCollisionShape(state, world, pos, context).move(0,
                willow.train.hexie.init.track.TrackMaterialInit.RAIL_HEIGHT_INCREASE, 0);
    }

    @Override
    public BlockState getBogeyAnchor(BlockGetter world, BlockPos pos, BlockState state) {
        Direction.Axis axis = switch (state.getValue(SHAPE)) {
            case XO -> Direction.Axis.X;
            default -> Direction.Axis.Z;
        };
        // Let Create's placement hooks choose the block before the station writes its
        // selected style. Returning CW2 unconditionally pairs it with another style's NBT.
        BlockState bogey = super.getBogeyAnchor(world, pos, state);
        if (bogey.is(com.simibubi.create.AllBlocks.SMALL_BOGEY.get()))
            bogey = TrackBlockInit.HX_STANDARD_BOGEY.getDefaultState();
        return bogey.setValue(BlockStateProperties.HORIZONTAL_AXIS, axis);
    }
}
