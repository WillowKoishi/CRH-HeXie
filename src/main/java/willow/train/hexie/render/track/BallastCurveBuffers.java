package willow.train.hexie.render.track;

// split from willow/train/hexie/client/render/track/BallastCurveBuffers.java

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.TrackBlockEntity;
import dev.engine_room.flywheel.api.event.ReloadLevelRendererEvent;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.common.NeoForge;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.WorldBlockBridge.BallastDataView;
import willow.train.hexie.core.api.railway.RailwayDefinition.Bed;

import static willow.train.hexie.render.track.BallastMesh.Quad;
import static willow.train.hexie.render.track.BallastRegionRendering.MaterialKey;
import static willow.train.hexie.render.track.BallastRegionRendering.Region;
import static willow.train.hexie.render.track.BallastRegionRendering.lastCamera;
import static willow.train.hexie.render.track.BallastRegionRendering.lightBit;
import static willow.train.hexie.render.track.BallastRegionRendering.nextViewCheck;
import static willow.train.hexie.render.track.BallastRegionRendering.regionKey;

/** Loaded track lifecycle owns discovery; render work is shared by Flywheel and region buffers. */
public final class BallastCurveBuffers {
    public static final Map<BezierConnection, Entry> CURVES = new IdentityHashMap<>();
    private static final Map<TrackBlockEntity, Set<BezierConnection>> TRACKS = new IdentityHashMap<>();
    private static final Map<BezierConnection, TrackBlockEntity> OWNERS = new IdentityHashMap<>();
    private static final Map<TrackBlockEntity, Boolean> dirtyTracks = new IdentityHashMap<>();
    private static final Set<BezierConnection> dirtyCurves = identitySet();
    public static final Set<Entry> pending = new LinkedHashSet<>();
    public static final ArrayDeque<Entry> ready = new ArrayDeque<>();
    public static boolean refreshQueue;
    // The lighting thread can flag a region; only the client thread touches its meshes and GPU buffers.
    public static final Map<Long, Region> REGIONS = new ConcurrentHashMap<>();
    public static volatile ClientLevel cachedLevel;
    public static VisualizationManager visuals;
    private static volatile boolean reloadRequested;

    private static <T> Set<T> identitySet() { return Collections.newSetFromMap(new IdentityHashMap<>()); }

    /** Notifications only enqueue; a packet's connection and ballast reads finish before reconciliation. */
    public static void changed(TrackBlockEntity track) {
        if (!(track.getLevel() instanceof ClientLevel)) return;
        synchronized (dirtyTracks) { dirtyTracks.put(track, false); }
    }

    /** Invalidation can precede the removed flag; honor the lifecycle event itself. */
    public static void removed(TrackBlockEntity track) {
        if (!(track.getLevel() instanceof ClientLevel)) return;
        synchronized (dirtyTracks) { dirtyTracks.put(track, true); }
    }

    /** Geometry/material edits keep the connection identity, so they need an explicit notification too. */
    public static void changed(BezierConnection curve) {
        synchronized (dirtyCurves) { dirtyCurves.add(curve); }
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(BallastRegionRendering::renderWorld);
        NeoForge.EVENT_BUS.addListener((ReloadLevelRendererEvent event) -> {
            // A shader-pack toggle can change the packed terrain vertex format even while Flywheel stays off.
            if (event.level() == cachedLevel) clear();
        });
        HexieBridges.modBootstrap().modBus().addListener((ModelEvent.BakingCompleted event) -> {
            BallastModels.clear();
            RenderSystem.recordRenderCall(BallastCurveBuffers::clear);
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            prepare();
            CurveBedTexture.refreshAnimations();
        });
    }

    /** Renderer/resource reloads preserve the lifecycle directory, without rediscovering loaded chunks. */
    public static void clear() { reloadRequested = true; }

