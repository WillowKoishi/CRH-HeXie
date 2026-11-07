package willow.train.hexie.api.interaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Broad-phase owners for one BlockGetter or one container-local coordinate system.
 * The holder owns that coordinate system: a world, a plot and a contraption must use
 * separate instances, and must transform rays before querying a container's index.
 *
 * <p>Registration covers every cell touched by an owner's conservative bounds. It
 * belongs to block/container load and geometry changes, never the picking loop.
 * Queries visit only cells crossed by the finite ray; callers precisely clip the
 * returned owners and compare hit distances with ordinary blocks.</p>
 */
public final class InteractionSpatialIndex {
    private static final long MAX_CELLS_PER_OWNER = 65_536;
    private static final double CROSSING_EPSILON = 1e-12;

    private final Map<BlockPos, Entry> owners = new HashMap<>();
    private final Map<BlockPos, Set<BlockPos>> cells = new HashMap<>();

    /** Bounds and owner must both be expressed in this index's coordinate system. */
    public void put(BlockPos owner, AABB bounds) {
        requireFinite(bounds.minX, bounds.minY, bounds.minZ);
        requireFinite(bounds.maxX, bounds.maxY, bounds.maxZ);
        int minX = cell(bounds.minX), minY = cell(bounds.minY), minZ = cell(bounds.minZ);
        int maxX = cell(bounds.maxX), maxY = cell(bounds.maxY), maxZ = cell(bounds.maxZ);
        long width = (long) maxX - minX + 1;
        long height = (long) maxY - minY + 1;
        long depth = (long) maxZ - minZ + 1;
        // Reject malformed/unsuitable geometry before replacing the previous entry.
        if (width > MAX_CELLS_PER_OWNER || height > MAX_CELLS_PER_OWNER || depth > MAX_CELLS_PER_OWNER
                || width * height > MAX_CELLS_PER_OWNER || width * height * depth > MAX_CELLS_PER_OWNER)
            throw new IllegalArgumentException("Interaction bounds cover too many cells");
        BlockPos immutableOwner = owner.immutable();
        Entry entry = new Entry(minX, minY, minZ, maxX, maxY, maxZ);
        if (entry.equals(owners.get(immutableOwner))) return;
        remove(immutableOwner);
        owners.put(immutableOwner, entry);
        entry.forEach(position -> cells.computeIfAbsent(position, ignored -> new LinkedHashSet<>()).add(immutableOwner));
    }

    public void remove(BlockPos owner) {
        Entry entry = owners.remove(owner);
        if (entry == null) return;
        entry.forEach(position -> {
            Set<BlockPos> bucket = cells.get(position);
            if (bucket == null) return;
            bucket.remove(owner);
            if (bucket.isEmpty()) cells.remove(position);
        });
    }

    public void clear() {
        owners.clear();
        cells.clear();
    }

    public int size() {
        return owners.size();
    }

    /** Owners overlapping one already traversed cell; does not run another ray. */
    public List<BlockPos> lookup(BlockPos cell) {
        Set<BlockPos> bucket = cells.get(cell);
        return bucket == null ? List.of() : List.copyOf(bucket);
    }

    /**
     * Returns each candidate once, including a surface touched at an edge/corner.
     * This is a broad phase, not nearest-hit order or permission to interact.
     * Neither the world nor the container's block/BE collection is consulted.
     */
    public List<BlockPos> query(Vec3 from, Vec3 to) {
        requireFinite(from.x, from.y, from.z);
        requireFinite(to.x, to.y, to.z);
        if (owners.isEmpty()) return List.of();
        int x = cell(from.x), y = cell(from.y), z = cell(from.z);
        int endX = cell(to.x), endY = cell(to.y), endZ = cell(to.z);
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        int stepX = Integer.compare(endX, x), stepY = Integer.compare(endY, y), stepZ = Integer.compare(endZ, z);
        double deltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dx);
        double deltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dy);
        double deltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dz);
        double nextX = crossing(from.x, x, dx, stepX);
        double nextY = crossing(from.y, y, dy, stepY);
        double nextZ = crossing(from.z, z, dz, stepZ);
        Set<BlockPos> found = new LinkedHashSet<>();
        collect(x, y, z, found);
        while (x != endX || y != endY || z != endZ) {
            double next = Math.min(nextX, Math.min(nextY, nextZ));
            boolean crossX = x != endX && nextX <= next + CROSSING_EPSILON;
            boolean crossY = y != endY && nextY <= next + CROSSING_EPSILON;
            boolean crossZ = z != endZ && nextZ <= next + CROSSING_EPSILON;
            // A diagonal step touches side cells too. A thin surface can end at
            // precisely that corner, so do not skip these at most seven cells.
            int mask = (crossX ? 1 : 0) | (crossY ? 2 : 0) | (crossZ ? 4 : 0);
            for (int subset = mask; subset != 0; subset = (subset - 1) & mask)
                collect(x + ((subset & 1) != 0 ? stepX : 0),
                        y + ((subset & 2) != 0 ? stepY : 0),
                        z + ((subset & 4) != 0 ? stepZ : 0), found);
            if (crossX) { x += stepX; nextX = x == endX ? Double.POSITIVE_INFINITY : nextX + deltaX; }
            if (crossY) { y += stepY; nextY = y == endY ? Double.POSITIVE_INFINITY : nextY + deltaY; }
            if (crossZ) { z += stepZ; nextZ = z == endZ ? Double.POSITIVE_INFINITY : nextZ + deltaZ; }
        }
        return new ArrayList<>(found);
    }

    private void collect(int x, int y, int z, Set<BlockPos> found) {
        Set<BlockPos> bucket = cells.get(new BlockPos(x, y, z));
        if (bucket != null) found.addAll(bucket);
    }

    private static double crossing(double origin, int cell, double direction, int step) {
        if (step == 0) return Double.POSITIVE_INFINITY;
        return ((step > 0 ? (double) cell + 1 : cell) - origin) / direction;
    }

    private static int cell(double coordinate) {
        double floored = Math.floor(coordinate);
        if (floored < Integer.MIN_VALUE || floored >= Integer.MAX_VALUE)
            throw new IllegalArgumentException("Interaction coordinate is outside the block grid");
        return (int) floored;
    }

    private static void requireFinite(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
            throw new IllegalArgumentException("Non-finite interaction coordinate");
    }

    private record Entry(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        private void forEach(java.util.function.Consumer<BlockPos> action) {
            for (int x = minX; x <= maxX; x++)
                for (int y = minY; y <= maxY; y++)
                    for (int z = minZ; z <= maxZ; z++) action.accept(new BlockPos(x, y, z));
        }
    }
}
