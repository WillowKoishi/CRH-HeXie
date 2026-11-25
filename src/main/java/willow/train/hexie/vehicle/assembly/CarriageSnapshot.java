package willow.train.hexie.vehicle.assembly;

// split from willow/train/hexie/vehicle/assembly/CarriageSnapshot.java

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.CarriageSnapshotView;
import willow.train.hexie.bridge.vehicle.VehicleBridge;
import willow.train.hexie.bridge.world.CarriageBlockBridge;
import willow.train.hexie.bridge.world.CouplerBlockBridge;

/** Immutable structure data. Capturing or decoding this record never places or removes blocks. */
public record CarriageSnapshot(UUID id, ResourceKey<Level> dimension, BlockPos origin,
                               List<Block> blocks, List<BlockPos> supports, List<AABB> glue) implements CarriageSnapshotView {
    public static final int SCHEMA_VERSION = 2;

    public CarriageSnapshot(UUID id, ResourceKey<Level> dimension, BlockPos origin,
                            List<Block> blocks, List<BlockPos> supports) {
        this(id, dimension, origin, blocks, supports, List.of());
    }

    public CarriageSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(dimension, "dimension");
        origin = Objects.requireNonNull(origin, "origin").immutable();
        // Known additive entities can be absent from snapshots captured before their introduction.
        var upgradedBlocks = new ArrayList<Block>(blocks.size());
        for (Block block : blocks) {
            if (canInitializeEntity(block.state()) && block.entityData() == null) {
                BlockPos world = absolute(origin, block.position());
                var data = new CompoundTag();
                var entity = ((net.minecraft.world.level.block.EntityBlock) block.state().getBlock()).newBlockEntity(world, block.state());
                data.putString("id", net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE
                        .getKey(Objects.requireNonNull(entity).getType()).toString());
                data.putInt("x", world.getX()); data.putInt("y", world.getY()); data.putInt("z", world.getZ());
                block = new Block(block.position(), block.state(), data);
            }
            upgradedBlocks.add(block);
        }
        blocks = HexieBridges.carriageBlock().headBogeyMigration().snapshot(List.copyOf(upgradedBlocks)).stream()
                .map(Block::fromView).toList();
        supports = supports.stream().map(BlockPos::immutable).toList();
        glue = List.copyOf(glue);
        for (AABB box : glue)
            if (!Double.isFinite(box.minX) || !Double.isFinite(box.minY) || !Double.isFinite(box.minZ)
                    || !Double.isFinite(box.maxX) || !Double.isFinite(box.maxY) || !Double.isFinite(box.maxZ)
                    || box.getXsize() <= 0 || box.getYsize() <= 0 || box.getZsize() <= 0)
                throw new IllegalArgumentException("Invalid local glue bounds");
        if (blocks.isEmpty() || supports.isEmpty())
            throw new IllegalArgumentException("A carriage needs blocks and supports");
        var positions = new HashSet<BlockPos>();
        for (Block block : blocks) {
            if (!positions.add(block.position()))
                throw new IllegalArgumentException("Duplicate block position: " + block.position());
            BlockPos world = absolute(origin, block.position());
            if (block.entityData() != null) {
                if (block.entityData().getInt("x") != world.getX()
                        || block.entityData().getInt("y") != world.getY()
                        || block.entityData().getInt("z") != world.getZ())
                    throw new IllegalArgumentException("Block entity coordinates do not match its block");
            }
        }
        if (!positions.contains(BlockPos.ZERO))
            throw new IllegalArgumentException("Carriage origin must be a captured block");
        var uniqueSupports = new HashSet<BlockPos>();
        for (BlockPos support : supports)
            if (!positions.contains(support) || !uniqueSupports.add(support))
                throw new IllegalArgumentException("Missing or duplicate support: " + support);
    }

    /** Block entity NBT retains complete original-world metadata, including id and coordinates. */
    public record Block(BlockPos position, BlockState state, CompoundTag entityData) implements VehicleBridge.CarriageSnapshotBlockView {
        public static Block fromView(VehicleBridge.CarriageSnapshotBlockView block) {
            return block instanceof Block captured ? captured : new Block(block.position(), block.state(), block.entityData());
        }

        public Block {
            position = Objects.requireNonNull(position, "position").immutable();
            Objects.requireNonNull(state, "state");
            // EntityBlock factories may omit entities for individual states, such as upper door halves.
            // Capture records actual world entities; validate supplied data without requiring it to exist.
            if (entityData != null && !state.hasBlockEntity())
                throw new IllegalArgumentException("Block entity data does not match block state");
            if (entityData != null) {
                require(entityData, "id", Tag.TAG_STRING);
                require(entityData, "x", Tag.TAG_INT);
                require(entityData, "y", Tag.TAG_INT);
                require(entityData, "z", Tag.TAG_INT);
                identifier(entityData.getString("id"));
                entityData = entityData.copy();
            }
        }

        @Override
        public CompoundTag entityData() { return entityData == null ? null : entityData.copy(); }
    }

    /** Registry checks without constructing a serialized structure or copying inventory NBT. */
    public void validate(RegistryAccess registries) {
        for (Block block : blocks) validateBlock(block, registries);
    }

    public CompoundTag save(RegistryAccess registries) {
        return CarriageSnapshotNbt.save(this, registries);
    }

    /** Strict decoding: unknown blocks/properties must not silently turn into air or defaults. */
    public static CarriageSnapshot load(CompoundTag root, RegistryAccess registries) {
        return CarriageSnapshotNbt.load(root, registries);
    }

    public static BlockState readState(CompoundTag tag, RegistryAccess registries) {
        return CarriageSnapshotNbt.readState(tag, registries);
    }

    private static boolean canInitializeEntity(BlockState state) {
        return state.getBlock() instanceof CouplerBlockBridge.AbstractCouplerBlockView
                || state.getBlock() instanceof CarriageBlockBridge.CRH2HeadBlockView
                || HexieBridges.signBlock().passengerDisplayPanel().supports(state.getBlock());
    }

    public static void validateEntityType(BlockState state, CompoundTag tag, RegistryAccess registries) {
        require(tag, "id", Tag.TAG_STRING);
        var name = identifier(tag.getString("id"));
        var registry = registries.registryOrThrow(Registries.BLOCK_ENTITY_TYPE);
        if (!registry.containsKey(name) || !registry.get(name).isValid(state))
            throw new IllegalArgumentException("Invalid block entity type for block: " + name);
    }

    static ResourceLocation validateBlock(Block block, RegistryAccess registries) {
        var registry = registries.registryOrThrow(Registries.BLOCK);
        var name = registry.getKey(block.state().getBlock());
        if (name == null || registry.get(name) != block.state().getBlock())
            throw new IllegalArgumentException("Unregistered captured block");
        if (block.entityData() != null) validateEntityType(block.state(), block.entityData(), registries);
        return name;
    }

    static ResourceLocation identifier(String text) {
        var value = ResourceLocation.tryParse(text);
        if (value == null || !text.contains(":")) throw new IllegalArgumentException("Invalid identifier: " + text);
        return value;
    }

    static void require(CompoundTag tag, String key, int type) {
        if (!tag.contains(key, type)) throw new IllegalArgumentException("Missing or invalid field: " + key);
    }

    private static BlockPos absolute(BlockPos origin, BlockPos local) {
        try {
            return new BlockPos(Math.addExact(origin.getX(), local.getX()),
                    Math.addExact(origin.getY(), local.getY()), Math.addExact(origin.getZ(), local.getZ()));
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Carriage position overflow", e);
        }
    }
}
