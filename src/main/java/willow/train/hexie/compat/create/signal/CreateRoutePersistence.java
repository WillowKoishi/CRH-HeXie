package willow.train.hexie.compat.create.signal;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import willow.train.hexie.compat.create.track.CreateTrackCompatibility;

// split from CreateRouteBinding.java
public final class CreateRoutePersistence {
    private final CreateRouteBinding routeBinding;

    public CreateRoutePersistence(CreateRouteBinding routeBinding) { this.routeBinding = routeBinding; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", routeBinding.geometryVersion);
        tag.putUUID("Graph", routeBinding.segments.getFirst().owner.id);
        ListTag graphs = new ListTag();
        for (var id : routeBinding.segments.stream().map(segment -> segment.owner.id).distinct().toList()) {
            var entry = new CompoundTag(); entry.putUUID("Id", id); graphs.add(entry);
        }
        tag.put("Graphs", graphs);
        tag.putString("Dimension", routeBinding.level.dimension().location().toString());
        tag.putDouble("Origin", routeBinding.originDistance);
        ListTag savedNodes = new ListTag();
        for (TrackNode node : routeBinding.nodes) savedNodes.add(node.getLocation().write(null));
        tag.put("Nodes", savedNodes);
        return tag;
    }

    public static CreateRouteBinding load(ServerLevel level, CompoundTag tag) {
        if (tag.getInt("Version") < 1 || tag.getInt("Version") > CreateRouteBinding.GEOMETRY_VERSION || !tag.hasUUID("Graph") || !tag.contains("Origin", Tag.TAG_DOUBLE)
                || !level.dimension().location().toString().equals(tag.getString("Dimension")))
            throw new IllegalArgumentException("Invalid saved route binding");
        ListTag saved = tag.getList("Nodes", Tag.TAG_COMPOUND);
        if (saved.size() < 2 || saved.size() > CreateRouteBinding.MAX_EDGES + 1)
            throw new IllegalArgumentException("Invalid saved route size");
        List<TrackNodeLocation> nodes = new ArrayList<>(saved.size());
        for (int i = 0; i < saved.size(); i++) {
            CompoundTag node = saved.getCompound(i);
            if (!node.contains("Pos")) throw new IllegalArgumentException("Missing saved node coordinates");
            nodes.add(TrackNodeLocation.read(node, null).in(level));
        }
        var savedGraphs = tag.getList("Graphs", Tag.TAG_COMPOUND);
        var graphIds = new ArrayList<UUID>();
        for (var item : savedGraphs) {
            var entry = (CompoundTag) item;
            if (!entry.hasUUID("Id")) throw new IllegalArgumentException("Invalid saved graph identity");
            graphIds.add(entry.getUUID("Id"));
        }
        if (graphIds.isEmpty()) graphIds.add(tag.getUUID("Graph"));
        var graph = CreateTrackCompatibility.resolveGraphs(Create.RAILWAYS, graphIds);
        if (graph == null) throw new IllegalArgumentException("Saved track graph is unavailable");
        graph = CreateTrackCompatibility.routeGraph(graph);
        var resolved = new ArrayList<TrackNode>(nodes.size());
        for (var location : nodes) {
            var node = graph.locateNode(location);
            if (node == null) throw new IllegalArgumentException("Saved route node is no longer in the graph");
            resolved.add(node);
        }
        return new CreateRouteBinding(level, graph, resolved, tag.getDouble("Origin"), null, tag.getInt("Version"));
    }
}
