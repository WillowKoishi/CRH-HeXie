package willow.train.hexie.render.rendererbridge;

import com.mojang.blaze3d.platform.NativeImage;
import dev.hexie.renderer.Vec;
import dev.hexie.renderer.parse.CompiledMesh;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Converts live Minecraft BakedModels (ordinary blocks, neoforge:obj, and StaticShellModel CRH1/CRH2 OBJ shells)
 * into hexie-renderer CompiledMesh instances preserving exact 3D positions, normals, block-atlas UVs, and render layers.
 */
final class BakedModelMeshCompiler {
    private BakedModelMeshCompiler() {}

    /** Compiles a single BakedModel in block-local coordinates for StaticShell registration. */
    static CompiledMesh compileBlockStateModel(BakedModel model, BlockState state) {
        if (model == null || state == null) return CompiledMesh.EMPTY;
        var rand = RandomSource.create(42L);
        var tris = new ArrayList<CompiledMesh.Tri>(64);
        int faceId = 0;

        for (RenderType layer : model.getRenderTypes(state, rand, ModelData.EMPTY)) {
            String mat = materialForRenderType(layer);
            boolean translucent = mat.endsWith(":translucent");
            boolean cutout = mat.endsWith(":cutout");
            for (int side = -1; side < 6; side++) {
                rand.setSeed(42L);
                Direction dir = side < 0 ? null : Direction.values()[side];
                for (BakedQuad q : model.getQuads(state, dir, rand, ModelData.EMPTY, layer)) {
                    faceId = unpackQuad(q, 0.0, 0.0, 0.0, mat, translucent, cutout, faceId, tris);
                }
            }
        }
        return tris.isEmpty() ? CompiledMesh.EMPTY : CompiledMesh.of(tris);
    }

    static CompiledMesh compileWorldBlocks(Level level, Set<BlockPos> positions, Direction facingHint) {
        var mc = Minecraft.getInstance();
        if (mc == null || level == null || positions.isEmpty()) return CompiledMesh.EMPTY;
        var dispatcher = mc.getBlockRenderer();
        var rand = RandomSource.create();
        var tris = new ArrayList<CompiledMesh.Tri>(positions.size() * 12);
        int faceId = 0;

        for (BlockPos bp : positions) {
            var state = level.getBlockState(bp);
            if (state.isAir()) continue;
            var model = dispatcher.getBlockModel(state);
            // Iterate all render types so both vanilla JSON and NeoForge CompositeModel/OBJ (CRH1/CRH2) emit quads.
            var quads = new ArrayList<BakedQuad>();
            var renderTypes = model.getRenderTypes(state, rand, ModelData.EMPTY);
            if (!renderTypes.isEmpty()) {
                for (RenderType layer : renderTypes) {
                    if (facingHint != null) {
                        rand.setSeed(state.getSeed(bp));
                        quads.addAll(model.getQuads(state, facingHint, rand, ModelData.EMPTY, layer));
                    } else {
                        for (Direction d : Direction.values()) {
                            rand.setSeed(state.getSeed(bp));
                            quads.addAll(model.getQuads(state, d, rand, ModelData.EMPTY, layer));
                        }
                    }
                    rand.setSeed(state.getSeed(bp));
                    quads.addAll(model.getQuads(state, null, rand, ModelData.EMPTY, layer));
                }
            }
            if (quads.isEmpty()) {
                if (facingHint != null) {
                    rand.setSeed(state.getSeed(bp));
                    quads.addAll(model.getQuads(state, facingHint, rand, ModelData.EMPTY, null));
                } else {
                    for (Direction d : Direction.values()) {
                        rand.setSeed(state.getSeed(bp));
                        quads.addAll(model.getQuads(state, d, rand, ModelData.EMPTY, null));
                    }
                }
                rand.setSeed(state.getSeed(bp));
                quads.addAll(model.getQuads(state, null, rand, ModelData.EMPTY, null));
            }

            for (BakedQuad q : quads) {
                faceId = unpackQuad(q, bp.getX(), bp.getY(), bp.getZ(), "atlas:blocks", false, false, faceId, tris);
            }
        }
        return tris.isEmpty() ? CompiledMesh.EMPTY : CompiledMesh.of(tris);
    }

    private static String materialForRenderType(RenderType layer) {
        if (layer == RenderType.translucent() || layer == RenderType.tripwire()) return "atlas:blocks:translucent";
        if (layer == RenderType.cutout() || layer == RenderType.cutoutMipped()) return "atlas:blocks:cutout";
        return "atlas:blocks";
    }

