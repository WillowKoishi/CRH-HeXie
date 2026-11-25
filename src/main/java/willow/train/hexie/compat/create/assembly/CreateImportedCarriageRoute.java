package willow.train.hexie.compat.create.assembly;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackGraphHelper;
import com.simibubi.create.content.trains.graph.TrackGraphLocation;
import com.simibubi.create.content.trains.graph.TrackGraphBounds;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.track.ITrackBlock;
import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.BezierTrackPointLocation;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.VehicleBridge.VehicleAssemblyPreparerPreparedView;
import willow.train.hexie.bridge.vehicle.VehicleBridge.VehicleConstraintSolverConstraintFailureView;
import willow.train.hexie.bridge.vehicle.VehicleBridge.VehicleConstraintSolverSolutionView;
import willow.train.hexie.compat.create.motion.SnapshotCarriageEntity;
import willow.train.hexie.compat.create.signal.CreateRouteBinding;
import willow.train.hexie.compat.create.track.CreateTrackCompatibility;
import willow.train.hexie.compat.create.track.CreateTrackGeometry;
import willow.train.hexie.core.api.vehicle.Point3;

// split from CreateRouteBinding.java
public final class CreateImportedCarriageRoute {
    private CreateImportedCarriageRoute() {}

    // Native bounds objects are replaced on graph edits. Weak keys retain neither old graphs nor worlds.
    private static final Map<TrackGraphBounds, Map<Long, List<BezierConnection>>> IMPORT_CURVES = new WeakHashMap<>();

