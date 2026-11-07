package willow.train.hexie.client.render.track;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.TrackBlockEntity;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.api.event.ReloadLevelRendererEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import willow.train.hexie.Main;
import willow.train.hexie.blocks.track.standard.BallastData;
import willow.train.hexie.init.track.TrackMaterialInit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static willow.train.hexie.client.render.track.BallastMesh.Quad;

/** Loaded track lifecycle owns discovery; render work is shared by Flywheel and region buffers. */
public final class BallastCurveBuffers {
    private static final Map<BezierConnection, Entry> CURVES = new IdentityHashMap<>();
    private static final Map<TrackBlockEntity, Set<BezierConnection>> TRACKS = new IdentityHashMap<>();
    private static final Map<BezierConnection, TrackBlockEntity> OWNERS = new IdentityHashMap<>();
    private static final Map<TrackBlockEntity, Boolean> dirtyTracks = new IdentityHashMap<>();
    private static final Set<BezierConnection> dirtyCurves = identitySet();
    private static final Set<Entry> pending = new LinkedHashSet<>();
    private static final ArrayDeque<Entry> ready = new ArrayDeque<>();
    private static boolean refreshQueue;
    // The lighting thread can flag a region; only the client thread touches its meshes and GPU buffers.
    private static final Map<Long, Region> REGIONS = new ConcurrentHashMap<>();
    private static volatile ClientLevel cachedLevel;
    private static VisualizationManager visuals;
    private static volatile boolean reloadRequested;
    private static Vec3 lastCamera;
    private static final Quaternionf lastRotation = new Quaternionf();
    private static final Matrix4f lastProjection = new Matrix4f();
    private static double lastRange;
    private static long nextViewCheck;

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
        NeoForge.EVENT_BUS.addListener(BallastCurveBuffers::renderWorld);
        NeoForge.EVENT_BUS.addListener((ReloadLevelRendererEvent event) -> {
            // A shader-pack toggle can change the packed terrain vertex format even while Flywheel stays off.
            if (event.level() == cachedLevel) clear();
        });
        Main.bus.addListener((ModelEvent.BakingCompleted event) -> {
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

    private static void prepare() {
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
        changed.forEach(BallastCurveBuffers::synchronize);
    }

    /** Compare only this changed owner's connections, never the global track/curve tables. */
    private static void synchronize(TrackBlockEntity be, boolean removed) {
        var previous = TRACKS.remove(be);
        Set<BezierConnection> current = identitySet();
        if (!removed && cachedLevel != null && be.getLevel() == cachedLevel && !be.isRemoved() && be instanceof BallastData data) {
            for (var curve : be.getConnections().values()) {
                current.add(curve);
                OWNERS.put(curve, be);
                boolean slab = curve.getMaterial() == TrackMaterialInit.BALLASTLESS;
                if (!curve.isPrimary() || !slab && curve.getMaterial() != TrackMaterialInit.STANDARD) {
                    var entry = CURVES.remove(curve);
                    if (entry != null) remove(entry);
                    continue;
                }
                var texture = slab ? Blocks.SMOOTH_STONE.defaultBlockState() : data.hexie$getBallast(curve.getKey());
                var entry = CURVES.get(curve);
                if (entry == null || entry.changed(be, texture, slab)) {
                    if (entry != null) remove(entry);
                    entry = new Entry(curve, be, texture, slab);
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

    private static void remove(Entry entry) {
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

    private static long regionKey(int sx, int sy, int sz) { return SectionPos.asLong(sx >> 1, sy >> 1, sz >> 1); }
    static int lightBit(int sx, int sy, int sz) { return 1 << ((sx & 1) | (sy & 1) << 1 | (sz & 1) << 2); }

    public static void lightChanged(ClientLevel level, SectionPos pos) {
        if (level != cachedLevel || REGIONS.isEmpty()) return;
        var region = REGIONS.get(regionKey(pos.x(), pos.y(), pos.z()));
        if (region != null) {
            int bit = lightBit(pos.x(), pos.y(), pos.z());
            region.dirtyLights.getAndUpdate(mask -> mask | bit);
        }
    }

    private static void renderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        prepare();
        if (cachedLevel == null) return;
        var mc = Minecraft.getInstance();
        if (mc.level != cachedLevel) return;
        if (pending.isEmpty() && REGIONS.isEmpty()) return;
        var camera = event.getCamera().getPosition();
        // Same maximum range as Create's track renderer. Measure to bounds, including long turns.
        double range = Math.min(192, mc.options.getEffectiveRenderDistance() * 16.0);
        double rangeSq = range * range;
        boolean viewChanged = false;
        long now = System.nanoTime();
        if (!pending.isEmpty() && now >= nextViewCheck) {
            nextViewCheck = now + 50_000_000L;
            viewChanged = lastCamera == null || !lastCamera.equals(camera)
                    || !lastRotation.equals(event.getCamera().rotation()) || lastRange != range
                    || !lastProjection.equals(event.getProjectionMatrix());
        }
        if (refreshQueue || viewChanged) {
            refreshQueue = false;
            lastCamera = camera;
            lastRotation.set(event.getCamera().rotation());
            lastProjection.set(event.getProjectionMatrix());
            lastRange = range;
            var visible = new ArrayList<Entry>();
            for (var entry : pending)
                if (entry.bounds.distanceToSqr(camera) <= rangeSq && event.getFrustum().isVisible(entry.bounds)) visible.add(entry);
            visible.sort(Comparator.comparingDouble(entry -> entry.bounds.distanceToSqr(camera)));
            ready.clear();
            ready.addAll(visible);
        }
        if (!ready.isEmpty()) {
            long start = System.nanoTime();
            while (!ready.isEmpty()) {
                var entry = ready.removeFirst();
                if (entry.bounds.distanceToSqr(camera) > rangeSq || !event.getFrustum().isVisible(entry.bounds)) {
                    refreshQueue = true;
                    continue;
                }
                if (!pending.remove(entry)) continue;
                entry.build(cachedLevel);
                // Spread first sightings after chunk loads over frames; a single long curve always makes progress.
                if (System.nanoTime() - start >= 2_000_000L) break;
            }
        }
        if (visuals != null || REGIONS.isEmpty()) return;
        var layer = RenderType.cutoutMipped();
        int previousTexture = RenderSystem.getShaderTexture(0);
        ResourceLocation boundTexture = null;
        ShaderInstance shader = null;
        boolean drawing = false;
        try {
            for (var region : REGIONS.values()) {
                if (region.bounds.distanceToSqr(camera) > rangeSq || !event.getFrustum().isVisible(region.bounds)) continue;
                if (!drawing) {
                    layer.setupRenderState();
                    drawing = true;
                    shader = Objects.requireNonNull(RenderSystem.getShader());
                }
                int lights = region.dirtyLights.getAndSet(0);
                float x = (float)(region.origin.getX() - camera.x);
                float y = (float)(region.origin.getY() - camera.y);
                float z = (float)(region.origin.getZ() - camera.z);
                var view = event.getModelViewMatrix();
                if (shader.CHUNK_OFFSET != null) shader.CHUNK_OFFSET.set(x, y, z);
                else view = modelView(view, x, y, z);
                boolean updateShader = true;
                for (var batch : region.batches.values()) {
                    if (!batch.texture.equals(boundTexture)) {
                        boundTexture = batch.texture;
                        RenderSystem.setShaderTexture(0, boundTexture);
                        updateShader = true;
                    }
                    if (updateShader) {
                        shader.setDefaultUniforms(VertexFormat.Mode.QUADS, view, event.getProjectionMatrix(), mc.getWindow());
                        shader.apply();
                        updateShader = false;
                    }
                    batch.draw(cachedLevel, lights);
                }
            }
        } finally {
            if (drawing) {
                if (shader != null) {
                    if (shader.CHUNK_OFFSET != null) shader.CHUNK_OFFSET.set(0f, 0f, 0f);
                    shader.clear();
                }
                VertexBuffer.unbind();
                layer.clearRenderState();
                RenderSystem.setShaderTexture(0, previousTexture);
            }
        }
    }

    // Kept for existing callers that draw a single endpoint-local vanilla buffer.
    public static Matrix4f modelView(Matrix4f view, double x, double y, double z) {
        return new Matrix4f(view).translate((float)x, (float)y, (float)z);
    }
    public static void drawBuffer(VertexBuffer buffer, Matrix4f modelView, Matrix4f projection) {
        var layer = RenderType.cutoutMipped();
        layer.setupRenderState();
        try {
            buffer.bind();
            var shader = RenderSystem.getShader();
            if (shader.CHUNK_OFFSET != null) shader.CHUNK_OFFSET.set(0f,0f,0f);
            buffer.drawWithShader(modelView, projection, shader);
        } finally { VertexBuffer.unbind(); layer.clearRenderState(); }
    }

    private record MaterialKey(BlockState texture, boolean slab) {}

    private static final class Region {
        final BlockPos origin;
        final AABB bounds;
        final Map<MaterialKey, CurveBedBatch> batches = new HashMap<>();
        final AtomicInteger dirtyLights = new AtomicInteger();
        Region(long key) {
            origin = new BlockPos(SectionPos.x(key) << 5, SectionPos.y(key) << 5, SectionPos.z(key) << 5);
            // The longest span is 4 m, with at most 2 m of shoulder beyond its centreline.
            bounds = new AABB(Vec3.atLowerCornerOf(origin), Vec3.atLowerCornerOf(origin.offset(32,32,32))).inflate(6);
        }
    }

    private static final class Entry {
        final BezierConnection curve;
        final TrackBlockEntity owner;
        final MaterialKey material;
        final AABB bounds;
        final Vec3 start, end, axis1, axis2, normal1, normal2;
        final List<CurveBedBatch.Piece> pieces = new ArrayList<>();
        CurveBedEffect effect;
        ResourceLocation tile;
        Entry(BezierConnection curve, TrackBlockEntity owner, BlockState texture, boolean slab) {
            // A fresh geometry snapshot also avoids a native lazy Runtime cached before an in-place edit.
            this.curve = curve.clone(); this.owner = owner; material = new MaterialKey(texture, slab);
            bounds = this.curve.getBounds().inflate(2);
            start = curve.starts.getFirst(); end = curve.starts.getSecond();
            axis1 = curve.axes.getFirst(); axis2 = curve.axes.getSecond();
            normal1 = curve.normals.getFirst(); normal2 = curve.normals.getSecond();
        }
        boolean changed(TrackBlockEntity be, BlockState texture, boolean slab) {
            var current = be.getConnections().get(curve.getKey());
            return owner != be || material.texture != texture || material.slab != slab
                    || current == null || !start.equals(current.starts.getFirst()) || !end.equals(current.starts.getSecond())
                    || !axis1.equals(current.axes.getFirst()) || !axis2.equals(current.axes.getSecond())
                    || !normal1.equals(current.normals.getFirst()) || !normal2.equals(current.normals.getSecond());
        }
        void build(ClientLevel level) {
            var mesh = material.slab ? SlabTrackModels.curve(curve) : BallastModels.curve(curve);
            var pos = owner.getBlockPos();
            if (mesh.isEmpty()) return;
            var texture = BallastModels.material(material.texture);
            tile = texture.curveTexture(!material.slab);
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