    private static int unpackQuad(BakedQuad q, double ox, double oy, double oz,
                                  String materialId, boolean translucent, boolean alphaPruneCutout,
                                  int nextFaceId, List<CompiledMesh.Tri> out) {
        int[] vd = q.getVertices();
        if (vd.length < 32) return nextFaceId;
        var vs = new CompiledMesh.Vertex[4];
        Vec[] ps = new Vec[4];
        float[] us = new float[4], vts = new float[4];
        Vec[] ns = new Vec[4];
        for (int i = 0; i < 4; i++) {
            int o = i * 8;
            ps[i] = new Vec(
                    ox + Float.intBitsToFloat(vd[o]),
                    oy + Float.intBitsToFloat(vd[o + 1]),
                    oz + Float.intBitsToFloat(vd[o + 2]));
            us[i] = Float.intBitsToFloat(vd[o + 4]);
            vts[i] = Float.intBitsToFloat(vd[o + 5]);
            int np = vd[o + 7];
            double nx = ((byte) (np & 0xFF)) / 127.0;
            double ny = ((byte) ((np >> 8) & 0xFF)) / 127.0;
            double nz = ((byte) ((np >> 16) & 0xFF)) / 127.0;
            double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
            ns[i] = nl > 1e-4 ? new Vec(nx / nl, ny / nl, nz / nl) : null;
        }
        Vec fn = ps[1].minus(ps[0]).cross(ps[2].minus(ps[0]));
        double fl = Math.sqrt(fn.dot(fn));
        if (fl <= 1e-10) return nextFaceId;
        Vec fallbackN = fn.times(1.0 / fl);
        for (int i = 0; i < 4; i++) {
            vs[i] = new CompiledMesh.Vertex(ps[i], ns[i] != null ? ns[i] : fallbackN, us[i], vts[i]);
        }

        // For cutout carriage shells (e.g. CRH1/CRH2 *_body OBJ models that share the same mesh as *_glass
        // and punch window holes via texture alpha == 0), prune 100%-transparent quads and greedy-merge
        // mixed quads into opaque sub-quads so raytraced light streams cleanly through every window!
        if (alphaPruneCutout && q.getSprite() != null) {
            int pruned = tryEmitAlphaPrunedCutout(q.getSprite(), vs, materialId, nextFaceId, out);
            if (pruned >= 0) return pruned;
        }

        CompiledMesh.Tri t0 = new CompiledMesh.Tri(nextFaceId, vs[0], vs[1], vs[2], materialId, 0xFFFFFFFF, translucent);
        if (t0.area2() > 1e-10) out.add(t0);
        CompiledMesh.Tri t1 = new CompiledMesh.Tri(nextFaceId, vs[0], vs[2], vs[3], materialId, 0xFFFFFFFF, translucent);
        if (t1.area2() > 1e-10) out.add(t1);
        return nextFaceId + 1;
    }

    /**
     * Evaluates the quad's sprite alpha mask. Returns updated nextFaceId if handled (0 tris for fully transparent
     * quads, greedy-merged opaque sub-quads for mixed window-hole quads), or -1 to emit the original quad unchanged.
     */
    private static int tryEmitAlphaPrunedCutout(TextureAtlasSprite sprite, CompiledMesh.Vertex[] vs,
                                                String materialId, int nextFaceId, List<CompiledMesh.Tri> out) {
        SpriteContents contents = sprite.contents();
        if (contents == null) return -1;
        int sw = contents.width(), sh = contents.height();
        if (sw <= 0 || sh <= 0) return -1;
        float u0 = sprite.getU0(), u1 = sprite.getU1();
        float v0 = sprite.getV0(), v1 = sprite.getV1();
        float du = u1 - u0, dv = v1 - v0;
        if (Math.abs(du) < 1e-9f || Math.abs(dv) < 1e-9f) return -1;

        double[] su = new double[4], sv = new double[4];
        double minSu = Double.POSITIVE_INFINITY, maxSu = Double.NEGATIVE_INFINITY;
        double minSv = Double.POSITIVE_INFINITY, maxSv = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            su[i] = (vs[i].u() - u0) / du;
            sv[i] = (vs[i].v() - v0) / dv;
            if (su[i] < minSu) minSu = su[i];
            if (su[i] > maxSu) maxSu = su[i];
            if (sv[i] < minSv) minSv = sv[i];
            if (sv[i] > maxSv) maxSv = sv[i];
        }
        int gxCount = Math.max(1, Math.min(32, (int) Math.ceil((maxSu - minSu) * sw)));
        int gyCount = Math.max(1, Math.min(32, (int) Math.ceil((maxSv - minSv) * sh)));
        NativeImage img = readableImage(contents);

