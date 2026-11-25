package willow.train.hexie.compat.create.track;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.trains.track.BezierTrackPointLocation;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackBlockEntity;
import com.simibubi.create.content.trains.track.TrackMaterial;
import com.simibubi.create.content.trains.track.TrackTargetingBehaviour;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.transform.Affine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.railway.TrackRegistryBridge;
import willow.train.hexie.bridge.world.WorldBlockBridge;

public class StandardTrackBlock extends TrackBlock implements WorldBlockBridge.StandardTrackBlockView {

    private static final ResourceLocation SMALL_BOGEY = ResourceLocation.fromNamespaceAndPath("create", "small_bogey");

    public StandardTrackBlock(BlockBehaviour.Properties properties, TrackMaterial material) {
        super(properties, material);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack,
            BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand,
            BlockHitResult hit) {
        return TrackBlockInteract.ForStandardTrackBlock.useItemOn(stack, state, level, pos, player, hand, hit, () -> super.useItemOn(stack, state, level, pos, player, hand, hit));
    }

    @Override
    public <Self extends Affine<Self>> PartialModel prepareTrackOverlay(Affine<Self> transform,
            BlockGetter world, BlockPos pos, BlockState state, BezierTrackPointLocation curvePoint,
            Direction.AxisDirection direction, TrackTargetingBehaviour.RenderedTrackOverlayType type) {
        // Apply before Create's slope/curve rotations so the offset remains vertical.
        TrackMaterial material = getMaterial();
        if (curvePoint != null && world.getBlockEntity(pos) instanceof TrackBlockEntity track) {
            var curve = track.getConnections().get(curvePoint.curveTarget());
            if (curve != null) material = curve.getMaterial();
        }
        transform.translate(0, (float) heightIncrease(material), 0);
        return super.prepareTrackOverlay(transform, world, pos, state, curvePoint, direction, type);
    }

    @Override
    public PartialModel prepareAssemblyOverlay(BlockGetter world, BlockPos pos, BlockState state,
            Direction direction, PoseStack pose) {
        pose.translate(0, heightIncrease(getMaterial()), 0);
        return super.prepareAssemblyOverlay(world, pos, state, direction, pose);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world,
            BlockPos pos, CollisionContext context) {
        return TrackCollision.ForStandardTrackBlock.getShape(this, state, world, pos, context, () -> super.getShape(state, world, pos, context));
    }

    @Override
    public VoxelShape getInteractionShape(BlockState state,
            BlockGetter world, BlockPos pos) {
        return TrackCollision.ForStandardTrackBlock.getInteractionShape(this, state, world, pos, () -> super.getInteractionShape(state, world, pos));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world,
            BlockPos pos, CollisionContext context) {
        return TrackCollision.ForStandardTrackBlock.getCollisionShape(this, state, world, pos, context, () -> super.getCollisionShape(state, world, pos, context));
    }

    public static double heightIncrease(TrackMaterial material) {
        // Existing subclasses/materials that did not use the registry retain their original height.
        return HexieBridges.createTrain().createRailwayBackend().supports(material) ? HexieBridges.createTrain().createRailwayBackend().heightIncrease(material)
                : TrackRegistryBridge.RAIL_HEIGHT_INCREASE;
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
        if (bogey.getBlockHolder().is(SMALL_BOGEY))
            bogey = HexieBridges.trackRegistry().trackBlockInit().hxStandardBogey().defaultBlockState();
        return bogey.setValue(BlockStateProperties.HORIZONTAL_AXIS, axis);
    }

    @Override public Block asBlock() { return this; }
}
