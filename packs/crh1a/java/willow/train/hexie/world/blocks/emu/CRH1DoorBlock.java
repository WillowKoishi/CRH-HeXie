package willow.train.hexie.world.blocks.emu;

import com.mojang.serialization.MapCodec;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.CreateCarriageBridge;
import willow.train.hexie.bridge.interaction.InteractionBridge;
import willow.train.hexie.bridge.world.CarriageBlockBridge;
import willow.train.hexie.core.HexieConstants;
import willow.train.hexie.core.api.interaction.BlockControlContext;
import willow.train.hexie.core.api.interaction.BlockControlHit;
import willow.train.hexie.core.api.interaction.ControlSurface;
import willow.train.hexie.core.api.render.DoorModelLocations;
import willow.train.hexie.world.blockentity.door.CustomRenderedDoorEntity;
import willow.train.hexie.world.blockinteract.EmuBlockInteract;
import willow.train.hexie.world.blocks.carriage.CustomRenderedDoorBlock;
import willow.train.hexie.world.blocks.parts.ModelRenderBounds;
import willow.train.hexie.world.blocks.parts.PartMemberBlock;
import willow.train.hexie.world.blocks.parts.PartStructure;
import willow.train.hexie.world.collision.EmuCollision;
import willow.train.hexie.world.placement.EmuPlacement;
import willow.train.hexie.world.placement.PartInstallation;
import willow.train.hexie.world.registry.blocks.BlockInit;

