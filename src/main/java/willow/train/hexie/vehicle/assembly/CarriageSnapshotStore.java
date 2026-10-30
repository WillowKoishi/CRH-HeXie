package willow.train.hexie.vehicle.assembly;

import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import willow.train.hexie.blocks.track.standard.StandardTrackBlock;
import org.slf4j.Logger;

/**
 * Per-dimension snapshots and lightweight active assembly ownership. Block/BE installation failures
 * are rolled back on the server thread. A compatibility installer owns its placement lifecycle,
 * including neighbour notifications; standalone restoration notifies after the world commit.
 * Persistence is ordinary SavedData/world saving, not crash-atomic across chunk and entity files.
 */
public final class CarriageSnapshotStore extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String DATA_NAME = "hexie_carriage_snapshots";
    public static final int SCHEMA_VERSION = 2;
    private static final int MOVE_FLAGS = Block.UPDATE_MOVE_BY_PISTON | Block.UPDATE_SUPPRESS_DROPS
            | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE;

    private final ResourceKey<Level> dimension;
    private final RegistryAccess registries;
    private final Thread serverThread;
    private final LinkedHashMap<UUID, CarriageSnapshot> snapshots = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, ActiveAssembly> active = new LinkedHashMap<>();

    /** Carriage UUIDs reference the one snapshot map; no duplicate block/NBT data is held here. */
    public record ActiveAssembly(UUID id, UUID owner, BlockPos station, Direction forward, List<UUID> carriages) {
        public ActiveAssembly {
            Objects.requireNonNull(id); Objects.requireNonNull(owner); Objects.requireNonNull(forward);
            station = Objects.requireNonNull(station).immutable();
            carriages = List.copyOf(carriages);
            if (forward.getAxis() == Direction.Axis.Y || carriages.isEmpty()
                    || new HashSet<>(carriages).size() != carriages.size())
                throw new IllegalArgumentException("Invalid active assembly");
        }
    }

    private CarriageSnapshotStore(ServerLevel level) {
        requireServerThread(level);
        dimension = level.dimension();
        registries = level.registryAccess();
        serverThread = Thread.currentThread();
    }

    public static CarriageSnapshotStore get(ServerLevel level) {
        requireServerThread(level);
        Path directory = DimensionType.getStorageFolder(level.dimension(),
                level.getServer().getWorldPath(LevelResource.ROOT)).resolve("data");
        return open(level, level.getDataStorage(), directory);
    }

    /* Shared by the real level accessor and isolated on-disk integration tests. */
    static CarriageSnapshotStore open(ServerLevel level, DimensionDataStorage storage, Path directory) {
        requireServerThread(level);
        var factory = factory(level);
        var existing = storage.get(factory, DATA_NAME);
        if (existing != null) return existing;
        Path file = directory.resolve(DATA_NAME + ".dat");
        // DimensionDataStorage catches deserialization exceptions and returns null. Do not let a
        // failed read silently become an empty store that can overwrite the original file.
        if (!Files.notExists(file)) throw new IllegalStateException("Could not load carriage snapshots: " + file);
        var created = factory.constructor().get();
        storage.set(DATA_NAME, created);
        return created;
    }

    static Factory<CarriageSnapshotStore> factory(ServerLevel level) {
        requireServerThread(level);
        return new Factory<>(() -> new CarriageSnapshotStore(level),
                (tag, provider) -> load(level, tag));
    }

    public Optional<CarriageSnapshot> find(UUID id) {
        requireServerThread();
        return Optional.ofNullable(snapshots.get(Objects.requireNonNull(id, "id")));
    }

    /** A stable immutable copy, not a live map or collection view. */
    public List<CarriageSnapshot> snapshots() {
        requireServerThread();
        return List.copyOf(snapshots.values());
    }

    public List<ActiveAssembly> active() {
        requireServerThread();
        return List.copyOf(active.values());
    }

    public Optional<ActiveAssembly> active(UUID id) {
        requireServerThread();
        return Optional.ofNullable(active.get(Objects.requireNonNull(id)));
    }

    public Optional<ActiveAssembly> activeAt(BlockPos station) {
        requireServerThread();
        return active.values().stream().filter(entry -> entry.station().equals(station)).findFirst();
    }

    public ActiveAssembly assemble(ServerLevel level, UUID owner, BlockPos station,
                                   VehicleAssemblyPreparer.Prepared prepared) {
        return assemble(level, owner, station, prepared.forward(),
                prepared.carriages().stream().map(VehicleAssemblyPreparer.Carriage::structure).toList());
    }

    /** Preflight everything, retain snapshots, then remove the source blocks in this same tick. */
    public ActiveAssembly assemble(ServerLevel level, UUID owner, BlockPos station, Direction forward,
                                   List<CarriageSnapshot> additions) {
        return assemble(level, UUID.randomUUID(), owner, station, forward, additions);
    }

    /** The caller may allocate ownership before mounting storage from still-present world entities. */
    public ActiveAssembly assemble(ServerLevel level, UUID assemblyId, UUID owner, BlockPos station,
                                   Direction forward, List<CarriageSnapshot> additions) {
        requireLevel(level);
        if (active.containsKey(Objects.requireNonNull(assemblyId)))
            throw new IllegalArgumentException("Assembly identity already exists");
        additions = List.copyOf(additions);
        var entry = new ActiveAssembly(assemblyId, owner, station, forward,
                additions.stream().map(CarriageSnapshot::id).toList());
        List<PlacedBlock> blocks = blocks(additions);
        for (PlacedBlock block : blocks) {
            requireLoaded(level, block.position);
            if (!level.getBlockState(block.position).equals(block.block.state()))
                throw new IllegalStateException("Assembly source changed: " + block.position);
            var entity = level.getBlockEntity(block.position);
            var expected = block.block.entityData();
            if (expected == null ? entity != null : entity == null
                    || !expected.equals(entity.saveWithFullMetadata(registries)))
                throw new IllegalStateException("Assembly block entity changed: " + block.position);
        }
        boolean wasDirty = isDirty();
        addAll(additions);
        active.put(entry.id(), entry);
        var changed = new ArrayList<PlacedBlock>();
        try {
            for (PlacedBlock block : blocks) {
                changed.add(block);
                replace(level, block.position, vacantState(block.block.state()));
            }
        } catch (RuntimeException failure) {
            try {
                restore(level, changed);
                active.remove(entry.id());
                entry.carriages().forEach(snapshots::remove);
                setDirty(wasDirty);
                notifyNeighbours(level, changed, block -> vacantState(block.block.state()));
            } catch (RuntimeException rollbackFailure) {
                // Retain the only complete snapshots if a third-party block also rejects rollback.
                failure.addSuppressed(rollbackFailure);
                setDirty();
            }
            throw failure;
        }
        setDirty();
        notifyNeighbours(level, blocks, block -> block.block.state());
        return entry;
    }

    /** Restores at the captured positions only; a conflicting or unloaded target changes nothing. */
    public void disassemble(ServerLevel level, UUID assemblyId) {
        disassemble(level, assemblyId, () -> { });
    }

    /** Only block states and block-entity data can change while the captured body is assembled. */
    public void updateSnapshots(UUID assemblyId, List<CarriageSnapshot> replacements) {
        requireServerThread();
        var entry = active(assemblyId).orElseThrow(() -> new IllegalArgumentException("Unknown active assembly"));
        var pending = new LinkedHashMap<UUID, CarriageSnapshot>();
        for (var replacement : List.copyOf(replacements)) {
            var original = snapshots.get(replacement.id());
            if (!entry.carriages().contains(replacement.id()) || original == null
                    || !original.dimension().equals(replacement.dimension()) || !original.origin().equals(replacement.origin())
                    || !original.supports().equals(replacement.supports()) || !original.glue().equals(replacement.glue())
                    || original.blocks().size() != replacement.blocks().size()
                    || pending.putIfAbsent(replacement.id(), replacement) != null)
                throw new IllegalArgumentException("Changed assembled structure identity or shape");
            var states = new java.util.HashMap<BlockPos, BlockState>();
            for (var block : original.blocks()) states.put(block.position(), block.state());
            for (var block : replacement.blocks()) {
                var old = states.remove(block.position());
                if (old == null || old.getBlock() != block.state().getBlock())
                    throw new IllegalArgumentException("Cannot replace/remove assembled blocks");
            }
            replacement.validate(registries);
        }
        if (pending.size() != entry.carriages().size())
            throw new IllegalArgumentException("All carriage updates must be supplied together");
        snapshots.putAll(pending);
        setDirty();
    }

    /** Action-time check before stopping behaviours; does not load chunks or mutate the world. */
    public void checkDisassembly(ServerLevel level, UUID assemblyId) {
        requireLevel(level);
        var entry = active(assemblyId).orElseThrow(() -> new IllegalArgumentException("Unknown active assembly"));
        for (var block : blocks(entry.carriages().stream().map(snapshots::get).toList())) checkTarget(level, block);
    }

    /** Preflight a transformed subset without publishing its destination as the saved source. */
    public void checkDisassembly(ServerLevel level, UUID assemblyId, List<CarriageSnapshot> targetSnapshots) {
        checkDisassembly(level, Map.of(assemblyId, targetSnapshots));
    }

    /** Check the complete consist, including overlaps between different source assemblies. */
    public void checkDisassembly(ServerLevel level, Map<UUID, List<CarriageSnapshot>> targetSnapshots) {
        requireLevel(level);
        var targets = disassemblyTargets(targetSnapshots).values().stream().flatMap(List::stream).toList();
        for (var block : blocks(targets)) checkTarget(level, block);
    }

    /** Restore mounted storage after all blocks/BE exist, while rollback still owns the snapshots. */
    public void disassemble(ServerLevel level, UUID assemblyId, Runnable afterBlocksRestored) {
        requireLevel(level);
        ActiveAssembly entry = active(assemblyId).orElseThrow(() -> new IllegalArgumentException("Unknown active assembly"));
        disassemble(level, entry, entry.carriages().stream().map(snapshots::get).toList(), null, afterBlocksRestored);
    }

    /**
     * Restore only the supplied carriage identities, possibly at a new position/orientation.
     * Original snapshots and active membership remain authoritative until all blocks, entities
     * and the installation callback succeed. Successful targets release their saved snapshots.
     */
    public void disassemble(ServerLevel level, UUID assemblyId, List<CarriageSnapshot> targetSnapshots,
                            Runnable afterInstall) {
        requireLevel(level);
        var entry = active(assemblyId).orElseThrow(() -> new IllegalArgumentException("Unknown active assembly"));
        disassemble(level, entry, disassemblyTargets(entry, targetSnapshots), null, afterInstall);
    }

    /**
     * The supplied backend performs the entire placement lifecycle using its live block data.
     * Targets describe occupied cells for preflight/rollback only; this store must not install
     * them first, reload their block entities or repeat the backend's neighbour notifications.
     */
    public void disassembleUsing(ServerLevel level, UUID assemblyId, List<CarriageSnapshot> targets,
                                 Runnable installer) {
        disassembleUsing(level, Map.of(assemblyId, targets), installer);
    }

    /** All source memberships commit together after one native installation/rollback boundary. */
    public void disassembleUsing(ServerLevel level, Map<UUID, List<CarriageSnapshot>> targets,
                                 Runnable installer) {
        requireLevel(level);
        disassemble(level, disassemblyTargets(targets), Objects.requireNonNull(installer), () -> { });
    }

    private Map<ActiveAssembly, List<CarriageSnapshot>> disassemblyTargets(
            Map<UUID, List<CarriageSnapshot>> targetsByAssembly) {
        if (targetsByAssembly.isEmpty()) throw new IllegalArgumentException("Disassembly needs an assembly");
        var groups = new LinkedHashMap<ActiveAssembly, List<CarriageSnapshot>>();
        var identities = new HashSet<UUID>();
        for (var group : targetsByAssembly.entrySet()) {
            var entry = active(group.getKey()).orElseThrow(() -> new IllegalArgumentException("Unknown active assembly"));
            var targets = disassemblyTargets(entry, group.getValue());
            for (var target : targets)
                if (!identities.add(target.id())) throw new IllegalArgumentException("Duplicate disassembly carriage");
            groups.put(entry, targets);
        }
        return groups;
    }

    private List<CarriageSnapshot> disassemblyTargets(ActiveAssembly entry, List<CarriageSnapshot> targets) {
        targets = List.copyOf(targets);
        if (targets.isEmpty()) throw new IllegalArgumentException("Disassembly needs a carriage");
        var identities = new HashSet<UUID>();
        for (var target : targets) {
            var original = snapshots.get(target.id());
            if (!entry.carriages().contains(target.id()) || original == null
                    || !identities.add(target.id()) || !dimension.equals(target.dimension()))
                throw new IllegalArgumentException("Disassembly target is not a unique member of this assembly");
            if (target.blocks().size() != original.blocks().size() || target.supports().size() != original.supports().size())
                throw new IllegalArgumentException("Disassembly transform cannot add or remove captured blocks/supports");
            var types = new java.util.HashMap<Block, Integer>();
            for (var block : original.blocks()) types.merge(block.state().getBlock(), 1, Integer::sum);
            for (var block : target.blocks()) {
                Integer count = types.get(block.state().getBlock());
                if (count == null) throw new IllegalArgumentException("Disassembly transform changed captured block types");
                if (count == 1) types.remove(block.state().getBlock());
                else types.put(block.state().getBlock(), count - 1);
            }
            target.validate(registries);
        }
        return targets;
    }

    private void disassemble(ServerLevel level, ActiveAssembly entry, List<CarriageSnapshot> targets,
                             Runnable installer, Runnable afterInstall) {
        disassemble(level, Map.of(entry, targets), installer, afterInstall);
    }

    private void disassemble(ServerLevel level, Map<ActiveAssembly, List<CarriageSnapshot>> groups,
                             Runnable installer, Runnable afterInstall) {
        Objects.requireNonNull(afterInstall);
        var targets = groups.values().stream().flatMap(List::stream).toList();
        List<PlacedBlock> blocks = blocks(targets);
        var previous = new LinkedHashMap<BlockPos, WorldBlock>();
        for (PlacedBlock block : blocks) {
            checkTarget(level, block);
            BlockState current = level.getBlockState(block.position);
            BlockEntity entity = level.getBlockEntity(block.position);
            previous.put(block.position, new WorldBlock(current,
                    entity == null ? null : entity.saveWithFullMetadata(registries)));
        }
        var changed = new ArrayList<PlacedBlock>();
        try {
            if (installer != null) {
                // The backend may throw after any cell. Back up its whole destination before entry.
                changed.addAll(blocks);
                installer.run();
            } else {
                // Install every state before loading block entities, so neighbouring parts already exist.
                for (PlacedBlock block : blocks) {
                    changed.add(block);
                    replace(level, block.position, block.block.state());
                }
                restoreEntities(level, blocks);
            }
            afterInstall.run();
        } catch (RuntimeException failure) {
            // Attempt every changed cell even if a third-party callback rejects one rollback.
            // The authoritative snapshots/membership have not been touched at this point.
            for (PlacedBlock block : changed.reversed()) {
                try { replace(level, block.position, previous.get(block.position).state); }
                catch (RuntimeException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
            }
            for (PlacedBlock block : changed) {
                var prior = previous.get(block.position);
                if (prior.entityData == null) continue;
                try {
                    var entity = BlockEntity.loadStatic(block.position, prior.state, prior.entityData, registries);
                    if (entity == null) throw new IllegalStateException("Rollback block entity is missing: " + block.position);
                    level.setBlockEntity(entity);
                    entity.setChanged();
                } catch (RuntimeException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
            }
            notifyNeighbours(level, changed, block -> block.block.state());
            throw failure;
        }
        var removed = new HashSet<UUID>();
        for (var target : targets) removed.add(target.id());
        for (var entry : groups.keySet()) {
            var remaining = entry.carriages().stream().filter(id -> !removed.contains(id)).toList();
            if (remaining.isEmpty()) active.remove(entry.id());
            else active.put(entry.id(), new ActiveAssembly(entry.id(), entry.owner(), entry.station(), entry.forward(), remaining));
        }
        for (var target : targets) snapshots.remove(target.id());
        setDirty();
        if (installer == null) notifyNeighbours(level, blocks, block -> previous.get(block.position).state);
    }

    private record WorldBlock(BlockState state, CompoundTag entityData) { }

    private static void checkTarget(ServerLevel level, PlacedBlock block) {
        requireLoaded(level, block.position);
        BlockState current = level.getBlockState(block.position);
        if ((!current.isAir() && !current.equals(vacantState(block.block.state())))
                || level.getBlockEntity(block.position) != null)
            throw new IllegalStateException("Disassembly target is occupied: " + block.position);
    }

    private List<PlacedBlock> blocks(List<CarriageSnapshot> values) {
        var result = new ArrayList<PlacedBlock>();
        var positions = new HashSet<BlockPos>();
        for (CarriageSnapshot snapshot : values) {
            if (snapshot == null || !dimension.equals(snapshot.dimension()))
                throw new IllegalArgumentException("Assembly snapshot dimension or reference is invalid");
            for (var block : snapshot.blocks()) {
                if (block.state().getBlock() instanceof StandardTrackBlock || block.state().getBlock() instanceof BaseRailBlock)
                    throw new IllegalArgumentException("Tracks cannot be part of an assembled carriage");
                BlockPos position = new BlockPos(Math.addExact(snapshot.origin().getX(), block.position().getX()),
                        Math.addExact(snapshot.origin().getY(), block.position().getY()),
                        Math.addExact(snapshot.origin().getZ(), block.position().getZ()));
                if (!positions.add(position)) throw new IllegalArgumentException("Overlapping assembly snapshots");
                result.add(new PlacedBlock(position, block));
            }
        }
        return result;
    }

    private static BlockState vacantState(BlockState state) {
        return state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)
                ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
    }

    private static void replace(ServerLevel level, BlockPos position, BlockState state) {
        // Removing the entity first prevents container onRemove hooks from dropping copied inventory.
        level.removeBlockEntity(position);
        level.setBlock(position, state, MOVE_FLAGS);
        if (!level.getBlockState(position).equals(state))
            throw new IllegalStateException("Block refused assembly state change: " + position);
    }

    private static void restore(ServerLevel level, List<PlacedBlock> blocks) {
        for (PlacedBlock block : blocks) replace(level, block.position, block.block.state());
        restoreEntities(level, blocks);
    }

    private static void restoreEntities(ServerLevel level, List<PlacedBlock> blocks) {
        for (PlacedBlock block : blocks) {
            var data = block.block.entityData();
            if (data == null) continue;
            var entity = level.getBlockEntity(block.position);
            // A removal callback can fail after dropping the entity but before changing its state.
            // Re-setting that same state need not create an entity, so restore it explicitly here.
            if (entity == null) {
                entity = BlockEntity.loadStatic(block.position, block.block.state(), data, level.registryAccess());
                if (entity == null) throw new IllegalStateException("Restored block entity is missing: " + block.position);
                level.setBlockEntity(entity);
            } else entity.loadWithComponents(data, level.registryAccess());
            entity.setChanged();
            level.sendBlockUpdated(block.position, block.block.state(), block.block.state(), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * The delayed neighbour/shape phase used after moving a complete structure. setBlock already
     * sent clients and updated POIs; repeating markAndNotifyBlock would process POI removal twice.
     * These are its remaining neighbour, comparator and shape updates, after the batch commits.
     * External neighbour callbacks can create drops or scheduled ticks, so their exceptions cannot
     * undo the committed batch. Log them and continue notifying its other positions instead.
     */
    private static void notifyNeighbours(ServerLevel level, List<PlacedBlock> blocks,
                                         Function<PlacedBlock, BlockState> previous) {
        int shapeFlags = Block.UPDATE_MOVE_BY_PISTON | Block.UPDATE_CLIENTS;
        for (PlacedBlock block : blocks) {
            try {
                BlockState oldState = previous.apply(block);
                BlockState current = level.getBlockState(block.position);
                level.blockUpdated(block.position, oldState.getBlock());
                if (current.hasAnalogOutputSignal())
                    level.updateNeighbourForOutputSignal(block.position, current.getBlock());
                oldState.updateIndirectNeighbourShapes(level, block.position, shapeFlags);
                current.updateNeighbourShapes(level, block.position, shapeFlags);
                current.updateIndirectNeighbourShapes(level, block.position, shapeFlags);
            } catch (RuntimeException failure) {
                LOGGER.error("Neighbour notification failed after committed carriage block change at {}", block.position, failure);
            }
        }
    }

    private static void requireLoaded(ServerLevel level, BlockPos position) {
        if (level.isOutsideBuildHeight(position) || !level.isLoaded(position))
            throw new IllegalStateException("Assembly target is outside the loaded world: " + position);
    }

    private void requireLevel(ServerLevel level) {
        requireServerThread();
        requireServerThread(level);
        if (!dimension.equals(level.dimension()) || registries != level.registryAccess())
            throw new IllegalArgumentException("Assembly store belongs to another level");
    }

    private record PlacedBlock(BlockPos position, CarriageSnapshot.Block block) { }

    /** Validate the entire batch before insertion; never replace an existing identity. */
    public void addAll(List<CarriageSnapshot> additions) {
        requireServerThread();
        var pending = new LinkedHashMap<UUID, CarriageSnapshot>();
        for (CarriageSnapshot snapshot : List.copyOf(additions)) {
            if (!dimension.equals(snapshot.dimension()))
                throw new IllegalArgumentException("Snapshot belongs to a different dimension: " + snapshot.id());
            if (snapshots.containsKey(snapshot.id()) || pending.putIfAbsent(snapshot.id(), snapshot) != null)
                throw new IllegalArgumentException("Duplicate carriage UUID: " + snapshot.id());
            // Validate registry-dependent data before any store mutation, including externally
            // constructed snapshots whose block entity type may not match the current registry.
            snapshot.validate(registries);
        }
        if (pending.isEmpty()) return;
        snapshots.putAll(pending);
        setDirty();
    }

    public boolean remove(UUID id) {
        requireServerThread();
        if (active.values().stream().anyMatch(entry -> entry.carriages().contains(id)))
            throw new IllegalStateException("Cannot remove the snapshot of an active assembly");
        if (snapshots.remove(Objects.requireNonNull(id, "id")) == null) return false;
        setDirty();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        requireServerThread();
        tag.putInt("Schema", SCHEMA_VERSION);
        tag.putString("Dimension", dimension.location().toString());
        var entries = new ListTag();
        for (CarriageSnapshot snapshot : snapshots.values()) entries.add(snapshot.save(registries));
        tag.put("Carriages", entries);
        var assemblies = new ListTag();
        for (ActiveAssembly entry : active.values()) {
            var saved = new CompoundTag();
            saved.putUUID("Id", entry.id()); saved.putUUID("Owner", entry.owner());
            saved.putIntArray("Station", new int[] {entry.station().getX(), entry.station().getY(), entry.station().getZ()});
            saved.putString("Forward", entry.forward().getName());
            var ids = new ListTag();
            for (UUID id : entry.carriages()) { var reference = new CompoundTag(); reference.putUUID("Id", id); ids.add(reference); }
            saved.put("Carriages", ids); assemblies.add(saved);
        }
        tag.put("Assemblies", assemblies);
        return tag;
    }

    private static CarriageSnapshotStore load(ServerLevel level, CompoundTag tag) {
        requireServerThread(level);
        var migrated = MissingBlockMigration.migrate(level, tag, "carriage-snapshots");
        boolean changed = migrated != tag;
        tag = migrated;
        if (!tag.contains("Schema", Tag.TAG_INT) || tag.getInt("Schema") < 1 || tag.getInt("Schema") > SCHEMA_VERSION)
            throw new IllegalArgumentException("Unsupported carriage store schema");
        if (!tag.contains("Dimension", Tag.TAG_STRING)
                || !tag.getString("Dimension").equals(level.dimension().location().toString()))
            throw new IllegalArgumentException("Carriage store belongs to a different dimension");
        if (!(tag.get("Carriages") instanceof ListTag entries)
                || (!entries.isEmpty() && entries.getElementType() != Tag.TAG_COMPOUND))
            throw new IllegalArgumentException("Missing or invalid carriage list");
        var result = new CarriageSnapshotStore(level);
        for (Tag entry : entries) {
            var snapshot = CarriageSnapshot.load((CompoundTag) entry, level.registryAccess());
            if (!snapshot.dimension().equals(level.dimension()))
                throw new IllegalArgumentException("Stored carriage belongs to a different dimension");
            if (result.snapshots.putIfAbsent(snapshot.id(), snapshot) != null)
                throw new IllegalArgumentException("Duplicate stored carriage UUID: " + snapshot.id());
        }
        if (tag.getInt("Schema") >= 2) {
            if (!(tag.get("Assemblies") instanceof ListTag assemblies)
                    || (!assemblies.isEmpty() && assemblies.getElementType() != Tag.TAG_COMPOUND))
                throw new IllegalArgumentException("Missing or invalid active assembly list");
            var claimed = new HashSet<UUID>();
            for (Tag value : assemblies) {
                var saved = (CompoundTag) value;
                if (!saved.hasUUID("Id") || !saved.hasUUID("Owner") || !saved.contains("Station", Tag.TAG_INT_ARRAY)
                        || !saved.contains("Forward", Tag.TAG_STRING)) throw new IllegalArgumentException("Invalid active assembly identity");
                int[] coordinates = saved.getIntArray("Station");
                Direction forward = Direction.byName(saved.getString("Forward"));
                if (coordinates.length != 3 || forward == null) throw new IllegalArgumentException("Invalid assembly location");
                if (!(saved.get("Carriages") instanceof ListTag references)
                        || references.isEmpty() || references.getElementType() != Tag.TAG_COMPOUND)
                    throw new IllegalArgumentException("Invalid assembly carriage references");
                var ids = new ArrayList<UUID>();
                for (Tag reference : references) {
                    var idTag = (CompoundTag) reference;
                    if (!idTag.hasUUID("Id")) throw new IllegalArgumentException("Invalid assembly carriage UUID");
                    UUID id = idTag.getUUID("Id");
                    if (!result.snapshots.containsKey(id) || !claimed.add(id))
                        throw new IllegalArgumentException("Missing or multiply-owned carriage snapshot");
                    ids.add(id);
                }
                var entry = new ActiveAssembly(saved.getUUID("Id"), saved.getUUID("Owner"),
                        new BlockPos(coordinates[0], coordinates[1], coordinates[2]), forward, ids);
                if (result.active.putIfAbsent(entry.id(), entry) != null)
                    throw new IllegalArgumentException("Duplicate active assembly identity");
            }
        }
        if (changed) result.setDirty();
        return result;
    }

    private void requireServerThread() {
        if (Thread.currentThread() != serverThread) throw new IllegalStateException("Server thread required");
    }

    private static void requireServerThread(ServerLevel level) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Server thread required");
    }
}
