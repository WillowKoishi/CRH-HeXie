package willow.train.hexie.api.railway;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * Model resource IDs, without the models/ prefix or .json suffix. HeXie translates these
 * authored shapes into its active railway backend's block states at resource reload.
 * Add-ons supply models/textures, never backend-specific blockstate JSON.
 */
public record RailwayModels(Map<Shape, ResourceLocation> blocks, ResourceLocation sleeper,
                            ResourceLocation leftRail, ResourceLocation rightRail,
                            ResourceLocation particle, ResourceLocation itemTexture) {
    /** Model orientation follows HeXie's supplied reference geometry; a slope rises south. */
    public enum Shape {
        EAST_WEST, NORTH_SOUTH, DIAGONAL_POSITIVE, DIAGONAL_NEGATIVE, SLOPE, PORTAL,
        CROSS_ORTHOGONAL, CROSS_DIAGONAL, CROSS_POSITIVE_EAST_WEST, CROSS_POSITIVE_NORTH_SOUTH,
        CROSS_NEGATIVE_EAST_WEST, CROSS_NEGATIVE_NORTH_SOUTH
    }

    public RailwayModels {
        blocks = Map.copyOf(blocks);
        for (var shape : Shape.values())
            if (!blocks.containsKey(shape)) throw new IllegalArgumentException("Missing railway model: " + shape);
        Objects.requireNonNull(sleeper); Objects.requireNonNull(leftRail); Objects.requireNonNull(rightRail);
        Objects.requireNonNull(particle); Objects.requireNonNull(itemTexture);
    }

    public RailwayModels withBlock(Shape shape, ResourceLocation model) {
        var next = new EnumMap<Shape, ResourceLocation>(Shape.class);
        next.putAll(blocks); next.put(Objects.requireNonNull(shape), Objects.requireNonNull(model));
        return new RailwayModels(next, sleeper, leftRail, rightRail, particle, itemTexture);
    }

    public RailwayModels withCurve(ResourceLocation sleeper, ResourceLocation leftRail, ResourceLocation rightRail) {
        return new RailwayModels(blocks, sleeper, leftRail, rightRail, particle, itemTexture);
    }

    public RailwayModels withParticle(ResourceLocation particle) {
        return new RailwayModels(blocks, sleeper, leftRail, rightRail, particle, itemTexture);
    }

    public RailwayModels withItemTexture(ResourceLocation texture) {
        return new RailwayModels(blocks, sleeper, leftRail, rightRail, particle, texture);
    }

    /** Existing HeXie model assets are the reference geometry and remain valid resource aliases. */
    public static RailwayModels standard(RailwayDefinition.Bed bed) {
        Objects.requireNonNull(bed);
        var blocks = new EnumMap<Shape, ResourceLocation>(Shape.class);
        String material = switch (bed) { case BALLAST -> "standard"; case SLAB -> "ballastless"; case NONE -> "tieless"; };
        for (var shape : Shape.values()) {
            String name = switch (shape) {
                case EAST_WEST -> "x_ortho"; case NORTH_SOUTH -> "z_ortho";
                case DIAGONAL_POSITIVE -> "diag"; case DIAGONAL_NEGATIVE -> "diag_2";
                case SLOPE -> "ascending"; case PORTAL -> "teleport";
                case CROSS_ORTHOGONAL -> "cross_ortho"; case CROSS_DIAGONAL -> "cross_diag";
                case CROSS_POSITIVE_EAST_WEST -> "cross_d1_xo"; case CROSS_POSITIVE_NORTH_SOUTH -> "cross_d1_zo";
                case CROSS_NEGATIVE_EAST_WEST -> "cross_d2_xo"; case CROSS_NEGATIVE_NORTH_SOUTH -> "cross_d2_zo";
            };
            boolean own = switch (shape) {
                case CROSS_ORTHOGONAL, CROSS_DIAGONAL -> false;
                case CROSS_POSITIVE_EAST_WEST, CROSS_POSITIVE_NORTH_SOUTH,
                     CROSS_NEGATIVE_EAST_WEST, CROSS_NEGATIVE_NORTH_SOUTH -> bed == RailwayDefinition.Bed.NONE;
                default -> true;
            };
            blocks.put(shape, hexie("block/track/" + (own ? material : "standard") + "/" + name));
        }
        return new RailwayModels(blocks,
                bed == RailwayDefinition.Bed.NONE ? hexie("block/track/standard/vacuum") : hexie("block/track/" + material + "/tie"),
                hexie("block/track/standard/segment_left"), hexie("block/track/standard/segment_right"),
                hexie("block/railway/default_particle"),
                hexie("item/track/" + material + "_track"));
    }

    private static ResourceLocation hexie(String path) { return ResourceLocation.fromNamespaceAndPath("hexie", path); }
}
