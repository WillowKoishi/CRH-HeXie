package willow.train.hexie.compat.create;

import java.util.HashSet;
import java.util.UUID;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.content.trains.signal.SignalBlock.SignalType;
import willow.train.hexie.vehicle.control.CtcsControl.Occupancy;

/** Live occupancy and conflicting reservations, including intersecting track groups. */
final class CreateSignalData {
    record Observation(SignalBoundary boundary, UUID group, boolean primary, boolean chained, Occupancy occupancy) {}

    /** Shared interpretation only; reservation and display publication remain in their own phases. */
    static Observation observe(SignalBoundary signal, TrackNode enteringNode, Train train) {
        boolean primary = signal.isPrimary(enteringNode);
        UUID group = signal.getGroup(enteringNode);
        if (signal.blockEntities.get(primary).isEmpty()) {
            if (signal.blockEntities.get(!primary).isEmpty()) return null;
            // The only signal faces the other way. This approach has a red at the boundary.
            return new Observation(signal, group, primary, false, Occupancy.BLOCKED);
        }
        return new Observation(signal, group, primary, signal.types.get(primary) == SignalType.CROSS_SIGNAL,
                signal.isForcedRed(enteringNode) ? Occupancy.BLOCKED : occupancy(group, train));
    }

    static Occupancy occupancy(UUID id, Train train) { return occupancy(id, train, new HashSet<>()); }
    private static Occupancy occupancy(UUID id, Train train, HashSet<UUID> visited) {
        if (id == null) return Occupancy.UNKNOWN;
        if (!visited.add(id)) return Occupancy.CLEAR;
        var group = Create.RAILWAYS.signalEdgeGroups.get(id);
        if (group == null) return Occupancy.UNKNOWN;
        if (group.trains.stream().anyMatch(other -> other != train)
                || group.reserved != null && !train.reservedSignalBlocks.contains(id)) return Occupancy.BLOCKED;
        var result = Occupancy.CLEAR;
        for (var next : group.intersecting.values()) {
            var state = occupancy(next, train, visited);
            if (state == Occupancy.BLOCKED) return state;
            if (state == Occupancy.UNKNOWN) result = state;
        }
        return result;
    }
}
