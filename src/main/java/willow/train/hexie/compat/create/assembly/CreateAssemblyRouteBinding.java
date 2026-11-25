package willow.train.hexie.compat.create.assembly;

import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackGraphLocation;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import willow.train.hexie.compat.create.signal.CreateRouteBinding;
import willow.train.hexie.compat.create.track.CreateTrackCompatibility;
import willow.train.hexie.compat.create.track.CreateTrackGeometry;

// split from CreateRouteBinding.java
public final class CreateAssemblyRouteBinding {
    private CreateAssemblyRouteBinding() {}

    /** Bind the station edge and cover at least the first assembly support. */
    public static CreateRouteBinding fromStation(StationBlockEntity station, Vec3 firstBogeyWorld,
                                                 Direction forward) {
        return fromStation(station, firstBogeyWorld, forward, List.of());
    }

    /**
     * Cover the captured supports along the station's straight assembly strip. A branch within
     * that strip follows the unique straight continuation, just like Create's assembly scan.
     * Beyond the supports, junctions remain undecided for the normal train controls.
     */
    public static CreateRouteBinding fromStation(StationBlockEntity station, Vec3 firstBogeyWorld,
                                                 Direction forward, List<BlockPos> supportPositions) {
        if (!(station.getLevel() instanceof ServerLevel level) || !station.edgePoint.hasValidTrack())
            throw new IllegalArgumentException("Station needs a server level and a valid target track");
        if (!forward.getAxis().isHorizontal()) throw new IllegalArgumentException("Assembly direction must be horizontal");
        var location = station.edgePoint.determineGraphLocation();
        if (location == null) throw new IllegalArgumentException("Station track is not in a graph");
        return fromAssemblyLocation(level, location, firstBogeyWorld, Vec3.atLowerCornerOf(forward.getNormal()),
                supportPositions.stream().map(Vec3::atCenterOf).toList());
    }

    public static CreateRouteBinding fromAssemblyLocation(ServerLevel level, TrackGraphLocation location,
                                                            Vec3 firstBogeyWorld, Vec3 direction,
                                                            List<Vec3> supportPositions) {
        TrackGraph graph = CreateTrackCompatibility.routeGraph(location.graph);
        TrackNode first = graph.locateNode(location.edge.getFirst());
        TrackNode second = graph.locateNode(location.edge.getSecond());
        if (first == null || second == null) throw new IllegalArgumentException("Station edge no longer exists");
        TrackEdge edge = graph.getConnectionsFrom(first).get(second);
        CreateTrackGeometry seed = new CreateTrackGeometry(graph, Objects.requireNonNull(edge, "Station edge"));
        var tangent = seed.sample(Math.max(0, Math.min(1, location.position / edge.getLength()))).tangent();
        double facing = tangent.x() * direction.x + tangent.y() * direction.y + tangent.z() * direction.z;
        if (Math.abs(facing) < .5) throw new IllegalArgumentException("Station route does not follow the assembly direction");
        if (facing < 0) {
            TrackNode swap = first;
            first = second;
            second = swap;
        }
        LinkedList<TrackNode> nodes = new LinkedList<>(List.of(first, second));
        Set<TrackNode> visited = new HashSet<>(nodes);
        double minimum = firstBogeyWorld.dot(direction), maximum = minimum;
        for (Vec3 support : supportPositions) {
            double coordinate = support.dot(direction);
            minimum = Math.min(minimum, coordinate);
            maximum = Math.max(maximum, coordinate);
        }
        CreateRouteBinding.RouteChoice assemblyChoice = assemblyContinuation(first.getLocation().getLocation(), direction, minimum, maximum);
        // Preserve the station's assembly direction first. On a loop, extending backwards
        // first can consume a node ahead of the station and truncate the assembly track.
        CreateRouteBinding.extend(graph, nodes, visited, true, assemblyChoice);
        CreateRouteBinding.extend(graph, nodes, visited, false, assemblyChoice);
        return new CreateRouteBinding(level, graph, nodes, 0, Objects.requireNonNull(firstBogeyWorld));
    }

    public static CreateRouteBinding.RouteChoice assemblyContinuation(Vec3 lineOrigin, Vec3 forward, double minimum, double maximum) {
        return (graph, point, front, candidates) -> {
            Vec3 position = point.node2.getLocation().getLocation();
            double coordinate = position.dot(forward);
            if (front ? coordinate >= maximum - 1e-6 : coordinate <= minimum + 1e-6) return null;
            Vec3 offset = position.subtract(lineOrigin);
            if (offset.subtract(forward.scale(offset.dot(forward))).lengthSqr() > 1e-12) return null;
            Vec3 direction = front ? forward : forward.scale(-1);
            Map.Entry<TrackNode, TrackEdge> selected = null;
            for (var candidate : candidates) {
                TrackEdge edge = candidate.getValue();
                // A curved branch may start with the same tangent; it is not the scanned straight track.
                if (edge.isTurn() || edge.isInterDimensional() || edge.getDirection(true).dot(direction) < 1 - 1e-6) continue;
                if (selected != null) return null;
                selected = candidate;
            }
            return selected;
        };
    }
}
