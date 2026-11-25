package willow.train.hexie.interaction.surface;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.interaction.SurfaceInputBridge.SurfaceInputSourcesRayView;
import willow.train.hexie.core.api.interaction.BlockControlContext;
import willow.train.hexie.core.api.interaction.BlockControlHost;
import willow.train.hexie.interaction.surface.SurfaceInputTarget.Pick;

// split from willow/train/hexie/client/interaction/SurfaceInputTarget.java
final class SurfacePointerQuery {
    static long frame;
    private static PointedQuery pointedQuery;

    private static final class PointedQuery {
        final Object nativeHit;
        final Vec3 from, to;
        final SurfaceInputSources.Query targets;
        final ClientGeometricPicker.Selection selection;

        PointedQuery(Object nativeHit, Vec3 from, Vec3 to, SurfaceInputSources.Query targets) {
            this.nativeHit = nativeHit;
            this.from = from;
            this.to = to;
            this.targets = targets;
            this.selection = ClientGeometricPicker.select(targets, from, to);
        }
    }

    static void invalidate() {
        ++frame;
        pointedQuery = null;
    }

    /** A key searches declared entry regions; a direct click searches actual control geometry. */
    static Pick pointed(Integer entryKey) { return pointed(entryKey, entryKey != null); }

    /** Read the nearest block-declared entry for presentation; this never changes input ownership. */
    static Pick pointedEntry() { return pointed(null, true); }

    private static PointedQuery query() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return null;
        // Ordinary use and entry keys follow the player's interaction ray, including in third person.
        float partial = SurfaceInputSources.partialTick();
        Vec3 from = SurfaceInputSources.eye(mc.player, partial), to = from.add(mc.player.getViewVector(partial)
                .scale(mc.player.blockInteractionRange()));
        var query = pointedQuery;
        if (query == null || query.nativeHit != mc.hitResult || !query.from.equals(from) || !query.to.equals(to))
            pointedQuery = query = new PointedQuery(mc.hitResult, from, to,
                    SurfaceInputSources.collect(mc, new SurfaceInputSources.Ray(from, to), partial));
        return query;
    }

    static ClientGeometricPicker.Selection selection() {
        var query = query();
        return query == null ? null : query.selection;
    }

    private static Pick pointed(Integer entryKey, boolean entries) {
        var query = query();
        if (query == null) return null;
        if (!entries) return query.selection == null ? null : query.selection.controlPick();
        Pick nearest = null;
        var obstructions = query.targets.obstructions();
        for (var target : query.targets.candidates()) {
            var picked = entry(target, query.from, query.to, entryKey);
            if (picked == null || obstructions.stream().anyMatch(block -> block.hides(target, query.from, picked.distance()))) continue;
            if (query.selection != null && !query.selection.target().equals(target)
                    && query.selection.distanceSquared() < picked.distance() * picked.distance()) continue;
            nearest = nearer(nearest, picked);
        }
        return nearest;
    }

    private static Pick entry(BlockControlContext target, Vec3 from, Vec3 to, Integer entryKey) {
        var host = BlockControlHost.find(target.state());
        if (host == null || !host.allowsWorldControl()) return null;
        var ray = localRay(target, from, to);
        Pick result = null;
        for (var entry : host.inputLayout(target).pointedEntries()) {
            if (entryKey != null && entry.key() != entryKey) continue;
            if (entryKey == null && entry.hint().isEmpty()) continue;
            var hit = entry.bounds().move(target.position()).clip(ray.from(), ray.to());
            if (hit.isEmpty()) continue;
            double distance = renderWorld(target, hit.get()).distanceTo(from);
            result = nearer(result, new Pick(new SurfaceInputTarget(null, target, hit.get()), null, distance, entry));
        }
        return result;
    }
    private static Pick nearer(Pick first, Pick second) {
        return second != null && (first == null || second.distance() < first.distance()) ? second : first;
    }
    static SurfaceInputSourcesRayView localRay(BlockControlContext target, Vec3 from, Vec3 to) {
        return HexieBridges.surfaceInput().localRay(target, from, to);
    }
    static Vec3 renderWorld(BlockControlContext target, Vec3 point) {
        return SurfaceInputSources.frame(target).toWorld(point);
    }
    static Vec3 eye() {
        return SurfaceInputSources.eye(Minecraft.getInstance().player, SurfaceInputSources.partialTick());
    }
}
