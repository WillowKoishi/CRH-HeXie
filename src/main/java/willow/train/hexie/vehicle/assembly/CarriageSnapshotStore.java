package willow.train.hexie.vehicle.assembly;

// split from willow/train/hexie/vehicle/assembly/CarriageSnapshotStore.java

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelResource;
import willow.train.hexie.bridge.vehicle.CarriageSnapshotView;
import willow.train.hexie.bridge.vehicle.VehicleBridge;
import willow.train.hexie.vehicle.assembly.CarriageWorldBlocks.PlacedBlock;

/**
 * Per-dimension snapshots and lightweight active assembly ownership. Block/BE installation failures
 * are rolled back on the server thread. A compatibility installer owns its placement lifecycle,
 * including neighbour notifications; standalone restoration notifies after the world commit.
 * Persistence is ordinary SavedData/world saving, not crash-atomic across chunk and entity files.
 */
public final class CarriageSnapshotStore extends SavedData implements VehicleBridge.CarriageSnapshotStoreView {
    private final CarriageAssemblyTransactions transactions = new CarriageAssemblyTransactions(this);
    private final CarriageWorldBlocks worldBlocks = new CarriageWorldBlocks(this);

    public static final String DATA_NAME = "hexie_carriage_snapshots";
    public static final int SCHEMA_VERSION = 2;

    private long syncGeneration;

    @Override public long syncGeneration() { return syncGeneration; }

    @Override public void setDirty(boolean dirty) {
        super.setDirty(dirty);
        if (dirty) syncGeneration++;
    }

    final ResourceKey<Level> dimension;
    final RegistryAccess registries;
    private final Thread serverThread;
    final LinkedHashMap<UUID, CarriageSnapshot> snapshots = new LinkedHashMap<>();
    final LinkedHashMap<UUID, ActiveAssembly> active = new LinkedHashMap<>();

    /** Carriage UUIDs reference the one snapshot map; no duplicate block/NBT data is held here. */
    public record ActiveAssembly(UUID id, UUID owner, BlockPos station, Direction forward, List<UUID> carriages) implements VehicleBridge.CarriageSnapshotStoreActiveAssemblyView {
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
        var loadFailure = new java.util.concurrent.atomic.AtomicReference<RuntimeException>();
        var reportingFactory = new Factory<CarriageSnapshotStore>(factory.constructor(), (tag, provider) -> {
            try { return factory.deserializer().apply(tag, provider); }
            catch (RuntimeException failure) { loadFailure.set(failure); throw failure; }
        });
        var existing = storage.get(reportingFactory, DATA_NAME);
        if (existing != null) return existing;
        Path file = directory.resolve(DATA_NAME + ".dat");
        // DimensionDataStorage catches deserialization exceptions and returns null. Do not let a
        // failed read silently become an empty store that can overwrite the original file.
        if (!Files.notExists(file)) throw new IllegalStateException("Could not load carriage snapshots: " + file, loadFailure.get());
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

    /** Register an already captured structure without reading or changing world blocks. */
    public ActiveAssembly importAssembly(UUID assemblyId, UUID owner, BlockPos reference, Direction forward,
                                         List<CarriageSnapshotView> structuresView) {
        var structures = structuresView.stream().map(CarriageSnapshot.class::cast).toList();
        requireServerThread();
        if (active.containsKey(Objects.requireNonNull(assemblyId)))
            throw new IllegalArgumentException("Assembly identity already exists");
        structures = List.copyOf(structures);
        var entry = new ActiveAssembly(assemblyId, owner, reference, forward,
                structures.stream().map(CarriageSnapshot::id).toList());
        // addAll validates every identity, dimension and block before inserting any snapshot.
        addAll(structures);
        active.put(entry.id(), entry);
        setDirty();
        return entry;
    }

    public ActiveAssembly assemble(ServerLevel level, UUID owner, BlockPos station,
                                   VehicleAssemblyPreparer.Prepared prepared) {
        return transactions.assemble(level, owner, station, prepared);
    }

    /** Preflight everything, retain snapshots, then remove the source blocks in this same tick. */
    public ActiveAssembly assemble(ServerLevel level, UUID owner, BlockPos station, Direction forward,
                                   List<CarriageSnapshot> additions) {
        return transactions.assemble(level, owner, station, forward, additions);
    }

    /** The caller may allocate ownership before mounting storage from still-present world entities. */
    public ActiveAssembly assemble(ServerLevel level, UUID assemblyId, UUID owner, BlockPos station,
                                   Direction forward, List<CarriageSnapshotView> additionsView) {
        var additions = additionsView.stream().map(CarriageSnapshot.class::cast).toList();
        return transactions.assemble(level, assemblyId, owner, station, forward, additions);
    }

    /** Restores at the captured positions only; a conflicting or unloaded target changes nothing. */
    public void disassemble(ServerLevel level, UUID assemblyId) {
        transactions.disassemble(level, assemblyId);
    }

    /** Only block states and block-entity data can change while the captured body is assembled. */
    public void updateSnapshots(UUID assemblyId, List<CarriageSnapshotView> replacementsView) {
        var replacements = replacementsView.stream().map(CarriageSnapshot.class::cast).toList();
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
        transactions.checkDisassembly(level, assemblyId);
    }

