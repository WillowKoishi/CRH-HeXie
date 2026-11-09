package willow.train.hexie.client.interaction;

import willow.train.hexie.api.interaction.ControlSurface;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Immutable broad phase over full-motion part bounds, built once with the surface. */
final class SurfaceControlIndex {
    private record Entry(int index, AABB bounds) {}
    private final Node root;
    private final BitSet fallback;

    SurfaceControlIndex(List<ControlSurface.Part> parts) {
        var bounded = new ArrayList<Entry>();
        fallback = new BitSet();
        for (int i = 0; i < parts.size(); i++) {
            var bounds = parts.get(i).bounds();
            if (bounds == null || !finite(bounds)) fallback.set(i);
            // Keep exact shape edges inside despite rounding differences in the slab calculation.
            else bounded.add(new Entry(i, bounds.inflate(1e-9)));
        }
        root = build(bounded, 0, bounded.size());
    }

    /** A BitSet retains declaration order for the exact nearest-hit and equal-distance rules. */
    BitSet query(Vec3 from, Vec3 ray, double reach) {
        var result = (BitSet) fallback.clone();
        if (root != null) root.collect(from, ray, reach, result);
        return result;
    }

    private static Node build(List<Entry> entries, int start, int end) {
        if (start == end) return null;
        AABB bounds = entries.get(start).bounds();
        for (int i = start + 1; i < end; i++) bounds = bounds.minmax(entries.get(i).bounds());
        if (end - start == 1) return new Node(bounds, entries.get(start).index(), null, null);
        double x = bounds.getXsize(), y = bounds.getYsize(), z = bounds.getZsize();
        int axis = x >= y && x >= z ? 0 : y >= z ? 1 : 2;
        entries.subList(start, end).sort(Comparator.comparingDouble(entry -> center(entry.bounds(), axis)));
        int middle = start + (end - start) / 2;
        return new Node(bounds, -1, build(entries, start, middle), build(entries, middle, end));
    }

    private static double center(AABB bounds, int axis) {
        return switch (axis) {
            case 0 -> bounds.minX * .5 + bounds.maxX * .5;
            case 1 -> bounds.minY * .5 + bounds.maxY * .5;
            default -> bounds.minZ * .5 + bounds.maxZ * .5;
        };
    }

    private record Node(AABB bounds, int index, Node left, Node right) {
        void collect(Vec3 from, Vec3 ray, double reach, BitSet result) {
            if (!intersects(bounds, from, ray, reach)) return;
            if (index >= 0) result.set(index);
            else {
                left.collect(from, ray, reach, result);
                right.collect(from, ray, reach, result);
            }
        }
    }

    /** Closed slabs include a ray starting inside and intersections with zero-thickness buttons. */
    private static boolean intersects(AABB bounds, Vec3 from, Vec3 ray, double reach) {
        double near = 0, far = reach;
        if (ray.x == 0) {
            if (from.x < bounds.minX || from.x > bounds.maxX) return false;
        } else {
            double a = (bounds.minX - from.x) / ray.x, b = (bounds.maxX - from.x) / ray.x;
            near = Math.max(near, Math.min(a, b)); far = Math.min(far, Math.max(a, b));
            if (near > far) return false;
        }
        if (ray.y == 0) {
            if (from.y < bounds.minY || from.y > bounds.maxY) return false;
        } else {
            double a = (bounds.minY - from.y) / ray.y, b = (bounds.maxY - from.y) / ray.y;
            near = Math.max(near, Math.min(a, b)); far = Math.min(far, Math.max(a, b));
            if (near > far) return false;
        }
        if (ray.z == 0) {
            if (from.z < bounds.minZ || from.z > bounds.maxZ) return false;
        } else {
            double a = (bounds.minZ - from.z) / ray.z, b = (bounds.maxZ - from.z) / ray.z;
            near = Math.max(near, Math.min(a, b)); far = Math.min(far, Math.max(a, b));
            if (near > far) return false;
        }
        return true;
    }

    private static boolean finite(AABB bounds) {
        return Double.isFinite(bounds.minX) && Double.isFinite(bounds.minY) && Double.isFinite(bounds.minZ)
                && Double.isFinite(bounds.maxX) && Double.isFinite(bounds.maxY) && Double.isFinite(bounds.maxZ);
    }
}

