package willow.train.hexie.vehicle.assembly;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import willow.train.hexie.blocks.coupler.TestCouplerBlock;

/** Immutable structure data. Capturing or decoding this record never places or removes blocks. */
public record CarriageSnapshot(UUID id, ResourceKey<Level> dimension, BlockPos origin,
                               List<Block> blocks, List<BlockPos> supports, List<AABB> glue) {
    public static final int SCHEMA_VERSION = 2;

    public CarriageSnapshot(UUID id, ResourceKey<Level> dimension, BlockPos origin,
                            List<Block> blocks, List<BlockPos> supports) {
        this(id, dimension, origin, blocks, supports, List.of());
    }

    public CarriageSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(dimension, "dimension");
        origin = Objects.requireNonNull(origin, "origin").immutable();
        // The original test coupler had no block entity. Upgrade those captured blocks
        // at their known world coordinates before enforcing the normal BE invariants.
        var upgradedBlocks = new ArrayList<Block>(blocks.size());
        for (Block block : blocks) {
            if (block.state().getBlock() instanceof TestCouplerBlock && block.entityData == null) {
                BlockPos world = absolute(origin, block.position());
                var data = new CompoundTag();
                data.putString("id", "hexie:coupler");
                data.putInt("x", world.getX()); data.putInt("y", world.getY()); data.putInt("z", world.getZ());
                block = new Block(block.position(), block.state(), data);
            }
            upgradedBlocks.add(block);
        }
        blocks = List.copyOf(upgradedBlocks);
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
            if (block.entityData != null) {
                if (block.entityData.getInt("x") != world.getX()
                        || block.entityData.getInt("y") != world.getY()
                        || block.entityData.getInt("z") != world.getZ())
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
    public record Block(BlockPos position, BlockState state, CompoundTag entityData) {
        public Block {
            position = Objects.requireNonNull(position, "position").immutable();
            Objects.requireNonNull(state, "state");
            if (state.isAir()) throw new IllegalArgumentException("Air is not a captured block");
            if (state.hasBlockEntity() != (entityData != null)
                    && !(state.getBlock() instanceof TestCouplerBlock && entityData == null))
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
        var root = new CompoundTag();
        root.putInt("Schema", SCHEMA_VERSION);
        root.putUUID("Id", id);
        root.putString("Dimension", dimension.location().toString());
        root.putIntArray("Origin", coordinates(origin));
        var blockList = new ListTag();
        for (Block block : blocks) {
            var tag = new CompoundTag();
            tag.putIntArray("Pos", coordinates(block.position()));
            var state = new CompoundTag();
            var name = validateBlock(block, registries);
            state.putString("Name", name.toString());
            var properties = new CompoundTag();
            block.state().getValues().forEach((property, value) ->
                    properties.putString(property.getName(), propertyName(property, value)));
            if (!properties.isEmpty()) state.put("Properties", properties);
            tag.put("State", state);
            if (block.entityData != null) {
                tag.put("BlockEntity", block.entityData.copy());
            }
            blockList.add(tag);
        }
        root.put("Blocks", blockList);
        var supportList = new ListTag();
        for (BlockPos support : supports) {
            var tag = new CompoundTag();
            tag.putIntArray("Pos", coordinates(support));
            supportList.add(tag);
        }
        root.put("Supports", supportList);
        var glueList = new ListTag();
        for (AABB box : glue) {
            var tag = new CompoundTag();
            tag.putDouble("MinX", box.minX); tag.putDouble("MinY", box.minY); tag.putDouble("MinZ", box.minZ);
            tag.putDouble("MaxX", box.maxX); tag.putDouble("MaxY", box.maxY); tag.putDouble("MaxZ", box.maxZ);
            glueList.add(tag);
        }
        root.put("Glue", glueList);
        return root;
    }

    /** Strict decoding: unknown blocks/properties must not silently turn into air or defaults. */
    public static CarriageSnapshot load(CompoundTag root, RegistryAccess registries) {
        require(root, "Schema", Tag.TAG_INT);
        if (root.getInt("Schema") < 1 || root.getInt("Schema") > SCHEMA_VERSION)
            throw new IllegalArgumentException("Unsupported carriage snapshot schema: " + root.getInt("Schema"));
        if (!root.hasUUID("Id")) throw new IllegalArgumentException("Missing or invalid carriage UUID");
        require(root, "Dimension", Tag.TAG_STRING);
        var dimension = ResourceKey.create(Registries.DIMENSION, identifier(root.getString("Dimension")));
        var blocks = new ArrayList<Block>();
        for (Tag item : list(root, "Blocks")) {
            var tag = (CompoundTag) item;
            require(tag, "State", Tag.TAG_COMPOUND);
            BlockState state = readState(tag.getCompound("State"), registries);
            CompoundTag entity = null;
            if (tag.contains("BlockEntity")) {
                require(tag, "BlockEntity", Tag.TAG_COMPOUND);
                entity = tag.getCompound("BlockEntity");
                validateEntityType(state, entity, registries);
            }
            blocks.add(new Block(position(tag, "Pos"), state, entity));
        }
        var supports = new ArrayList<BlockPos>();
        for (Tag item : list(root, "Supports")) supports.add(position((CompoundTag) item, "Pos"));
        var glue = new ArrayList<AABB>();
        if (root.getInt("Schema") >= 2) for (Tag item : list(root, "Glue")) {
            var tag = (CompoundTag) item;
            for (String key : List.of("MinX", "MinY", "MinZ", "MaxX", "MaxY", "MaxZ")) require(tag, key, Tag.TAG_DOUBLE);
            glue.add(new AABB(tag.getDouble("MinX"), tag.getDouble("MinY"), tag.getDouble("MinZ"),
                    tag.getDouble("MaxX"), tag.getDouble("MaxY"), tag.getDouble("MaxZ")));
        }
        return new CarriageSnapshot(root.getUUID("Id"), dimension, position(root, "Origin"), blocks, supports, glue);
    }

    private static BlockState readState(CompoundTag tag, RegistryAccess registries) {
        require(tag, "Name", Tag.TAG_STRING);
        var name = identifier(tag.getString("Name"));
        var registry = registries.registryOrThrow(Registries.BLOCK);
        if (!registry.containsKey(name)) throw new IllegalArgumentException("Unknown block: " + name);
        BlockState state = registry.get(name).defaultBlockState();
        if (!state.getProperties().isEmpty() && !tag.contains("Properties", Tag.TAG_COMPOUND))
            throw new IllegalArgumentException("Missing block state properties: " + name);
        if (tag.contains("Properties")) {
            require(tag, "Properties", Tag.TAG_COMPOUND);
            var properties = tag.getCompound("Properties");
            if (properties.size() != state.getProperties().size())
                throw new IllegalArgumentException("Incomplete block state properties: " + name);
            for (String key : properties.getAllKeys()) {
                require(properties, key, Tag.TAG_STRING);
                Property<?> property = state.getBlock().getStateDefinition().getProperty(key);
                if (property == null) throw new IllegalArgumentException("Unknown block property: " + key);
                state = withProperty(state, property, properties.getString(key));
            }
        }
        return state;
    }

    private static void validateEntityType(BlockState state, CompoundTag tag, RegistryAccess registries) {
        require(tag, "id", Tag.TAG_STRING);
        var name = identifier(tag.getString("id"));
        var registry = registries.registryOrThrow(Registries.BLOCK_ENTITY_TYPE);
        if (!registry.containsKey(name) || !registry.get(name).isValid(state))
            throw new IllegalArgumentException("Invalid block entity type for block: " + name);
    }

    private static ResourceLocation validateBlock(Block block, RegistryAccess registries) {
        var registry = registries.registryOrThrow(Registries.BLOCK);
        var name = registry.getKey(block.state().getBlock());
        if (name == null || registry.get(name) != block.state().getBlock())
            throw new IllegalArgumentException("Unregistered captured block");
        if (block.entityData != null) validateEntityType(block.state(), block.entityData, registries);
        return name;
    }

    private static <T extends Comparable<T>> BlockState withProperty(BlockState state, Property<T> property, String value) {
        return state.setValue(property, property.getValue(value)
                .orElseThrow(() -> new IllegalArgumentException("Invalid property value: " + property.getName() + "=" + value)));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> String propertyName(Property<T> property, Comparable<?> value) {
        return property.getName((T) value);
    }

    private static ListTag list(CompoundTag root, String key) {
        require(root, key, Tag.TAG_LIST);
        ListTag value = (ListTag) root.get(key);
        if (!value.isEmpty() && value.getElementType() != Tag.TAG_COMPOUND)
            throw new IllegalArgumentException("Invalid list entries: " + key);
        return value;
    }

    private static BlockPos position(CompoundTag tag, String key) {
        require(tag, key, Tag.TAG_INT_ARRAY);
        int[] xyz = tag.getIntArray(key);
        if (xyz.length != 3) throw new IllegalArgumentException("Invalid position: " + key);
        return new BlockPos(xyz[0], xyz[1], xyz[2]);
    }

    private static int[] coordinates(BlockPos pos) { return new int[] {pos.getX(), pos.getY(), pos.getZ()}; }

    private static ResourceLocation identifier(String text) {
        var value = ResourceLocation.tryParse(text);
        if (value == null || !text.contains(":")) throw new IllegalArgumentException("Invalid identifier: " + text);
        return value;
    }

    private static void require(CompoundTag tag, String key, int type) {
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
