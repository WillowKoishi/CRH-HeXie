package willow.train.hexie.render.sticker;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.ByteArrayInputStream;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.core.HexieConstants;
import willow.train.hexie.core.sticker.*;
import willow.train.hexie.render.rendererbridge.HexieRendererBridge;

/**
 * Pure-vanilla client renderer for custom windows (parametric rounded frame + translucent glass),
 * projected image decals (DynamicTexture from uploaded PNG bytes), live placement previews
 * (including Ctrl 16x16 grid & edge-distance HUD), and 3D Up/Down/Left/Right arrow gizmos.
 */
public final class StickerWorldRenderer {
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final Map<String, ResourceLocation> DECAL_TEXTURES = new HashMap<>();
    private static final Set<String> REQUESTED_HASHES = new HashSet<>();

    private StickerWorldRenderer() {}

    public static void install() {
        BlockModelCutter.install();
        NeoForge.EVENT_BUS.addListener(StickerWorldRenderer::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(StickerWorldRenderer::onRenderGui);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.LevelEvent.Unload e) -> {
            if (e.getLevel().isClientSide()) {
                BlockModelCutter.INSTANCE.clearAll();
                HexieRendererBridge.onLevelUnload();
                REQUESTED_HASHES.clear();
            }
        });
    }

    private static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);

        StickerEntry preview = HexieBridges.Client.stickerClient().activePreviewOrEditing();
        boolean editing = HexieBridges.Client.stickerClient().isFreeCamActive();

        try (var mem = new ByteBufferBuilder(65536)) {
            var buffers = MultiBufferSource.immediate(mem);
            int pcx = mc.player.blockPosition().getX() >> 4;
            int pcz = mc.player.blockPosition().getZ() >> 4;
            int radius = Math.min(6, mc.options.renderDistance().get());

            for (int cx = pcx - radius; cx <= pcx + radius; cx++) {
                for (int cz = pcz - radius; cz <= pcz + radius; cz++) {
                    var entries = HexieBridges.sticker().entriesInChunk(mc.level, cx, cz);
                    // Placed DECALs render natively through HeXie's embedded renderer (StandardGeometry + PixelImageSource).
                    HexieRendererBridge.publishChunkStickers(mc.level, cx, cz, entries);
                }
            }

            if (preview != null) {
                if (editing && preview.mode() == StickerMode.WINDOW) {
                    // During Free-Camera Edit Mode, BlockModelCutter live-cuts the real block mesh; draw 3D arrow gizmos on actual surface.
                    drawArrowGizmos(mc.level, preview, pose, buffers, HexieBridges.Client.stickerClient().activeGizmoAxis());
                } else if (preview.mode() == StickerMode.DECAL) {
                    // Placement preview projects directly onto real surfaces (never floating over empty air)
                    drawClippedDecal(mc.level, preview, pose, buffers, true);
                } else {
                    // Window placement preview clipped strictly to real surfaces (empty space gets nothing!)
                    drawClippedWindowPreview(mc.level, preview, pose, buffers);
                    if (HexieBridges.Client.stickerClient().activePrecisionSnap() != null) {
                        double surfOff = sampleSurfaceOutwardOffset(mc.level, preview.anchorPos(), preview.facing(), preview.affectedPositions());
                        drawGrid16Overlay(preview.anchorPos(), preview.facing(), surfOff, pose, buffers);
                    }
                }
            }
            buffers.endBatch();
        } finally {
            pose.popPose();
        }
    }

    private record WorldSurfQuad(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, Vec3 normal, double outDist, int light) {}

    /**
     * Collects outward-facing BakedQuads belonging to the ACTUAL frontmost surface layer of each sampled block
     * (including multi-block OBJ roots), independent of whether that surface sits at anchorPos's cube face.
     */
    private static List<WorldSurfQuad> collectFrontmostOutwardQuads(net.minecraft.world.level.Level level,
                                                                    BlockPos anchorPos, Direction facing,
                                                                    Set<BlockPos> affected) {
        var mc = Minecraft.getInstance();
        var dispatcher = mc.getBlockRenderer();
        var rand = net.minecraft.util.RandomSource.create();
        Vec3 outN = StickerPlaneMath.outward(facing);
        Vec3 uAxis = StickerPlaneMath.axisU(facing);
        Vec3 planeOrigin = StickerPlaneMath.faceCenter(anchorPos, facing);
        BlockPos uStep = new BlockPos((int) Math.round(uAxis.x), (int) Math.round(uAxis.y), (int) Math.round(uAxis.z));

        var sampleBlocks = new LinkedHashSet<BlockPos>();
        for (BlockPos bp : affected) {
            sampleBlocks.add(bp);
            // Multi-block OBJ / JSON roots (e.g. 2-block-tall CRH1/CRH2 panels & wide DF11G panels)
            sampleBlocks.add(bp.above());
            sampleBlocks.add(bp.below());
            sampleBlocks.add(bp.below(2));
            if (!uStep.equals(BlockPos.ZERO)) {
                sampleBlocks.add(bp.offset(uStep));
                sampleBlocks.add(bp.subtract(uStep));
            }
        }

        var result = new ArrayList<WorldSurfQuad>();
        for (BlockPos bp : sampleBlocks) {
            var state = level.getBlockState(bp);
            if (state.isAir()) continue;
            var model = dispatcher.getBlockModel(state);
            int light = LevelRenderer.getLightColor(level, bp.relative(facing));
            var quads = new ArrayList<net.minecraft.client.renderer.block.model.BakedQuad>();
            var renderTypes = model.getRenderTypes(state, rand, net.neoforged.neoforge.client.model.data.ModelData.EMPTY);
            if (!renderTypes.isEmpty()) {
                for (RenderType rt : renderTypes) {
                    rand.setSeed(state.getSeed(bp));
                    quads.addAll(model.getQuads(state, facing, rand, net.neoforged.neoforge.client.model.data.ModelData.EMPTY, rt));
                    rand.setSeed(state.getSeed(bp));
                    quads.addAll(model.getQuads(state, null, rand, net.neoforged.neoforge.client.model.data.ModelData.EMPTY, rt));
                }
            }
            if (quads.isEmpty()) {
                rand.setSeed(state.getSeed(bp));
                quads.addAll(model.getQuads(state, facing, rand, net.neoforged.neoforge.client.model.data.ModelData.EMPTY, null));
                rand.setSeed(state.getSeed(bp));
                quads.addAll(model.getQuads(state, null, rand, net.neoforged.neoforge.client.model.data.ModelData.EMPTY, null));
            }

            var blockOutward = new ArrayList<WorldSurfQuad>();
            double maxOutDist = Double.NEGATIVE_INFINITY;
            for (var q : quads) {
                int[] vd = q.getVertices();
                if (vd.length < 32) continue;
                Vec3[] p = new Vec3[4];
                for (int i = 0; i < 4; i++) {
                    int o = i * 8;
                    p[i] = new Vec3(bp.getX() + Float.intBitsToFloat(vd[o]),
                            bp.getY() + Float.intBitsToFloat(vd[o + 1]),
                            bp.getZ() + Float.intBitsToFloat(vd[o + 2]));
                }
                Vec3 qn = p[1].subtract(p[0]).cross(p[2].subtract(p[0]));
                double qlen = qn.length();
                if (qlen <= 1e-8) continue;
                qn = qn.scale(1.0 / qlen);
                if (qn.dot(outN) < 0.25) continue;
                double dOut = p[0].add(p[1]).add(p[2]).add(p[3]).scale(0.25).subtract(planeOrigin).dot(outN);
                if (dOut < -1.05 || dOut > 0.85) continue;
                maxOutDist = Math.max(maxOutDist, dOut);
                blockOutward.add(new WorldSurfQuad(p[0], p[1], p[2], p[3], qn, dOut, light));
            }
            // Keep ONLY the actual frontmost surface layer of this block (never paint behind a front wall!)
            for (var sq : blockOutward) {
                if (sq.outDist >= maxOutDist - 0.35) result.add(sq);
            }
        }
        return result;
    }

    private static double sampleSurfaceOutwardOffset(net.minecraft.world.level.Level level,
                                                     BlockPos anchorPos, Direction facing,
                                                     Set<BlockPos> affected) {
        var quads = collectFrontmostOutwardQuads(level, anchorPos, facing, affected);
        if (quads.isEmpty()) return 0.0;
        double best = Double.NEGATIVE_INFINITY;
        for (var q : quads) best = Math.max(best, q.outDist);
        return Double.isFinite(best) ? best : 0.0;
    }

    /** Clips a rotated/scaled decal strictly onto the actual frontmost outward BakedQuads (never into empty air). */
    private static void drawClippedDecal(net.minecraft.world.level.Level level, StickerEntry entry,
                                         PoseStack pose, MultiBufferSource.BufferSource buffers, boolean preview) {
        DecalStickerParams d = entry.decal();
        if (!d.hasImage()) return;
        ResourceLocation tex = resolveDecalTexture(d.imageHash());
        if (tex == null) return;

        Direction facing = entry.facing();
        Vec3 outN = StickerPlaneMath.outward(facing);
        Vec3 uAxis = StickerPlaneMath.axisU(facing);
        Vec3 vAxis = StickerPlaneMath.axisV(facing);
        Vec3 planeOrigin = StickerPlaneMath.faceCenter(entry.anchorPos(), facing);
        double rad = Math.toRadians(d.rotationDegrees());
        double cos = Math.cos(rad), sin = Math.sin(rad);
        double hw = d.width() * 0.5, hh = d.height() * 0.5;
        double bias = preview ? 0.007 : 0.003;
        int tint = preview ? 0xCCFFFFFF : 0xFFFFFFFF;
        var buf = buffers.getBuffer(RenderType.entityTranslucent(tex));
        var mat = pose.last();

        for (var sq : collectFrontmostOutwardQuads(level, entry.anchorPos(), facing, entry.affectedPositions())) {
            Vec3[] p = {sq.p0, sq.p1, sq.p2, sq.p3};
            List<DecalClipVtx> poly = new ArrayList<>(4);
            for (int i = 0; i < 4; i++) {
                Vec3 rel = p[i].subtract(planeOrigin);
                double du = rel.dot(uAxis) - d.offsetU();
                double dv = rel.dot(vAxis) - d.offsetV();
                double lu = du * cos + dv * sin;
                double lv = -du * sin + dv * cos;
                double tu = (lu + hw) / (2.0 * hw);
                double tv = 1.0 - (lv + hh) / (2.0 * hh);
                poly.add(new DecalClipVtx(p[i].add(sq.normal.scale(bias)), tu, tv));
            }
            poly = clipDecalPlane(poly, v -> v.u);
            poly = clipDecalPlane(poly, v -> 1.0 - v.u);
            poly = clipDecalPlane(poly, v -> v.v);
            poly = clipDecalPlane(poly, v -> 1.0 - v.v);
            if (poly.size() < 3) continue;
            int light = preview ? LightTexture.FULL_BRIGHT : sq.light;
            DecalClipVtx v0 = poly.getFirst();
            for (int i = 1; i + 1 < poly.size(); i++) {
                DecalClipVtx v1 = poly.get(i), v2 = poly.get(i + 1);
                emitUvVert(buf, mat, v0.pos, (float) v0.u, (float) v0.v, sq.normal, tint, light);
                emitUvVert(buf, mat, v1.pos, (float) v1.u, (float) v1.v, sq.normal, tint, light);
                emitUvVert(buf, mat, v2.pos, (float) v2.u, (float) v2.v, sq.normal, tint, light);
                emitUvVert(buf, mat, v2.pos, (float) v2.u, (float) v2.v, sq.normal, tint, light);
            }
        }
    }

    /**
     * Window placement preview: clips translucent frame & glass strictly onto real existing surfaces (nothing stuffed into air),
     * plus draws a subtle contour outline at the real surface depth so parameter bounds remain visible while positioning.
     */
    private static void drawClippedWindowPreview(net.minecraft.world.level.Level level, StickerEntry entry,
                                                 PoseStack pose, MultiBufferSource.BufferSource buffers) {
        WindowStickerParams w = entry.window();
        Direction facing = entry.facing();
        Vec3 uAxis = StickerPlaneMath.axisU(facing);
        Vec3 vAxis = StickerPlaneMath.axisV(facing);
        Vec3 planeOrigin = StickerPlaneMath.faceCenter(entry.anchorPos(), facing);
        double cx = w.offsetU(), cy = w.offsetV();
        double hw = w.width() * 0.5, hh = w.height() * 0.5;
        double thick = Math.min(w.frameThickness(), Math.min(hw, hh));
        double rOut = Math.min(w.cornerRadius(), Math.min(hw, hh));
        double ihw = Math.max(0.0, hw - thick), ihh = Math.max(0.0, hh - thick);
        double rIn = Math.max(0.0, Math.min(rOut - thick, Math.min(ihw, ihh)));
        int steps = rOut > 1e-4 ? 6 : 1;

        var outerLoop = ensureCcwUv(StickerPlaneMath.sampleRoundedRect(cx, cy, hw, hh, rOut, steps));
        var innerLoop = (ihw > 1e-3 && ihh > 1e-3)
                ? ensureCcwUv(StickerPlaneMath.sampleRoundedRect(cx, cy, ihw, ihh, rIn, steps))
                : null;

        int frameColor = (0xA8 << 24) | w.frameColorRgb();
        int gAlpha = Math.max(12, Math.min(200, Math.round(w.glassAlpha() * 170f)));
        int glassColor = (gAlpha << 24) | w.glassColorRgb();
        var buf = buffers.getBuffer(RenderType.entityTranslucent(WHITE));
        var mat = pose.last();
        double bias = 0.006;

        var surfQuads = collectFrontmostOutwardQuads(level, entry.anchorPos(), facing, entry.affectedPositions());
        double maxSurfOut = Double.NEGATIVE_INFINITY;
        for (var sq : surfQuads) {
            maxSurfOut = Math.max(maxSurfOut, sq.outDist);
            Vec3[] p = {sq.p0, sq.p1, sq.p2, sq.p3};
            List<DecalClipVtx> basePoly = new ArrayList<>(4);
            for (int i = 0; i < 4; i++) {
                Vec3 rel = p[i].subtract(planeOrigin);
                basePoly.add(new DecalClipVtx(p[i].add(sq.normal.scale(bias)), rel.dot(uAxis), rel.dot(vAxis)));
            }
            List<DecalClipVtx> cutPoly = clipByConvexLoop(basePoly, outerLoop);
            if (cutPoly.size() < 3) continue;

            if (innerLoop == null) {
                if (thick > 1e-4) emitPolyFan(buf, mat, cutPoly, sq.normal, frameColor, LightTexture.FULL_BRIGHT);
                continue;
            }
            List<DecalClipVtx> glassPoly = cutPoly;
            for (int k = 0; k < innerLoop.size() && glassPoly.size() >= 3; k++) {
                var e0 = innerLoop.get(k);
                var e1 = innerLoop.get((k + 1) % innerLoop.size());
                var split = splitByEdge(glassPoly, e0, e1);
                if (thick > 1e-4 && split.out.size() >= 3) {
                    emitPolyFan(buf, mat, split.out, sq.normal, frameColor, LightTexture.FULL_BRIGHT);
                }
                glassPoly = split.in;
            }
            if (glassPoly.size() >= 3 && w.glassAlpha() > 0.004f) {
                emitPolyFan(buf, mat, glassPoly, sq.normal, glassColor, LightTexture.FULL_BRIGHT);
            }
        }

        // Subtle wireframe outline on the real surface depth so parameter bounds are always legible
        double lineOut = Double.isFinite(maxSurfOut) ? maxSurfOut + 0.008 : 0.008;
        var lineBuf = buffers.getBuffer(RenderType.lines());
        for (int i = 0; i < outerLoop.size(); i++) {
            var a = outerLoop.get(i);
            var b = outerLoop.get((i + 1) % outerLoop.size());
            Vec3 pa = StickerPlaneMath.unproject(entry.anchorPos(), facing, a.u(), a.v(), lineOut);
            Vec3 pb = StickerPlaneMath.unproject(entry.anchorPos(), facing, b.u(), b.v(), lineOut);
            emitLine(lineBuf, mat, pa, pb, 0xCC7CE6FF);
        }
    }

    private record PreviewSplit(List<DecalClipVtx> in, List<DecalClipVtx> out) {}

    private static List<DecalClipVtx> clipByConvexLoop(List<DecalClipVtx> poly, List<StickerPlaneMath.UV> loopCcw) {
        List<DecalClipVtx> cur = poly;
        for (int k = 0; k < loopCcw.size() && cur.size() >= 3; k++) {
            cur = splitByEdge(cur, loopCcw.get(k), loopCcw.get((k + 1) % loopCcw.size())).in;
        }
        return cur;
    }

    private static PreviewSplit splitByEdge(List<DecalClipVtx> poly, StickerPlaneMath.UV a, StickerPlaneMath.UV b) {
        var in = new ArrayList<DecalClipVtx>(poly.size() + 2);
        var out = new ArrayList<DecalClipVtx>(poly.size() + 2);
        int n = poly.size();
        for (int i = 0; i < n; i++) {
            DecalClipVtx p = poly.get(i), q = poly.get((i + 1) % n);
            double dp = (b.u() - a.u()) * (p.v - a.v()) - (b.v() - a.v()) * (p.u - a.u());
            double dq = (b.u() - a.u()) * (q.v - a.v()) - (b.v() - a.v()) * (q.u - a.u());
            if (dp >= -1e-7) in.add(p);
            if (dp <= 1e-7) out.add(p);
            if ((dp > 1e-7 && dq < -1e-7) || (dp < -1e-7 && dq > 1e-7)) {
                DecalClipVtx mid = p.lerp(q, dp / (dp - dq));
                in.add(mid);
                out.add(mid);
            }
        }
        return new PreviewSplit(in, out);
    }

    private static void emitPolyFan(VertexConsumer buf, PoseStack.Pose mat, List<DecalClipVtx> poly,
                                    Vec3 n, int argb, int light) {
        DecalClipVtx v0 = poly.getFirst();
        for (int i = 1; i + 1 < poly.size(); i++) {
            DecalClipVtx v1 = poly.get(i), v2 = poly.get(i + 1);
            emitQuad(buf, mat, v0.pos, v1.pos, v2.pos, v2.pos, n, argb, light);
        }
    }

    private static List<StickerPlaneMath.UV> ensureCcwUv(List<StickerPlaneMath.UV> pts) {
        double area2 = 0;
        for (int i = 0; i < pts.size(); i++) {
            var a = pts.get(i);
            var b = pts.get((i + 1) % pts.size());
            area2 += a.u() * b.v() - b.u() * a.v();
        }
        if (area2 >= 0) return pts;
        var rev = new ArrayList<>(pts);
        Collections.reverse(rev);
        return rev;
    }

    private record DecalClipVtx(Vec3 pos, double u, double v) {
        DecalClipVtx lerp(DecalClipVtx o, double t) {
            return new DecalClipVtx(pos.add(o.pos.subtract(pos).scale(t)), u + (o.u - u) * t, v + (o.v - v) * t);
        }
    }

    private static List<DecalClipVtx> clipDecalPlane(List<DecalClipVtx> in, java.util.function.ToDoubleFunction<DecalClipVtx> fn) {
        if (in.isEmpty()) return in;
        var out = new ArrayList<DecalClipVtx>(in.size() + 2);
        DecalClipVtx prev = in.getLast();
        double dp = fn.applyAsDouble(prev);
        for (var cur : in) {
            double dc = fn.applyAsDouble(cur);
            if (dc >= -1e-7) {
                if (dp < -1e-7) out.add(prev.lerp(cur, dp / (dp - dc)));
                out.add(cur);
            } else if (dp >= -1e-7) {
                out.add(prev.lerp(cur, dp / (dp - dc)));
            }
            prev = cur;
            dp = dc;
        }
        return out;
    }

    private static void emitUvVert(VertexConsumer buf, PoseStack.Pose mat, Vec3 p, float u, float v, Vec3 n, int argb, int light) {
        buf.addVertex(mat, (float) p.x, (float) p.y, (float) p.z).setColor(argb).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(mat, (float) n.x, (float) n.y, (float) n.z);
    }

    private static void drawArrowGizmos(net.minecraft.world.level.Level level, StickerEntry entry,
                                        PoseStack pose, MultiBufferSource.BufferSource buffers, int activeAxis) {
        var w = entry.window();
        double cx = w.offsetU(), cy = w.offsetV();
        double hw = w.width() * 0.5, hh = w.height() * 0.5;
        double bias = sampleSurfaceOutwardOffset(level, entry.anchorPos(), entry.facing(), entry.affectedPositions()) + 0.025;
        Vec3 n = StickerPlaneMath.outward(entry.facing());
        var buf = buffers.getBuffer(RenderType.entityTranslucent(WHITE));
        var mat = pose.last();

        // UP (1), DOWN (2), LEFT (3), RIGHT (4)
        drawArrow(buf, mat, entry.anchorPos(), entry.facing(), cx, cy + hh + 0.06, 0, 1, n,
                activeAxis == 1 ? 0xFFFFEE44 : 0xEE44DD55, bias);
        drawArrow(buf, mat, entry.anchorPos(), entry.facing(), cx, cy - hh - 0.06, 0, -1, n,
                activeAxis == 2 ? 0xFFFFEE44 : 0xEE44DD55, bias);
        drawArrow(buf, mat, entry.anchorPos(), entry.facing(), cx - hw - 0.06, cy, -1, 0, n,
                activeAxis == 3 ? 0xFFFFEE44 : 0xEEEE4444, bias);
        drawArrow(buf, mat, entry.anchorPos(), entry.facing(), cx + hw + 0.06, cy, 1, 0, n,
                activeAxis == 4 ? 0xFFFFEE44 : 0xEEEE4444, bias);
    }

    private static void drawArrow(VertexConsumer buf, PoseStack.Pose mat, BlockPos pos, Direction face,
                                  double sx, double sy, double dx, double dy, Vec3 n, int color, double bias) {
        double px = -dy, py = dx;
        double shaftLen = 0.20, shaftHalf = 0.035, headLen = 0.14, headHalf = 0.09;
        Vec3 s0 = StickerPlaneMath.unproject(pos, face, sx + px * shaftHalf, sy + py * shaftHalf, bias);
        Vec3 s1 = StickerPlaneMath.unproject(pos, face, sx - px * shaftHalf, sy - py * shaftHalf, bias);
        Vec3 s2 = StickerPlaneMath.unproject(pos, face, sx + dx * shaftLen - px * shaftHalf, sy + dy * shaftLen - py * shaftHalf, bias);
        Vec3 s3 = StickerPlaneMath.unproject(pos, face, sx + dx * shaftLen + px * shaftHalf, sy + dy * shaftLen + py * shaftHalf, bias);
        emitQuad(buf, mat, s0, s1, s2, s3, n, color, LightTexture.FULL_BRIGHT);

        Vec3 h0 = StickerPlaneMath.unproject(pos, face, sx + dx * shaftLen + px * headHalf, sy + dy * shaftLen + py * headHalf, bias);
        Vec3 h1 = StickerPlaneMath.unproject(pos, face, sx + dx * shaftLen - px * headHalf, sy + dy * shaftLen - py * headHalf, bias);
        Vec3 tip = StickerPlaneMath.unproject(pos, face, sx + dx * (shaftLen + headLen), sy + dy * (shaftLen + headLen), bias);
        emitQuad(buf, mat, h0, h1, tip, tip, n, color, LightTexture.FULL_BRIGHT);
    }

    private static void drawGrid16Overlay(BlockPos pos, Direction facing, double surfOffset,
                                          PoseStack pose, MultiBufferSource.BufferSource buffers) {
        var buf = buffers.getBuffer(RenderType.lines());
        var mat = pose.last();
        double bias = surfOffset + 0.008;
        for (int i = 0; i <= 16; i++) {
            double c = i / 16.0 - 0.5;
            int col = (i == 0 || i == 8 || i == 16) ? 0xCC66D9FF : 0x55FFFFFF;
            Vec3 a = StickerPlaneMath.unproject(pos, facing, c, -0.5, bias);
            Vec3 b = StickerPlaneMath.unproject(pos, facing, c, 0.5, bias);
            emitLine(buf, mat, a, b, col);
            Vec3 c0 = StickerPlaneMath.unproject(pos, facing, -0.5, c, bias);
            Vec3 c1 = StickerPlaneMath.unproject(pos, facing, 0.5, c, bias);
            emitLine(buf, mat, c0, c1, col);
        }
    }

    private static void onRenderGui(RenderGuiEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null) return;
        var snap = HexieBridges.Client.stickerClient().activePrecisionSnap();
        if (snap != null) {
            var gui = event.getGuiGraphics();
            int cx = mc.getWindow().getGuiScaledWidth() / 2;
            int cy = mc.getWindow().getGuiScaledHeight() / 2;
            String text = String.format("16x16 精細放置 | 上:%d格  下:%d格  左:%d格  右:%d格",
                    snap.topPx(), snap.bottomPx(), snap.leftPx(), snap.rightPx());
            int w = mc.font.width(text) + 12;
            gui.fill(cx - w / 2, cy + 16, cx + w / 2, cy + 32, 0xBB12161E);
            gui.drawCenteredString(mc.font, text, cx, cy + 20, 0xFF8CE6FF);
            return;
        }
        var preview = HexieBridges.Client.stickerClient().activePreviewOrEditing();
        if (preview != null && !HexieBridges.Client.stickerClient().isFreeCamActive() && preview.mode() == StickerMode.DECAL) {
            var gui = event.getGuiGraphics();
            int cx = mc.getWindow().getGuiScaledWidth() / 2;
            int cy = mc.getWindow().getGuiScaledHeight() / 2;
            var d = preview.decal();
            String text = String.format(Locale.ROOT, "貼花預覽 | %.2f×%.2f 旋轉:%.0f° (滾輪縮放 / Ctrl+滾輪旋轉 / 右鍵上傳)",
                    d.width(), d.height(), d.rotationDegrees());
            int w = mc.font.width(text) + 12;
            gui.fill(cx - w / 2, cy + 16, cx + w / 2, cy + 32, 0xBB12161E);
            gui.drawCenteredString(mc.font, text, cx, cy + 20, 0xFFA8F0B8);
        }
    }

    private static ResourceLocation resolveDecalTexture(String hash) {
        ResourceLocation existing = DECAL_TEXTURES.get(hash);
        if (existing != null) return existing;
        var bytesOpt = HexieBridges.sticker().clientImage(hash);
        if (bytesOpt.isEmpty()) {
            if (REQUESTED_HASHES.add(hash)) {
                HexieBridges.sync().sendStickerImageRequest(hash);
            }
            return null;
        }
        try {
            NativeImage img = NativeImage.read(new ByteArrayInputStream(bytesOpt.get()));
            DynamicTexture dyn = new DynamicTexture(img);
            String safe = hash.substring(0, Math.min(24, hash.length())).replaceAll("[^a-z0-9_]", "_");
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(HexieConstants.MOD_ID, "dynamic/sticker_" + safe);
            Minecraft.getInstance().getTextureManager().register(loc, dyn);
            DECAL_TEXTURES.put(hash, loc);
            return loc;
        } catch (Exception e) {
            return null;
        }
    }

    private static void emitQuad(VertexConsumer buf, PoseStack.Pose mat, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3,
                                 Vec3 n, int argb, int light) {
        buf.addVertex(mat, (float) p0.x, (float) p0.y, (float) p0.z).setColor(argb).setUv(0, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(mat, (float) n.x, (float) n.y, (float) n.z);
        buf.addVertex(mat, (float) p1.x, (float) p1.y, (float) p1.z).setColor(argb).setUv(1, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(mat, (float) n.x, (float) n.y, (float) n.z);
        buf.addVertex(mat, (float) p2.x, (float) p2.y, (float) p2.z).setColor(argb).setUv(1, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(mat, (float) n.x, (float) n.y, (float) n.z);
        buf.addVertex(mat, (float) p3.x, (float) p3.y, (float) p3.z).setColor(argb).setUv(0, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(mat, (float) n.x, (float) n.y, (float) n.z);
    }

    private static void emitTexturedQuad(VertexConsumer buf, PoseStack.Pose mat, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3,
                                         Vec3 n, int argb, int light) {
        buf.addVertex(mat, (float) p0.x, (float) p0.y, (float) p0.z).setColor(argb).setUv(0, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(mat, (float) n.x, (float) n.y, (float) n.z);
        buf.addVertex(mat, (float) p1.x, (float) p1.y, (float) p1.z).setColor(argb).setUv(1, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(mat, (float) n.x, (float) n.y, (float) n.z);
        buf.addVertex(mat, (float) p2.x, (float) p2.y, (float) p2.z).setColor(argb).setUv(1, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(mat, (float) n.x, (float) n.y, (float) n.z);
        buf.addVertex(mat, (float) p3.x, (float) p3.y, (float) p3.z).setColor(argb).setUv(0, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(mat, (float) n.x, (float) n.y, (float) n.z);
    }

    private static void emitLine(VertexConsumer buf, PoseStack.Pose mat, Vec3 a, Vec3 b, int argb) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        float nx = len > 1e-6 ? (float) (d.x / len) : 0f;
        float ny = len > 1e-6 ? (float) (d.y / len) : 1f;
        float nz = len > 1e-6 ? (float) (d.z / len) : 0f;
        buf.addVertex(mat, (float) a.x, (float) a.y, (float) a.z).setColor(argb).setNormal(mat, nx, ny, nz);
        buf.addVertex(mat, (float) b.x, (float) b.y, (float) b.z).setColor(argb).setNormal(mat, nx, ny, nz);
    }
}