    /**
     * Bind a copied body at its final pasted pose, never to its former graph or a nearby parallel line.
     * The caller has already restored its own structure. No match leaves that structure unbound.
     */
    public static CreateRouteBinding forImportedCarriage(SnapshotCarriageEntity carrier,
                                                 VehicleAssemblyPreparerPreparedView prepared) {
        if (!(carrier.level() instanceof ServerLevel level) || prepared.carriages().size() != 1)
            throw new IllegalArgumentException("Import needs one carriage in a server level");
        var definition = prepared.carriages().getFirst().definition();
        Vec3 origin = carrier.position().add(0, .5, 0);
        Vec3 direction = importedPoint(carrier, new Point3(0, 0, -1)).subtract(origin).normalize();
        var supports = definition.bogeys().stream().map(bogey -> {
            var body = bogey.mount().bodyPoint();
            var track = bogey.mount().trackPivot();
            return importedPoint(carrier, new Point3(body.x() - track.x(), body.y() - track.y(), body.z() - track.z()));
        }).toList();
        var visited = new HashSet<BlockPos>();
        for (Vec3 support : supports) {
            BlockPos near = BlockPos.containing(support);
            // The neighbouring cells cover raised/canted rails and mount offsets at cell edges.
            // Exact pose validation below rejects a neighbour that is a different physical line.
            for (BlockPos mutable : BlockPos.betweenClosed(near.offset(-1, -1, -1), near.offset(1, 1, 1))) {
                BlockPos position = mutable.immutable();
                if (!visited.add(position) || !level.hasChunkAt(position)) continue;
                var state = level.getBlockState(position);
                if (!(state.getBlock() instanceof ITrackBlock track)) continue;
                var axis = track.getNearestTrackAxis(level, position, state, direction);
                var location = TrackGraphHelper.getGraphLocationAt(level, position, axis.getSecond(), axis.getFirst());
                if (location == null) continue;
                TrackGraph graph = CreateTrackCompatibility.routeGraph(location.graph);
                TrackNode first = graph.locateNode(location.edge.getFirst());
                TrackNode second = graph.locateNode(location.edge.getSecond());
                if (first == null || second == null || graph.getConnectionsFrom(first) == null) continue;
                TrackEdge edge = graph.getConnectionsFrom(first).get(second);
                if (edge == null || !CreateRouteBinding.isOwnTrack(edge) || edge.isInterDimensional()) continue;
                var tangent = new CreateTrackGeometry(graph, edge).sample(
                        Math.clamp(location.position / edge.getLength(), 0, 1)).tangent();
                if (Math.abs(tangent.x() * direction.x + tangent.y() * direction.y + tangent.z() * direction.z) < .5)
                    continue;
                CreateRouteBinding candidate = CreateAssemblyRouteBinding.fromAssemblyLocation(level, location, origin, direction, supports);
                var fitted = fitImportedPose(carrier, prepared, candidate, origin, direction);
                if (fitted != null) return fitted;
            }
        }
        // Curve interiors have no ITrackBlock. Reuse native graph bounds, indexed once per
        // bounds revision, so an unbound body does not rescan the railway on each retry.
        for (TrackGraph physical : Create.RAILWAYS.trackNetworks.values()) {
            var indexed = IMPORT_CURVES.computeIfAbsent(physical.getBounds(level), CreateImportedCarriageRoute::indexImportCurves);
            var curves = new HashSet<BezierConnection>();
            for (Vec3 support : supports)
                curves.addAll(indexed.getOrDefault(ChunkPos.asLong(BlockPos.containing(support)), List.of()));
            for (var curve : curves) {
                if (supports.stream().noneMatch(curve.getBounds()::contains)) continue;
                var start = curve.bePositions.getFirst();
                TrackGraphLocation location = level.hasChunkAt(start)
                        ? TrackGraphHelper.getBezierGraphLocationAt(level, start, Direction.AxisDirection.NEGATIVE,
                                new BezierTrackPointLocation(curve.bePositions.getSecond(), 0)) : null;
                if (location == null) {
                    // The graph retains a curve even when its endpoint blocks are unloaded.
                    var firstLocation = new TrackNodeLocation(curve.starts.getFirst()).in(level);
                    var secondLocation = new TrackNodeLocation(curve.starts.getSecond()).in(level);
                    if (curve.smoothing != null) {
                        firstLocation.yOffsetPixels = curve.smoothing.getFirst();
                        secondLocation.yOffsetPixels = curve.smoothing.getSecond();
                    }
                    var first = physical.locateNode(firstLocation);
                    var second = physical.locateNode(secondLocation);
                    if (first == null || second == null) continue;
                    var edge = physical.getConnectionsFrom(first).get(second);
                    if (edge == null || edge.getTurn() != curve || !CreateRouteBinding.isOwnTrack(edge)) continue;
                    location = new TrackGraphLocation();
                    location.graph = physical;
                    location.edge = Couple.create(firstLocation, secondLocation);
                }
                var graph = CreateTrackCompatibility.routeGraph(location.graph);
                var first = graph.locateNode(location.edge.getFirst());
                var second = graph.locateNode(location.edge.getSecond());
                if (first == null || second == null) continue;
                var edge = graph.getConnectionsFrom(first).get(second);
                if (edge == null || !CreateRouteBinding.isOwnTrack(edge)) continue;
                var segment = new CreateRouteBinding(level, graph, List.of(first, second), 0, null);
                location.position = segment.pointAt(segment.project(origin)).position;
                var tangent = new CreateTrackGeometry(graph, edge).sample(
                        Math.clamp(location.position / edge.getLength(), 0, 1)).tangent();
                if (Math.abs(tangent.x() * direction.x + tangent.y() * direction.y + tangent.z() * direction.z) < .5)
                    continue;
                var candidate = CreateAssemblyRouteBinding.fromAssemblyLocation(level, location, origin, direction, supports);
                var fitted = fitImportedPose(carrier, prepared, candidate, origin, direction);
                if (fitted != null) return fitted;
            }
        }
        return null;
    }

    public static Map<Long, List<BezierConnection>> indexImportCurves(TrackGraphBounds bounds) {
        var indexed = new HashMap<Long, List<BezierConnection>>();
        for (var curve : bounds.beziers) {
            var box = curve.getBounds();
            int minX = net.minecraft.util.Mth.floor(box.minX) >> 4, maxX = net.minecraft.util.Mth.floor(box.maxX) >> 4;
            int minZ = net.minecraft.util.Mth.floor(box.minZ) >> 4, maxZ = net.minecraft.util.Mth.floor(box.maxZ) >> 4;
            for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
                indexed.computeIfAbsent(ChunkPos.asLong(x, z), ignored -> new ArrayList<>()).add(curve);
        }
        return indexed;
    }