    private static void releaseRendering(boolean rebuild) {
        for (var entry : CURVES.values()) {
            if (visuals != null && entry.effect != null) visuals.effects().queueRemove(entry.effect);
            entry.effect = null;
            entry.tile = null;
            entry.pieces.clear();
        }
        REGIONS.values().forEach(region -> region.batches.values().forEach(CurveBedBatch::close));
        REGIONS.clear();
        pending.clear();
        ready.clear();
        CurveBedTexture.clear();
        if (rebuild) pending.addAll(CURVES.values());
        refreshQueue = rebuild && !pending.isEmpty();
        lastCamera = null;
        nextViewCheck = 0;
    }

    public static void prepare() {
        ClientLevel level = Minecraft.getInstance().level;
        var manager = level == null ? null : VisualizationManager.get(level);
        if (level != cachedLevel) {
            releaseRendering(false);
            CURVES.clear();
            TRACKS.clear();
            OWNERS.clear();
            cachedLevel = level;
            visuals = manager;
            reloadRequested = false;
        } else if (reloadRequested || visuals != manager) {
            releaseRendering(true);
            visuals = manager;
            reloadRequested = false;
        }
        synchronized (dirtyCurves) {
            synchronized (dirtyTracks) {
                for (var curve : dirtyCurves) {
                    var owner = OWNERS.get(curve);
                    if (owner != null) dirtyTracks.putIfAbsent(owner, false);
                }
            }
            dirtyCurves.clear();
        }
        Map<TrackBlockEntity, Boolean> changed;
        synchronized (dirtyTracks) {
            if (dirtyTracks.isEmpty()) return;
            changed = new IdentityHashMap<>(dirtyTracks);
            dirtyTracks.clear();
        }
        // The primary owner may be at the other end. Recheck it when a secondary
        // endpoint loads or changes placement type, even if the curve geometry is identical.
        for (var track : List.copyOf(changed.keySet())) {
            if (cachedLevel == null || track.getLevel() != cachedLevel) continue;
            for (var target : track.getConnections().keySet())
                if (cachedLevel.hasChunkAt(target) && cachedLevel.getBlockEntity(target) instanceof TrackBlockEntity other)
                    changed.putIfAbsent(other, false);
        }
        changed.forEach(BallastCurveBuffers::synchronize);
    }

    /** Compare only this changed owner's connections, never the global track/curve tables. */
    private static void synchronize(TrackBlockEntity be, boolean removed) {
        var previous = TRACKS.remove(be);
        Set<BezierConnection> current = identitySet();
        if (!removed && cachedLevel != null && be.getLevel() == cachedLevel && !be.isRemoved() && be instanceof BallastDataView data) {
            for (var curve : be.getConnections().values()) {
                current.add(curve);
                OWNERS.put(curve, be);
                boolean slab = HexieBridges.createTrain().createRailwayBackend().bed(curve.getMaterial()) == Bed.SLAB;
                if (!curve.isPrimary() || !slab && HexieBridges.createTrain().createRailwayBackend().bed(curve.getMaterial()) != Bed.BALLAST) {
                    var entry = CURVES.remove(curve);
                    if (entry != null) remove(entry);
                    continue;
                }
                var texture = slab ? Blocks.SMOOTH_STONE.defaultBlockState() : data.hexie$getBallast(curve.getKey());
                var entry = CURVES.get(curve);
                var ends = BallastCurveGeometry.curveEnds(curve, cachedLevel, entry == null ? null : entry.ends);
                if (entry == null || entry.changed(be, texture, slab, ends)) {
                    if (entry != null) remove(entry);
                    entry = new Entry(curve, be, texture, slab, ends);
                    CURVES.put(curve, entry);
                    pending.add(entry);
                    refreshQueue = true;
                }
            }
        }
        if (previous != null) for (var curve : previous) {
            if (current.contains(curve) || OWNERS.get(curve) != be) continue;
            OWNERS.remove(curve);
            var entry = CURVES.remove(curve);
            if (entry != null) remove(entry);
        }
        if (!current.isEmpty()) TRACKS.put(be, current);
        if (pending.isEmpty()) ready.clear();
    }