        boolean[] opaque = new boolean[gxCount * gyCount];
        int opaqueCount = 0;
        for (int gy = 0; gy < gyCount; gy++) {
            double t = (gy + 0.5) / gyCount;
            for (int gx = 0; gx < gxCount; gx++) {
                double s = (gx + 0.5) / gxCount;
                double lu = bilerp(su[0], su[1], su[2], su[3], s, t);
                double lv = bilerp(sv[0], sv[1], sv[2], sv[3], s, t);
                int px = Math.max(0, Math.min(sw - 1, (int) Math.floor(lu * sw)));
                int py = Math.max(0, Math.min(sh - 1, (int) Math.floor(lv * sh)));
                boolean isOpaque = sampleOpaque(contents, img, px, py);
                if (isOpaque) {
                    opaque[gy * gxCount + gx] = true;
                    opaqueCount++;
                }
            }
        }
        if (opaqueCount == 0) {
            return nextFaceId; // 100% transparent cutout quad (e.g. window glass faces in *_body OBJ): drop completely!
        }
        if (opaqueCount == opaque.length) {
            return -1; // 100% opaque: emit original 2 triangles
        }
        // Mixed quad (opaque frame/wall + transparent window opening): 2D greedy rectangle merge!
        int fid = nextFaceId;
        for (int y = 0; y < gyCount; y++) {
            for (int x = 0; x < gxCount; x++) {
                if (!opaque[y * gxCount + x]) continue;
                int rw = 1;
                while (x + rw < gxCount && opaque[y * gxCount + x + rw]) rw++;
                int rh = 1;
                outerH:
                while (y + rh < gyCount) {
                    int row = (y + rh) * gxCount + x;
                    for (int dx = 0; dx < rw; dx++) {
                        if (!opaque[row + dx]) break outerH;
                    }
                    rh++;
                }
                for (int dy = 0; dy < rh; dy++) {
                    int row = (y + dy) * gxCount + x;
                    for (int dx = 0; dx < rw; dx++) opaque[row + dx] = false;
                }
                double s0 = (double) x / gxCount, s1 = (double) (x + rw) / gxCount;
                double t0 = (double) y / gyCount, t1 = (double) (y + rh) / gyCount;
                var c0 = bilerpVertex(vs, s0, t0);
                var c1 = bilerpVertex(vs, s0, t1);
                var c2 = bilerpVertex(vs, s1, t1);
                var c3 = bilerpVertex(vs, s1, t0);
                var tri0 = new CompiledMesh.Tri(fid, c0, c1, c2, materialId, 0xFFFFFFFF, false);
                if (tri0.area2() > 1e-10) out.add(tri0);
                var tri1 = new CompiledMesh.Tri(fid, c0, c2, c3, materialId, 0xFFFFFFFF, false);
                if (tri1.area2() > 1e-10) out.add(tri1);
                fid++;
            }
        }
        return fid;
    }

    private static NativeImage readableImage(SpriteContents contents) {
        try {
            NativeImage orig = contents.getOriginalImage();
            if (orig != null && orig.getWidth() > 0 && orig.getHeight() > 0) {
                orig.getPixelRGBA(0, 0); // verifies native pointer is still valid
                return orig;
            }
        } catch (Throwable ignored) {}
        try {
            if (contents.byMipLevel != null && contents.byMipLevel.length > 0 && contents.byMipLevel[0] != null) {
                contents.byMipLevel[0].getPixelRGBA(0, 0);
                return contents.byMipLevel[0];
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static boolean sampleOpaque(SpriteContents contents, NativeImage img, int px, int py) {
        if (img != null) {
            try {
                int alpha = (img.getPixelRGBA(px, py) >>> 24) & 0xFF;
                return alpha >= 128;
            } catch (Throwable ignored) {}
        }
        try {
            return !contents.isTransparent(0, px, py);
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static double bilerp(double v0, double v1, double v2, double v3, double s, double t) {
        double a = v0 + (v1 - v0) * t;
        double b = v3 + (v2 - v3) * t;
        return a + (b - a) * s;
    }

    private static CompiledMesh.Vertex bilerpVertex(CompiledMesh.Vertex[] vs, double s, double t) {
        double px = bilerp(vs[0].pos().x(), vs[1].pos().x(), vs[2].pos().x(), vs[3].pos().x(), s, t);
        double py = bilerp(vs[0].pos().y(), vs[1].pos().y(), vs[2].pos().y(), vs[3].pos().y(), s, t);
        double pz = bilerp(vs[0].pos().z(), vs[1].pos().z(), vs[2].pos().z(), vs[3].pos().z(), s, t);
        double nx = bilerp(vs[0].normal().x(), vs[1].normal().x(), vs[2].normal().x(), vs[3].normal().x(), s, t);
        double ny = bilerp(vs[0].normal().y(), vs[1].normal().y(), vs[2].normal().y(), vs[3].normal().y(), s, t);
        double nz = bilerp(vs[0].normal().z(), vs[1].normal().z(), vs[2].normal().z(), vs[3].normal().z(), s, t);
        double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
        Vec n = nl > 1e-6 ? new Vec(nx / nl, ny / nl, nz / nl) : vs[0].normal();
        double u = bilerp(vs[0].u(), vs[1].u(), vs[2].u(), vs[3].u(), s, t);
        double v = bilerp(vs[0].v(), vs[1].v(), vs[2].v(), vs[3].v(), s, t);
        return new CompiledMesh.Vertex(new Vec(px, py, pz), n, u, v);
    }
}
