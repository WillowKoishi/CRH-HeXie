package willow.train.hexie.api.railway;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** A railway using HeXie's standard gauge and running rules, with an authored rail-head height. */
public record RailwayDefinition(ResourceLocation blockId, String displayName, Bed bed, RailwayModels models,
                                double railSurfaceHeight) {
    public static final double STANDARD_SURFACE_HEIGHT = 0.75;
    /** BALLAST/SLAB opt into HeXie's beds. NONE leaves bed models and block interactions to the add-on. */
    public enum Bed { BALLAST, SLAB, NONE }

    public RailwayDefinition {
        Objects.requireNonNull(blockId); Objects.requireNonNull(displayName);
        Objects.requireNonNull(bed); Objects.requireNonNull(models);
        if (displayName.isBlank()) throw new IllegalArgumentException("Railway display name must not be blank");
        if (!Double.isFinite(railSurfaceHeight) || railSurfaceHeight < 0)
            throw new IllegalArgumentException("Rail surface height must be finite and non-negative");
        if (bed != Bed.NONE && railSurfaceHeight != STANDARD_SURFACE_HEIGHT)
            throw new IllegalArgumentException("HeXie's built-in beds use the standard rail height; custom heights require an add-on-owned bed");
    }

    public RailwayDefinition(ResourceLocation blockId, String displayName, Bed bed, RailwayModels models) {
        this(blockId, displayName, bed, models, STANDARD_SURFACE_HEIGHT);
    }

    /**
     * An add-on owns its bed models and interactions. HeXie's ballast/slab rendering and
     * material-changing interactions are disabled. Height is in blocks above the track block's base;
     * straight models must already be authored at that height. Curve rails remain relative to the rail head.
     */
    public static RailwayDefinition custom(ResourceLocation blockId, String displayName,
            double railSurfaceHeight, RailwayModels models) {
        return new RailwayDefinition(blockId, displayName, Bed.NONE, models, railSurfaceHeight);
    }

    public static RailwayDefinition standard(ResourceLocation blockId, String displayName, Bed bed) {
        return new RailwayDefinition(blockId, displayName, bed, RailwayModels.standard(bed));
    }
}
