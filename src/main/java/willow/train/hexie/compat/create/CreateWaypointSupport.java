package willow.train.hexie.compat.create;

import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.Navigation;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.createmod.catnip.data.Couple;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;

/** Optional Steam 'n' Rails instruction, identified by its public registry ID. */
public final class CreateWaypointSupport {
    static final double DISPLAY_DISTANCE = 16000;
    private static final ResourceLocation WAYPOINT = ResourceLocation.fromNamespaceAndPath("railways", "waypoint_destination");
    private CreateWaypointSupport() {}

    static boolean currentInstruction(Train train) {
        var schedule = train.runtime.getSchedule();
        int index = train.runtime.currentEntry;
        return schedule != null && index >= 0 && index < schedule.entries.size()
                && WAYPOINT.equals(schedule.entries.get(index).instruction.getId());
    }

    static boolean active(Train train) {
        return !train.manualTick && !train.runtime.paused && !train.runtime.completed && currentInstruction(train);
    }

    /** Future station instructions, ending at the next stopping station; never advances the schedule. */
    static List<DestinationInstruction> followingDestinations(Train train) {
        var schedule = train.runtime.getSchedule();
        if (schedule == null || schedule.entries.isEmpty()) return List.of();
        int start = train.runtime.currentEntry;
        if (start < 0 || start >= schedule.entries.size()) return List.of();
        var result = new ArrayList<DestinationInstruction>();
        for (int offset = 1; offset <= schedule.entries.size(); offset++) {
            int index = start + offset;
            if (index >= schedule.entries.size()) {
                if (!schedule.cyclic) break;
                index %= schedule.entries.size();
            }
            var instruction = schedule.entries.get(index).instruction;
            if (!(instruction instanceof DestinationInstruction destination)) continue;
            result.add(destination);
            if (!WAYPOINT.equals(instruction.getId())) break;
        }
        return List.copyOf(result);
    }

    static String nextStationLabel(Train train) {
        for (var instruction : followingDestinations(train))
            if (!WAYPOINT.equals(instruction.getId())) return instruction.getFilter();
        return "";
    }

    /** Only the disposable preview Navigation gets a different origin; live navigation is untouched. */
    public static TravellingPoint previewPoint(Navigation navigation) {
        return navigation instanceof PreviewNavigation preview ? preview.origin : null;
    }

    public interface PathAccess {
        void hexie$previewPath(List<Couple<TrackNode>> path);
    }

    private static final class PreviewNavigation extends Navigation {
        private final TravellingPoint origin;
        PreviewNavigation(Train train, TravellingPoint origin, GlobalStation from, int direction) {
            super(train);
            this.origin = origin;
            destination = from;
            destinationBehindTrain = direction < 0;
            ticksWaitingForSignal = train.navigation.ticksWaitingForSignal;
        }
    }

    /** Each leg keeps a native copied switch itinerary, including all required intermediate waypoints. */
    private record Leg(TravellingPoint origin, Navigation navigation, GlobalStation destination,
                       double distance, boolean reached) {}

    record Preview(TrackGraph graph, int direction, List<Leg> legs, GlobalStation station, double stationDistance,
                   boolean blocked) {
        double observedDistance() { return legs.stream().mapToDouble(Leg::distance).sum(); }
        void traverse(double maximumDistance, TravellingPoint.IEdgePointListener listener) {
            double offset = 0;
            var seen = new java.util.IdentityHashMap<com.simibubi.create.content.trains.signal.TrackEdgePoint, Double>();
            for (var leg : legs) {
                if (offset > maximumDistance) break;
                double base = offset;
                boolean[] stopped = {false};
                var point = CreateTrackCompatibility.copy(graph, leg.origin());
                // Short curve travel applies a local Bezier correction, whereas observed edge-point
                // distances use parameter * edge length. A longer scout request reaches the exact
                // observed endpoint; the listener, not that approximation, bounds the observation.
                point.travel(graph, Math.max(6, Math.min(leg.distance(), maximumDistance - offset) + .001) * direction,
                        leg.navigation().controlSignalScout(), (distance, event) -> {
                            if (base + distance > maximumDistance || distance > leg.distance() + 1e-7) return true;
                            var previous = seen.put(event.getFirst(), base + distance);
                            if (previous != null && Math.abs(previous - base - distance) < 1e-7)
                                return event.getFirst() == leg.destination();
                            if (listener.test(base + distance, event)) return stopped[0] = true;
                            return event.getFirst() == leg.destination();
                        }, point.ignoreTurns());
                if (stopped[0] || !leg.reached() || offset + leg.distance() >= maximumDistance) break;
                offset += leg.distance();
            }
        }
    }

