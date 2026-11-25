package willow.train.hexie.sync.cache;

import java.util.*;
import java.util.function.Function;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import willow.train.hexie.bridge.sync.RegionData.*;
import willow.train.hexie.bridge.sync.ServerSyncPort;
import willow.train.hexie.bridge.sync.VehicleSyncData.*;
import willow.train.hexie.sync.channel.vehicle.*;
import willow.train.hexie.sync.engine.RegionSync;
import willow.train.hexie.sync.engine.SyncMessage.*;
import willow.train.hexie.sync.engine.SyncServerTracker;

/** All revisions and sends are committed on the server thread. A respawn retains its play connection. */
public final class ServerVehicleSync implements ServerSyncPort {
    private final Map<UUID, CarriageContent> bodies = new HashMap<>();
    private SyncServerTracker tracker = new SyncServerTracker(bodies::get);
    private final Map<UUID, Peer> peers = new HashMap<>();

    private final class Peer {
        final ServerGamePacketListenerImpl connection;
        final SyncServerTracker.Client client = tracker.new Client();
        final Map<RegionKey, Function<RegionKey, RegionSnapshot>> sources = new HashMap<>();
        final RegionSync.Subscription regions = new RegionSync.Subscription(key -> sources.get(key).apply(key));

        Peer(ServerGamePacketListenerImpl connection) { this.connection = connection; }
    }

    static boolean supports(ServerPlayer player) {
        return NetworkRegistry.hasChannel(player.connection, CompositionPayload.TYPE.id())
                && NetworkRegistry.hasChannel(player.connection, InterestPayload.TYPE.id())
                && NetworkRegistry.hasChannel(player.connection, GetBodyPayload.TYPE.id())
                && NetworkRegistry.hasChannel(player.connection, BodyPayload.TYPE.id())
                && NetworkRegistry.hasChannel(player.connection, CellPatchPayload.TYPE.id());
    }

    public void login(ServerPlayer player) {
        if (!supports(player)) return;
        var previous = peers.get(player.getUUID());
        if (previous != null && previous.connection == player.connection) return;
        peers.put(player.getUUID(), new Peer(player.connection));
        player.connection.send(new CompositionPayload(tracker.snapshot()));
    }

    private Peer peer(ServerPlayer player) {
        var peer = peers.get(player.getUUID());
        return peer != null && peer.connection == player.connection ? peer : null;
    }

    public void logout(ServerPlayer player) {
        if (peer(player) != null) peers.remove(player.getUUID());
    }

    @Override public void tracking(ServerPlayer player, UUID id, boolean active) {
        if (active) login(player);
        var peer = peer(player);
        if (peer != null) peer.client.tracking(id, active).ifPresent(p -> peer.connection.send(new InterestPayload(p)));
    }

    public void request(ServerPlayer player, GetBody get) {
        var peer = peer(player);
        if (peer != null) peer.client.receive(get).ifPresent(p -> peer.connection.send(new BodyPayload(p)));
    }

    @Override public void compose(Collection<TrainRecord> changed, List<UUID> removed) {
        publish(tracker.compose(changed, removed));
    }

    private void publish(Composition composition) {
        var packet = new CompositionPayload(composition);
        peers.values().forEach(peer -> peer.connection.send(packet));
    }

    @Override public void putBody(CarriageRecord body) {
        putBody(body.carriageId(), body);
    }

    void putBody(UUID id, CarriageContent body) {
        var previous = bodies.get(id);
        if (previous != null && body.contentRevision() <= previous.contentRevision()) return;
        bodies.put(id, body);
        if (previous == null || peers.values().stream().noneMatch(peer -> peer.client.tracks(id))) return;
        var changes = new ArrayList<CellChange>();
        previous.cells().keySet().stream().filter(cell -> !body.cells().containsKey(cell))
                .forEach(cell -> changes.add(new CellChange(cell, Optional.empty())));
        body.cells().forEach((cell, block) -> {
            if (!block.equals(previous.cells().get(cell))) changes.add(new CellChange(cell, Optional.of(block)));
        });
        for (var peer : peers.values()) {
            peer.client.patch(id, previous.contentRevision(), body.contentRevision(), changes)
                    .ifPresent(p -> peer.connection.send(new CellPatchPayload(p)));
        }
    }

    @Override public void retire(UUID id) {
        for (var peer : peers.values()) peer.client.tracking(id, false)
                .ifPresent(p -> peer.connection.send(new InterestPayload(p)));
        tracker.retire(id).ifPresent(this::publish);
        bodies.remove(id);
    }

    @Override public void subscribeRegion(ServerPlayer player, RegionKey key, Function<RegionKey, RegionSnapshot> snapshots) {
        login(player);
        var peer = peer(player);
        if (peer == null || !NetworkRegistry.hasChannel(player.connection, RegionPayload.TYPE.id())
                || !NetworkRegistry.hasChannel(player.connection, GetRegionPayload.TYPE.id())) return;
        var page = Objects.requireNonNull(snapshots.apply(key));
        if (!page.page().key().equals(key)) throw new IllegalArgumentException("Wrong subscription region");
        peer.sources.put(key, snapshots);
        peer.regions.subscribe(page);
        peer.connection.send(new RegionPayload.Snapshot(page, true));
    }

    @Override public void unsubscribeRegion(ServerPlayer player, RegionKey key) {
        var peer = peer(player);
        if (peer == null || peer.sources.remove(key) == null) return;
        peer.regions.unsubscribe(key);
        peer.connection.send(new RegionPayload.Leave(key));
    }

    public void recover(ServerPlayer player, RegionKey key) {
        var peer = peer(player);
        if (peer != null) peer.regions.recover(key).ifPresent(page -> peer.connection.send(new RegionPayload.Snapshot(page, false)));
    }

    @Override public void publishRegions(Collection<RegionPatch> patches) {
        for (var peer : peers.values()) peer.regions.publish(patches)
                .forEach(delta -> peer.connection.send(new RegionPayload.Delta(delta)));
    }

    public void stop() {
        peers.clear();
        bodies.clear();
        tracker = new SyncServerTracker(bodies::get);
    }
}
