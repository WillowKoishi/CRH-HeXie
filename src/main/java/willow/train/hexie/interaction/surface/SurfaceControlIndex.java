package willow.train.hexie.interaction.surface;

import net.minecraft.world.phys.AABB;
import willow.train.hexie.core.api.interaction.ControlSurface;
import willow.train.hexie.interaction.spatial.SurfaceIndex.PartBounds;

/** Geometry supplied by hosts stays in north-facing, full-motion block coordinates. */
final class SurfaceControlIndex {
    static PartBounds build(ControlSurface surface) {
        return new PartBounds(surface.parts().stream().map(ControlSurface.Part::bounds)
                .map(SurfaceControlIndex::bounds).toList());
    }

    private static willow.train.hexie.interaction.spatial.model.AABB bounds(AABB box) {
        if (box == null || !Double.isFinite(box.minX) || !Double.isFinite(box.minY) || !Double.isFinite(box.minZ)
                || !Double.isFinite(box.maxX) || !Double.isFinite(box.maxY) || !Double.isFinite(box.maxZ)) return null;
        // Rounding in slab intersection must keep hits exactly on the shape edge.
        return SurfaceRay.box(box.inflate(1e-9));
    }

    private SurfaceControlIndex() {}
}
