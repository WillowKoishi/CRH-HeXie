package willow.train.hexie.render.track;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import static willow.train.hexie.render.track.BallastBufferDrawing.modelView;
import static willow.train.hexie.render.track.BallastCurveBuffers.Entry;
import static willow.train.hexie.render.track.BallastCurveBuffers.REGIONS;
import static willow.train.hexie.render.track.BallastCurveBuffers.cachedLevel;
import static willow.train.hexie.render.track.BallastCurveBuffers.pending;
import static willow.train.hexie.render.track.BallastCurveBuffers.prepare;
import static willow.train.hexie.render.track.BallastCurveBuffers.ready;
import static willow.train.hexie.render.track.BallastCurveBuffers.refreshQueue;
import static willow.train.hexie.render.track.BallastCurveBuffers.visuals;

// split from willow/train/hexie/client/render/track/BallastCurveBuffers.java, lines 52-56, 207-312, 329-341
public final class BallastRegionRendering {
    static Vec3 lastCamera;
    private static final Quaternionf lastRotation = new Quaternionf();
    private static final Matrix4f lastProjection = new Matrix4f();
    private static double lastRange;
    static long nextViewCheck;

    static long regionKey(int sx, int sy, int sz) { return SectionPos.asLong(sx >> 1, sy >> 1, sz >> 1); }
    static int lightBit(int sx, int sy, int sz) { return 1 << ((sx & 1) | (sy & 1) << 1 | (sz & 1) << 2); }

    public static void lightChanged(ClientLevel level, SectionPos pos) {
        if (level != cachedLevel || REGIONS.isEmpty()) return;
        var region = REGIONS.get(regionKey(pos.x(), pos.y(), pos.z()));
        if (region != null) {
            int bit = lightBit(pos.x(), pos.y(), pos.z());
            region.dirtyLights.getAndUpdate(mask -> mask | bit);
        }
    }

    static void renderWorld(RenderLevelStageEvent event) {
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

    record MaterialKey(BlockState texture, boolean slab) {}

    static final class Region {
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
}
