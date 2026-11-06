package willow.train.hexie.compat.create.railx;

import willow.train.hexie.compat.create.TrackRailSurface;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import com.lhwdev.minecraft.railx.RailXConfig;
import com.lhwdev.minecraft.railx.common.carriageTilt.DimensionalCarriageEntityWithTilt;
import com.lhwdev.minecraft.railx.realisticSpeed.ITrainWithRealisticTrainSpeed;
import com.lhwdev.minecraft.railx.splitGraph.AllConnectedTrackGraphs;
import com.lhwdev.minecraft.railx.splitGraph.CarriageSyncDataForSplit;
import com.lhwdev.minecraft.railx.splitGraph.MergedTrackGraph;
import com.lhwdev.minecraft.railx.splitGraph.MergedTrackGraphBase;
import com.lhwdev.minecraft.railx.splitGraph.MergedTrackGraphImpl;
import com.lhwdev.minecraft.railx.splitGraph.MovingTravellingPoint;
import com.lhwdev.minecraft.railx.splitGraph.SplittingTrackNode;
import com.lhwdev.minecraft.railx.splitGraph.TrainForSplit;
import com.lhwdev.minecraft.railx.splitGraph.TravelingPointSplitUtils;
import com.lhwdev.minecraft.railx.throttle.TrainWithThrottle;
import com.lhwdev.minecraft.railx.throttle.ThrottlesServer;
import com.lhwdev.minecraft.railx.throttle.ServerThrottleControl;
import com.lhwdev.minecraft.railx.realisticSpeed.control.RealisticSpeedControl;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.GlobalRailwayManager;
import com.simibubi.create.content.trains.entity.CarriageSyncData;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackNode;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.compat.create.CreateRouteBinding;
import willow.train.hexie.compat.create.CreateTrackCompatibility;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;

/** Loaded only after the RailX mod guard. Does not invoke either mod's train physics. */
public final class RailxTrackCompatibility {
    private RailxTrackCompatibility() {}

    private static List<TrackGraph> members(TrackGraph graph) {
        return graph instanceof MergedTrackGraph merged ? List.copyOf(merged.getGraphs()) : List.of(graph);
    }

    public static TrackGraph connected(GlobalRailwayManager manager, TrackGraph graph) {
        var base = MergedTrackGraphBase.getBase(graph);
        var current = manager.trackNetworks.get(base.id);
        if (current == null) return graph;
        var connected = new AllConnectedTrackGraphs(manager, current);
        return connected.getGraphs().size() == 1 ? current : connected;
    }

    public static boolean valid(TrackGraph graph) {
        for (var member : members(graph)) if (Create.RAILWAYS.trackNetworks.get(member.id) != member) return false;
        return true;
    }

    public static boolean currentScope(TrackGraph graph) {
        return valid(graph) && new LinkedHashSet<>(members(connected(Create.RAILWAYS, graph)))
                .equals(new LinkedHashSet<>(members(graph)));
    }

    public static boolean owns(TrackGraph graph, TrackNode node) {
        return members(graph).stream().anyMatch(member -> member.locateNode(node.getLocation()) == node);
    }

    public static TrackNode canonical(TrackGraph graph, TrackNode node) {
        return node instanceof SplittingTrackNode ? graph.locateNode(node.getLocation()) : node;
    }

    public static boolean sameNetwork(TrackGraph first, TrackGraph second) {
        return new LinkedHashSet<>(members(connected(Create.RAILWAYS, first)))
                .equals(new LinkedHashSet<>(members(connected(Create.RAILWAYS, second))));
    }

    public static List<UUID> graphIds(TrackGraph graph) {
        return members(graph).stream().map(member -> member.id).toList();
    }

    public static TrackGraph resolve(GlobalRailwayManager manager, List<UUID> ids) {
        var graphs = new ArrayList<TrackGraph>();
        for (var id : ids) {
            var graph = manager.trackNetworks.get(id);
            if (graph == null) return null;
            graphs.add(graph);
        }
        return graphs.size() == 1 ? graphs.getFirst() : new MergedTrackGraphImpl(graphs);
    }

    public static TravellingPoint point(TrackGraph graph, TravellingPoint source) {
        var result = new MovingTravellingPoint(source);
        TrackGraph owner = physicalGraph(graph, source.edge);
        result.node1 = owner.locateNode(source.node1.getLocation());
        result.node2 = owner.locateNode(source.node2.getLocation());
        TravelingPointSplitUtils.setDestinationGraph(result, owner);
        return result;
    }

