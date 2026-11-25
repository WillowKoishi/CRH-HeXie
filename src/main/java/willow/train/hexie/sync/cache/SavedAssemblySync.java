package willow.train.hexie.sync.cache;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import willow.train.hexie.bridge.sync.VehicleSyncData.*;
import willow.train.hexie.bridge.vehicle.CarriageSnapshotView;
import willow.train.hexie.bridge.vehicle.VehicleBridge.CarriageSnapshotStoreView;

/** Mirrors committed immutable snapshots. A rolled-back assembly never becomes visible between server ticks. */
final class SavedAssemblySync {
    private final ServerVehicleSync server;
    private final CarriageSnapshotStoreView store;
    private final Map<UUID, SnapshotBody> bodies = new HashMap<>();
    private Map<UUID, TrainRecord> trains = Map.of();
    private long generation = -1;

    SavedAssemblySync(ServerVehicleSync server, CarriageSnapshotStoreView store) {
        this.server = server;
        this.store = store;
    }

    void refresh() {
        long next = store.syncGeneration();
        if (next >= 0 && generation == next) return;
        var active = new HashSet<UUID>();
        var nextTrains = new HashMap<UUID, TrainRecord>();
        var changed = new ArrayList<TrainRecord>();
        for (var assembly : store.active()) {
            var members = new ArrayList<TrainRecord.Member>();
            for (UUID id : assembly.carriages()) {
                active.add(id);
                var snapshot = store.find(id).orElseThrow();
                var prev = bodies.get(id);
                if (prev == null || prev.snapshot != snapshot) {
                    long revision = prev == null ? 0 : prev.revision + 1;
                    var body = new SnapshotBody(snapshot, revision);
                    server.putBody(id, body);
                    bodies.put(id, body);
                }
                members.add(new TrainRecord.Member(id, false));
            }
            var train = new TrainRecord(assembly.id(), members);
            if (!train.equals(trains.get(train.trainId()))) changed.add(train);
            nextTrains.put(train.trainId(), train);
        }
        var removed = trains.keySet().stream().filter(id -> !nextTrains.containsKey(id)).toList();
        if (!changed.isEmpty() || !removed.isEmpty()) server.compose(changed, removed);
        trains = nextTrains;
        for (var id : bodies.keySet().stream().filter(id -> !active.contains(id)).toList()) {
            server.retire(id);
            bodies.remove(id);
        }
        generation = next;
    }

    void unload() {
        if (!trains.isEmpty()) server.compose(List.of(), List.copyOf(trains.keySet()));
        bodies.keySet().forEach(server::retire);
    }

    private static final class SnapshotBody implements CarriageContent {
        private final CarriageSnapshotView snapshot;
        private final long revision;
        private Map<Cell, BlockData> cells;

        SnapshotBody(CarriageSnapshotView snapshot, long revision) {
            this.snapshot = snapshot;
            this.revision = revision;
        }

        @Override public long contentRevision() { return revision; }

        @Override public Map<Cell, BlockData> cells() {
            if (cells == null) cells = convert(snapshot);
            return cells;
        }
    }

    private static Map<Cell, BlockData> convert(CarriageSnapshotView snapshot) {
        var cells = new HashMap<Cell, BlockData>();
        for (var block : snapshot.blocks()) {
            var p = block.position();
            var state = block.state();
            var name = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            var properties = new HashMap<String, String>();
            for (var property : state.getProperties()) properties.put(property.getName(), value(state, property));
            var data = Optional.ofNullable(block.entityData()).map(SavedAssemblySync::encode);
            cells.put(new Cell(p.getX(), p.getY(), p.getZ()), new BlockData(new Id(name.getNamespace(), name.getPath()), properties, data));
        }
        return Map.copyOf(cells);
    }

    private static <T extends Comparable<T>> String value(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private static String encode(CompoundTag tag) {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            NbtIo.write(tag, out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }
}
