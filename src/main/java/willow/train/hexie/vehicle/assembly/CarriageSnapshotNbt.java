package willow.train.hexie.vehicle.assembly;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.CarriageBlockBridge;
import willow.train.hexie.bridge.world.SignBlockBridge;
import willow.train.hexie.bridge.world.WorldBlockBridge;
import willow.train.hexie.vehicle.assembly.CarriageSnapshot.Block;

// split from willow/train/hexie/vehicle/assembly/CarriageSnapshot.java
final class CarriageSnapshotNbt {
    static CompoundTag save(CarriageSnapshot snapshot, RegistryAccess registries) {
        var root = new CompoundTag();
        root.putInt("Schema", CarriageSnapshot.SCHEMA_VERSION);
        root.putUUID("Id", snapshot.id());
        root.putString("Dimension", snapshot.dimension().location().toString());
        root.putIntArray("Origin", coordinates(snapshot.origin()));
        var blockList = new ListTag();
        for (Block block : snapshot.blocks()) {
            var tag = new CompoundTag();
            tag.putIntArray("Pos", coordinates(block.position()));
            var state = new CompoundTag();
            var name = CarriageSnapshot.validateBlock(block, registries);
            state.putString("Name", name.toString());
            var properties = new CompoundTag();
            block.state().getValues().forEach((property, value) ->
                    properties.putString(property.getName(), propertyName(property, value)));
            if (!properties.isEmpty()) state.put("Properties", properties);
            tag.put("State", state);
            if (block.entityData() != null) {
                tag.put("BlockEntity", block.entityData().copy());
            }
            blockList.add(tag);
        }
        root.put("Blocks", blockList);
        var supportList = new ListTag();
        for (BlockPos support : snapshot.supports()) {
            var tag = new CompoundTag();
            tag.putIntArray("Pos", coordinates(support));
            supportList.add(tag);
        }
        root.put("Supports", supportList);
        var glueList = new ListTag();
        for (AABB box : snapshot.glue()) {
            var tag = new CompoundTag();
            tag.putDouble("MinX", box.minX); tag.putDouble("MinY", box.minY); tag.putDouble("MinZ", box.minZ);
            tag.putDouble("MaxX", box.maxX); tag.putDouble("MaxY", box.maxY); tag.putDouble("MaxZ", box.maxZ);
            glueList.add(tag);
        }
        root.put("Glue", glueList);
        return root;
    }

    /** Strict decoding: unknown blocks/properties must not silently turn into air or defaults. */
    static CarriageSnapshot load(CompoundTag root, RegistryAccess registries) {
        CarriageSnapshot.require(root, "Schema", Tag.TAG_INT);
        if (root.getInt("Schema") < 1 || root.getInt("Schema") > CarriageSnapshot.SCHEMA_VERSION)
            throw new IllegalArgumentException("Unsupported carriage snapshot schema: " + root.getInt("Schema"));
        if (!root.hasUUID("Id")) throw new IllegalArgumentException("Missing or invalid carriage UUID");
        CarriageSnapshot.require(root, "Dimension", Tag.TAG_STRING);
        var dimension = ResourceKey.create(Registries.DIMENSION, CarriageSnapshot.identifier(root.getString("Dimension")));
        var blocks = new ArrayList<Block>();
        for (Tag item : list(root, "Blocks")) {
            var tag = (CompoundTag) item;
            CarriageSnapshot.require(tag, "State", Tag.TAG_COMPOUND);
            BlockState state = readState(tag.getCompound("State"), registries);
            CompoundTag entity = null;
            if (tag.contains("BlockEntity")) {
                CarriageSnapshot.require(tag, "BlockEntity", Tag.TAG_COMPOUND);
                entity = tag.getCompound("BlockEntity");
                CarriageSnapshot.validateEntityType(state, entity, registries);
            }
            blocks.add(new Block(position(tag, "Pos"), state, entity));
        }
        var supports = new ArrayList<BlockPos>();
        for (Tag item : list(root, "Supports")) supports.add(position((CompoundTag) item, "Pos"));
        var glue = new ArrayList<AABB>();
        if (root.getInt("Schema") >= 2) for (Tag item : list(root, "Glue")) {
            var tag = (CompoundTag) item;
            for (String key : List.of("MinX", "MinY", "MinZ", "MaxX", "MaxY", "MaxZ")) CarriageSnapshot.require(tag, key, Tag.TAG_DOUBLE);
            glue.add(new AABB(tag.getDouble("MinX"), tag.getDouble("MinY"), tag.getDouble("MinZ"),
                    tag.getDouble("MaxX"), tag.getDouble("MaxY"), tag.getDouble("MaxZ")));
        }
        return new CarriageSnapshot(root.getUUID("Id"), dimension, position(root, "Origin"), blocks, supports, glue);
    }