    public static void remove(Entry entry) {
        pending.remove(entry);
        if (entry.effect != null && visuals != null) visuals.effects().queueRemove(entry.effect);
        for (var piece : entry.pieces) {
            var region = REGIONS.get(piece.region);
            var batch = region.batches.get(entry.material);
            if (batch.remove(piece)) {
                batch.close();
                region.batches.remove(entry.material);
                if (region.batches.isEmpty()) REGIONS.remove(piece.region);
            }
        }
        if (entry.tile != null) CurveBedTexture.release(entry.tile);
    }

    public static final class Entry {
        final BezierConnection curve;
        final TrackBlockEntity owner;
        final MaterialKey material;
        final BallastCurveGeometry.CurveEnds ends;
        final AABB bounds;
        final Vec3 start, end, axis1, axis2, normal1, normal2;
        final List<CurveBedBatch.Piece> pieces = new ArrayList<>();
        CurveBedEffect effect;
        ResourceLocation tile;
        Entry(BezierConnection curve, TrackBlockEntity owner, BlockState texture, boolean slab, BallastCurveGeometry.CurveEnds ends) {
            // A fresh geometry snapshot also avoids a native lazy Runtime cached before an in-place edit.
            this.curve = curve.clone(); this.owner = owner; material = new MaterialKey(texture, slab);
            this.ends = ends;
            bounds = this.curve.getBounds().inflate(2);
            start = curve.starts.getFirst(); end = curve.starts.getSecond();
            axis1 = curve.axes.getFirst(); axis2 = curve.axes.getSecond();
            normal1 = curve.normals.getFirst(); normal2 = curve.normals.getSecond();
        }
        boolean changed(TrackBlockEntity be, BlockState texture, boolean slab, BallastCurveGeometry.CurveEnds ends) {
            var current = be.getConnections().get(curve.getKey());
            return owner != be || material.texture() != texture || material.slab() != slab || !this.ends.equals(ends)
                    || current == null || !start.equals(current.starts.getFirst()) || !end.equals(current.starts.getSecond())
                    || !axis1.equals(current.axes.getFirst()) || !axis2.equals(current.axes.getSecond())
                    || !normal1.equals(current.normals.getFirst()) || !normal2.equals(current.normals.getSecond());
        }
        void build(ClientLevel level) {
            var mesh = material.slab() ? SlabTrackModels.curve(curve, ends) : BallastCurveGeometry.curve(curve, ends);
            var pos = owner.getBlockPos();
            if (mesh.isEmpty()) return;
            var texture = BallastModels.material(material.texture());
            tile = texture.curveTexture(!material.slab());
            if (visuals != null) {
                effect = new CurveBedEffect(level, pos, CurveBedModel.of(mesh, tile), texture.color(),
                        CurveBedModel.lightSections(pos, mesh));
                visuals.effects().queueAdd(effect);
                return;
            }
            var bySection = new HashMap<Long,List<Quad>>();
            for (var q : mesh) {
                long section = SectionPos.asLong(CurveBedBatch.lightProbe(pos, q));
                bySection.computeIfAbsent(section, unused -> new ArrayList<>()).add(q);
            }
            for (var part : bySection.entrySet()) {
                long section = part.getKey();
                int sx = SectionPos.x(section), sy = SectionPos.y(section), sz = SectionPos.z(section);
                long key = regionKey(sx, sy, sz);
                var region = REGIONS.computeIfAbsent(key, Region::new);
                var batch = region.batches.computeIfAbsent(material, unused -> new CurveBedBatch(texture.color(), tile));
                var piece = batch.add(level, key, lightBit(sx, sy, sz), pos, region.origin, part.getValue());
                pieces.add(piece);
            }
        }
    }
}
