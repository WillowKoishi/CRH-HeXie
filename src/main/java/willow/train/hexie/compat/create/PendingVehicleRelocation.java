package willow.train.hexie.compat.create;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackGraph;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.createmod.catnip.data.Pair;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import willow.train.hexie.vehicle.assembly.VehicleRuntimeNbt;
import willow.train.hexie.vehicle.runtime.BogeyDefinition;
import willow.train.hexie.vehicle.runtime.VehicleRuntime;

/** A saved owner can receive a native wrench result even when its old route no longer loads. */
final class PendingVehicleRelocation {
    private final ServerLevel level;
    private final CompoundTag saved;
    private final VehicleRuntime.Checkpoint checkpoint;
    private final java.util.Map<UUID, AssembledVehicleMotion.Source> sources;
    private final List<Candidate> candidates = new ArrayList<>();

    PendingVehicleRelocation(ServerLevel level, CompoundTag saved) {
        this.level = level;
        this.saved = saved;
        checkpoint = VehicleRuntimeNbt.readCheckpoint(saved.getCompound("Runtime"));
        sources = AssembledVehicleMotion.readSources(level, saved);
        var endpoints = new HashMap<UUID, UUID>();
        var remaining = new java.util.LinkedHashSet<UUID>();
        for (var carriage : checkpoint.carriages()) {
            var definition = carriage.definition();
            remaining.add(definition.id());
            definition.couplings().forEach(endpoint -> endpoints.put(endpoint.id(), definition.id()));
        }
        while (!remaining.isEmpty()) {
            var selected = new HashSet<UUID>();
            selected.add(remaining.iterator().next());
            boolean changed;
            do {
                changed = false;
                for (var connection : checkpoint.connections()) {
                    UUID first = endpoints.get(connection.first()), second = endpoints.get(connection.second());
                    if (first == null || second == null) throw new IllegalArgumentException("Unknown saved coupling endpoint");
                    if (selected.contains(first) || selected.contains(second)) {
                        changed |= selected.add(first);
                        changed |= selected.add(second);
                    }
                }
            } while (changed);
            remaining.removeAll(selected);
            candidates.add(new Candidate(selected));
        }
    }

    void tick(SnapshotCarriageEntity owner) {
        tick();
    }