    public static TrackGraph physicalGraph(TrackGraph graph, TrackEdge edge) {
        for (var member : members(graph)) {
            if (CreateTrackCompatibility.containsDirectedEdge(member, edge)) return member;
        }
        throw new CreateRouteBinding.RouteInvalidException();
    }

    public static TrackRailSurface.Section flexibleSection(Vec3 base, Vec3 tangent, Vec3 normal, double along) {
        var direction = new com.lhwdev.minecraft.railx.flexiTrack.FlexiDirection.Two(tangent.normalize(), normal.normalize());
        var rotation = com.lhwdev.minecraft.railx.flexiTrack.rotate.RotationUtils.rotationValue(direction);
        Vec3 forward = vector(rotation.transform(new org.joml.Vector3d(1, 0, 0)));
        Vec3 side = vector(rotation.transform(new org.joml.Vector3d(0, 0, -1)));
        Vec3 up = vector(rotation.transform(new org.joml.Vector3d(0, 1, 0)));
        return TrackRailSurface.rotatedSection(base, forward, side, up, along);
    }

    private static Vec3 vector(org.joml.Vector3d v) { return new Vec3(v.x, v.y, v.z); }

    /** Node locations identify topology at 1/128 precision; curve starts retain the real joint. */
    public static Vec3 straightEndpoint(TrackGraph graph, TrackEdge edge, boolean first) {
        var owner = physicalGraph(graph, edge);
        TrackNode node = owner.locateNode((first ? edge.node1 : edge.node2).getLocation());
        Vec3 endpoint = null;
        var candidates = new ArrayList<Vec3>();
        for (var adjacent : graph.getConnectionsFrom(node).entrySet()) {
            var curve = adjacent.getValue();
            if (!curve.isTurn()) continue;
            var reverse = graph.getConnectionsFrom(adjacent.getKey()).get(node);
            // Match the primary curve used by CreateRouteBinding in either travel direction.
            Vec3 candidate = !curve.getTurn().primary && reverse != null && reverse.isTurn()
                    && reverse.getTurn().primary ? reverse.getTurn().starts.getSecond()
                    : curve.getTurn().starts.getFirst();
            candidates.add(candidate);
            if (endpoint == null || comparePosition(candidate, endpoint) < 0) endpoint = candidate;
        }
        // Distinct real joints can quantize to one graph node. Validate against the final
        // candidate so neither geometry nor ambiguity depends on map iteration order.
        for (var candidate : candidates)
            if (endpoint.distanceToSqr(candidate) > 1e-12)
                throw new CreateRouteBinding.RouteInvalidException();
        return endpoint == null ? edge.getPosition(graph, first ? 0 : 1) : endpoint;
    }

    private static int comparePosition(Vec3 first, Vec3 second) {
        int order = Double.compare(first.x, second.x);
        if (order == 0) order = Double.compare(first.y, second.y);
        return order == 0 ? Double.compare(first.z, second.z) : order;
    }

    public static void copyPointState(TravellingPoint source, TravellingPoint target) {
        TravelingPointSplitUtils.setDestinationGraph(target, TravelingPointSplitUtils.getDestinationGraph(source));
    }

    /** Mirror occupied graph ownership after our solver updates points, without Train.tick motion. */
    public static void synchronizeGraph(Train train) {
        if (train.graph == null) return;
        var graphs = new LinkedHashSet<TrackGraph>();
        for (var carriage : train.carriages) carriage.bogeys.forEach(bogey -> {
            if (bogey != null) java.util.List.of(bogey.leading(), bogey.trailing()).forEach(point -> {
                var graph = TravelingPointSplitUtils.getDestinationGraph(point);
                if (graph != null) graphs.add(graph);
            });
        });
        if (graphs.isEmpty()) return;
        TrackGraph graph = graphs.size() == 1 ? graphs.iterator().next() : new MergedTrackGraphImpl(graphs);
        ((TrainForSplit) train).railx$replaceGraphPreserving(train.graph, graph);
    }

    public static void prepareClientSync(CarriageSyncData data, TrackGraph graph) {
        ((CarriageSyncDataForSplit) data).railx$setConnectedGraph(graph);
    }

