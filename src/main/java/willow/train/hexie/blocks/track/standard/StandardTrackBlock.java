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
        if (material == TrackMaterialInit.STANDARD)
            registerDefaultState(defaultBlockState().setValue(HAS_BE, true));
    }

    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return TrackBallast.enabled(state) ? com.simibubi.create.AllBlockEntityTypes.TRACK.create(pos, state)
                : super.newBlockEntity(pos, state);
    }

    @Override
    public void onRemove(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
            BlockState replacement, boolean moving) {
        if (TrackBallast.enabled(state)) {
            // Legacy straight tracks may have turn=false, but now also own a block entity.
            state = state.setValue(HAS_BE, true);
            if (replacement.is(this)) replacement = replacement.setValue(HAS_BE, true);
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    @Override
    public net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,
            BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
            net.minecraft.world.phys.BlockHitResult hit) {
        if (!TrackBallast.enabled(state) || !TrackBallast.canPaint(player, stack))
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof com.simibubi.create.content.trains.track.TrackBlockEntity track) {
            BlockState texture = ((net.minecraft.world.item.BlockItem) stack.getItem()).getBlock().defaultBlockState();
            TrackBallast.paint(track, null, texture);
            for (var connection : track.getConnections().values())
                if (connection.getMaterial() == TrackMaterialInit.STANDARD)
                    TrackBallast.paint(track, connection.getKey(), texture);
        }
        return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
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
        BlockState bogey = TrackBlockInit.HX_STANDARD_BOGEY.getDefaultState();
        return bogey.setValue(BlockStateProperties.HORIZONTAL_AXIS, axis);
    }
}
