package willow.train.hexie.blocks.emu.crh1a;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import willow.train.hexie.api.interaction.BlockControl;
import willow.train.hexie.api.interaction.BlockControlContext;
import willow.train.hexie.api.interaction.BlockControlHit;
import willow.train.hexie.api.interaction.BlockControlUse;
import willow.train.hexie.blockentity.CRH1HeadEntity;
import willow.train.hexie.blocks.coupler.CouplerBlock;
import willow.train.hexie.blocks.parts.PartInstallation;
import willow.train.hexie.blocks.parts.PartStructure;
import willow.train.hexie.blocks.parts.PlayerCollisionShell;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;
import willow.train.hexie.compat.create.SnapshotCouplerInteraction;
import willow.train.hexie.init.block.BlockInit;
import willow.train.hexie.interaction.NativeControlSurface;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint.Axis;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint.Point;

/** A complete CRH1A head with its floor, covers and Scharfenberg coupling point. */
@EventBusSubscriber(modid = "hexie")
public final class CRH1HeadBlock extends Block implements EntityBlock, PartStructure,
        CouplerBlock, NativeControlSurface, PlayerCollisionShell {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final String COUPLER_MODEL = willow.train.hexie.blocks.coupler.ScharfenbergCouplerBlock.MODEL;
    public static final int ROOT_PART = 4, LEGACY_BOGEY_PART = 1;
    private static final Map<ServerLevel, Map<BlockPos, Map<BlockPos, BlockState>>> MOVED = new WeakHashMap<>();
    static { willow.train.hexie.blocks.parts.PlayerCollisionShells.register(CRH1HeadGeometry::collisions); }

    public CRH1HeadBlock(Properties properties) {
        super(properties.dynamicShape());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(BlockStateProperties.OPEN, false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, BlockStateProperties.OPEN);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        return defaultBlockState().setValue(FACING, face.getAxis().isHorizontal() ? face : context.getHorizontalDirection());
    }
    @Override public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }
    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }
    public static BlockPos offset(int part, Direction facing) {
        int x = part % 3 - 1, z = part / 3 - 1;
        return switch (facing) {
            case EAST -> new BlockPos(-z, 0, x);
            case SOUTH -> new BlockPos(-x, 0, -z);
            case WEST -> new BlockPos(z, 0, -x);
            default -> new BlockPos(x, 0, z);
        };
    }
    @Override public BlockPos partRoot(BlockGetter level, BlockPos pos, BlockState state) { return pos; }
    @Override public boolean schematicRoot() { return true; }
    @Override public Map<BlockPos, BlockState> partCells(BlockGetter level, BlockPos root, BlockState state) {
        var cells = new LinkedHashMap<BlockPos, BlockState>();
        for (int i = 0; i < 6; i++) {
            var pos = root.offset(offset(i, state.getValue(FACING)));
            cells.put(pos, i == ROOT_PART ? state : BlockInit.CRH1A_HEAD_FLOOR.get().defaultBlockState()
                    .setValue(FACING, state.getValue(FACING)).setValue(CRH1HeadFloorBlock.PART, i));
        }
        return cells;
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CRH1HeadEntity(pos, state); }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        for (var cell : partCells(level, pos, state).entrySet()) {
            if (!level.hasChunkAt(cell.getKey())) return;
            if (!level.getBlockState(cell.getKey()).equals(cell.getValue())) {
                PartInstallation.install(level, partCells(level, pos, state), ignored -> false, ignored -> true);
                break;
            }
        }
    }
    @Override public void onBlockStateChange(LevelReader world, BlockPos pos, BlockState before, BlockState after) {
        if (world instanceof Level level) {
            if (level.isClientSide)
                willow.train.hexie.client.render.CRH1StaticSections.changed(world, pos, before, after);
            PartInstallation.stateChanged(level, pos, before, after);
            if (!level.isClientSide && before.is(this) && after.is(this) && before.getValue(FACING) != after.getValue(FACING))
                level.scheduleTick(pos, this, 1);
        }
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!level.isClientSide && !state.is(next.getBlock()) && !PartInstallation.mutating()) {
            var cells = partCells(level, pos, state);
            if (moving && level instanceof ServerLevel server)
                MOVED.computeIfAbsent(server, ignored -> new HashMap<>()).put(pos.immutable(), cells);
            else if (!moving) PartInstallation.removeOtherCells(level, pos, cells);
        }
        super.onRemove(state, level, pos, next, moving);
    }
    @SubscribeEvent public static void cleanMovedRemainders(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel world)) return;
        var removed = MOVED.remove(world);
        if (removed == null) return;
        removed.forEach((root, cells) -> {
            if (!(world.getBlockState(root).getBlock() instanceof CRH1HeadBlock))
                PartInstallation.removeOtherCells(world, root, cells);
        });
    }
    @Override public String model() { return COUPLER_MODEL; }
    @Override public boolean requiresReadyState() { return true; }
    @Override public boolean readyForCoupling(BlockState state, net.minecraft.nbt.CompoundTag data, double time) {
        return CRH1HeadAnimation.ready(state, data, time);
    }
    @Override public boolean hasStateDrivenRelease() { return true; }
    @Override public boolean releaseForState(BlockState state, net.minecraft.nbt.CompoundTag data) {
        return !state.getValue(BlockStateProperties.OPEN);
    }
    @Override public boolean allowsAdapters() { return false; }
    @Override public Direction facing(BlockState state) { return state.getValue(FACING); }
    @Override public Axis axis() { return new Axis(CRH1HeadGeometry.AXIS.x - .5, CRH1HeadGeometry.AXIS.z - .5); }
    @Override public Point start() { return complete(); }
    @Override public Point complete() {
        return new Point(0, CRH1HeadGeometry.CONTACT_HEIGHT - .5, -.5 - 25 / 16d - CRH1HeadAnimation.EXTENSION - .5);
    }
    @Override public double maxExtension() { return .5; }
    @Override public AABB controlBounds(BlockState state) { return CRH1HeadGeometry.controlBounds(state); }
    @Override public BlockControlHit pickControl(BlockControlContext target, Vec3 from, Vec3 to) {
        return CRH1HeadGeometry.pick(target, from, to);
    }
    @Override public boolean nativeInteraction() { return false; }
    @Override public java.util.List<AABB> playerCollisionBoxes(BlockState state) {
        return CRH1HeadGeometry.playerBoxes(state.getValue(FACING));
    }
    @Override public InteractionResult operateControl(BlockControlUse use, BlockControl control, int value) {
        if (use.player().isSpectator() || !use.player().mayBuild()) return InteractionResult.FAIL;
        var target = use.target();
        if (control == NativeControlSurface.Control.COUPLER_HEAD) {
            return target.carrier() instanceof SnapshotCarriageEntity carriage
                    && SnapshotCouplerInteraction.handle(carriage, use.player(), target.position(), use.hand())
                    ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (control != NativeControlSurface.Control.COUPLER_BODY) return InteractionResult.PASS;
        if (target.carrier() instanceof SnapshotCarriageEntity carriage) {
            var info = carriage.getContraption().getBlocks().get(target.position());
            if (info == null) return InteractionResult.FAIL;
            if (info.state().getValue(BlockStateProperties.OPEN)) CRH1HeadCoupling.release(carriage, target.position());
            var next = CRH1HeadAnimation.toggle(info, carriage.level().getGameTime());
            carriage.setBlock(target.position(), next);
            if (!carriage.level().isClientSide)
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntity(carriage,
                        willow.train.hexie.network.s2c.CRH1HeadAnimationPacket.create(carriage, next));
            carriage.syncCarriage();
            return InteractionResult.SUCCESS;
        }
        if (!(target.blocks() instanceof Level level)
                || !(level.getBlockEntity(target.position()) instanceof CRH1HeadEntity head)) return InteractionResult.FAIL;
        head.beginAnimation(level.getGameTime());
        if (!level.setBlock(target.position(), target.state().cycle(BlockStateProperties.OPEN), Block.UPDATE_ALL))
            return InteractionResult.FAIL;
        head.syncAnimation();
        return InteractionResult.SUCCESS;
    }
}
