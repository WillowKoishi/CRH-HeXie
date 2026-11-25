package willow.train.hexie.railway.engine;

import java.util.List;
import willow.train.hexie.core.api.vehicle.Point3;

/** Ordered polyline samples run from fromNode to toNode in world metres. */
public record EdgeGeometry(long edgeId, long fromNode, long toNode, long geometryRevision, List<Point3> samples) {
    public EdgeGeometry {
        samples = List.copyOf(samples);
        if (samples.size() < 2) {
            throw new IllegalArgumentException("An edge needs at least two samples");
        }
    }
}
