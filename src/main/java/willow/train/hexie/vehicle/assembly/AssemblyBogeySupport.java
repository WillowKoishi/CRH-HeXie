package willow.train.hexie.vehicle.assembly;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.jetbrains.annotations.Nullable;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.VehicleBridge;
import willow.train.hexie.bridge.world.BlockEntityViews;
import willow.train.hexie.bridge.world.CarriageBlockBridge;
import willow.train.hexie.bridge.world.WorldBlockBridge;

/** A support's state describes its bogey; entityData belongs to its real owner, never a fabricated world block. */
public record AssemblyBogeySupport(BlockPos owner, BlockPos position, BlockState state, CompoundTag entityData) implements VehicleBridge.AssemblyBogeySupportView {
    public AssemblyBogeySupport {
        owner = Objects.requireNonNull(owner).immutable();
        position = Objects.requireNonNull(position).immutable();
        Objects.requireNonNull(state);
        entityData = Objects.requireNonNull(entityData).copy();
    }

    @Override public CompoundTag entityData() { return entityData.copy(); }

    /** The effective state/data are only for bogey consumers; never place this block in the world. */
    public CarriageSnapshot.Block capturedBlock() {
        if (owner.equals(position)) return new CarriageSnapshot.Block(position, state, entityData);
        var absoluteOwner = new BlockPos(entityData.getInt("x"), entityData.getInt("y"), entityData.getInt("z"));
        return new CarriageSnapshot.Block(position, state,
                entityData(state, absoluteOwner.offset(position.subtract(owner)), entityData.getCompound(CarriageBlockBridge.HeadBogeyOperations.DATA)));
    }

    public static boolean isSupport(BlockGetter level, BlockPos pos) {
        if (level instanceof Level world && !world.hasChunkAt(pos)) return false;
        return level.getBlockState(pos).getBlock() instanceof WorldBlockBridge.HexieBogeyBlockView || resolve(level, pos) != null;
    }

    public static @Nullable AssemblyBogeySupport resolve(BlockGetter level, BlockPos pos) {
        if (level instanceof Level world && !world.hasChunkAt(pos)) return null;
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof WorldBlockBridge.HexieBogeyBlockView) {
            var entity = level.getBlockEntity(pos);
            var data = entity != null && entity.getLevel() != null
                    ? entity.saveWithFullMetadata(entity.getLevel().registryAccess()) : entityData(state, pos, new CompoundTag());
            return new AssemblyBogeySupport(pos, pos, state, data);
        }
        if (state.getBlock() instanceof VehicleBridge.OwnedBogeyMountView mount && mount.isBogeyMount(state)) {
            var root = mount.bogeyOwner(pos, state);
            if (level instanceof Level world && !world.hasChunkAt(root)) return null;
            var entity = level.getBlockEntity(root);
            if (entity == null || entity.getLevel() == null) return null;
            var data = entity.saveWithFullMetadata(entity.getLevel().registryAccess());
            if (!mount.bogeyEnabled(data)) return null;
            return new AssemblyBogeySupport(root, pos, mount.bogeyState(level.getBlockState(root)), data);
        }
        if (!headMount(state)) return null;
        var root = HexieBridges.carriageBlock().headGeometry().owner(level, pos);
        if (root == null || !(level.getBlockEntity(root) instanceof BlockEntityViews.CRH2HeadEntityView head) || !head.bogeyEnabled()) return null;
        var facing = level.getBlockState(root).getValue(BlockStateProperties.HORIZONTAL_FACING);
        if (state.getValue(BlockStateProperties.HORIZONTAL_FACING) != facing || !HexieBridges.carriageBlock().headBogey().mount(root, facing).equals(pos)) return null;
        var effective = HexieBridges.carriageBlock().headBogey().state(facing);
        if (head.asBlockEntity().getLevel() == null) return null;
        return new AssemblyBogeySupport(root, pos, effective, head.asBlockEntity().saveWithFullMetadata(head.asBlockEntity().getLevel().registryAccess()));
    }

    /** Positions remain in the snapshot's local frame; NBT coordinates remain absolute as usual. */
    public static @Nullable AssemblyBogeySupport resolve(CarriageSnapshot snapshot, BlockPos pos) {
        return resolve(position -> snapshot.blocks().stream().filter(value -> value.position().equals(position))
                .map(value -> new SavedCell(value.state(), value.entityData())).findFirst().orElse(null), pos);
    }

    /** Resolves live assembled data, including settings changed since the saved snapshot was captured. */
    public static @Nullable AssemblyBogeySupport resolve(Map<BlockPos, StructureBlockInfo> blocks, BlockPos pos) {
        return resolve(position -> {
            var block = blocks.get(position);
            return block == null ? null : new SavedCell(block.state(), block.nbt());
        }, pos);
    }

    private record SavedCell(BlockState state, CompoundTag entityData) {}

    private static @Nullable AssemblyBogeySupport resolve(Function<BlockPos, SavedCell> blocks, BlockPos pos) {
        var block = blocks.apply(pos);
        if (block == null) return null;
        if (block.state().getBlock() instanceof WorldBlockBridge.HexieBogeyBlockView && block.entityData() != null)
            return new AssemblyBogeySupport(pos, pos, block.state(), block.entityData());
        if (block.state().getBlock() instanceof VehicleBridge.OwnedBogeyMountView mount && mount.isBogeyMount(block.state())) {
            var root = mount.bogeyOwner(pos, block.state());
            var owner = blocks.apply(root);
            if (owner == null || owner.entityData() == null || !mount.bogeyEnabled(owner.entityData())) return null;
            return new AssemblyBogeySupport(root, pos, mount.bogeyState(owner.state()), owner.entityData());
        }
        if (!headMount(block.state())) return null;
        var facing = block.state().getValue(BlockStateProperties.HORIZONTAL_FACING);
        var root = pos.subtract(HexieBridges.carriageBlock().headGeometry().offset(CarriageBlockBridge.HeadGeometryOperations.BOGEY_PART, facing));
        var owner = blocks.apply(root);
        if (owner == null || !(owner.state().getBlock() instanceof CarriageBlockBridge.CRH2HeadBlockView)
                || owner.state().getValue(BlockStateProperties.HORIZONTAL_FACING) != facing || owner.entityData() == null) return null;
        var data = owner.entityData();
        if (!data.getBoolean(CarriageBlockBridge.HeadBogeyOperations.ENABLED)) return null;
        var effective = HexieBridges.carriageBlock().headBogey().state(facing);
        return new AssemblyBogeySupport(root, pos, effective, data);
    }

    private static boolean headMount(BlockState state) {
        return state.getBlock() instanceof CarriageBlockBridge.HeadFloorBlockView && state.getValue(HexieBridges.carriageBlock().headFloorBlock().part()) == CarriageBlockBridge.HeadGeometryOperations.BOGEY_PART;
    }

    private static CompoundTag entityData(BlockState state, BlockPos pos, CompoundTag bogeyData) {
        var data = new CompoundTag();
        var entity = Objects.requireNonNull(((EntityBlock) state.getBlock()).newBlockEntity(pos, state));
        data.putString("id", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType()).toString());
        data.putInt("x", pos.getX()); data.putInt("y", pos.getY()); data.putInt("z", pos.getZ());
        data.put(CarriageBlockBridge.HeadBogeyOperations.DATA, bogeyData.copy());
        return data;
    }
}
