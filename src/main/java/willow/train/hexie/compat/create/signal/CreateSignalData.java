package willow.train.hexie.compat.create.signal;

import java.util.HashSet;
import java.util.UUID;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.content.trains.signal.SignalBlock.SignalType;
import willow.train.hexie.bridge.vehicle.CabControlBridge.CtcsControlOccupancy;

/** Live occupancy and conflicting reservations, including intersecting track groups. */
final class CreateSignalData {
    record Observation(SignalBoundary boundary, UUID group, boolean primary, boolean chained, CtcsControlOccupancy occupancy) {}

    /** Shared interpretation only; reservation and display publication remain in their own phases. */
    static Observation observe(SignalBoundary signal, TrackNode enteringNode, Train train) {
        boolean primary = signal.isPrimary(enteringNode);
        UUID group = signal.getGroup(enteringNode);
        if (signal.blockEntities.get(primary).isEmpty()) {
            if (signal.blockEntities.get(!primary).isEmpty()) return null;
            // The only signal faces the other way. This approach has a red at the boundary.
            return new Observation(signal, group, primary, false, CtcsControlOccupancy.BLOCKED);
        }
        return new Observation(signal, group, primary, signal.types.get(primary) == SignalType.CROSS_SIGNAL,
                signal.isForcedRed(enteringNode) ? CtcsControlOccupancy.BLOCKED : occupancy(group, train));
    }

    static CtcsControlOccupancy occupancy(UUID id, Train train) { return occupancy(id, train, new HashSet<>()); }
    private static CtcsControlOccupancy occupancy(UUID id, Train train, HashSet<UUID> visited) {
        if (id == null) return CtcsControlOccupancy.UNKNOWN;
        if (!visited.add(id)) return CtcsControlOccupancy.CLEAR;
        var group = Create.RAILWAYS.signalEdgeGroups.get(id);
        if (group == null) return CtcsControlOccupancy.UNKNOWN;
        if (group.trains.stream().anyMatch(other -> other != train)
                || group.reserved != null && !train.reservedSignalBlocks.contains(id)) return CtcsControlOccupancy.BLOCKED;
        var result = CtcsControlOccupancy.CLEAR;
        for (var next : group.intersecting.values()) {
            var state = occupancy(next, train, visited);
            if (state == CtcsControlOccupancy.BLOCKED) return state;
            if (state == CtcsControlOccupancy.UNKNOWN) result = state;
        }
        return result;
    }
}
