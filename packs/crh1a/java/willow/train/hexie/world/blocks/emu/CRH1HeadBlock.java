package willow.train.hexie.world.blocks.emu;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.interaction.InteractionBridge;
import willow.train.hexie.bridge.vehicle.CouplingBridge;
import willow.train.hexie.bridge.world.CarriageBlockBridge;
import willow.train.hexie.core.api.interaction.BlockControl;
import willow.train.hexie.core.api.interaction.BlockControlContext;
import willow.train.hexie.core.api.interaction.BlockControlHit;
import willow.train.hexie.core.api.interaction.BlockControlUse;
import willow.train.hexie.core.api.vehicle.Point3;
import willow.train.hexie.world.blockentity.head.CRH1HeadEntity;
import willow.train.hexie.world.blockinteract.EmuBlockInteract;
import willow.train.hexie.world.blocks.coupler.CouplerBlock;
import willow.train.hexie.world.blocks.coupler.ScharfenbergCouplerBlock;
import willow.train.hexie.world.blocks.parts.PartStructure;
import willow.train.hexie.world.collision.EmuCollision;
import willow.train.hexie.world.collision.PlayerCollisionShell;
import willow.train.hexie.world.placement.EmuPlacement;
import willow.train.hexie.world.placement.PartInstallation;
import willow.train.hexie.world.registry.blocks.BlockInit;

/** A complete CRH1A head with its floor, covers and Scharfenberg coupling point. */
@EventBusSubscriber(modid = "hexie")
public final class CRH1HeadBlock extends Block implements EntityBlock, PartStructure,
        CouplerBlock, InteractionBridge.NativeControlSurfaceView, PlayerCollisionShell, CarriageBlockBridge.CRH1HeadBlockView {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final String COUPLER_MODEL = ScharfenbergCouplerBlock.MODEL;
    public static final int ROOT_PART = 4, LEGACY_BOGEY_PART = 1;
    private static final Map<ServerLevel, Map<BlockPos, Map<BlockPos, BlockState>>> MOVED = new WeakHashMap<>();
    static { EmuCollision.ForCRH1HeadBlock.initialize(); }

    public CRH1HeadBlock(BlockBehaviour.Properties properties) {
        super(properties.dynamicShape());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(BlockStateProperties.OPEN, false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, BlockStateProperties.OPEN);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return EmuPlacement.ForCRH1HeadBlock.getStateForPlacement(this, context);
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
        EmuPlacement.ForCRH1HeadBlock.setPlacedBy(this, level, pos, state, placer, stack, () -> super.setPlacedBy(level, pos, state, placer, stack));
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
                HexieBridges.Client.carriageRender().crh1StaticSections().changed(world, pos, before, after);
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
    @Override public boolean readyForCoupling(BlockState state, CompoundTag data, double time) {
        return CRH1HeadAnimation.ready(state, data, time);
    }
    @Override public boolean hasStateDrivenRelease() { return true; }
    @Override public boolean releaseForState(BlockState state, CompoundTag data) {
        return !state.getValue(BlockStateProperties.OPEN);
    }
    @Override public boolean allowsAdapters() { return false; }
    @Override public Direction facing(BlockState state) { return state.getValue(FACING); }
    @Override public CouplingBridge.CouplingEndpointAxisView axis() { return HexieBridges.coupling().createCouplingEndpointAxis(CRH1HeadGeometry.AXIS.x - .5, CRH1HeadGeometry.AXIS.z - .5); }
    @Override public Point3 start() { return complete(); }
    @Override public Point3 complete() {
        return new Point3(0, CRH1HeadGeometry.CONTACT_HEIGHT - .5, -.5 - 25 / 16d - CRH1HeadAnimation.EXTENSION - .5);
    }
    @Override public double maxExtension() { return .5; }
    @Override public AABB controlBounds(BlockState state) {
        return EmuBlockInteract.ForCRH1HeadBlock.controlBounds(state);
    }
    @Override public BlockControlHit pickControl(BlockControlContext target, Vec3 from, Vec3 to) {
        return EmuBlockInteract.ForCRH1HeadBlock.pickControl(target, from, to);
    }
    @Override public boolean nativeInteraction() {
        return EmuBlockInteract.ForCRH1HeadBlock.nativeInteraction();
    }
    @Override public List<AABB> playerCollisionBoxes(BlockState state) {
        return EmuCollision.ForCRH1HeadBlock.playerCollisionBoxes(state);
    }
    @Override public InteractionResult operateControl(BlockControlUse use, BlockControl control, int value) {
        return EmuBlockInteract.ForCRH1HeadBlock.operateControl(use, control, value);
    }

    @Override public Block asBlock() { return this; }
}