    /** The canonical saved owner can be an unloaded native train, rather than a carriage entity. */
    boolean tick() {
        for (Candidate candidate : candidates) {
            var current = Create.RAILWAYS.trains.get(candidate.trainId);
            if (!(current instanceof CompatibilityTrain train) || train.bridge != null || train.graph == null
                    || train.derailed || !saved.getUUID("Assembly").equals(train.assemblyId)
                    || !candidate.ids.equals(Set.copyOf(train.carriageIds))) continue;
            // Only the selected native consist is being repositioned. Its unrelated saved peers
            // keep their native carriage/cargo records even when their entities are unloaded.
            SnapshotCarriageEntity selectedOwner = null;
            boolean loaded = true;
            for (UUID id : candidate.ids) {
                if (!(level.getEntity(id) instanceof SnapshotCarriageEntity carrier) || carrier.isRemoved()
                        || !sources.get(id).assembly().equals(carrier.assemblyId())) {
                    loaded = false;
                    break;
                }
                if (selectedOwner == null) selectedOwner = carrier;
            }
            if (!loaded) continue;
            if (!candidate.changed(train)) continue;
            // Remember only an attempted placement; a changed native result is checked next tick,
            // while an unchanged bad graph never causes repeated decoding or route reconstruction.
            candidate.remember(train);
            CreateRouteBinding route;
            var distances = new LinkedHashMap<UUID, Double>();
            try {
                var first = train.carriages.getFirst().getLeadingPoint();
                route = CreateRouteBinding.fromPoint(level, train.graph, first, true,
                        (graph, preview, forward, choices) -> {
                            var steer = train.manualSteer;
                            if (!forward) steer = switch (steer) {
                                case LEFT -> TravellingPoint.SteerDirection.RIGHT;
                                case RIGHT -> TravellingPoint.SteerDirection.LEFT;
                                case NONE -> TravellingPoint.SteerDirection.NONE;
                            };
                            return preview.steer(steer, preview.edge.getNormal(graph, .5))
                                    .apply(graph, Pair.of(true, choices));
                        });
                boolean moved = !train.graph.id.equals(saved.getCompound("Route").getUUID("Graph"));
                for (var value : saved.getList("DerailedCarriages", net.minecraft.nbt.Tag.TAG_COMPOUND))
                    moved |= candidate.ids.contains(((CompoundTag) value).getUUID("Carriage"));
                for (int i = 0; i < train.carriageIds.size(); i++) {
                    UUID id = train.carriageIds.get(i);
                    var definition = checkpoint.carriages().stream().filter(car -> car.definition().id().equals(id))
                            .findFirst().orElseThrow().definition();
                    var front = definition.bogeys().stream().max(java.util.Comparator.comparingDouble(BogeyDefinition::offset))
                            .orElseThrow();
                    var nativeCar = train.carriages.get(i);
                    var nativeBogey = nativeCar.leadingBogey();
                    double centre = (route.distanceOf(nativeBogey.leading())
                            + route.distanceOf(nativeBogey.trailing())) / 2;
                    distances.put(id, centre - front.offset() - front.mount().trackPivot().z());
                    var previous = checkpoint.placement().bogeys().get(front.id()).track();
                    var next = route.path().sample(centre);
                    var delta = next.position().subtract(previous.position());
                    moved |= delta.dot(delta) > 1e-10;
                    moved |= next.tangent().dot(previous.tangent()) < 1 - 1e-8
                            || next.normal().dot(previous.normal()) < 1 - 1e-8;
                }
                if (!moved) continue;
            } catch (CreateRouteBinding.RouteInvalidException | IllegalArgumentException unavailable) {
                // Graph sync/rebuild may temporarily leave an old proxy point detached. Keep the
                // original pending state intact and retry only when the native placement changes.
                continue;
            }
            // Do not swallow failures after the ownership transfer has begun.
            if (AssembledVehicleMotion.recoverPendingRelocation(selectedOwner, saved, checkpoint, train, route, distances)) return true;
        }
        return false;
    }

    private static final class Candidate {
        final Set<UUID> ids;
        final UUID trainId;
        CompatibilityTrain previous;
        TrackGraph graph;
        List<PointState> points = List.of();

        Candidate(Set<UUID> ids) {
            this.ids = Set.copyOf(ids);
            String identity = ids.stream().sorted().map(UUID::toString).reduce("hexie:consist", (a, b) -> a + ":" + b);
            trainId = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
        }

        boolean changed(CompatibilityTrain train) {
            if (previous != train || graph != train.graph) return true;
            int index = 0;
            for (var carriage : train.carriages) {
                for (int b = 0; b < (carriage.isOnTwoBogeys() ? 2 : 1); b++) {
                    var bogey = carriage.bogeys.get(b == 0);
                    for (int p = 0; p < 2; p++) {
                        var point = p == 0 ? bogey.leading() : bogey.trailing();
                        if (index >= points.size() || !points.get(index++).matches(point)) return true;
                    }
                }
            }
            return index != points.size();
        }

        void remember(CompatibilityTrain train) {
            previous = train;
            graph = train.graph;
            var copy = new ArrayList<PointState>();
            train.forEachTravellingPoint(point -> copy.add(new PointState(point.node1, point.node2, point.edge, point.position)));
            points = List.copyOf(copy);
        }
    }

    private record PointState(Object first, Object second, Object edge, double position) {
        boolean matches(TravellingPoint point) {
            return first == point.node1 && second == point.node2 && edge == point.edge
                    && Math.abs(position - point.position) <= 1e-6;
        }
    }
}
