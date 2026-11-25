package willow.train.hexie.core.api.bogey;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import willow.train.hexie.core.api.vehicle.Point3;

public record BogeyModel(ResourceLocation id, BogeyGeometry geometry, BogeyParameters operating,
                    Point3 pivot, MountType type, double lateralLimit, double blockCenterHeight) {
    public enum MountType { BOLSTERED, BOLSTERLESS }

    public BogeyModel(ResourceLocation id, BogeyGeometry geometry, BogeyParameters operating) {
        this(id, geometry, operating, new Point3(0, 0, 0));
    }

    /** Legacy direct attachment mode; production registrations should specify the model's type. */
    public BogeyModel(ResourceLocation id, BogeyGeometry geometry, BogeyParameters operating, Point3 pivot) {
        this(id, geometry, operating, pivot, MountType.BOLSTERED, 0);
    }

    /** Existing callers whose track reference already passes through the block centre. */
    public BogeyModel(ResourceLocation id, BogeyGeometry geometry, BogeyParameters operating,
                 Point3 pivot, MountType type, double lateralLimit) {
        this(id, geometry, operating, pivot, type, lateralLimit, 0);
    }

    public BogeyModel {
        Objects.requireNonNull(id);
        Objects.requireNonNull(geometry);
        Objects.requireNonNull(operating);
        Objects.requireNonNull(pivot, "pivot");
        Objects.requireNonNull(type, "type");
        if (!Double.isFinite(lateralLimit) || lateralLimit < 0)
            throw new IllegalArgumentException("Lateral limit must be finite and non-negative");
        if (!Double.isFinite(blockCenterHeight))
            throw new IllegalArgumentException("Block centre height must be finite");
        if (Math.abs(geometry.wheelRadius() - operating.wheelRadiusM()) > 1e-9)
            throw new IllegalArgumentException("Geometry and operating wheel radii disagree");
    }
}
