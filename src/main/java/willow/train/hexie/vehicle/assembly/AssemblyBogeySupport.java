package willow.train.hexie.vehicle.assembly;

import java.util.Objects;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.jetbrains.annotations.Nullable;
import willow.train.hexie.blockentity.CRH2HeadEntity;
import willow.train.hexie.blocks.bogeys.HexieBogeyBlock;
import willow.train.hexie.blocks.emu.head.CRH2HeadBlock;
import willow.train.hexie.blocks.emu.head.HeadBogey;
import willow.train.hexie.blocks.emu.head.HeadFloorBlock;
import willow.train.hexie.blocks.emu.head.HeadGeometry;

/** A support's state describes its bogey; entityData belongs to its real owner, never a fabricated world block. */
public record AssemblyBogeySupport(BlockPos owner, BlockPos position, BlockState state, CompoundTag entityData) {
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
                entityData(state, absoluteOwner.offset(position.subtract(owner)), entityData.getCompound(HeadBogey.DATA)));
    }

    public static boolean isSupport(BlockGetter level, BlockPos pos) {
        if (level instanceof Level world && !world.hasChunkAt(pos)) return false;
        return level.getBlockState(pos).getBlock() instanceof HexieBogeyBlock<?> || resolve(level, pos) != null;
    }

    public static @Nullable AssemblyBogeySupport resolve(BlockGetter level, BlockPos pos) {
        if (level instanceof Level world && !world.hasChunkAt(pos)) return null;
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof HexieBogeyBlock<?>) {
            var entity = level.getBlockEntity(pos);
            var data = entity != null && entity.getLevel() != null
                    ? entity.saveWithFullMetadata(entity.getLevel().registryAccess()) : entityData(state, pos, new CompoundTag());
            return new AssemblyBogeySupport(pos, pos, state, data);
        }
        if (!headMount(state)) return null;
        var root = HeadGeometry.owner(level, pos);
        if (root == null || !(level.getBlockEntity(root) instanceof CRH2HeadEntity head) || !head.bogeyEnabled()) return null;
        var facing = level.getBlockState(root).getValue(CRH2HeadBlock.FACING);
        if (state.getValue(CRH2HeadBlock.FACING) != facing || !HeadBogey.mount(root, facing).equals(pos)) return null;
        var effective = HeadBogey.state(facing);
        if (head.getLevel() == null) return null;
        return new AssemblyBogeySupport(root, pos, effective, head.saveWithFullMetadata(head.getLevel().registryAccess()));
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
        if (block.state().getBlock() instanceof HexieBogeyBlock<?> && block.entityData() != null)
            return new AssemblyBogeySupport(pos, pos, block.state(), block.entityData());
        if (!headMount(block.state())) return null;
        var facing = block.state().getValue(CRH2HeadBlock.FACING);
        var root = pos.subtract(HeadGeometry.offset(HeadGeometry.BOGEY_PART, facing));
        var owner = blocks.apply(root);
        if (owner == null || !(owner.state().getBlock() instanceof CRH2HeadBlock)
                || owner.state().getValue(CRH2HeadBlock.FACING) != facing || owner.entityData() == null) return null;
        var data = owner.entityData();
        if (!data.getBoolean(HeadBogey.ENABLED)) return null;
        var effective = HeadBogey.state(facing);
        return new AssemblyBogeySupport(root, pos, effective, data);
    }

    private static boolean headMount(BlockState state) {
        return state.getBlock() instanceof HeadFloorBlock && state.getValue(HeadFloorBlock.PART) == HeadGeometry.BOGEY_PART;
    }

    private static CompoundTag entityData(BlockState state, BlockPos pos, CompoundTag bogeyData) {
        var data = new CompoundTag();
        var type = ((HexieBogeyBlock<?>) state.getBlock()).getBlockEntityType();
        data.putString("id", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type).toString());
        data.putInt("x", pos.getX()); data.putInt("y", pos.getY()); data.putInt("z", pos.getZ());
        data.put(HeadBogey.DATA, bogeyData.copy());
        return data;
    }
}
