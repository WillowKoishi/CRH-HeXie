package willow.train.hexie.compat.create;

import java.util.List;
import java.util.UUID;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.GlobalRailwayManager;
import com.simibubi.create.content.trains.entity.CarriageSyncData;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import willow.train.hexie.compat.create.railx.RailxTrackCompatibility;

/** Optional extensions to Create's graph/point contract, confined to this compatibility package. */
public final class CreateTrackCompatibility {
    private CreateTrackCompatibility() {}

    private static boolean railx() { return ModList.get().isLoaded("railx"); }

    public static TrackGraph routeGraph(TrackGraph graph) {
        return graph == null || !railx() ? graph : RailxTrackCompatibility.connected(Create.RAILWAYS, graph);
    }

    static TrackGraph graph(GlobalRailwayManager manager, UUID id) {
        var graph = manager.trackNetworks.get(id);
        return graph == null || !railx() ? graph : RailxTrackCompatibility.connected(manager, graph);
    }

    static boolean currentScope(TrackGraph graph) {
        return !railx() || RailxTrackCompatibility.currentScope(graph);
    }

    static boolean owns(TrackGraph graph, TrackNode node) {
        return railx() ? RailxTrackCompatibility.owns(graph, node) : graph.locateNode(node.getLocation()) == node;
    }

    static boolean sameNetwork(TrackGraph first, TrackGraph second) {
        return first == second || first != null && second != null && railx()
                && RailxTrackCompatibility.sameNetwork(first, second);
    }

    static TrackNode canonical(TrackGraph graph, TrackNode node) {
        return railx() ? RailxTrackCompatibility.canonical(graph, node) : node;
    }

    static TrackGraph physicalGraph(TrackGraph graph, TrackEdge edge) {
        return railx() ? RailxTrackCompatibility.physicalGraph(graph, edge) : graph;
    }

    static Vec3 straightEndpoint(TrackGraph graph, TrackEdge edge, boolean first) {
        return railx() ? RailxTrackCompatibility.straightEndpoint(graph, edge, first)
                : edge.getPosition(graph, first ? 0 : 1);
    }

    static boolean straightUsesAdjacentCurves() { return railx(); }

    static List<UUID> graphIds(TrackGraph graph) {
        return graph == null ? List.of() : railx() ? RailxTrackCompatibility.graphIds(graph) : List.of(graph.id);
    }

    static TrackGraph resolveGraphs(GlobalRailwayManager manager, List<UUID> ids) {
        if (ids.isEmpty()) return null;
        return railx() ? RailxTrackCompatibility.resolve(manager, ids) : manager.trackNetworks.get(ids.getFirst());
    }

    public static TravellingPoint point(TrackGraph graph, TrackNode from, TrackNode to, TrackEdge edge,
                                        double position, boolean upsideDown) {
        var point = new TravellingPoint(from, to, edge, position, upsideDown);
        return railx() ? RailxTrackCompatibility.point(graph, point) : point;
    }

    public static TravellingPoint copy(TrackGraph graph, TravellingPoint source) {
        var result = point(graph, source.node1, source.node2, source.edge, source.position, source.upsideDown);
        result.blocked = source.blocked;
        return result;
    }

    static void copy(TravellingPoint source, TravellingPoint target) {
        target.node1 = source.node1; target.node2 = source.node2; target.edge = source.edge;
        target.position = source.position; target.upsideDown = source.upsideDown; target.blocked = source.blocked;
        if (railx()) RailxTrackCompatibility.copyPointState(source, target);
    }

    static void synchronizeGraph(Train train) {
        if (railx()) RailxTrackCompatibility.synchronizeGraph(train);
    }

    /** Mirror the solved body's roll alongside Create's position and rotation anchors. */
    public static void synchronizePose(Carriage.DimensionalCarriageEntity dimensional, double rollRadians) {
        if (railx()) RailxTrackCompatibility.synchronizePose(dimensional, rollRadians);
    }

    static void prepareClientSync(CarriageSyncData data, TrackGraph graph) {
        if (railx()) RailxTrackCompatibility.prepareClientSync(data, graph);
    }

    static void clientPointsApplied(Carriage carriage, TrackGraph graph) {
        if (railx() && graph != null) RailxTrackCompatibility.clientPointsApplied(carriage, graph);
    }

    public static void restoreTrain(Train source, Train target, CompoundTag tag) {
        if (railx()) RailxTrackCompatibility.restoreTrain(source, target, tag);
    }

    static void transferTrain(net.minecraft.world.level.LevelAccessor level, Train source, Train target) {
        if (railx()) RailxTrackCompatibility.transferTrain(level, source, target);
    }

    /** Absolute runtime route direction and normalized handle input; the cab converter applies its limits. */
    public record ManualInput(double power, double brake, int reverser) {}

    public static ManualInput manualInput(Train train) {
        return railx() ? RailxTrackCompatibility.manualInput(train) : null;
    }
}