/** CRH1A plug-sliding passenger door with its own fixed shell, movement and picking. */
public final class CRH1DoorBlock extends CustomRenderedDoorBlock implements InteractionBridge.NativeControlSurfaceView, ModelRenderBounds,
        PartStructure, CarriageBlockBridge.CRH1DoorBlockView {
    public static final MapCodec<CRH1DoorBlock> CODEC = simpleCodec(CRH1DoorBlock::new);
    private final EmuCollision.ForCRH1DoorBlock collisionShapes = new EmuCollision.ForCRH1DoorBlock();

    public CRH1DoorBlock(BlockBehaviour.Properties properties) {
        super(properties, models("left"), models("right"), RenderShape.ENTITYBLOCK_ANIMATED, true);
    }

    @Override public MapCodec<? extends DoorBlock> codec() { return CODEC; }
    @Override public int animationDurationTicks() { return CRH1DoorGeometry.ANIMATION_TICKS; }

    @Override public BlockPos partRoot(BlockGetter world, BlockPos pos, BlockState state) { return root(state, pos); }
    @Override public boolean schematicRoot() { return true; }

    @Override public Map<BlockPos, BlockState> partCells(BlockGetter world, BlockPos root, BlockState state) {
        var cells = new LinkedHashMap<BlockPos, BlockState>();
        state = state.setValue(HALF, DoubleBlockHalf.LOWER);
        cells.put(root, state);
        cells.put(root.above(), state.setValue(HALF, DoubleBlockHalf.UPPER));
        BlockPos right = root.relative(state.getValue(FACING).getClockWise());
        for (BlockPos pos : new BlockPos[]{right, right.above()})
            cells.put(pos, PartMemberBlock.withOwnerOffset(
                    BlockInit.CRH1A_PASSENGER_DOOR_MEMBER.get().defaultBlockState(), root.subtract(pos)));
        return cells;
    }

    @Override protected void onRemove(BlockState state, Level world, BlockPos pos, BlockState next, boolean moving) {
        if (!moving && !state.is(next.getBlock()))
            PartInstallation.removeOtherCells(world, pos, partCells(world, root(state, pos), state));
        super.onRemove(state, world, pos, next, moving);
    }

    @Override public void neighborChanged(BlockState state, Level world, BlockPos pos,
            Block changed, BlockPos from, boolean moving) {
        if (world.isClientSide || moving || PartInstallation.mutating()) return;
        BlockPos root = root(state, pos);
        var owner = world.getBlockState(root);
        if (!owner.is(this)) return;
        if (world instanceof ServerLevel server) CRH1DoorRepair.schedule(server, root);
        var cells = partCells(world, root, owner);
        boolean powered = cells.keySet().stream().anyMatch(world::hasNeighborSignal);
        if (powered == owner.getValue(POWERED)) return;
        var next = owner.setValue(POWERED, powered).setValue(OPEN, powered);
        if (!PartInstallation.install(world, partCells(world, root, next),
                at -> cells.get(at) == world.getBlockState(at), at -> true)) return;
        if (owner.getValue(OPEN) != powered) {
            world.playSound(null, root, powered ? SoundEvents.IRON_DOOR_OPEN
                    : SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1, .9f);
            world.gameEvent(null, powered ? GameEvent.BLOCK_OPEN
                    : GameEvent.BLOCK_CLOSE, root);
        }
    }

    @Override protected void tick(BlockState state, ServerLevel world, BlockPos pos,
            RandomSource random) {
        if (CRH1DoorRepair.repair(world, pos) == CRH1DoorRepair.Result.DEFERRED)
            world.scheduleTick(pos, this, 20);
    }

    private static DoorModelLocations models(String side) {
        var model = HexieConstants.asResource("crh1a/doors/crh1a_psg_door_" + side);
        return new DoorModelLocations(model, model);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return EmuPlacement.ForCRH1DoorBlock.getStateForPlacement(context, () -> super.getStateForPlacement(context));
    }

    public static BlockPos root(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    public boolean fullyOpen(BlockState state, BlockGetter world, BlockPos pos) {
        if (!state.getValue(OPEN)) return false;
        BlockPos lower = root(state, pos);
        if (world instanceof CreateCarriageBridge.ContraptionWorldAccessorView view)
            return HexieBridges.createDoor().crh1DoorMovement().fullyOpen(view.hexie$getContraption(), lower, this);
        return world.getBlockEntity(lower) instanceof CustomRenderedDoorEntity door && door.crh2Progress() >= 1;
    }

    public static int key(BlockState state, boolean open) {
        return EmuCollision.ForCRH1DoorBlock.key(state, open);
    }

    public static VoxelShape box(BlockState state, AABB box) {
        return EmuCollision.ForCRH1DoorBlock.box(state, box);
    }

    /** Only completed poses are cached. No per-frame collision mesh is constructed. */
    public VoxelShape interactionShape(BlockState state, boolean open) {
        return collisionShapes.interactionShape(state, open);
    }

    public static VoxelShape half(VoxelShape shape, boolean upper) {
        return EmuCollision.ForCRH1DoorBlock.half(shape, upper);
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return collisionShapes.getShape(this, state, world, pos, context);
    }

    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return collisionShapes.getCollisionShape(this, state, world, pos, context);
    }

    /** Each occupied column contributes its own slab, without overlapping another cell's collider. */
    public VoxelShape collisionCellShape(BlockState state, BlockGetter world, BlockPos pos, int column) {
        return collisionShapes.collisionCellShape(this, state, world, pos, column);
    }

    @Override public AABB modelRenderBounds(BlockState state) {
        AABB bounds = HeadGeometry.rotate(CRH1DoorGeometry.RENDER_BOUNDS, state.getValue(FACING));
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? bounds.move(0, -1, 0) : bounds;
    }

    @Override public ControlSurface surfaceGeometry(BlockState state) {
        return EmuBlockInteract.ForCRH1DoorBlock.surfaceGeometry(state);
    }

    @Override public AABB controlBounds(BlockState state) {
        return EmuBlockInteract.ForCRH1DoorBlock.controlBounds(this, state);
    }

    @Override public BlockControlHit pickControl(BlockControlContext target, Vec3 from, Vec3 to) {
        return EmuBlockInteract.ForCRH1DoorBlock.pickControl(this, target, from, to);
    }
}