    public static void synchronizePose(Carriage.DimensionalCarriageEntity dimensional, double rollRadians) {
        // RailX normally fills these in updateContraptionAnchors. Our solver owns the body
        // pose, so supply its roll directly before RailX's alignEntity hook consumes it.
        var tilt = ((DimensionalCarriageEntityWithTilt) dimensional).getRailx$tilt();
        double rollDegrees = Math.toDegrees(rollRadians);
        tilt.setFirst(rollDegrees);
        tilt.setSecond(rollDegrees);
    }

    public static void clientPointsApplied(Carriage carriage, TrackGraph graph) {
        carriage.bogeys.forEach(bogey -> {
            if (bogey != null) java.util.List.of(bogey.leading(), bogey.trailing()).forEach(point -> {
                if (point.edge == null) return;
                var resolved = point(graph, point);
                copyPointState(resolved, point);
            });
        });
    }

    public static void restoreTrain(Train source, Train target, CompoundTag tag) {
        ((TrainWithThrottle) target).setRailx$throttle(((TrainWithThrottle) source).getRailx$throttle());
        var realistic = ((ITrainWithRealisticTrainSpeed) target).getRailx$realisticSpeed();
        if (realistic != null && tag.contains("railx:RealisticSpeed")) realistic.read(tag.getCompound("railx:RealisticSpeed"));
        ((TrainForSplit) target).railx$replaceGraphPreserving(target.graph, target.graph);
    }

    /** Replacing a native proxy preserves input values, never the old train-owned helper objects. */
    public static void transferTrain(net.minecraft.world.level.LevelAccessor level, Train source, Train target) {
        ((TrainWithThrottle) target).setRailx$throttle(((TrainWithThrottle) source).getRailx$throttle());
        if (source.id.equals(target.id)) {
            var previous = ((ITrainWithRealisticTrainSpeed) source).getRailx$realisticSpeed();
            var next = ((ITrainWithRealisticTrainSpeed) target).getRailx$realisticSpeed();
            var saved = previous == null ? null : previous.write();
            if (next != null && saved != null) next.read(saved);
        }
        // A regrouped consist gets fresh RailX mass properties; its independent dynamics are
        // already held by VehicleRuntime. The graph tracker always belongs to the new proxy.
        ((TrainForSplit) target).railx$replaceGraphPreserving(target.graph, target.graph);
        var controls = ThrottlesServer.INSTANCE.getReceivedControls().get(level).getByPlayer();
        controls.replaceAll((player, previous) -> {
            if (previous.getTrain() != source || previous.getEntity().getCarriage().train != target) return previous;
            var next = new ServerThrottleControl(previous.getPlayerId(), previous.getEntity(), previous.getControlsPos());
            next.setThrottle(previous.getThrottle());
            next.getKeys().addAll(previous.getKeys());
            return next;
        });
        // This cache only feeds RailX's HUD. Recreate it on its own next tick against the
        // transferred input context/current carriage, instead of retaining an obsolete Train.
        var displays = RealisticSpeedControl.INSTANCE.getControls().get(level);
        displays.getByPlayer().values().removeIf(control -> control.getTrain() == source);
        displays.getByTrain().values().removeIf(control -> control.getTrain() == source);
    }

    public static void clearManualInput(SnapshotCarriageEntity carriage, BlockPos pos, UUID player) {
        var controls = ThrottlesServer.INSTANCE.getReceivedControls().get(carriage.level());
        var input = controls.getByPlayer().get(player);
        if (input != null && input.getEntity() == carriage && pos.equals(input.getControlsPos()))
            controls.removeControl(input);
    }

    public static CreateTrackCompatibility.ManualInput manualInput(Train train) {
        if (RailXConfig.Server.Value.getThrottle().getEnabled().isFalse()) return null;
        var throttle = ((TrainWithThrottle) train).getRailx$throttle();
        double amount = Math.clamp(throttle.getAcceleration(), -1, 1);
        // RailX's absolute reverser follows Create's control-facing convention. Snapshot carriers
        // expose initialOrientation = assemblyForward.clockwise, while positive runtime travel
        // is opposite the operator-facing ControlsBlock when it faces assemblyForward.
        return new CreateTrackCompatibility.ManualInput(Math.max(0, amount),
                Math.max(0, -amount), -throttle.getReverser().getStep());
    }
}
