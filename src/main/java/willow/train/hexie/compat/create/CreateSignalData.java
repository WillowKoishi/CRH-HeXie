package willow.train.hexie.compat.create;

import java.util.HashSet;
import java.util.UUID;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import willow.train.hexie.vehicle.control.CtcsControl.Occupancy;

/** Live occupancy and conflicting reservations, including intersecting track groups. */
final class CreateSignalData {
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
