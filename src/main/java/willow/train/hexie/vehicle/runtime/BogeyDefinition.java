package willow.train.hexie.vehicle.runtime;

import java.util.Objects;
import java.util.UUID;
import willow.train.hexie.vehicle.bogey.BogeyGeometry;

/** Offset is the body mount's signed forward distance, equal to -mount.bodyPoint().z(). */
public record BogeyDefinition(UUID id, String modelId, double offset, BogeyGeometry geometry,
                              BogeyParameters parameters, BogeyMount mount) {
    public BogeyDefinition(UUID id, String modelId, double offset, BogeyGeometry geometry,
                           BogeyParameters parameters) {
        this(id, modelId, offset, geometry, parameters, new BogeyMount(
                new CouplingEndpoint.Point(0, 0, -offset), new CouplingEndpoint.Point(0, 0, 0)));
    }

    public BogeyDefinition {
        Objects.requireNonNull(id, "id");
        if (modelId == null || modelId.isBlank()) throw new IllegalArgumentException("modelId");
        if (!Double.isFinite(offset)) throw new IllegalArgumentException("offset");
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(mount, "mount");
        if (Math.abs(offset + mount.bodyPoint().z()) > 1e-9)
            throw new IllegalArgumentException("Bogey offset and body mount disagree");
        if (Math.abs(geometry.wheelRadius() - parameters.wheelRadiusM()) > 1e-9)
            throw new IllegalArgumentException("Geometry and operating wheel radii disagree");
    }
}
