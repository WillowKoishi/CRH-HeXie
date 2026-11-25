package willow.train.hexie.compat.create.track;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import willow.train.hexie.bridge.railway.RailwayBridge.TrackRouteLocationView;
import willow.train.hexie.bridge.railway.TrackGeometryView;
import willow.train.hexie.bridge.railway.TrackGeometryView.Sample;
import willow.train.hexie.bridge.railway.TrackGeometryView.Vector;
import willow.train.hexie.bridge.railway.TrackRegistryBridge;
import willow.train.hexie.compat.create.signal.CreateRouteBinding;
import willow.train.hexie.core.api.railway.BogeyRailwayRegistry;

// split from CreateRouteBinding.java
public final class CreateRouteGeometry {
    private final CreateRouteBinding routeBinding;

    public CreateRouteGeometry(CreateRouteBinding routeBinding) { this.routeBinding = routeBinding; }

    /** Continuous steering frame for animation; the solved rail contact and route distances stay authoritative. */
    public Sample animationSample(double distance, Sample solved) {
        final TrackRouteLocationView location;
        try {
            location = routeBinding.route.locate(distance);
        } catch (CreateRouteBinding.RouteInvalidException unavailable) {
            // A carrier may still display its last solved pose while the graph is being rebound.
            return solved;
        }
        BoundGeometry segment = routeBinding.segments.get(location.segmentIndex());
        if (segment.surface == null) return solved;
        double parameter = segment.reversed ? 1 - location.parameter() : location.parameter();
        var frame = segment.geometry.animationSample(parameter);
        return new Sample(solved.position(),
                segment.reversed ? frame.tangent().scale(-1) : frame.tangent(), frame.normal(),
                segment.reversed ? -frame.cantRadians() : frame.cantRadians());
    }

    /** Assembly-only nearest projection; returns geometric arc distance, never a Bezier parameter. */
    public double project(Vec3 worldPosition) {
        if (!Double.isFinite(worldPosition.x) || !Double.isFinite(worldPosition.y) || !Double.isFinite(worldPosition.z))
            throw new IllegalArgumentException("Projection position must be finite");
        double bestError = Double.POSITIVE_INFINITY;
        int bestSegment = 0;
        double bestParameter = 0;
        // Create curves are cubic. Bracket their nearest coarse sample, then refine on that segment.
        for (int i = 0; i < routeBinding.segments.size(); i++) {
            BoundGeometry segment = routeBinding.segments.get(i);
            int nearest = 0;
            double error = Double.POSITIVE_INFINITY;
            for (int j = 0; j <= 64; j++) {
                double value = squaredDistance(segment.directedSample(j / 64.0).position(), worldPosition);
                if (value < error) { error = value; nearest = j; }
            }
            double lower = Math.max(0, (nearest - 1) / 64.0);
            double upper = Math.min(1, (nearest + 1) / 64.0);
            for (int j = 0; j < 48; j++) {
                double a = lower + (upper - lower) / 3;
                double b = upper - (upper - lower) / 3;
                if (squaredDistance(segment.directedSample(a).position(), worldPosition)
                        <= squaredDistance(segment.directedSample(b).position(), worldPosition)) upper = b;
                else lower = a;
            }
            double parameter = (lower + upper) * .5;
            double refined = squaredDistance(segment.directedSample(parameter).position(), worldPosition);
            if (refined < error) error = refined;
            else parameter = nearest / 64.0;
            if (error < bestError) { bestError = error; bestSegment = i; bestParameter = parameter; }
        }
        return routeBinding.route.distanceAt(bestSegment, bestParameter);
    }

    public static double squaredDistance(Vector position, Vec3 other) {
        double x = position.x() - other.x, y = position.y() - other.y, z = position.z() - other.z;
        return x * x + y * y + z * z;
    }

    public static final class BoundGeometry implements TrackGeometryView {
        private final TrackGraph graph;
        public final TrackGraph owner;
        public final TrackNode from;
        public final TrackNode to;
        public final TrackEdge edge;
        private final TrackEdge canonicalEdge;
        public final boolean reversed;
        private final CreateTrackGeometry geometry;
        private final TrackRailSurface surface;
        private final TrackRailSurface.Section straightFrom, straightTo;
        private final Map<TrackNode, TrackEdge> fromSmoothingEdges;
        private final Map<TrackNode, TrackEdge> toSmoothingEdges;