    static BlockState readState(CompoundTag tag, RegistryAccess registries) {
        CarriageSnapshot.require(tag, "Name", Tag.TAG_STRING);
        var name = CarriageSnapshot.identifier(tag.getString("Name"));
        var registry = registries.registryOrThrow(Registries.BLOCK);
        if (!registry.containsKey(name)) throw new IllegalArgumentException("Unknown block: " + name);
        BlockState state = registry.get(name).defaultBlockState();
        if (!state.getProperties().isEmpty() && !tag.contains("Properties", Tag.TAG_COMPOUND))
            throw new IllegalArgumentException("Missing block state properties: " + name);
        if (tag.contains("Properties")) {
            CarriageSnapshot.require(tag, "Properties", Tag.TAG_COMPOUND);
            var properties = tag.getCompound("Properties");
            // This additive flag did not exist before integral heads. Its absence means
            // an ordinary bogey; all pre-existing style and geometry fields stay strict.
            if (state.getBlock() instanceof WorldBlockBridge.HexieBogeyBlockView
                    && !properties.contains("head_mounted")) {
                properties = properties.copy();
                properties.putString("head_mounted", "false");
            }
            if(state.hasProperty(HexieBridges.partBlock().legacyPartLayout().occupied())&&!properties.contains("occupied")) {
                properties=properties.copy();
                // Old snapshots remain single-root layouts. Their cells are not invented during decoding.
                properties.putString("occupied","false");
            }
            // Pre-display panels had no attachments. Only this known additive field defaults;
            // existing facing/hinge fields and explicit display values remain strictly validated.
            if (state.getBlock() instanceof SignBlockBridge.PassengerDisplayPanelView && state.hasProperty(HexieBridges.signBlock().panelDisplayMount().property())
                    && !properties.contains(HexieBridges.signBlock().panelDisplayMount().property().getName())) {
                properties = properties.copy();
                properties.putString(HexieBridges.signBlock().panelDisplayMount().property().getName(), SignBlockBridge.PanelDisplayMount.NONE.getSerializedName());
            }
            // Older display mounts did not record the clicked column. Keep them on the root cell.
            if (state.getBlock() instanceof SignBlockBridge.PassengerDisplayPanelView && state.hasProperty(HexieBridges.signBlock().panelDisplayColumn().property())
                    && !properties.contains(HexieBridges.signBlock().panelDisplayColumn().property().getName())) {
                properties = properties.copy();
                properties.putString(HexieBridges.signBlock().panelDisplayColumn().property().getName(), SignBlockBridge.PanelDisplayColumn.ROOT.getSerializedName());
            }
            // Before connector variants existed, this block used the original bare end wall.
            if (state.getBlock() instanceof CarriageBlockBridge.CRH2EndFaceBlockView
                    && !properties.contains(HexieBridges.carriageBlock().crh2EndFaceBlock().variant().getName())) {
                properties = properties.copy();
                properties.putString(HexieBridges.carriageBlock().crh2EndFaceBlock().variant().getName(),
                        CarriageBlockBridge.CRH2EndFaceBlockType.ORIGINAL.getSerializedName());
            }
            if (state.getBlock() instanceof CarriageBlockBridge.SS7DCenterCarportBlockView)
                properties = HexieBridges.carriageBlock().ss7DCenterCarportBlock().upgradeSnapshotProperties(properties);
            if (state.getBlock() instanceof CarriageBlockBridge.SS7DFloorBlockView)
                properties = HexieBridges.carriageBlock().ss7DFloorBlock().upgradeSnapshotProperties(properties);
            if (properties.size() != state.getProperties().size())
                throw new IllegalArgumentException("Incomplete block state properties: " + name);
            for (String key : properties.getAllKeys()) {
                CarriageSnapshot.require(properties, key, Tag.TAG_STRING);
                Property<?> property = state.getBlock().getStateDefinition().getProperty(key);
                if (property == null) throw new IllegalArgumentException("Unknown block property: " + key);
                state = withProperty(state, property, properties.getString(key));
            }
        }
        return state;
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
        CarriageSnapshot.require(root, key, Tag.TAG_LIST);
        ListTag value = (ListTag) root.get(key);
        if (!value.isEmpty() && value.getElementType() != Tag.TAG_COMPOUND)
            throw new IllegalArgumentException("Invalid list entries: " + key);
        return value;
    }

    private static BlockPos position(CompoundTag tag, String key) {
        CarriageSnapshot.require(tag, key, Tag.TAG_INT_ARRAY);
        int[] xyz = tag.getIntArray(key);
        if (xyz.length != 3) throw new IllegalArgumentException("Invalid position: " + key);
        return new BlockPos(xyz[0], xyz[1], xyz[2]);
    }

    private static int[] coordinates(BlockPos pos) { return new int[] {pos.getX(), pos.getY(), pos.getZ()}; }
}
