package willow.train.hexie.compat.create;

import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.init.track.TrackMaterialInit;
import willow.train.hexie.vehicle.track.TrackGeometry;

import java.util.Objects;

/** Read on the owning game thread; resolve again after a graph edit. Never owns Create's graph. */
public final class CreateTrackGeometry implements TrackGeometry {
    private final TrackGraph graph;
    private final TrackEdge edge;

    public CreateTrackGeometry(TrackGraph graph, TrackEdge edge) {
        this.graph = Objects.requireNonNull(graph);
        this.edge = Objects.requireNonNull(edge);
        // Accept exactly our registered materials, not Create STANDARD or a third-party track.
        var material = edge.getTrackMaterial();
        if (material != TrackMaterialInit.STANDARD && material != TrackMaterialInit.BALLASTLESS
                && material != TrackMaterialInit.TIELESS)
            throw new IllegalArgumentException("Not a HeXie track edge");
        if (edge.isInterDimensional() || !Double.isFinite(edge.getLength()) || edge.getLength() <= 0)
            throw new IllegalArgumentException("A spatial edge with positive length is required");
    }

    @Override
    public double length() { return edge.getLength(); }

    @Override
    public Sample sample(double parameter) {
        if (!Double.isFinite(parameter) || parameter < 0 || parameter > 1)
            throw new IllegalArgumentException("Track parameter must be between 0 and 1");
        double step = Math.min(0.001, 0.01 / length());
        Vec3 before = edge.getPosition(graph, Math.max(0, parameter - step));
        Vec3 after = edge.getPosition(graph, Math.min(1, parameter + step));
        // No cant is introduced into existing tracks. No render-height offset is applied here.
        return new Sample(vector(edge.getPosition(graph, parameter)), vector(after.subtract(before)),
                vector(edge.getNormal(graph, parameter)), 0);
    }

    private static Vector vector(Vec3 value) { return new Vector(value.x, value.y, value.z); }
}
