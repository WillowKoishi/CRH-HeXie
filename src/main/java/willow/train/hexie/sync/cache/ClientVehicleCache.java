package willow.train.hexie.sync.cache;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.core.api.vehicle.Point3;
import willow.train.hexie.bridge.sync.RegionData.*;
import willow.train.hexie.bridge.sync.VehicleCacheView;
import willow.train.hexie.core.api.interaction.BlockControlContext;
import willow.train.hexie.core.api.interaction.BlockControlHit;
import willow.train.hexie.core.api.interaction.BlockControlHost;
import willow.train.hexie.sync.engine.*;
import willow.train.hexie.sync.engine.SyncMessage.*;

/** Connection-local bodies and level-local carrier bindings. Accessed on the client thread. */
public final class ClientVehicleCache implements VehicleCacheView {
    private final Consumer<GetBody> request;
    private final Consumer<RegionKey> recover;
    private SyncClientCache<Void> bodies = new SyncClientCache<>();
    private RegionSync.Cache regions = new RegionSync.Cache();
    private final Map<UUID, Timeline> bindings = new HashMap<>();
    private final Map<UUID, Host> hosts = new HashMap<>();
    private final Map<UUID, List<RenderCell>> models = new HashMap<>();
    private final Map<UUID, VehicleBlockView> views = new HashMap<>();
    private BiFunction<UUID, List<RenderCell>, List<RenderCell>> mountedModels = (id, cells) -> cells;

    public ClientVehicleCache(Consumer<GetBody> request, Consumer<RegionKey> recover) {
        this.request = Objects.requireNonNull(request);
        this.recover = Objects.requireNonNull(recover);
    }

    private record Sample(long tick, Pose pose, Point3 velocity) {}

    private record Timeline(Sample previous, Sample current) {
        Pose at(float partialTick) {
            double t = Math.clamp(partialTick, 0, 1);
            if (t == 0 || previous.tick == current.tick) return previous.pose;
            if (t == 1) return current.pose;
            // Cubic Hermite tangents use blocks per tick, scaled by the actual sample interval.
            double dt = current.tick - previous.tick;
            double u = 1 - t;
            double blend = t * t * (3 - 2 * t);
            double fromTangent = dt * t * u * u, toTangent = -dt * t * t * u;
            var a = previous.pose.position();
            var b = current.pose.position();
            var v = previous.velocity;
            var w = current.velocity;
            var position = new Point3(
                    a.x() + (b.x() - a.x()) * blend + v.x() * fromTangent + w.x() * toTangent,
                    a.y() + (b.y() - a.y()) * blend + v.y() * fromTangent + w.y() * toTangent,
                    a.z() + (b.z() - a.z()) * blend + v.z() * fromTangent + w.z() * toTangent);
            return new Pose(position, previous.pose.rotation().slerp(current.pose.rotation(), t));
        }
    }

    @Override public void bind(UUID id, long tick, Pose pose, Point3 velocity) {
        bind(id, tick, pose, velocity, null);
    }

    @Override public void bind(UUID id, long tick, Pose pose, Point3 velocity, Host host) {
        unbind(id);
        var sample = new Sample(tick, pose, velocity);
        bindings.put(id, new Timeline(sample, sample));
        if (host != null) {
            hosts.put(id, host);
            body(id).ifPresent(body -> host.bind(id, pose, body));
        }
    }

    @Override public void move(UUID id, long tick, Pose pose, Point3 velocity) {
        var window = bindings.get(id);
        if (window == null || tick < window.current.tick) return;
        var next = new Sample(tick, pose, velocity);
        var previous = tick == window.current.tick ? window.previous : window.current;
        bindings.put(id, new Timeline(previous.tick == tick ? next : previous, next));
        var host = hosts.get(id);
        if (host != null && body(id).isPresent()) host.move(id, pose);
    }

    @Override public Optional<Pose> poseAt(UUID id, float partialTick) {
        return Optional.ofNullable(bindings.get(id)).map(window -> window.at(partialTick));
    }

    @Override public Set<UUID> boundCarriages() { return Collections.unmodifiableSet(bindings.keySet()); }
    @Override public void unbind(UUID id) {
        bindings.remove(id);
        var host = hosts.remove(id);
        if (host != null && body(id).isPresent()) host.unbind(id);
    }

    @Override public Map<UUID, MemberView> membership() { return bodies.membership(); }
    @Override public Optional<CarriageBody<Void>> body(UUID id) { return bodies.body(id); }

    public SyncClientCache<Void> bodies() { return bodies; }

    public void receive(Interest message) {
        var previous = body(message.carriageId());
        var get = bodies.receive(message);
        if (previous.isPresent() && body(message.carriageId()).isEmpty()) invalidate(message.carriageId());
        get.ifPresent(request);
    }

    public void receive(Composition message) { bodies.receive(message).forEach(request); }

    public void receive(Body message) {
        if (bodies.receive(message)) invalidate(message.carriageId());
    }

    public void receive(CellPatch message) {
        var before = bodies.body(message.carriageId()).orElse(null);
        var get = bodies.receive(message);
        if (before != bodies.body(message.carriageId()).orElse(null)) invalidate(message.carriageId());
        get.ifPresent(request);
    }

    private void invalidate(UUID id) {
        models.remove(id);
        views.remove(id);
        var host = hosts.get(id);
        if (host != null) body(id).ifPresentOrElse(body -> host.bind(id, bindings.get(id).current.pose, body),
                () -> host.unbind(id));
    }

    @Override public List<RenderCell> renderCells(UUID id) {
        var body = bodies.body(id);
        if (body.isEmpty()) return List.of();
        var cells = models.computeIfAbsent(id, key -> body.get().cells().entrySet().stream()
                .map(e -> new RenderCell(VehicleBlockView.pos(e.getKey()), VehicleBlockView.state(e.getValue())))
                .filter(cell -> !cell.state().isAir()).toList());
        return mountedModels.apply(id, cells);
    }

    @Override public void registerMountedModels(BiFunction<UUID, List<RenderCell>, List<RenderCell>> adapter) {
        mountedModels = Objects.requireNonNull(adapter);
    }

    @Override public Optional<BlockGetter> blockView(Level level, UUID id) {
        return bodies.body(id).map(body -> views.compute(id, (key, old) -> old != null && old.level == level
                ? old : new VehicleBlockView(level, body.cells())));
    }

    @Override public Optional<BlockControlHit> pickControl(Entity carrier, BlockPos cell, Vec3 from, Vec3 to,
                                                          float partialTick) {
        if (!bindings.containsKey(carrier.getUUID())) return Optional.empty();
        return blockView(carrier.level(), carrier.getUUID()).map(blocks -> {
            var target = new BlockControlContext(blocks, cell, carrier, partialTick);
            var host = BlockControlHost.find(target.state());
            return host == null ? null : host.pickControl(target, from, to);
        });
    }

    public void subscribeRegion(RegionSnapshot page) {
        regions.subscribe(page.page().key());
        regions.receive(page);
    }

    public void receive(RegionSnapshot page) { regions.receive(page); }
    public void receive(RegionPatch delta) { regions.receive(delta).ifPresent(recover); }
    public void unsubscribeRegion(RegionKey key) { regions.unsubscribe(key); }
    @Override public Optional<RegionSnapshot> region(RegionKey key) { return regions.page(key); }

    public void leaveLevel() {
        List.copyOf(bindings.keySet()).forEach(this::unbind);
        views.clear();
        models.clear();
    }

    public void disconnect() {
        leaveLevel();
        regions = new RegionSync.Cache();
        bodies = new SyncClientCache<>();
    }
}
