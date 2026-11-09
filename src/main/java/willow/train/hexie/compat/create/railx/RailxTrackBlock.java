package willow.train.hexie.compat.create.railx;

import com.lhwdev.minecraft.railx.flexiTrack.FlexiTrackBlock;
import com.simibubi.create.content.trains.track.BezierTrackPointLocation;
import com.simibubi.create.content.trains.track.TrackMaterial;
import com.simibubi.create.content.trains.track.TrackTargetingBehaviour.RenderedTrackOverlayType;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.transform.Affine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.init.track.TrackMaterialInit;
import willow.train.hexie.compat.create.track.CreateRailwayBackend;
import willow.train.hexie.blocks.track.standard.TrackBallast;

/** RailX owns flexible placement and connections; the original material owns the rail models. */
public final class RailxTrackBlock extends FlexiTrackBlock {
    public RailxTrackBlock(Properties properties, TrackMaterial material) {
        super(properties, material);
    }

    @Override public String getDescriptionId() { return getNormalBlock().getDescriptionId(); }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        return TrackBallast.apply(stack, state, level, pos, player)
                ? ItemInteractionResult.sidedSuccess(level.isClientSide)
                : super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        Vec3 offset = heightOffset(world, pos, state);
        return super.getShape(state, world, pos, context).move(offset.x, offset.y, offset.z);
    }

    @Override
    public VoxelShape getInteractionShape(BlockState state, BlockGetter world, BlockPos pos) {
        Vec3 offset = heightOffset(world, pos, state);
        return super.getInteractionShape(state, world, pos).move(offset.x, offset.y, offset.z);
    }

    @Override
    public <Self extends Affine<Self>> PartialModel prepareTrackOverlay(Affine<Self> transform,
            BlockGetter world, BlockPos pos, BlockState state, BezierTrackPointLocation curvePoint,
            Direction.AxisDirection direction, RenderedTrackOverlayType type) {
        Vec3 offset = heightOffset(world, pos, state);
        var track = blockEntity(world, pos);
        if (curvePoint != null && track != null) {
            var curve = track.getConnections().get(curvePoint.curveTarget());
            if (curve != null) offset = curve.getNormal(curve.getSegmentT(curvePoint.segment() + 1))
                    .scale(heightIncrease(curve.getMaterial()));
        }
        transform.translate(offset);
        return super.prepareTrackOverlay(transform, world, pos, state, curvePoint, direction, type);
    }

    /** Shared by the selection shape, overlays and RailX's rotation settings box. */
    public Vec3 heightOffset(BlockGetter world, BlockPos pos, BlockState state) {
        var track = blockEntity(world, pos);
        if (track != null && !track.getState().getShapeCache().isEmpty()) {
            // Match the same rotation as RailX's model and first-axis selection box,
            // including combined gradient and cant, rather than deriving another rotation.
            var offset = track.getState().getShapeCache().getFirst().getRotationValueDouble()
                    .transform(new org.joml.Vector3d(0, heightIncrease(getMaterial()), 0));
            return new Vec3(offset.x, offset.y, offset.z);
        }
        return getUpNormal(world, pos, state).scale(heightIncrease(getMaterial()));
    }

    private static double heightIncrease(TrackMaterial material) {
        return CreateRailwayBackend.supports(material) ? CreateRailwayBackend.heightIncrease(material)
                : TrackMaterialInit.RAIL_HEIGHT_INCREASE;
    }
}