    /** Read-only native searches through passing stations to the next scheduled stopping station. */
    static Preview preview(Train train) {
        try { return buildPreview(train); }
        catch (CreateRouteBinding.RouteInvalidException | IllegalArgumentException unavailable) { return null; }
    }

    private static Preview buildPreview(Train train) {
        if (!currentInstruction(train) || train.graph == null) return null;
        var graph = CreateTrackCompatibility.routeGraph(train.graph);
        int direction = train.speed != 0 ? (train.speed > 0 ? 1 : -1)
                : train.navigation.destination != null ? (train.navigation.destinationBehindTrain ? -1 : 1)
                : train.currentlyBackwards ? -1 : 1;
        var leading = direction > 0 ? train.carriages.getFirst().getLeadingPoint() : train.carriages.getLast().getTrailingPoint();
        if (leading.edge == null) return null;
        var point = CreateTrackCompatibility.copy(graph, leading);
        var legs = new ArrayList<Leg>();
        var navigation = train.navigation;
        if (navigation.destination == null || navigation.destinationBehindTrain != (direction < 0)) {
            var instruction = train.runtime.getSchedule().entries.get(train.runtime.currentEntry).instruction;
            if (!(instruction instanceof DestinationInstruction destination)) return null;
            navigation = findNext(train, graph, point, null, direction, destination);
            if (navigation == null) return null;
        }
        double total = 0;
        GlobalStation station = null;
        var following = followingDestinations(train);
        int next = 0;
        while (total < DISPLAY_DISTANCE) {
            var origin = CreateTrackCompatibility.copy(graph, point);
            var destination = navigation.destination;
            double[] observed = {-1};
            // Native remaining distance is an estimate updated by vehicle travel; our arc-length
            // movement and Create's curve parameter can differ. Follow the retained switch path
            // until the station event itself, rather than truncating at that estimate.
            double requested = DISPLAY_DISTANCE - total;
            double travelled = Math.abs(point.travel(graph, Math.max(6, requested + .001) * direction,
                    navigation.controlSignalScout(), (distance, event) -> {
                        if (distance > requested) return true;
                        if (event.getFirst() != destination || !destination.canApproachFrom(event.getSecond().getSecond())) return false;
                        observed[0] = distance;
                        return true;
                    }, point.ignoreTurns()));
            boolean reached = observed[0] >= 0 && observed[0] <= DISPLAY_DISTANCE - total;
            double distance = reached ? observed[0] : Math.min(travelled, DISPLAY_DISTANCE - total);
            legs.add(new Leg(origin, navigation, destination, distance, reached));
            total += distance;
            if (!reached) break;
            if (next > 0 && !WAYPOINT.equals(following.get(next - 1).getId())) { station = destination; break; }
            if (next >= following.size()) break;
            navigation = findNext(train, graph, point, destination, direction, following.get(next++));
            if (navigation == null) break;
        }
        return new Preview(graph, direction, List.copyOf(legs), station, station == null ? -1 : total, point.blocked);
    }

    private static PreviewNavigation findNext(Train train, TrackGraph graph, TravellingPoint origin,
            GlobalStation from, int direction, DestinationInstruction instruction) {
        var candidates = new ArrayList<GlobalStation>();
        String filter = instruction.getFilterForRegex();
        for (var candidate : graph.getPoints(EdgePointType.STATION))
            if (candidate.name.matches(filter)) candidates.add(candidate);
        if (candidates.isEmpty()) return null;
        // A real candidate supplies Navigation's direction guard; no reservation or startNavigation is performed.
        var preview = new PreviewNavigation(train, CreateTrackCompatibility.copy(graph, origin),
                from == null ? candidates.getFirst() : from, direction);
        var path = preview.findPathTo(candidates, Double.MAX_VALUE);
        if (path == null || !Double.isFinite(path.distance) || path.distance != 0 && Math.signum(path.distance) != direction) return null;
        preview.destination = path.destination;
        preview.distanceToDestination = Math.abs(path.distance);
        ((PathAccess) (Object) preview).hexie$previewPath(new ArrayList<>(path.path));
        return preview;
    }
}