    /** Convert the runtime's north-facing attachment coordinates to the carrier's source-block frame. */
    public static Vec3 importedPoint(SnapshotCarriageEntity carrier, Point3 point) {
        Vec3 local = switch (carrier.originalForward()) {
            case NORTH -> new Vec3(point.x(), point.y(), point.z());
            case EAST -> new Vec3(-point.z(), point.y(), point.x());
            case SOUTH -> new Vec3(-point.x(), point.y(), -point.z());
            case WEST -> new Vec3(point.z(), point.y(), -point.x());
            default -> throw new IllegalArgumentException("Carriage source direction must be horizontal");
        };
        return carrier.toGlobalVector(local.add(.5, .5, .5), 1);
    }

    public static CreateRouteBinding fitImportedPose(SnapshotCarriageEntity carrier,
            VehicleAssemblyPreparerPreparedView prepared, CreateRouteBinding route, Vec3 origin, Vec3 direction) {
        double previousDistance = Double.NaN, previousError = Double.NaN;
        // Rigid bodies span chords, so their reference mileage is not generally the projection
        // of the body centre on a curve. Correct only this import seed using the existing solver.
        for (int step = 0; step < 12; step++) {
            var placement = importedPlacement(prepared, route);
            if (placement == null) return null;
            if (matchesImportedPose(carrier, prepared, placement)) return route;
            var body = placement.bodies().get(prepared.carriages().getFirst().definition().id()).position();
            double error = new Vec3(body.x(), body.y(), body.z()).subtract(origin).dot(direction);
            if (Math.abs(error) < 1e-9) return null;
            double distance = route.originDistance();
            double adjustment = error;
            if (Double.isFinite(previousError) && Math.abs(error - previousError) > 1e-12)
                adjustment = error * (distance - previousDistance) / (error - previousError);
            double next = distance - adjustment;
            if (!Double.isFinite(next) || next < route.path().minimumDistance()
                    || next > route.path().maximumDistance()) return null;
            previousDistance = distance;
            previousError = error;
            route = new CreateRouteBinding(route, route.nodes, route.segments, route.route, next - distance);
        }
        return null;
    }

    public static VehicleConstraintSolverSolutionView importedPlacement(VehicleAssemblyPreparerPreparedView prepared,
                                                                       CreateRouteBinding route) {
        var imported = prepared.carriages().getFirst();
        var definition = imported.definition();
        double distance = route.originDistance() + imported.initialDistance();
        for (var bogey : definition.bogeys()) {
            double support = distance + bogey.offset() + bogey.mount().trackPivot().z();
            if (support < route.path().minimumDistance() || support > route.path().maximumDistance()) return null;
        }
        var runtime = HexieBridges.vehicle().vehicleRuntime().create(route.path());
        try { runtime.addCarriage(definition, distance, 0); }
        catch (VehicleConstraintSolverConstraintFailureView noPlacement) { return null; }
        return runtime.placement();
    }

    public static boolean matchesImportedPose(SnapshotCarriageEntity carrier,
            VehicleAssemblyPreparerPreparedView prepared, VehicleConstraintSolverSolutionView placement) {
        var definition = prepared.carriages().getFirst().definition();
        var body = placement.bodies().get(definition.id());
        if (body == null) return false;
        // Same geometric tolerance as the normal assembly's original-pose check.
        double toleranceSquared = 1e-8;
        var origin = body.position();
        if (new Vec3(origin.x(), origin.y(), origin.z()).distanceToSqr(carrier.position().add(0, .5, 0)) > toleranceSquared)
            return false;
        for (Point3 axis : List.of(new Point3(1, 0, 0), new Point3(0, 1, 0), new Point3(0, 0, -1))) {
            var point = body.transform(axis);
            if (new Vec3(point.x(), point.y(), point.z()).distanceToSqr(importedPoint(carrier, axis)) > toleranceSquared)
                return false;
        }
        for (var bogey : definition.bogeys()) {
            var support = placement.bogeys().get(bogey.id());
            if (support == null) return false;
            var attachment = support.attachment();
            if (new Vec3(attachment.x(), attachment.y(), attachment.z()).distanceToSqr(
                    importedPoint(carrier, bogey.mount().bodyPoint())) > toleranceSquared) return false;
            if (support.offRailOffset().dot(support.offRailOffset()) > toleranceSquared) return false;
        }
        return true;
    }
}
