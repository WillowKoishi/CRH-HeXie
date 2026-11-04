package willow.train.hexie.client.render.track;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.TrackBlockEntity;
import com.simibubi.create.content.trains.track.TrackBlockOutline;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import willow.train.hexie.Main;
import willow.train.hexie.blocks.track.standard.BallastData;
import willow.train.hexie.init.track.TrackMaterialInit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static willow.train.hexie.client.render.track.BallastMesh.Quad;

/** Share curve discovery between Flywheel static effects and the non-instanced region buffers. */
public final class BallastCurveBuffers {
    private static final Map<BezierConnection, Entry> CURVES = new IdentityHashMap<>();
    private static final Set<Entry> pending = new LinkedHashSet<>();
    private static final ArrayDeque<Entry> ready = new ArrayDeque<>();
    private static boolean refreshQueue;
    // The lighting thread can flag a region; only the client thread touches its meshes and GPU buffers.
    private static final Map<Long, Region> REGIONS = new ConcurrentHashMap<>();
    private static volatile ClientLevel cachedLevel;
    private static VisualizationManager visuals;

    public static void register() {
        CurveBedShader.register();
        NeoForge.EVENT_BUS.addListener(BallastCurveBuffers::renderWorld);
        Main.bus.addListener((ModelEvent.BakingCompleted event) -> {
            BallastModels.clear();
            RenderSystem.recordRenderCall(BallastCurveBuffers::clear);
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            ClientLevel level = Minecraft.getInstance().level;
            var manager = level == null ? null : VisualizationManager.get(level);
            if (level != cachedLevel || visuals != manager) {
                clear();
                cachedLevel = level;
                visuals = manager;
            }
            if (level != null) synchronize(level);
        });
    }

    public static void clear() {
        if (visuals != null) for (var entry : CURVES.values())
            if (entry.effect != null) visuals.effects().queueRemove(entry.effect);
        visuals = null;
        REGIONS.values().forEach(region -> region.batches.values().forEach(CurveBedBatch::close));
        REGIONS.clear();
        CURVES.clear();
        pending.clear();
        ready.clear();
    }

    private static void synchronize(ClientLevel level) {
        for (var be : TrackBlockOutline.TRACKS_WITH_TURNS.get(level).values()) {
            if (be.isRemoved() || be.getLevel() != level || !(be instanceof BallastData data)) continue;
            for (var curve : be.getConnections().values()) {
                boolean slab = curve.getMaterial() == TrackMaterialInit.BALLASTLESS;
                if (!curve.isPrimary() || !slab && curve.getMaterial() != TrackMaterialInit.STANDARD) continue;
                var texture = slab ? Blocks.SMOOTH_STONE.defaultBlockState() : data.hexie$getBallast(curve.getKey());
                var entry = CURVES.get(curve);
                if (entry == null || entry.changed(be, texture, slab)) {
                    if (entry != null) remove(entry);
                    entry = new Entry(curve, be, texture, slab);
                    CURVES.put(curve, entry);
                    pending.add(entry);
                }
                entry.seen = true;
            }
        }
        CURVES.values().removeIf(entry -> {
            if (entry.seen) { entry.seen = false; return false; }
            remove(entry);
            return true;
        });
        refreshQueue = !pending.isEmpty();
        if (!refreshQueue) ready.clear();
    }

    private static void remove(Entry entry) {
        pending.remove(entry);
        if (entry.effect != null) visuals.effects().queueRemove(entry.effect);
        for (var piece : entry.pieces) {
            var region = REGIONS.get(piece.region);
            var batch = region.batches.get(entry.material);
            if (batch.remove(piece)) {
                batch.close();
                region.batches.remove(entry.material);
                if (region.batches.isEmpty()) REGIONS.remove(piece.region);
            }
        }
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
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES || cachedLevel == null) return;
        var mc = Minecraft.getInstance();
        if (mc.level != cachedLevel) return;
        if (pending.isEmpty() && REGIONS.isEmpty()) return;
        var camera = event.getCamera().getPosition();
        // Same maximum range as Create's track renderer. Measure to bounds, including long turns.
        double range = Math.min(192, mc.options.getEffectiveRenderDistance() * 16.0);
        double rangeSq = range * range;
        if (refreshQueue) {
            refreshQueue = false;
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
                if (!pending.remove(entry)) continue;
                entry.build(cachedLevel);
                // Spread first sightings after chunk loads over frames; a single long curve always makes progress.
                if (System.nanoTime() - start >= 2_000_000L) break;
            }
        }
        if (visuals != null || REGIONS.isEmpty()) return;
        boolean drawing = false;
        var layer = RenderType.cutoutMipped();
        var shader = CurveBedShader.instance();
        if (shader == null) return;
        try {
            for (var region : REGIONS.values()) {
                if (region.bounds.distanceToSqr(camera) > rangeSq || !event.getFrustum().isVisible(region.bounds)) continue;
                if (!drawing) {
                    layer.setupRenderState();
                    drawing = true;
                    shader.setDefaultUniforms(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                            event.getModelViewMatrix(), event.getProjectionMatrix(), mc.getWindow());
                }
                int lights = region.dirtyLights.getAndSet(0);
                shader.CHUNK_OFFSET.set((float)(region.origin.getX() - camera.x), (float)(region.origin.getY() - camera.y),
                        (float)(region.origin.getZ() - camera.z));
                for (var batch : region.batches.values()) batch.draw(cachedLevel, lights);
            }
        } finally {
            if (drawing) {
                shader.clear();
                VertexBuffer.unbind();
                layer.clearRenderState();
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
        boolean seen;
        Entry(BezierConnection curve, TrackBlockEntity owner, BlockState texture, boolean slab) {
            this.curve = curve; this.owner = owner; material = new MaterialKey(texture, slab);
            bounds = curve.getBounds().inflate(2);
            start = curve.starts.getFirst(); end = curve.starts.getSecond();
            axis1 = curve.axes.getFirst(); axis2 = curve.axes.getSecond();
            normal1 = curve.normals.getFirst(); normal2 = curve.normals.getSecond();
        }
        boolean changed(TrackBlockEntity be, BlockState texture, boolean slab) {
            return owner != be || material.texture != texture || material.slab != slab
                    || !start.equals(curve.starts.getFirst()) || !end.equals(curve.starts.getSecond())
                    || !axis1.equals(curve.axes.getFirst()) || !axis2.equals(curve.axes.getSecond())
                    || !normal1.equals(curve.normals.getFirst()) || !normal2.equals(curve.normals.getSecond());
        }
        void build(ClientLevel level) {
            var mesh = material.slab ? SlabTrackModels.curve(curve) : BallastModels.curve(curve);
            var pos = owner.getBlockPos();
            if (mesh.isEmpty()) return;
            if (visuals != null) {
                var texture = BallastModels.material(material.texture);
                effect = new CurveBedEffect(level, pos, CurveBedModel.of(mesh), texture, !material.slab,
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
                var batch = region.batches.computeIfAbsent(material, unused -> new CurveBedBatch(material.texture, !material.slab));
                var piece = batch.add(level, key, lightBit(sx, sy, sz), pos, region.origin, part.getValue());
                pieces.add(piece);
            }
        }
    }
}