    /** Preflight a transformed subset without publishing its destination as the saved source. */
    public void checkDisassembly(ServerLevel level, UUID assemblyId, List<CarriageSnapshot> targetSnapshots) {
        transactions.checkDisassembly(level, assemblyId, targetSnapshots);
    }

    /** Check the complete consist, including overlaps between different source assemblies. */
    public void checkDisassembly(ServerLevel level, Map<UUID, List<CarriageSnapshot>> targetSnapshots) {
        transactions.checkDisassembly(level, targetSnapshots);
    }

    /** The placement backend decides how occupied cells are replaced or dropped. */
    public void checkDisassemblyUsing(ServerLevel level, Map<UUID, List<CarriageSnapshotView>> targetSnapshotsView) {
        var targetSnapshots = snapshotGroups(targetSnapshotsView);
        transactions.checkDisassemblyUsing(level, targetSnapshots);
    }

    /** Restore mounted storage after all blocks/BE exist, while rollback still owns the snapshots. */
    public void disassemble(ServerLevel level, UUID assemblyId, Runnable afterBlocksRestored) {
        transactions.disassemble(level, assemblyId, afterBlocksRestored);
    }

    /**
     * Restore only the supplied carriage identities, possibly at a new position/orientation.
     * Original snapshots and active membership remain authoritative until all blocks, entities
     * and the installation callback succeed. Successful targets release their saved snapshots.
     */
    public void disassemble(ServerLevel level, UUID assemblyId, List<CarriageSnapshot> targetSnapshots,
                            Runnable afterInstall) {
        transactions.disassemble(level, assemblyId, targetSnapshots, afterInstall);
    }

    /**
     * The supplied backend performs the entire placement lifecycle using its live block data.
     * Targets describe occupied cells for preflight/rollback only; this store must not install
     * them first, reload their block entities or repeat the backend's neighbour notifications.
     * Existing world blocks are left to the backend's own replacement and drop rules.
     */
    public void disassembleUsing(ServerLevel level, UUID assemblyId, List<CarriageSnapshot> targets,
                                 Runnable installer) {
        transactions.disassembleUsing(level, assemblyId, targets, installer);
    }

    /** All source memberships commit together after one native installation/rollback boundary. */
    public void disassembleUsing(ServerLevel level, Map<UUID, List<CarriageSnapshotView>> targetsView,
                                 Runnable installer) {
        var targets = snapshotGroups(targetsView);
        transactions.disassembleUsing(level, targets, installer);
    }

    static void checkTarget(ServerLevel level, PlacedBlock block) {
        CarriageWorldBlocks.checkTarget(level, block);
    }

    List<PlacedBlock> blocks(List<CarriageSnapshot> values) {
        return worldBlocks.blocks(values);
    }

    static BlockState vacantState(BlockState state) {
        return CarriageWorldBlocks.vacantState(state);
    }

    static void replace(ServerLevel level, BlockPos position, BlockState state) {
        CarriageWorldBlocks.replace(level, position, state);
    }

    static void restore(ServerLevel level, List<PlacedBlock> blocks) {
        CarriageWorldBlocks.restore(level, blocks);
    }

    static void restoreEntities(ServerLevel level, List<PlacedBlock> blocks) {
        CarriageWorldBlocks.restoreEntities(level, blocks);
    }

    /**
     * The delayed neighbour/shape phase used after moving a complete structure. setBlock already
     * sent clients and updated POIs; repeating markAndNotifyBlock would process POI removal twice.
     * These are its remaining neighbour, comparator and shape updates, after the batch commits.
     * External neighbour callbacks can create drops or scheduled ticks, so their exceptions cannot
     * undo the committed batch. Log them and continue notifying its other positions instead.
     */
    static void notifyNeighbours(ServerLevel level, List<PlacedBlock> blocks,
                                         Function<PlacedBlock, BlockState> previous) {
        CarriageWorldBlocks.notifyNeighbours(level, blocks, previous);
    }

    static void requireLoaded(ServerLevel level, BlockPos position) {
        CarriageWorldBlocks.requireLoaded(level, position);
    }

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

    /** Permanently delete assembled carriages without installing their blocks into the world. */
    public void discardCarriages(java.util.Set<UUID> ids) {
        requireServerThread();
        for (var entry : List.copyOf(active.values())) {
            var remaining = entry.carriages().stream().filter(id -> !ids.contains(id)).toList();
            if (remaining.size() == entry.carriages().size()) continue;
            if (remaining.isEmpty()) active.remove(entry.id());
            else active.put(entry.id(), new ActiveAssembly(entry.id(), entry.owner(), entry.station(), entry.forward(), remaining));
        }
        ids.forEach(snapshots::remove);
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

    void requireServerThread() {
        if (Thread.currentThread() != serverThread) throw new IllegalStateException("Server thread required");
    }

    static void requireServerThread(ServerLevel level) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Server thread required");
    }

    private static Map<UUID, List<CarriageSnapshot>> snapshotGroups(Map<UUID, List<CarriageSnapshotView>> groups) {
        var result = new java.util.LinkedHashMap<UUID, List<CarriageSnapshot>>();
        groups.forEach((id, snapshots) -> result.put(id, snapshots.stream().map(CarriageSnapshot.class::cast).toList()));
        return result;
    }
}
