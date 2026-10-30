package willow.train.hexie.compat.create;

import com.simibubi.create.Create;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint;
import willow.train.hexie.vehicle.track.TrackGeometry.Vector;

/** Broad phase only. Sharing a route domain neither attracts a car nor engages its coupler. */
final class VehicleContactPreparation {
    private static final double TICK_SECONDS = 1.0 / 20;
    private static final double NEARBY_MARGIN = 1;

    private VehicleContactPreparation() {}

    static void prepare() {
        Map<UUID, AssembledVehicleMotion> owners = owners();
        var motions = new java.util.LinkedHashSet<>(owners.values());
        var endpoints = new ArrayList<End>();
        for (var motion : motions) collect(motion, endpoints);
        if (endpoints.stream().noneMatch(e -> e.travel > 1e-10)) return;
        var candidates = new ArrayList<Pair>();
        var components = new HashMap<UUID, Set<UUID>>();
        for (int i = 0; i < endpoints.size(); i++) for (int j = i + 1; j < endpoints.size(); j++) {
            End first = endpoints.get(i), second = endpoints.get(j);
            var a = owners.get(first.carriage);
            var b = owners.get(second.carriage);
            if (a == null || b == null || a.level() != b.level()
                    || a.route().graph() != b.route().graph() || !nearby(first, second)) continue;
            if (a == b && a.runtime().connected(first.carriage, second.carriage)) continue;
            candidates.add(new Pair(first, second));
            var members = new HashSet<>(a.runtime().state(first.carriage).consist());
            members.addAll(b.runtime().state(second.carriage).consist());
            for (UUID id : List.copyOf(members)) members.addAll(components.getOrDefault(id, Set.of()));
            for (UUID id : members) components.put(id, members);
        }
        for (var pair : candidates) {
            End first = pair.first, second = pair.second;
            var a = owners.get(first.carriage);
            var b = owners.get(second.carriage);
            if (a == b) continue;
            try {
                // All candidates are known before transfer. Preserve an earlier A-B preparation
                // when B-C is processed, without including unrelated cars from either assembly.
                a.prepareContact(first.carriage, first.endpoint, b, second.carriage, second.endpoint,
                        components.get(first.carriage));
                // A merge transfers only the selected consists. Refresh ownership of their
                // untouched neighbours as well, including carriages whose entities are unloaded.
                owners = owners();
            } catch (IllegalArgumentException | IllegalStateException invalidRoute) {
                // Nearby or crossing tracks need not have one common directed route.
                // The detached merge validates that before changing either live owner.
            }
        }
    }

    private static Map<UUID, AssembledVehicleMotion> owners() {
        var result = new LinkedHashMap<UUID, AssembledVehicleMotion>();
        for (var value : Create.RAILWAYS.trains.values()) {
            if (!(value instanceof CompatibilityTrain train) || train.bridge == null) continue;
            var motion = train.bridge.motion();
            for (UUID id : train.carriageIds) result.put(id, motion);
        }
        return result;
    }

    private static void collect(AssembledVehicleMotion motion, List<End> result) {
        var runtime = motion.runtime();
        var occupied = new HashSet<UUID>();
        var manualConsists = new HashSet<UUID>();
        var endpointOwners = new HashMap<UUID, UUID>();
        for (var carriage : runtime.definitions()) for (var endpoint : carriage.couplings())
            endpointOwners.put(endpoint.id(), carriage.id());
        for (var state : runtime.couplingStates()) {
            occupied.add(state.first()); occupied.add(state.second());
            if (!state.connected()) {
                manualConsists.addAll(runtime.state(endpointOwners.get(state.first())).consist());
                manualConsists.addAll(runtime.state(endpointOwners.get(state.second())).consist());
            }
        }
        double accelerationBound = 0;
        for (var carriage : runtime.definitions()) for (var bogey : carriage.bogeys())
            accelerationBound = Math.max(accelerationBound,
                    bogey.parameters().tractionForceN() / bogey.parameters().loadKg());
        for (var carriage : runtime.definitions()) {
            if (motion.isDerailed(carriage.id()) || manualConsists.contains(carriage.id())) continue;
            var body = runtime.placement().bodies().get(carriage.id());
            double speed = runtime.speed(carriage.id());
            var reference = runtime.placement().bogeys().get(carriage.referenceBogey().id());
            Vector velocity = reference.track().tangent().scale(speed);
            double travel = (Math.abs(speed) + accelerationBound * TICK_SECONDS) * TICK_SECONDS;
            for (var endpoint : carriage.couplings()) {
                if (occupied.contains(endpoint.id()) || endpoint.kind() != CouplingEndpoint.Kind.RIGID) continue;
                Vector facing = body.rotate(endpoint.rotated(new CouplingEndpoint.Point(0, 0, -1)));
                result.add(new End(carriage.id(), endpoint.id(), endpoint.effectiveModel(),
                        runtime.placement().endpoints().get(endpoint.id()).complete(), facing, velocity, travel,
                        accelerationBound * TICK_SECONDS));
            }
        }
    }

    private static boolean nearby(End a, End b) {
        if (!a.model.equals(b.model) || a.facing.dot(b.facing) >= 0) return false;
        Vector gap = b.position.subtract(a.position);
        Vector closing = a.velocity.subtract(b.velocity);
        // This is only a broad phase: a parked powered car can start and cross a tiny gap
        // in this very tick. Actual controls and endpoint coincidence remain runtime decisions.
        if (closing.dot(a.facing) + a.velocityChange + b.velocityChange <= 1e-8
                || gap.dot(a.facing) < -1e-5) return false;
        double reach = a.travel + b.travel + NEARBY_MARGIN;
        return gap.dot(gap) <= reach * reach;
    }

    private record End(UUID carriage, UUID endpoint, String model, Vector position, Vector facing, Vector velocity,
                       double travel, double velocityChange) {}
    private record Pair(End first, End second) {}
}
