package willow.train.hexie.api.interaction;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Supplements native cell traversal, after the host (including Sable) has transformed its ray. */
public final class BlockControlPicking {
    private static final ThreadLocal<Query> QUERY = new ThreadLocal<>();
    @FunctionalInterface
    public interface Picker {
        BlockControlHit pick(BlockControlHost host, BlockControlContext target, Vec3 from, Vec3 to);
    }
    private static Picker clientPicker;

    /** Installs the shared implementation once during client setup, independently of block registration. */
    public static void registerClientPicker(Picker picker) {
        clientPicker = java.util.Objects.requireNonNull(picker);
    }

    /** Default host selection from its declared surface. Existing host overrides keep their own native routing. */
    public static BlockControlHit pickDeclared(BlockControlHost host, BlockControlContext target, Vec3 from, Vec3 to) {
        boolean client = target.carrier() != null ? target.carrier().level().isClientSide
                : target.blocks() instanceof Level level && level.isClientSide;
        if (client && clientPicker != null) return clientPicker.pick(host, target, from, to);
        Vec3 offset = Vec3.atLowerCornerOf(target.position()), ray = to.subtract(from);
        var hit = host.surfaceGeometry(target.state()).pick(host.surfaceFacing(target.state()), from.subtract(offset), ray,
                ray.length(), host.surfaceState(target), host.animation(target));
        if (hit == null) return null;
        var face = Direction.getNearest(-ray.x, -ray.y, -ray.z);
        return new BlockControlHit(hit.control(), new BlockHitResult(hit.point().add(offset), face, target.position(), false));
    }
    private record Key(BlockGetter space, BlockPos owner, Vec3 from, Vec3 to) {}
    private static final class Query {
        final float partial;
        final Map<Key, BlockControlHit> hits = new HashMap<>();
        Query(float partial) { this.partial = partial; }
    }
    private BlockControlPicking() {}
    public static <T> T query(float partial, Supplier<T> operation) {
        var previous = QUERY.get();
        QUERY.set(new Query(partial));
        try { return operation.get(); }
        finally { if (previous == null) QUERY.remove(); else QUERY.set(previous); }
    }
    public static BlockHitResult clipCell(BlockGetter blocks, BlockPos cell, Vec3 from, Vec3 to, BlockHitResult nativeHit) {
        var query = QUERY.get();
        if (query == null || !(blocks instanceof Level level)) return nativeHit;
        BlockHitResult result = nativeHit;
        var host = level.hasChunkAt(cell) ? BlockControlHost.find(blocks.getBlockState(cell)) : null;
        if (host != null) {
            var picked = hit(query, blocks, cell, from, to);
            if (host.nativeInteraction()) result = inCell(picked, cell);
            else if (inCell(picked, cell) != null) result = picked.blockHit();
        }
        for (var owner : WorldInteractionTargets.lookup(level, cell)) {
            if (owner.equals(cell)) continue;
            var picked = inCell(hit(query, blocks, owner, from, to), cell);
            if (picked != null && (result == null || picked.getLocation().distanceToSqr(from)
                    < result.getLocation().distanceToSqr(from))) result = picked;
        }
        return result;
    }
    private static BlockControlHit hit(Query query, BlockGetter blocks, BlockPos position, Vec3 from, Vec3 to) {
        var key = new Key(blocks, position.immutable(), from, to);
        if (query.hits.containsKey(key)) return query.hits.get(key);
        // An extended surface can be indexed in a neighbouring chunk. Never load its owner
        // while picking if an unload or a plot replacement has made that index entry stale.
        if (blocks instanceof Level level && !level.hasChunkAt(position)) {
            query.hits.put(key, null);
            return null;
        }
        var state = blocks.getBlockState(position);
        var host = BlockControlHost.find(state);
        var hit = host == null ? null : host.pickControl(new BlockControlContext(blocks, position, null, query.partial), from, to);
        query.hits.put(key, hit);
        return hit;
    }
    /** A conservative owner envelope must never make DDA skip a nearer obstruction in the next cell. */
    private static BlockHitResult inCell(BlockControlHit hit, BlockPos cell) {
        if (hit == null) return null;
        var p = hit.blockHit().getLocation();
        double epsilon = 1e-6;
        return p.x >= cell.getX() - epsilon && p.x <= cell.getX() + 1 + epsilon
                && p.y >= cell.getY() - epsilon && p.y <= cell.getY() + 1 + epsilon
                && p.z >= cell.getZ() - epsilon && p.z <= cell.getZ() + 1 + epsilon ? hit.blockHit() : null;
    }
}
