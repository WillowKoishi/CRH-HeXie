package willow.train.hexie.blocks.emu.crh1a;

import com.mojang.serialization.MapCodec;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.Main;
import willow.train.hexie.api.interaction.BlockControlContext;
import willow.train.hexie.api.interaction.BlockControlHit;
import willow.train.hexie.api.interaction.ControlSurface;
import willow.train.hexie.blockentity.CustomRenderedDoorEntity;
import willow.train.hexie.blocks.carriage.CustomRenderedDoorBlock;
import willow.train.hexie.blocks.emu.head.HeadGeometry;
import willow.train.hexie.blocks.parts.ModelRenderBounds;
import willow.train.hexie.compat.create.CRH1DoorMovement;
import willow.train.hexie.interaction.NativeControlSurface;
import willow.train.hexie.mixins.mixin.ContraptionWorldAccessor;

/** CRH1A plug-sliding passenger door with its own fixed shell, movement and picking. */
public final class CRH1DoorBlock extends CustomRenderedDoorBlock implements NativeControlSurface, ModelRenderBounds,
        willow.train.hexie.blocks.parts.PartStructure {
    public static final MapCodec<CRH1DoorBlock> CODEC = simpleCodec(CRH1DoorBlock::new);
    private final VoxelShape[] interactions = new VoxelShape[16];
    private final VoxelShape[] outlines = new VoxelShape[32];
    private final VoxelShape[] collisions = new VoxelShape[32];
    private final VoxelShape[] collisionCells = new VoxelShape[64];

    public CRH1DoorBlock(Properties properties) {
        super(properties, models("left"), models("right"), RenderShape.ENTITYBLOCK_ANIMATED, true);
    }

    @Override public MapCodec<? extends DoorBlock> codec() { return CODEC; }
    @Override public int animationDurationTicks() { return CRH1DoorGeometry.ANIMATION_TICKS; }

    @Override public BlockPos partRoot(BlockGetter world, BlockPos pos, BlockState state) { return root(state, pos); }
    @Override public boolean schematicRoot() { return true; }

    @Override public java.util.Map<BlockPos, BlockState> partCells(BlockGetter world, BlockPos root, BlockState state) {
        var cells = new java.util.LinkedHashMap<BlockPos, BlockState>();
        state = state.setValue(HALF, DoubleBlockHalf.LOWER);
        cells.put(root, state);
        cells.put(root.above(), state.setValue(HALF, DoubleBlockHalf.UPPER));
        BlockPos right = root.relative(state.getValue(FACING).getClockWise());
        for (BlockPos pos : new BlockPos[]{right, right.above()})
            cells.put(pos, willow.train.hexie.blocks.parts.PartMemberBlock.withOwnerOffset(
                    willow.train.hexie.init.block.BlockInit.CRH1A_PASSENGER_DOOR_MEMBER.get().defaultBlockState(), root.subtract(pos)));
        return cells;
    }

    @Override protected void onRemove(BlockState state, net.minecraft.world.level.Level world, BlockPos pos, BlockState next, boolean moving) {
        if (!moving && !state.is(next.getBlock()))
            willow.train.hexie.blocks.parts.PartInstallation.removeOtherCells(world, pos, partCells(world, root(state, pos), state));
        super.onRemove(state, world, pos, next, moving);
    }

    @Override public void neighborChanged(BlockState state, net.minecraft.world.level.Level world, BlockPos pos,
            net.minecraft.world.level.block.Block changed, BlockPos from, boolean moving) {
        if (world.isClientSide || moving || willow.train.hexie.blocks.parts.PartInstallation.mutating()) return;
        BlockPos root = root(state, pos);
        var owner = world.getBlockState(root);
        if (!owner.is(this)) return;
        if (world instanceof net.minecraft.server.level.ServerLevel server) CRH1DoorRepair.schedule(server, root);
        var cells = partCells(world, root, owner);
        boolean powered = cells.keySet().stream().anyMatch(world::hasNeighborSignal);
        if (powered == owner.getValue(POWERED)) return;
        var next = owner.setValue(POWERED, powered).setValue(OPEN, powered);
        if (!willow.train.hexie.blocks.parts.PartInstallation.install(world, partCells(world, root, next),
                at -> cells.get(at) == world.getBlockState(at), at -> true)) return;
        if (owner.getValue(OPEN) != powered) {
            world.playSound(null, root, powered ? net.minecraft.sounds.SoundEvents.IRON_DOOR_OPEN
                    : net.minecraft.sounds.SoundEvents.IRON_DOOR_CLOSE, net.minecraft.sounds.SoundSource.BLOCKS, 1, .9f);
            world.gameEvent(null, powered ? net.minecraft.world.level.gameevent.GameEvent.BLOCK_OPEN
                    : net.minecraft.world.level.gameevent.GameEvent.BLOCK_CLOSE, root);
        }
    }

    @Override protected void tick(BlockState state, net.minecraft.server.level.ServerLevel world, BlockPos pos,
            net.minecraft.util.RandomSource random) {
        if (CRH1DoorRepair.repair(world, pos) == CRH1DoorRepair.Result.DEFERRED)
            world.scheduleTick(pos, this, 20);
    }

    private static Couple<ResourceLocation> models(String side) {
        var model = Main.asResource("crh1a/doors/crh1a_psg_door_" + side);
        return Couple.create(model, model);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) return null;
        Direction face = context.getClickedFace();
        Direction facing = face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getOpposite();
        Direction right = facing.getClockWise();
        Vec3 hit = context.getClickLocation();
        BlockPos pos = context.getClickedPos();
        double side = (hit.x - pos.getX() - .5) * right.getStepX()
                + (hit.z - pos.getZ() - .5) * right.getStepZ();
        return state.setValue(FACING, facing).setValue(HINGE, side > 0 ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT);
    }

    private static BlockPos root(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    public boolean fullyOpen(BlockState state, BlockGetter world, BlockPos pos) {
        if (!state.getValue(OPEN)) return false;
        BlockPos lower = root(state, pos);
        if (world instanceof ContraptionWorldAccessor view)
            return CRH1DoorMovement.fullyOpen(view.hexie$getContraption(), lower, this);
        return world.getBlockEntity(lower) instanceof CustomRenderedDoorEntity door && door.crh2Progress() >= 1;
    }

    private static int key(BlockState state, boolean open) {
        return state.getValue(FACING).get2DDataValue() * 4
                + (state.getValue(HINGE) == DoorHingeSide.RIGHT ? 2 : 0) + (open ? 1 : 0);
    }

    private static VoxelShape box(BlockState state, AABB box) {
        box = CRH1DoorGeometry.mirror(box, state.getValue(HINGE) == DoorHingeSide.RIGHT);
        return Shapes.create(HeadGeometry.rotate(box, state.getValue(FACING)));
    }

    /** Only completed poses are cached. No per-frame collision mesh is constructed. */
    public VoxelShape interactionShape(BlockState state, boolean open) {
        int key = key(state, open);
        if (interactions[key] == null) {
            VoxelShape shape = Shapes.empty();
            for (AABB jamb : CRH1DoorGeometry.JAMBS) shape = Shapes.or(shape, box(state, jamb));
            Vec3 shift = CRH1DoorGeometry.offset(open ? 1 : 0, false);
            interactions[key] = Shapes.or(shape, box(state, CRH1DoorGeometry.LEAF.move(shift)));
        }
        return interactions[key];
    }

    private static VoxelShape half(VoxelShape shape, boolean upper) {
        double y = upper ? 1 : 0;
        return Shapes.joinUnoptimized(shape, Shapes.create(new AABB(-4, upper ? 1 : -1, -4,
                4, upper ? 3 : 1, 4)), BooleanOp.AND).move(0, -y, 0);
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        boolean open = fullyOpen(state, world, pos), upper = state.getValue(HALF) == DoubleBlockHalf.UPPER;
        int key = key(state, open) * 2 + (upper ? 1 : 0);
        if (outlines[key] == null) outlines[key] = half(interactionShape(state, open), upper);
        return outlines[key];
    }

    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return collisionCellShape(state, world, pos, 0);
    }

    /** Each occupied column contributes its own slab, without overlapping another cell's collider. */
    public VoxelShape collisionCellShape(BlockState state, BlockGetter world, BlockPos pos, int column) {
        boolean open = fullyOpen(state, world, pos), upper = state.getValue(HALF) == DoubleBlockHalf.UPPER;
        int key = key(state, open) * 2 + (upper ? 1 : 0);
        if (collisions[key] == null) {
            VoxelShape shape = Shapes.empty();
            for (AABB fixed : CRH1DoorGeometry.FIXED) shape = Shapes.or(shape, box(state, fixed));
            if (!open) shape = Shapes.or(shape, box(state, CRH1DoorGeometry.LEAF));
            collisions[key] = half(shape, upper);
        }
        int cell = key * 2 + column;
        if (collisionCells[cell] == null) {
            var region = Shapes.create(HeadGeometry.rotate(new AABB(column, -1, -4, column + 1, 3, 4), state.getValue(FACING)));
            collisionCells[cell] = Shapes.joinUnoptimized(collisions[key], region, BooleanOp.AND);
        }
        return collisionCells[cell];
    }

    @Override public AABB modelRenderBounds(BlockState state) {
        AABB bounds = HeadGeometry.rotate(CRH1DoorGeometry.RENDER_BOUNDS, state.getValue(FACING));
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? bounds.move(0, -1, 0) : bounds;
    }

    @Override public ControlSurface surfaceGeometry(BlockState state) { return DOOR; }

    @Override public AABB controlBounds(BlockState state) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) return new AABB(0, 0, 0, 1, 1, 1);
        return interactionShape(state, false).bounds().minmax(interactionShape(state, true).bounds());
    }

    @Override public BlockControlHit pickControl(BlockControlContext target, Vec3 from, Vec3 to) {
        BlockPos lower = root(target.state(), target.position());
        BlockState state = target.blocks().getBlockState(lower);
        if (!state.is(this)) return null;
        var hit = interactionShape(state, fullyOpen(state, target.blocks(), lower)).clip(from, to, lower);
        return hit == null ? null : new BlockControlHit(Control.DOOR,
                hit.withPosition(hit.getLocation().y >= lower.getY() + 1 ? lower.above() : lower));
    }
}