        public BoundGeometry(TrackGraph graph, TrackNode from, TrackNode to, int geometryVersion) {
            this.graph = graph;
            this.from = from;
            this.to = to;
            edge = graph.getConnectionsFrom(from).get(to);
            if (edge == null) throw new IllegalArgumentException("Selected directed edge is unavailable");
            owner = CreateTrackCompatibility.physicalGraph(graph, edge);
            TrackEdge opposite = graph.getConnectionsFrom(to).get(from);
            if (opposite == null) throw new IllegalArgumentException("Selected track needs a reverse edge");
            // Create renders primary curves. secondary() recomputes handles and can produce
            // a different curve, so both travel directions must measure that same primary.
            boolean useOpposite = geometryVersion >= 2 && (edge.isTurn() && opposite.isTurn()
                    && edge.getTurn().primary != opposite.getTurn().primary
                    ? opposite.getTurn().primary : orderedAfter(from, to));
            canonicalEdge = useOpposite ? opposite : edge;
            reversed = useOpposite;
            geometry = new CreateTrackGeometry(graph, canonicalEdge, geometryVersion >= 3, geometryVersion >= 4);
            surface = geometryVersion >= 5 && canonicalEdge.isTurn() ? new TrackRailSurface(canonicalEdge.getTurn()) : null;
            if (geometryVersion >= 5 && !canonicalEdge.isTurn()) {
                var joints = geometry.straightSections();
                straightFrom = joints.getFirst();
                straightTo = joints.getSecond();
            } else {
                straightFrom = straightTo = null;
            }
            boolean smoothed = !edge.isTurn()
                    && (from.getLocation().yOffsetPixels != 0 || to.getLocation().yOffsetPixels != 0
                    || geometryVersion >= 5 || geometryVersion >= 4 && CreateTrackCompatibility.straightUsesAdjacentCurves());
            fromSmoothingEdges = smoothed ? Map.copyOf(graph.getConnectionsFrom(from)) : null;
            toSmoothingEdges = smoothed ? Map.copyOf(graph.getConnectionsFrom(to)) : null;
        }

        public boolean valid() {
            return Create.RAILWAYS.trackNetworks.get(owner.id) == owner
                    && CreateTrackCompatibility.containsDirectedEdge(owner, edge)
                    && graph.getConnectionsFrom(from).get(to) == edge
                    && (!reversed || graph.getConnectionsFrom(to).get(from) == canonicalEdge)
                    // Raised Create straights and RailX straights depend on adjacent curve endpoints.
                    && (fromSmoothingEdges == null || (fromSmoothingEdges.equals(graph.getConnectionsFrom(from))
                    && toSmoothingEdges.equals(graph.getConnectionsFrom(to))));
        }

        @Override public double length() { return geometry.length(); }

        @Override public boolean allowsBogey(String modelId) {
            return BogeyRailwayRegistry.allows(modelId, edge.getTrackMaterial().id);
        }

        private static boolean orderedAfter(TrackNode first, TrackNode second) {
            var a = first.getLocation().getLocation();
            var b = second.getLocation().getLocation();
            int order = Double.compare(a.x, b.x);
            if (order == 0) order = Double.compare(a.y, b.y);
            if (order == 0) order = Double.compare(a.z, b.z);
            return order > 0;
        }

        private Sample directedSample(double parameter) {
            return sample(reversed ? 1 - parameter : parameter);
        }

        @Override public int linearSpanCount() { return surface == null ? 0 : surface.count(); }
        @Override public double linearSpanEnd(int index) { return surface.parameter(index + 1); }

        @Override public Sample sample(double parameter) {
            if (!valid()) throw new CreateRouteBinding.RouteInvalidException();
            if (surface != null) return surface.contact(parameter);
            if (straightFrom != null) {
                Vec3 tangent = straightTo.center().subtract(straightFrom.center());
                Vec3 normal = straightFrom.heightAxis().lerp(straightTo.heightAxis(), parameter);
                return TrackRailSurface.contact(straightFrom.center().lerp(straightTo.center(), parameter), tangent, normal);
            }
            Sample sample = geometry.sample(parameter);
            var p = sample.position();
            // Versions 1-4 reproduce the saved frames before migrating.
            return Sample.restoreNormalized(new Vector(p.x(), p.y() + TrackRegistryBridge.RAIL_SURFACE_HEIGHT, p.z()),
                    sample.tangent(), sample.normal(), sample.cantRadians());
        }
    }
}
