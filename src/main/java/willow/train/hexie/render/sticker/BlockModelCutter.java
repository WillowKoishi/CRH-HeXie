package willow.train.hexie.render.sticker;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.sticker.StickerMeshCutterBridge;
import willow.train.hexie.core.HexieConstants;
import willow.train.hexie.core.sticker.StickerEntry;
import willow.train.hexie.core.sticker.StickerMode;
import willow.train.hexie.core.sticker.StickerPlaneMath;
import willow.train.hexie.core.sticker.StickerPlaneMath.UV;
import willow.train.hexie.core.sticker.WindowStickerParams;

/**
 * Real BakedQuad coplanar/curved single-layer & zero-thickness cutter for BOTH ordinary blocks
 * (via SectionCompiler) AND OBJ models (neoforge:obj multi-block models & CRH1/CRH2 StaticShellSectionGeometry).
 *
 * Cutting semantics:
 * - Projects the rounded-rect window contour onto the aimed facing plane (u, v, w).
 * - Works on flat blocks AND curved/sloped OBJ carriage walls using per-point surface depth fields wEntry(u,v) & wExit(u,v).
 * - Solid / thick shell: cuts entry layer + exit layer, hollows interior, bridges cavity walls following the surface curve,
 *   and NEVER cuts deeper behind wExit.
 * - Zero-thickness shell: single cut (一刀), no cavity walls, frame & glass seated flush on wEntry(u,v).
 * - Supports cross-block windows, offset-to-neighbor windows (anchor untouched), and multi-block root/member OBJ models.
 */
public final class BlockModelCutter implements StickerMeshCutterBridge {
    private static final double THICKNESS_EPS = 0.004;
    private static final double LAYER_GAP_EPS = 0.02;
    private static final double CLIP_EPS = 1e-7;
    private static final double NORMAL_DOT_MIN = 0.25;
    private static final ResourceLocation WHITE_SPRITE_ID = HexieConstants.asResource("block/white_pixel");

    private final Map<Long, List<StickerEntry>> chunkWindows = new ConcurrentHashMap<>();
    private final Map<BlockPos, List<StickerEntry>> blockWindows = new ConcurrentHashMap<>();
    private final Map<BlockPos, List<StickerEntry>> nearBlockWindows = new ConcurrentHashMap<>();
    private final List<java.util.function.Consumer<Set<BlockPos>>> cutChangedListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private volatile StickerEntry liveOverride;

    public static final BlockModelCutter INSTANCE = new BlockModelCutter();

    private BlockModelCutter() {}

    public void addCutChangedListener(java.util.function.Consumer<Set<BlockPos>> listener) {
        cutChangedListeners.add(listener);
    }

    public static void install() {
        HexieBridges.Client.registerStickerMeshCutter(INSTANCE);
    }

    public synchronized void clearAll() {
        chunkWindows.clear();
        blockWindows.clear();
        nearBlockWindows.clear();
        liveOverride = null;
    }

    @Override
    public void onChunkSynced(int chunkX, int chunkZ, List<StickerEntry> entries) {
        long key = ChunkPos.asLong(chunkX, chunkZ);
        var dirty = new HashSet<BlockPos>();
        List<StickerEntry> prev = chunkWindows.get(key);
        if (prev != null) collectDirty(prev, dirty);
        var nextWins = entries.stream().filter(e -> e.mode() == StickerMode.WINDOW).toList();
        if (nextWins.isEmpty()) chunkWindows.remove(key);
        else chunkWindows.put(key, nextWins);
        collectDirty(nextWins, dirty);
        rebuildBlockIndex();
        notifyCutListeners(dirty);
        markSectionsDirty(dirty);
    }

    @Override
    public void setLiveOverride(StickerEntry editing) {
        var dirty = new HashSet<BlockPos>();
        StickerEntry prev = liveOverride;
        if (prev != null) collectDirty(List.of(prev), dirty);
        liveOverride = (editing != null && editing.mode() == StickerMode.WINDOW) ? editing : null;
        if (liveOverride != null) collectDirty(List.of(liveOverride), dirty);
        rebuildBlockIndex();
        notifyCutListeners(dirty);
        markSectionsDirty(dirty);
    }

    private void notifyCutListeners(Set<BlockPos> dirty) {
        if (dirty.isEmpty() || cutChangedListeners.isEmpty()) return;
        Set<BlockPos> snapshot = Set.copyOf(dirty);
        var mc = Minecraft.getInstance();
        if (mc != null) {
            mc.execute(() -> {
                for (var l : cutChangedListeners) l.accept(snapshot);
            });
        }
    }

    private static void collectDirty(List<StickerEntry> list, Set<BlockPos> out) {
        for (var e : list) {
            out.add(e.anchorPos());
            for (BlockPos bp : e.affectedPositions()) {
                out.add(bp);
                // Include immediate vertical/horizontal neighbors so multi-block OBJ roots rebuild too
                out.add(bp.below());
                out.add(bp.below(2));
                out.add(bp.above());
                out.add(bp.north());
                out.add(bp.south());
                out.add(bp.east());
                out.add(bp.west());
            }
        }
    }

    private synchronized void rebuildBlockIndex() {
        var exact = new HashMap<BlockPos, List<StickerEntry>>();
        var near = new HashMap<BlockPos, Set<StickerEntry>>();
        StickerEntry ov = liveOverride;
        for (var list : chunkWindows.values()) {
            for (var e : list) {
                if (ov != null && e.anchorKey().equals(ov.anchorKey())) continue;
                indexEntry(exact, near, e);
            }
        }
        if (ov != null) indexEntry(exact, near, ov);
        blockWindows.clear();
        exact.forEach((p, l) -> blockWindows.put(p, List.copyOf(l)));
        nearBlockWindows.clear();
        near.forEach((p, s) -> nearBlockWindows.put(p, List.copyOf(s)));
    }

    private static void indexEntry(Map<BlockPos, List<StickerEntry>> exact,
                                   Map<BlockPos, Set<StickerEntry>> near, StickerEntry e) {
        for (BlockPos bp : e.affectedPositions()) {
            BlockPos imm = bp.immutable();
            exact.computeIfAbsent(imm, k -> new ArrayList<>()).add(e);
            // Multi-block OBJ / JSON models can have their root block up to +-1 XZ, -2..+1 Y from a member cell
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = -2; dy <= 1; dy++) {
                        near.computeIfAbsent(imm.offset(dx, dy, dz), k -> new LinkedHashSet<>()).add(e);
                    }
                }
            }
        }
    }

    private static void markSectionsDirty(Set<BlockPos> positions) {
        if (positions.isEmpty()) return;
        var mc = Minecraft.getInstance();
        if (mc == null || mc.levelRenderer == null) return;
        mc.execute(() -> {
            if (mc.levelRenderer == null) return;
            var sections = new HashSet<Long>();
            for (BlockPos p : positions) {
                int sx = p.getX() >> 4, sy = p.getY() >> 4, sz = p.getZ() >> 4;
                long sk = (((long) sx & 0x3FFFFFL) << 42) | (((long) sz & 0x3FFFFFL) << 20) | ((long) sy & 0xFFFFFL);
                if (sections.add(sk)) {
                    mc.levelRenderer.setSectionDirtyWithNeighbors(sx, sy, sz);
                }
            }
        });
    }

    @Override
    public boolean hasWindowCutAt(BlockPos pos) {
        return !blockWindows.isEmpty() && blockWindows.containsKey(pos);
    }

    @Override
    public boolean hasCandidateCutAt(BlockPos pos) {
        return !nearBlockWindows.isEmpty() && nearBlockWindows.containsKey(pos);
    }

    @Override
    public boolean shouldExposeFaceIntoCut(BlockPos pos, Direction side, BlockPos neighborPos) {
        if (blockWindows.isEmpty()) return false;
        List<StickerEntry> neighborWins = blockWindows.get(neighborPos);
        if (neighborWins == null || neighborWins.isEmpty()) return false;
        for (var e : neighborWins) {
            if (e.facing().getAxis() == side.getAxis()) return true;
        }
        return false;
    }

    /**
     * Returns all active window cuts whose affectedPositions intersect any cell of an OBJ owner.
     */
    public List<StickerEntry> windowsForOwnerCells(Set<BlockPos> ownerCells) {
        if (blockWindows.isEmpty() || ownerCells.isEmpty()) return List.of();
        LinkedHashSet<StickerEntry> found = null;
        for (BlockPos cell : ownerCells) {
            List<StickerEntry> list = blockWindows.get(cell);
            if (list != null && !list.isEmpty()) {
                if (found == null) found = new LinkedHashSet<>();
                found.addAll(list);
            }
        }
        return found == null ? List.of() : List.copyOf(found);
    }

    public boolean hasAnyWindowCutInCells(Set<BlockPos> cells) {
        if (blockWindows.isEmpty() || cells.isEmpty()) return false;
        for (BlockPos cell : cells) {
            if (blockWindows.containsKey(cell)) return true;
        }
        return false;
    }

    /**
     * Cuts a CRH1/CRH2 OBJ StaticShell owner by recombining its split cell quads into ownerRoot-local space
     * so overhanging curved skins (e.g. z in [-0.0875, 0] + [0, 0.1875]) and stacked sub-boxes cut seamlessly.
     */
    public void tesselateStaticShellOwner(BlockPos ownerRoot, BlockPos sectionOrigin,
                                          List<StaticShellQuadInput> sectionQuads,
                                          List<StaticShellQuadInput> fullOwnerQuads,
                                          Set<BlockPos> ownerCells,
                                          List<StickerEntry> windows,
                                          BlockAndTintGetter level,
                                          PoseStack pose,
                                          Function<RenderType, VertexConsumer> layerSupplier) {
        if (windows.isEmpty() || sectionQuads.isEmpty()) return;

        // Group this section's quads by RenderType in ownerRoot-local coordinates
        var byLayer = new LinkedHashMap<RenderType, List<MeshTri>>();
        for (var sq : sectionQuads) {
            var list = byLayer.computeIfAbsent(sq.renderType(), k -> new ArrayList<>());
            unpackStaticShellQuad(sq.quad(), sq.cellOffset(), sq.lights(), list);
        }

        // Build full-owner triangle list (across all cells of this owner) for continuous curved depth-field analysis
        var fullTris = new ArrayList<MeshTri>(fullOwnerQuads.size() * 2);
        for (var sq : fullOwnerQuads) {
            unpackStaticShellQuad(sq.quad(), sq.cellOffset(), sq.lights(), fullTris);
        }

        var depthFields = new LinkedHashMap<StickerEntry, SurfaceDepthField>();
        for (var win : windows) {
            var df = analyzeDepthField(fullTris, Map.of(), ownerRoot, win);
            if (df.valid) depthFields.put(win, df);
        }

        var cutByLayer = new LinkedHashMap<RenderType, List<MeshTri>>();
        var frameQuads = new ArrayList<ColoredQuad>();
        var glassQuads = new ArrayList<ColoredQuad>();
        for (var layerEntry : byLayer.entrySet()) {
            List<MeshTri> layerTris = layerEntry.getValue();
            for (var dfEntry : depthFields.entrySet()) {
                var cut = applyCutWithDepthField(layerTris, ownerRoot, level, dfEntry.getKey(), dfEntry.getValue());
                layerTris = cut.remaining;
                frameQuads.addAll(cut.frameQuads);
                glassQuads.addAll(cut.glassQuads);
            }
            cutByLayer.put(layerEntry.getKey(), layerTris);
        }

        pose.pushPose();
        pose.translate(ownerRoot.getX() - sectionOrigin.getX(),
                ownerRoot.getY() - sectionOrigin.getY(),
                ownerRoot.getZ() - sectionOrigin.getZ());
        PoseStack.Pose mat = pose.last();
        for (var layerEntry : cutByLayer.entrySet()) {
            VertexConsumer buf = layerSupplier.apply(layerEntry.getKey());
            for (var tri : layerEntry.getValue()) emitQuadTri(buf, mat, tri);
        }
        emitFrameAndGlass(frameQuads, glassQuads, mat, layerSupplier);
        pose.popPose();
    }

    public record StaticShellQuadInput(BlockPos cellOffset, BakedQuad quad, RenderType renderType, int[] lights) {}

    @Override
    public boolean tesselateCutBlock(BlockRenderDispatcher dispatcher, BlockState state, BlockPos pos,
                                     BlockAndTintGetter region, PoseStack pose, VertexConsumer layerConsumer,
                                     Function<RenderType, VertexConsumer> layerSupplier, RandomSource random,
                                     ModelData modelData, RenderType renderType) {
        List<StickerEntry> exactWins = blockWindows.get(pos);
        List<StickerEntry> nearWins = nearBlockWindows.get(pos);
        if ((exactWins == null || exactWins.isEmpty()) && (nearWins == null || nearWins.isEmpty())) return false;

        BakedModel model = dispatcher.getBlockModel(state);
        var tris = collectBlockTriangles(model, state, pos, region, random, modelData, renderType);
        if (tris.isEmpty()) return false;

        // Exact windows always apply; nearby windows apply ONLY when this block's model actually
        // extends outside [0, 1]^3 and enters one of the window's affectedPositions (e.g. multi-block OBJ / JSON panels).
        List<StickerEntry> windows;
        if (!extendsOutsideUnitCell(tris)) {
            if (exactWins == null || exactWins.isEmpty()) return false;
            windows = exactWins;
        } else {
            var applicable = new LinkedHashSet<StickerEntry>();
            if (exactWins != null) applicable.addAll(exactWins);
            if (nearWins != null) {
                for (var w : nearWins) {
                    if (!applicable.contains(w) && trianglesEnterAffected(tris, pos, w.affectedPositions())) {
                        applicable.add(w);
                    }
                }
            }
            if (applicable.isEmpty()) return false;
            windows = List.copyOf(applicable);
        }

        var allBackDepths = collectUnculledQuadsByFace(model, state, random, modelData, renderType);

        var frameQuads = new ArrayList<ColoredQuad>();
        var glassQuads = new ArrayList<ColoredQuad>();
        boolean anyCut = false;

        for (var entry : windows) {
            var depthField = analyzeDepthField(tris, allBackDepths, pos, entry);
            if (!depthField.valid) continue;
            var res = applyCutWithDepthField(tris, pos, region, entry, depthField);
            tris = res.remaining;
            if (res.modified) {
                anyCut = true;
                frameQuads.addAll(res.frameQuads);
                glassQuads.addAll(res.glassQuads);
            }
        }

        if (!anyCut) return false;

        PoseStack.Pose mat = pose.last();
        for (var tri : tris) emitQuadTri(layerConsumer, mat, tri);
        emitFrameAndGlass(frameQuads, glassQuads, mat, layerSupplier);
        return true;
    }

    private static boolean trianglesEnterAffected(List<MeshTri> tris, BlockPos modelPos, Set<BlockPos> affected) {
        for (BlockPos bp : affected) {
            double rx0 = bp.getX() - modelPos.getX() - 0.01;
            double ry0 = bp.getY() - modelPos.getY() - 0.01;
            double rz0 = bp.getZ() - modelPos.getZ() - 0.01;
            double rx1 = rx0 + 1.02, ry1 = ry0 + 1.02, rz1 = rz0 + 1.02;
            for (var t : tris) {
                double minX = Math.min(t.a.x, Math.min(t.b.x, t.c.x));
                double maxX = Math.max(t.a.x, Math.max(t.b.x, t.c.x));
                if (maxX < rx0 || minX > rx1) continue;
                double minY = Math.min(t.a.y, Math.min(t.b.y, t.c.y));
                double maxY = Math.max(t.a.y, Math.max(t.b.y, t.c.y));
                if (maxY < ry0 || minY > ry1) continue;
                double minZ = Math.min(t.a.z, Math.min(t.b.z, t.c.z));
                double maxZ = Math.max(t.a.z, Math.max(t.b.z, t.c.z));
                if (maxZ >= rz0 && minZ <= rz1) return true;
            }
        }
        return false;
    }

    private static boolean extendsOutsideUnitCell(List<MeshTri> tris) {
        for (var t : tris) {
            if (outside01(t.a) || outside01(t.b) || outside01(t.c)) return true;
        }
        return false;
    }

    private static boolean outside01(Vtx v) {
        return v.x < -0.02 || v.x > 1.02 || v.y < -0.02 || v.y > 1.02 || v.z < -0.02 || v.z > 1.02;
    }

    private static void emitFrameAndGlass(List<ColoredQuad> frameQuads, List<ColoredQuad> glassQuads,
                                          PoseStack.Pose mat, Function<RenderType, VertexConsumer> layerSupplier) {
        if (frameQuads.isEmpty() && glassQuads.isEmpty()) return;
        TextureAtlasSprite white = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(WHITE_SPRITE_ID);
        float uMid = (white.getU0() + white.getU1()) * 0.5f;
        float vMid = (white.getV0() + white.getV1()) * 0.5f;
        if (!frameQuads.isEmpty()) {
            VertexConsumer solidBuf = layerSupplier.apply(RenderType.solid());
            for (var q : frameQuads) emitColoredQuad(solidBuf, mat, q, uMid, vMid);
        }
        if (!glassQuads.isEmpty()) {
            VertexConsumer transBuf = layerSupplier.apply(RenderType.translucent());
            for (var q : glassQuads) emitColoredQuad(transBuf, mat, q, uMid, vMid);
        }
    }

    // --- Unpacked BakedQuad Triangle Representation ---
    record Vtx(double x, double y, double z, float u, float v, int r, int g, int b, int a, int light, float nx, float ny, float nz) {
        Vtx lerp(Vtx o, double t) {
            return new Vtx(
                    x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t,
                    (float) (u + (o.u - u) * t), (float) (v + (o.v - v) * t),
                    (int) Math.round(r + (o.r - r) * t), (int) Math.round(g + (o.g - g) * t),
                    (int) Math.round(b + (o.b - b) * t), (int) Math.round(a + (o.a - a) * t),
                    light, nx, ny, nz);
        }
        Vec3 pos() { return new Vec3(x, y, z); }
    }

    record MeshTri(Vtx a, Vtx b, Vtx c, TextureAtlasSprite sprite, Direction face, boolean shade) {
        Vec3 geometricNormal() {
            Vec3 n = b.pos().subtract(a.pos()).cross(c.pos().subtract(a.pos()));
            double len = n.length();
            return len > 1e-9 ? n.scale(1.0 / len) : new Vec3(0, 1, 0);
        }
        double area2() {
            return b.pos().subtract(a.pos()).cross(c.pos().subtract(a.pos())).length();
        }
    }

    record ColoredQuad(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, Vec3 normal, int argb, int light) {}

    private static void unpackStaticShellQuad(BakedQuad quad, BlockPos cellOffset, int[] perVertexLights, List<MeshTri> out) {
        int[] data = quad.getVertices();
        if (data.length < 32) return;
        double ox = cellOffset.getX(), oy = cellOffset.getY(), oz = cellOffset.getZ();
        Vtx[] vs = new Vtx[4];
        for (int i = 0; i < 4; i++) {
            int o = i * 8;
            double x = Float.intBitsToFloat(data[o]) + ox;
            double y = Float.intBitsToFloat(data[o + 1]) + oy;
            double z = Float.intBitsToFloat(data[o + 2]) + oz;
            int col = data[o + 3];
            int cr = col & 0xFF;
            int cg = (col >>> 8) & 0xFF;
            int cb = (col >>> 16) & 0xFF;
            int ca = (col >>> 24) & 0xFF;
            float u = Float.intBitsToFloat(data[o + 4]);
            float v = Float.intBitsToFloat(data[o + 5]);
            int light = perVertexLights != null && perVertexLights.length == 4 ? perVertexLights[i] : data[o + 6];
            int nPacked = data[o + 7];
            float nx = ((byte) (nPacked & 0xFF)) / 127f;
            float ny = ((byte) ((nPacked >> 8) & 0xFF)) / 127f;
            float nz = ((byte) ((nPacked >> 16) & 0xFF)) / 127f;
            vs[i] = new Vtx(x, y, z, u, v, cr, cg, cb, ca, light, nx, ny, nz);
        }
        MeshTri t0 = new MeshTri(vs[0], vs[1], vs[2], quad.getSprite(), quad.getDirection(), quad.isShade());
        MeshTri t1 = new MeshTri(vs[0], vs[2], vs[3], quad.getSprite(), quad.getDirection(), quad.isShade());
        if (t0.area2() > 1e-10) out.add(t0);
        if (t1.area2() > 1e-10) out.add(t1);
    }

    private static List<MeshTri> collectBlockTriangles(BakedModel model, BlockState state, BlockPos pos,
                                                       BlockAndTintGetter region, RandomSource random,
                                                       ModelData modelData, RenderType renderType) {
        var out = new ArrayList<MeshTri>(24);
        long seed = state.getSeed(pos);
        for (Direction side : Direction.values()) {
            if (!Block.shouldRenderFace(state, region, pos, side, pos.relative(side))) continue;
            random.setSeed(seed);
            for (BakedQuad q : model.getQuads(state, side, random, modelData, renderType)) {
                unpackQuad(q, state, pos, region, side, out);
            }
        }
        random.setSeed(seed);
        for (BakedQuad q : model.getQuads(state, null, random, modelData, renderType)) {
            unpackQuad(q, state, pos, region, q.getDirection(), out);
        }
        return out;
    }

    private static Map<Direction, List<BakedQuad>> collectUnculledQuadsByFace(BakedModel model, BlockState state,
                                                                              RandomSource random, ModelData modelData,
                                                                              RenderType renderType) {
        var map = new EnumMap<Direction, List<BakedQuad>>(Direction.class);
        for (Direction d : Direction.values()) {
            random.setSeed(42L);
            map.put(d, model.getQuads(state, d, random, modelData, renderType));
        }
        return map;
    }

    private static void unpackQuad(BakedQuad quad, BlockState state, BlockPos pos, BlockAndTintGetter region,
                                   Direction face, List<MeshTri> out) {
        int[] data = quad.getVertices();
        if (data.length < 32) return;
        float shade = region.getShade(quad.getDirection(), quad.isShade());
        int tint = quad.isTinted()
                ? Minecraft.getInstance().getBlockColors().getColor(state, region, pos, quad.getTintIndex())
                : 0xFFFFFF;
        float tr = ((tint >> 16) & 0xFF) / 255f * shade;
        float tg = ((tint >> 8) & 0xFF) / 255f * shade;
        float tb = (tint & 0xFF) / 255f * shade;

        BlockPos lightPos = face != null ? pos.relative(face) : pos;
        int packedLight = LevelRenderer.getLightColor(region, lightPos);
        if (packedLight == 0) packedLight = LevelRenderer.getLightColor(region, pos);

        Vtx[] vs = new Vtx[4];
        for (int i = 0; i < 4; i++) {
            int o = i * 8;
            double x = Float.intBitsToFloat(data[o]);
            double y = Float.intBitsToFloat(data[o + 1]);
            double z = Float.intBitsToFloat(data[o + 2]);
            int col = data[o + 3];
            int cr = Math.min(255, Math.round((col & 0xFF) * tr));
            int cg = Math.min(255, Math.round(((col >>> 8) & 0xFF) * tg));
            int cb = Math.min(255, Math.round(((col >>> 16) & 0xFF) * tb));
            int ca = (col >>> 24) & 0xFF;
            float u = Float.intBitsToFloat(data[o + 4]);
            float v = Float.intBitsToFloat(data[o + 5]);
            int nPacked = data[o + 7];
            float nx = ((byte) (nPacked & 0xFF)) / 127f;
            float ny = ((byte) ((nPacked >> 8) & 0xFF)) / 127f;
            float nz = ((byte) ((nPacked >> 16) & 0xFF)) / 127f;
            if (nx * nx + ny * ny + nz * nz < 1e-4f && face != null) {
                nx = face.getStepX(); ny = face.getStepY(); nz = face.getStepZ();
            }
            vs[i] = new Vtx(x, y, z, u, v, cr, cg, cb, ca, packedLight, nx, ny, nz);
        }
        MeshTri t0 = new MeshTri(vs[0], vs[1], vs[2], quad.getSprite(), face, quad.isShade());
        MeshTri t1 = new MeshTri(vs[0], vs[2], vs[3], quad.getSprite(), face, quad.isShade());
        if (t0.area2() > 1e-10) out.add(t0);
        if (t1.area2() > 1e-10) out.add(t1);
    }

    // --- Depth-Field Single-Layer / Curved OBJ & Zero-Thickness Cutting Engine ---
    private record LocalPlane(Vec3 origin, Vec3 u, Vec3 v, Vec3 w) {
        UV project(Vec3 p) {
            Vec3 d = p.subtract(origin);
            return new UV(d.dot(u), d.dot(v));
        }
        double depth(Vec3 p) { return p.subtract(origin).dot(w); }
        Vec3 unproject(double pu, double pv, double pw) {
            return origin.add(u.scale(pu)).add(v.scale(pv)).add(w.scale(pw));
        }
    }

    private record ProjectedCap(UV a, UV b, UV c, double da, double db, double dc, double det) {
        boolean covers(UV p) {
            double s = Math.signum(det);
            return cross2D(a, b, p) * s >= -1e-6 && cross2D(b, c, p) * s >= -1e-6 && cross2D(c, a, p) * s >= -1e-6;
        }
        double depthAt(UV p) {
            return da + cross2D(a, p, c) / det * (db - da) + cross2D(a, b, p) / det * (dc - da);
        }
        UV centroid() {
            return new UV((a.u() + b.u() + c.u()) / 3.0, (a.v() + b.v() + c.v()) / 3.0);
        }
        double nearestVertexDepth(UV p) {
            double d0 = distSq(p, a), d1 = distSq(p, b), d2 = distSq(p, c);
            if (d0 <= d1 && d0 <= d2) return da;
            return d1 <= d2 ? db : dc;
        }
        double minVertexDistSq(UV p) {
            return Math.min(distSq(p, a), Math.min(distSq(p, b), distSq(p, c)));
        }
    }

    private record SurfaceDepthField(
            boolean valid,
            LocalPlane plane,
            List<UV> outerLoop,
            List<ProjectedCap> entryCaps,
            List<ProjectedCap> exitCaps,
            double minEntryDepth,
            double fallbackEntry,
            double fallbackExit,
            boolean zeroThickness,
            double maxEntryDepth,
            double maxExitDepth
    ) {
        double entryDepthAt(UV p) {
            double best = Double.POSITIVE_INFINITY;
            for (var c : entryCaps) {
                if (c.covers(p)) {
                    double d = c.depthAt(p);
                    if (d < best) best = d;
                }
            }
            if (Double.isFinite(best)) return best;
            // Smooth nearest-cap fallback for multi-panel wide windows / curved boundaries
            double bestDist = Double.POSITIVE_INFINITY;
            double bestDepth = fallbackEntry;
            for (var c : entryCaps) {
                double dist = Math.min(distSq(p, c.centroid()), c.minVertexDistSq(p));
                if (dist < bestDist) {
                    bestDist = dist;
                    bestDepth = c.nearestVertexDepth(p);
                }
            }
            return bestDepth;
        }

        double exitDepthAt(UV p) {
            return exitDepthAt(p, entryDepthAt(p));
        }

        double exitDepthAt(UV p, double wIn) {
            if (zeroThickness) return wIn;
            double best = Double.POSITIVE_INFINITY;
            for (var c : exitCaps) {
                if (c.covers(p)) {
                    double d = c.depthAt(p);
                    if (d > wIn + THICKNESS_EPS && d < best) best = d;
                }
            }
            if (Double.isFinite(best)) return best;
            double bestDist = Double.POSITIVE_INFINITY;
            double bestDepth = Math.max(wIn + THICKNESS_EPS, fallbackExit);
            for (var c : exitCaps) {
                double cand = c.nearestVertexDepth(p);
                if (cand <= wIn + THICKNESS_EPS) continue;
                double dist = Math.min(distSq(p, c.centroid()), c.minVertexDistSq(p));
                if (dist < bestDist) {
                    bestDist = dist;
                    bestDepth = cand;
                }
            }
            return bestDepth;
        }

        boolean isFirstLayerEntry(UV mid, double triDepth) {
            if (triDepth < minEntryDepth - LAYER_GAP_EPS || triDepth > maxEntryDepth + LAYER_GAP_EPS) return false;
            // Must not lie behind an earlier first-layer exit at the same (u, v)
            for (var ex : exitCaps) {
                if (ex.covers(mid) && ex.depthAt(mid) < triDepth - THICKNESS_EPS) return false;
            }
            return true;
        }

        boolean isFirstLayerExit(UV mid, double triDepth) {
            if (zeroThickness) return Math.abs(triDepth - entryDepthAt(mid)) <= THICKNESS_EPS;
            // Must lie behind an entry cap and NOT behind an earlier exit cap at the same (u, v)
            double wIn = entryDepthAt(mid);
            if (triDepth <= wIn + THICKNESS_EPS) return false;
            for (var ex : exitCaps) {
                if (ex.covers(mid)) {
                    double exD = ex.depthAt(mid);
                    if (exD > wIn + THICKNESS_EPS && exD < triDepth - LAYER_GAP_EPS) return false;
                }
            }
            return triDepth <= maxExitDepth + LAYER_GAP_EPS;
        }
    }

    private static LocalPlane localPlaneForBlock(BlockPos blockPos, StickerEntry entry) {
        Direction f = entry.facing();
        Vec3 relAnchor = new Vec3(
                entry.anchorPos().getX() - blockPos.getX() + 0.5,
                entry.anchorPos().getY() - blockPos.getY() + 0.5,
                entry.anchorPos().getZ() - blockPos.getZ() + 0.5);
        Vec3 out = StickerPlaneMath.outward(f);
        return new LocalPlane(relAnchor.add(out.scale(0.5)), StickerPlaneMath.axisU(f), StickerPlaneMath.axisV(f), out.scale(-1.0));
    }

    /**
     * Analyzes flat AND curved/sloped OBJ shells along the facing axis to isolate the SINGLE frontmost
     * material layer [wEntry(u,v), wExit(u,v)] using per-centroid ray occlusion — never cuts deeper walls behind it.
     */
    private static SurfaceDepthField analyzeDepthField(List<MeshTri> allTris,
                                                       Map<Direction, List<BakedQuad>> allBackQuads,
                                                       BlockPos blockPos, StickerEntry entry) {
        LocalPlane plane = localPlaneForBlock(blockPos, entry);
        WindowStickerParams w = entry.window();
        int steps = w.cornerRadius() > 1e-4f ? 6 : 1;
        List<UV> outerLoop = ensureCcw(dedupLoop(StickerPlaneMath.sampleRoundedRect(
                w.offsetU(), w.offsetV(), w.width() * 0.5, w.height() * 0.5, w.cornerRadius(), steps)));

        var rawEntries = new ArrayList<ProjectedCap>();
        var rawExits = new ArrayList<ProjectedCap>();
        double minEntry = Double.POSITIVE_INFINITY;

        for (var t : allTris) {
            Vec3 n = t.geometricNormal();
            double nw = n.dot(plane.w);
            if (Math.abs(nw) < NORMAL_DOT_MIN) continue;
            UV a = plane.project(t.a.pos()), b = plane.project(t.b.pos()), c = plane.project(t.c.pos());
            double det = cross2D(a, b, c);
            if (Math.abs(det) <= 1e-10) continue;
            if (!triIntersectsConvex(a, b, c, outerLoop)) continue;
            double da = plane.depth(t.a.pos()), db = plane.depth(t.b.pos()), dc = plane.depth(t.c.pos());
            var cap = new ProjectedCap(a, b, c, da, db, dc, det);
            if (nw < -NORMAL_DOT_MIN) {
                rawEntries.add(cap);
                minEntry = Math.min(minEntry, Math.min(da, Math.min(db, dc)));
            } else {
                rawExits.add(cap);
            }
        }

        if (rawEntries.isEmpty()) {
            return new SurfaceDepthField(false, plane, outerLoop, List.of(), List.of(), 0, 0, 0, true, 0, 0);
        }

        // Step 1: Isolate ONLY frontmost-layer entry caps (reject any entry cap that sits behind an exit cap at its centroid!)
        var firstEntries = new ArrayList<ProjectedCap>(rawEntries.size());
        double maxEntryD = minEntry;
        for (var en : rawEntries) {
            UV mid = en.centroid();
            double enD = (en.da + en.db + en.dc) / 3.0;
            boolean behindEarlierExit = false;
            for (var ex : rawExits) {
                if (ex.covers(mid) && ex.depthAt(mid) < enD - THICKNESS_EPS) {
                    behindEarlierExit = true;
                    break;
                }
            }
            if (!behindEarlierExit) {
                firstEntries.add(en);
                maxEntryD = Math.max(maxEntryD, Math.max(en.da, Math.max(en.db, en.dc)));
            }
        }
        if (firstEntries.isEmpty()) firstEntries.addAll(rawEntries);

        // Step 2: Isolate ONLY first-layer exit caps directly closing a first-layer entry cap!
        var firstExits = new ArrayList<ProjectedCap>(rawExits.size());
        double minExit = Double.POSITIVE_INFINITY;
        double maxExitD = Double.NEGATIVE_INFINITY;
        for (var ex : rawExits) {
            UV mid = ex.centroid();
            double exD = (ex.da + ex.db + ex.dc) / 3.0;
            double frontEnD = Double.POSITIVE_INFINITY;
            for (var en : firstEntries) {
                if (en.covers(mid)) {
                    double d = en.depthAt(mid);
                    if (d < exD - THICKNESS_EPS && d < frontEnD) frontEnD = d;
                }
            }
            if (!Double.isFinite(frontEnD)) continue;
            // Reject if another exit cap already closed this layer between frontEnD and exD
            boolean behindEarlierExit = false;
            for (var otherEx : rawExits) {
                if (otherEx == ex) continue;
                if (otherEx.covers(mid)) {
                    double oD = otherEx.depthAt(mid);
                    if (oD > frontEnD + THICKNESS_EPS && oD < exD - LAYER_GAP_EPS) {
                        behindEarlierExit = true;
                        break;
                    }
                }
            }
            if (!behindEarlierExit) {
                firstExits.add(ex);
                minExit = Math.min(minExit, exD);
                maxExitD = Math.max(maxExitD, Math.max(ex.da, Math.max(ex.db, ex.dc)));
            }
        }

        // Fallback if exit cap centroids didn't overlap entry cap directly (e.g. partial overhang)
        if (firstExits.isEmpty()) {
            for (var ex : rawExits) {
                double exD = (ex.da + ex.db + ex.dc) / 3.0;
                if (exD > minEntry + THICKNESS_EPS) {
                    if (!Double.isFinite(minExit) || exD < minExit + LAYER_GAP_EPS) {
                        firstExits.add(ex);
                        minExit = Math.min(minExit, exD);
                        maxExitD = Math.max(maxExitD, Math.max(ex.da, Math.max(ex.db, ex.dc)));
                    }
                }
            }
        }
        // Check culled opposite face of solid cube (when a 2nd solid block sits right behind it)
        if (!Double.isFinite(minExit)) {
            for (BakedQuad bq : allBackQuads.getOrDefault(entry.facing().getOpposite(), List.of())) {
                int[] vd = bq.getVertices();
                if (vd.length >= 24) {
                    Vec3 p = new Vec3(Float.intBitsToFloat(vd[0]), Float.intBitsToFloat(vd[1]), Float.intBitsToFloat(vd[2]));
                    double d = plane.depth(p);
                    if (d > minEntry + THICKNESS_EPS && d < minExit) {
                        minExit = d;
                        maxExitD = d;
                    }
                }
            }
        }

        boolean zeroThickness = !Double.isFinite(minExit) || (minExit - minEntry <= THICKNESS_EPS);
        double fallbackExit = zeroThickness ? minEntry : minExit;
        if (!Double.isFinite(maxExitD)) maxExitD = fallbackExit;

        if (zeroThickness) {
            firstExits.clear();
            for (var ex : rawExits) {
                double exD = (ex.da + ex.db + ex.dc) / 3.0;
                if (Math.abs(exD - minEntry) <= THICKNESS_EPS) firstExits.add(ex);
            }
        }

        return new SurfaceDepthField(true, plane, outerLoop, firstEntries, firstExits,
                minEntry, minEntry, fallbackExit, zeroThickness, maxEntryD, maxExitD);
    }

    record CutStep(List<MeshTri> remaining, List<ColoredQuad> frameQuads, List<ColoredQuad> glassQuads, boolean modified) {}
    private record CutSeg(PolyV p0, PolyV p1, TextureAtlasSprite sprite, int r, int g, int b, int aCol) {}

    /** Package-private headless entry point for JUnit 5 verification of flat, curved OBJ, seam, overhang & double-wall cuts. */
    static CutStep cutTrianglesForTest(List<MeshTri> tris, BlockPos modelPos, StickerEntry entry) {
        var field = analyzeDepthField(tris, Map.of(), modelPos, entry);
        if (!field.valid) return new CutStep(tris, List.of(), List.of(), false);
        return applyCutWithDepthField(tris, modelPos, null, entry, field);
    }

    private static CutStep applyCutWithDepthField(List<MeshTri> tris, BlockPos blockPos, BlockAndTintGetter region,
                                                  StickerEntry entry, SurfaceDepthField field) {
        LocalPlane plane = field.plane;
        WindowStickerParams wp = entry.window();
        double hw = wp.width() * 0.5, hh = wp.height() * 0.5;
        double thick = Math.min(wp.frameThickness(), Math.min(hw, hh));
        double rOut = Math.min(wp.cornerRadius(), Math.min(hw, hh));
        double ihw = Math.max(0.0, hw - thick), ihh = Math.max(0.0, hh - thick);
        double rIn = Math.max(0.0, Math.min(rOut - thick, Math.min(ihw, ihh)));
        int steps = rOut > 1e-4 ? 6 : 1;
        List<UV> innerLoop = (ihw > 1e-3 && ihh > 1e-3)
                ? ensureCcw(dedupLoop(StickerPlaneMath.sampleRoundedRect(wp.offsetU(), wp.offsetV(), ihw, ihh, rIn, steps)))
                : null;

        int frontLight = region != null ? LevelRenderer.getLightColor(region, blockPos.relative(entry.facing())) : LightTexture.FULL_BRIGHT;
        int backLight = region != null ? LevelRenderer.getLightColor(region, blockPos.relative(entry.facing().getOpposite())) : LightTexture.FULL_BRIGHT;
        int midLight = maxLight(frontLight, backLight);

        var out = new ArrayList<MeshTri>(tris.size() + 24);
        var frameQuads = new ArrayList<ColoredQuad>();
        var glassQuads = new ArrayList<ColoredQuad>();
        var wallSegs = new ArrayList<CutSeg>();
        boolean modified = false;

        for (var t : tris) {
            Vec3 n = t.geometricNormal();
            double nw = n.dot(plane.w);
            double da = plane.depth(t.a.pos()), db = plane.depth(t.b.pos()), dc = plane.depth(t.c.pos());
            if (Math.abs(nw) < NORMAL_DOT_MIN) {
                // Perpendicular / side / internal seam triangle (e.g. y=1.0 seam between stacked OBJ cubes):
                // Clip ONLY the portion inside [wEntry, wExit] and inside outerLoop; NEVER cut behind wExit!
                if (field.zeroThickness) {
                    out.add(t);
                    continue;
                }
                double minW = Math.min(da, Math.min(db, dc));
                double maxW = Math.max(da, Math.max(db, dc));
                if (maxW <= field.minEntryDepth - 0.005 || minW >= field.maxExitDepth + 0.005) {
                    out.add(t);
                    continue;
                }
                UV midUv = plane.project(t.a.pos().add(t.b.pos()).add(t.c.pos()).scale(1.0 / 3.0));
                double wExitLocal = field.exitDepthAt(midUv);
                var splitW = splitTriByMaxDepth(t, plane, wExitLocal + 0.002);
                // Keep everything deeper than wExit untouched ("到底了也不往裏面切")
                out.addAll(splitW.deeper);
                for (var shallowTri : splitW.shallower) {
                    var clip = subtractConvex(shallowTri, plane, field.outerLoop, false, wallSegs);
                    out.addAll(clip.outside);
                    if (clip.cut) modified = true;
                }
                continue;
            }
            UV midUv = plane.project(t.a.pos().add(t.b.pos()).add(t.c.pos()).scale(1.0 / 3.0));
            double d = (da + db + dc) / 3.0;
            boolean isEntry = nw < -NORMAL_DOT_MIN && field.isFirstLayerEntry(midUv, d);
            boolean isExit = nw > NORMAL_DOT_MIN && field.isFirstLayerExit(midUv, d);
            if (!isEntry && !isExit) {
                out.add(t);
                continue;
            }
            var clip = subtractConvex(t, plane, field.outerLoop, isEntry && !field.zeroThickness, wallSegs);
            out.addAll(clip.outside);
            if (clip.cut) {
                modified = true;
                // Generate frame & glass STRICTLY clipped to this actually-cut entry triangle (never in empty space!)
                if (isEntry && clip.inside.size() >= 3) {
                    emitClippedWindowForCutPoly(clip.inside, n, plane, field, wp, thick, innerLoop,
                            frontLight, backLight, midLight, frameQuads, glassQuads);
                }
            }
        }

        if (!modified) return new CutStep(tris, List.of(), List.of(), false);

        // Bridge hollow cavity walls strictly along actually-cut outerLoop edges (using exact cut vertex 3D positions!)
        if (!field.zeroThickness && !wallSegs.isEmpty()) {
            for (var seg : wallSegs) {
                Vec3 p0f = seg.p0.v.pos();
                Vec3 p1f = seg.p1.v.pos();
                double w0f = plane.depth(p0f), w1f = plane.depth(p1f);
                double w0b = field.exitDepthAt(seg.p0.uv, w0f), w1b = field.exitDepthAt(seg.p1.uv, w1f);
                Vec3 p0b = p0f.add(plane.w.scale(w0b - w0f));
                Vec3 p1b = p1f.add(plane.w.scale(w1b - w1f));
                Vec3 edge = p1f.subtract(p0f);
                Vec3 wn = edge.cross(plane.w);
                double len = wn.length();
                if (len <= 1e-8) continue;
                wn = wn.scale(1.0 / len);
                double depthSpan = Math.max(THICKNESS_EPS, ((w0b - w0f) + (w1b - w1f)) * 0.5);
                float u0 = seg.sprite != null ? seg.sprite.getU0() : 0f;
                float u1 = seg.sprite != null ? seg.sprite.getU(Math.min(1f, (float) edge.length())) : 1f;
                float v0 = seg.sprite != null ? seg.sprite.getV0() : 0f;
                float v1 = seg.sprite != null ? seg.sprite.getV(Math.min(1f, (float) depthSpan)) : 1f;
                float nx = (float) wn.x, ny = (float) wn.y, nz = (float) wn.z;
                Vtx vf0 = new Vtx(p0f.x, p0f.y, p0f.z, u0, v0, seg.r, seg.g, seg.b, seg.aCol, midLight, nx, ny, nz);
                Vtx vf1 = new Vtx(p1f.x, p1f.y, p1f.z, u1, v0, seg.r, seg.g, seg.b, seg.aCol, midLight, nx, ny, nz);
                Vtx vb1 = new Vtx(p1b.x, p1b.y, p1b.z, u1, v1, seg.r, seg.g, seg.b, seg.aCol, midLight, nx, ny, nz);
                Vtx vb0 = new Vtx(p0b.x, p0b.y, p0b.z, u0, v1, seg.r, seg.g, seg.b, seg.aCol, midLight, nx, ny, nz);
                // CCW winding facing inward into the window opening (normal == edge x plane.w)
                out.add(new MeshTri(vf0, vf1, vb1, seg.sprite, null, true));
                out.add(new MeshTri(vf0, vb1, vb0, seg.sprite, null, true));
            }
        }
        return new CutStep(out, frameQuads, glassQuads, true);
    }

    /**
     * Emits parametric window frame & translucent glass STRICTLY inside `cutPoly` (an actually-cut entry triangle
     * clipped to outerLoop). Vertices hug the actual 3D triangle surface — nothing is ever emitted in empty air!
     */
    private static void emitClippedWindowForCutPoly(List<PolyV> cutPoly, Vec3 surfNormal, LocalPlane plane,
                                                    SurfaceDepthField field, WindowStickerParams wp, double thick,
                                                    List<UV> innerLoop, int frontLight, int backLight, int midLight,
                                                    List<ColoredQuad> frameOut, List<ColoredQuad> glassOut) {
        Vec3 frontN = surfNormal;
        Vec3 backN = surfNormal.scale(-1.0);
        int fColor = wp.frameArgb();
        int fRevealColor = shadeRgb(fColor, 0.84f);
        int gColor = wp.glassArgb();

        if (innerLoop == null) {
            if (thick > 1e-4) {
                emitFramePolyFrontBack(cutPoly, plane, field, frontN, backN, fColor, frontLight, backLight, frameOut);
            }
            return;
        }

        // Split cutPoly against convex innerLoop: outside pieces = frame ring; inside piece = glass opening!
        List<PolyV> glassPoly = cutPoly;
        int m = innerLoop.size();
        for (int k = 0; k < m; k++) {
            if (glassPoly.size() < 3) break;
            UV e0 = innerLoop.get(k), e1 = innerLoop.get((k + 1) % m);
            var sp = splitPoly(glassPoly, e0, e1);
            if (thick > 1e-4 && sp.out.size() >= 3 && polyArea2D(sp.out) > 1e-9) {
                emitFramePolyFrontBack(sp.out, plane, field, frontN, backN, fColor, frontLight, backLight, frameOut);
            }
            glassPoly = sp.in;
        }

        if (glassPoly.size() < 3 || polyArea2D(glassPoly) <= 1e-9) return;

        // 1. Translucent glass strictly inside cutPoly ∩ innerLoop
        if (wp.glassAlpha() > 0.004f) {
            Vec3[] mids = new Vec3[glassPoly.size()];
            for (int i = 0; i < glassPoly.size(); i++) {
                PolyV pv = glassPoly.get(i);
                Vec3 pf = pv.v.pos();
                double wIn = plane.depth(pf);
                double wOut = field.exitDepthAt(pv.uv, wIn);
                mids[i] = field.zeroThickness ? pf : pf.add(plane.w.scale(0.5 * (wOut - wIn)));
            }
            Vec3 c0 = mids[0];
            for (int i = 1; i + 1 < mids.length; i++) {
                glassOut.add(new ColoredQuad(c0, mids[i], mids[i + 1], mids[i + 1], frontN, gColor, midLight));
                glassOut.add(new ColoredQuad(c0, mids[i + 1], mids[i], mids[i], backN, gColor, midLight));
            }
        }

        // 2. Inner frame reveal (thickness wall facing INTO the glass opening) strictly along innerLoop edges!
        if (thick > 1e-4 && !field.zeroThickness) {
            for (int i = 0; i < glassPoly.size(); i++) {
                PolyV p0 = glassPoly.get(i), p1 = glassPoly.get((i + 1) % glassPoly.size());
                if (distSq(p0.uv, p1.uv) <= 1e-10) continue;
                PolyV[] aligned = matchLoopEdge(p0, p1, innerLoop);
                if (aligned == null) continue;
                Vec3 p0f = aligned[0].v.pos();
                Vec3 p1f = aligned[1].v.pos();
                double w0f = plane.depth(p0f), w1f = plane.depth(p1f);
                double w0b = field.exitDepthAt(aligned[0].uv, w0f), w1b = field.exitDepthAt(aligned[1].uv, w1f);
                Vec3 p0b = p0f.add(plane.w.scale(w0b - w0f));
                Vec3 p1b = p1f.add(plane.w.scale(w1b - w1f));
                Vec3 edge = p1f.subtract(p0f);
                Vec3 inN = edge.cross(plane.w);
                double inLen = inN.length();
                if (inLen > 1e-7) {
                    // CCW winding facing INTO the glass opening (normal == edge x plane.w)
                    frameOut.add(new ColoredQuad(p0f, p1f, p1b, p0b, inN.scale(1.0 / inLen), fRevealColor, midLight));
                }
            }
        }
    }

    private static void emitFramePolyFrontBack(List<PolyV> poly, LocalPlane plane, SurfaceDepthField field,
                                               Vec3 frontN, Vec3 backN, int argb, int frontLight, int backLight,
                                               List<ColoredQuad> frameOut) {
        int n = poly.size();
        Vec3[] front = new Vec3[n];
        Vec3[] back = new Vec3[n];
        for (int i = 0; i < n; i++) {
            PolyV pv = poly.get(i);
            Vec3 pf = pv.v.pos();
            double wIn = plane.depth(pf);
            double wOut = field.exitDepthAt(pv.uv, wIn);
            front[i] = pf.add(plane.w.scale(-0.002));
            back[i] = pf.add(plane.w.scale(wOut - wIn + 0.002));
        }
        for (int i = 1; i + 1 < n; i++) {
            frameOut.add(new ColoredQuad(front[0], front[i], front[i + 1], front[i + 1], frontN, argb, frontLight));
            frameOut.add(new ColoredQuad(back[0], back[i + 1], back[i], back[i], backN, argb, backLight));
        }
    }

    private record DepthSplit(List<MeshTri> shallower, List<MeshTri> deeper) {}

    private static DepthSplit splitTriByMaxDepth(MeshTri tri, LocalPlane plane, double maxW) {
        double da = plane.depth(tri.a.pos()), db = plane.depth(tri.b.pos()), dc = plane.depth(tri.c.pos());
        if (da <= maxW && db <= maxW && dc <= maxW) return new DepthSplit(List.of(tri), List.of());
        if (da >= maxW && db >= maxW && dc >= maxW) return new DepthSplit(List.of(), List.of(tri));
        List<Vtx> poly = List.of(tri.a, tri.b, tri.c);
        double[] ds = {da, db, dc};
        var shallow = new ArrayList<Vtx>(4);
        var deep = new ArrayList<Vtx>(4);
        for (int i = 0; i < 3; i++) {
            int j = (i + 1) % 3;
            Vtx p = poly.get(i), q = poly.get(j);
            double dp = ds[i] - maxW, dq = ds[j] - maxW;
            if (dp <= CLIP_EPS) shallow.add(p);
            if (dp >= -CLIP_EPS) deep.add(p);
            if ((dp < -CLIP_EPS && dq > CLIP_EPS) || (dp > CLIP_EPS && dq < -CLIP_EPS)) {
                Vtx mid = p.lerp(q, dp / (dp - dq));
                shallow.add(mid);
                deep.add(mid);
            }
        }
        var sTris = new ArrayList<MeshTri>(2);
        var dTris = new ArrayList<MeshTri>(2);
        fanEmitVtx(shallow, tri, sTris);
        fanEmitVtx(deep, tri, dTris);
        return new DepthSplit(sTris, dTris);
    }

    private static void fanEmitVtx(List<Vtx> poly, MeshTri src, List<MeshTri> out) {
        if (poly.size() < 3) return;
        Vtx v0 = poly.getFirst();
        for (int i = 1; i + 1 < poly.size(); i++) {
            MeshTri t = new MeshTri(v0, poly.get(i), poly.get(i + 1), src.sprite, src.face, src.shade);
            if (t.area2() > 1e-10) out.add(t);
        }
    }

    // --- 2D Convex Complement Clipping Helpers ---
    private record PolyV(Vtx v, UV uv) {
        PolyV lerp(PolyV o, double t) {
            return new PolyV(v.lerp(o.v, t), new UV(uv.u() + (o.uv.u() - uv.u()) * t, uv.v() + (o.uv.v() - uv.v()) * t));
        }
    }
    private record ClipRes(List<MeshTri> outside, List<PolyV> inside, boolean cut) {}
    private record SplitRes(List<PolyV> in, List<PolyV> out) {}

    private static ClipRes subtractConvex(MeshTri tri, LocalPlane plane, List<UV> convexCcw,
                                          boolean collectWalls, List<CutSeg> walls) {
        List<PolyV> inside = List.of(
                new PolyV(tri.a, plane.project(tri.a.pos())),
                new PolyV(tri.b, plane.project(tri.b.pos())),
                new PolyV(tri.c, plane.project(tri.c.pos())));
        var outside = new ArrayList<MeshTri>();
        int m = convexCcw.size();
        for (int k = 0; k < m; k++) {
            if (inside.size() < 3) break;
            UV e0 = convexCcw.get(k), e1 = convexCcw.get((k + 1) % m);
            var sp = splitPoly(inside, e0, e1);
            if (sp.out.size() >= 3) fanEmit(sp.out, tri, outside);
            inside = sp.in;
        }
        if (inside.size() < 3 || polyArea2D(inside) <= 1e-9) {
            return new ClipRes(List.of(tri), List.of(), false);
        }
        if (collectWalls) {
            for (int i = 0; i < inside.size(); i++) {
                PolyV a = inside.get(i), b = inside.get((i + 1) % inside.size());
                if (distSq(a.uv, b.uv) <= 1e-10) continue;
                PolyV[] aligned = matchLoopEdge(a, b, convexCcw);
                if (aligned != null) {
                    walls.add(new CutSeg(aligned[0], aligned[1], tri.sprite, tri.a.r, tri.a.g, tri.a.b, tri.a.a));
                }
            }
        }
        return new ClipRes(outside, inside, true);
    }

    private static SplitRes splitPoly(List<PolyV> poly, UV a, UV b) {
        var in = new ArrayList<PolyV>(poly.size() + 2);
        var out = new ArrayList<PolyV>(poly.size() + 2);
        int n = poly.size();
        for (int i = 0; i < n; i++) {
            PolyV p = poly.get(i), q = poly.get((i + 1) % n);
            double dp = cross2D(a, b, p.uv), dq = cross2D(a, b, q.uv);
            if (dp >= -CLIP_EPS) in.add(p);
            if (dp <= CLIP_EPS) out.add(p);
            if ((dp > CLIP_EPS && dq < -CLIP_EPS) || (dp < -CLIP_EPS && dq > CLIP_EPS)) {
                PolyV mid = p.lerp(q, dp / (dp - dq));
                in.add(mid);
                out.add(mid);
            }
        }
        return new SplitRes(dedupPoly(in), dedupPoly(out));
    }

    private static List<PolyV> dedupPoly(List<PolyV> pts) {
        if (pts.size() < 2) return pts;
        var out = new ArrayList<PolyV>(pts.size());
        for (var p : pts) {
            if (out.isEmpty() || distSq(out.getLast().uv, p.uv) > 1e-12) out.add(p);
        }
        if (out.size() > 1 && distSq(out.getFirst().uv, out.getLast().uv) <= 1e-12) out.removeLast();
        return out;
    }

    private static List<UV> dedupLoop(List<UV> pts) {
        var out = new ArrayList<UV>(pts.size());
        for (var p : pts) {
            if (out.isEmpty() || distSq(out.getLast(), p) > 1e-10) out.add(p);
        }
        if (out.size() > 1 && distSq(out.getFirst(), out.getLast()) <= 1e-10) out.removeLast();
        return out;
    }

    private static void fanEmit(List<PolyV> poly, MeshTri src, List<MeshTri> out) {
        Vtx v0 = poly.getFirst().v;
        for (int i = 1; i + 1 < poly.size(); i++) {
            MeshTri t = new MeshTri(v0, poly.get(i).v, poly.get(i + 1).v, src.sprite, src.face, src.shade);
            if (t.area2() > 1e-10) out.add(t);
        }
    }

    private static boolean triIntersectsConvex(UV a, UV b, UV c, List<UV> convexCcw) {
        Vtx dummy = new Vtx(0, 0, 0, 0, 0, 255, 255, 255, 255, 0, 0, 1, 0);
        List<PolyV> cur = List.of(new PolyV(dummy, a), new PolyV(dummy, b), new PolyV(dummy, c));
        for (int k = 0; k < convexCcw.size() && cur.size() >= 3; k++) {
            cur = splitPoly(cur, convexCcw.get(k), convexCcw.get((k + 1) % convexCcw.size())).in;
        }
        return cur.size() >= 3 && polyArea2D(cur) > 1e-9;
    }

    private static PolyV[] matchLoopEdge(PolyV p0, PolyV p1, List<UV> loopCcw) {
        for (int k = 0; k < loopCcw.size(); k++) {
            UV e0 = loopCcw.get(k), e1 = loopCcw.get((k + 1) % loopCcw.size());
            double len = Math.sqrt(distSq(e0, e1));
            if (len <= 1e-10) continue;
            if (Math.abs(cross2D(e0, e1, p0.uv)) / len <= 1e-5 && Math.abs(cross2D(e0, e1, p1.uv)) / len <= 1e-5) {
                double dot = (p1.uv.u() - p0.uv.u()) * (e1.u() - e0.u()) + (p1.uv.v() - p0.uv.v()) * (e1.v() - e0.v());
                return dot >= 0 ? new PolyV[]{p0, p1} : new PolyV[]{p1, p0};
            }
        }
        return null;
    }

    private static List<UV> ensureCcw(List<UV> pts) {
        double area2 = 0;
        for (int i = 0; i < pts.size(); i++) {
            UV a = pts.get(i), b = pts.get((i + 1) % pts.size());
            area2 += a.u() * b.v() - b.u() * a.v();
        }
        if (area2 >= 0) return pts;
        var rev = new ArrayList<>(pts);
        Collections.reverse(rev);
        return rev;
    }

    private static double polyArea2D(List<PolyV> poly) {
        double sum = 0;
        for (int i = 0; i < poly.size(); i++) {
            UV a = poly.get(i).uv, b = poly.get((i + 1) % poly.size()).uv;
            sum += a.u() * b.v() - b.u() * a.v();
        }
        return Math.abs(sum) * 0.5;
    }

    private static double cross2D(UV a, UV b, UV c) {
        return (b.u() - a.u()) * (c.v() - a.v()) - (b.v() - a.v()) * (c.u() - a.u());
    }

    private static double distSq(UV a, UV b) {
        double du = a.u() - b.u(), dv = a.v() - b.v();
        return du * du + dv * dv;
    }

    private static int maxLight(int l1, int l2) {
        return LightTexture.pack(
                Math.max(LightTexture.block(l1), LightTexture.block(l2)),
                Math.max(LightTexture.sky(l1), LightTexture.sky(l2)));
    }

    private static int shadeRgb(int argb, float factor) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.min(255, Math.round(((argb >>> 16) & 0xFF) * factor));
        int g = Math.min(255, Math.round(((argb >>> 8) & 0xFF) * factor));
        int b = Math.min(255, Math.round((argb & 0xFF) * factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static void emitQuadTri(VertexConsumer buf, PoseStack.Pose mat, MeshTri t) {
        putVtx(buf, mat, t.a);
        putVtx(buf, mat, t.b);
        putVtx(buf, mat, t.c);
        putVtx(buf, mat, t.c);
    }

    private static void putVtx(VertexConsumer buf, PoseStack.Pose mat, Vtx v) {
        buf.addVertex(mat, (float) v.x, (float) v.y, (float) v.z)
                .setColor(v.r, v.g, v.b, v.a)
                .setUv(v.u, v.v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(v.light)
                .setNormal(mat, v.nx, v.ny, v.nz);
    }

    private static void emitColoredQuad(VertexConsumer buf, PoseStack.Pose mat, ColoredQuad q, float u, float v) {
        float nx = (float) q.normal.x, ny = (float) q.normal.y, nz = (float) q.normal.z;
        buf.addVertex(mat, (float) q.p0.x, (float) q.p0.y, (float) q.p0.z).setColor(q.argb).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(q.light).setNormal(mat, nx, ny, nz);
        buf.addVertex(mat, (float) q.p1.x, (float) q.p1.y, (float) q.p1.z).setColor(q.argb).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(q.light).setNormal(mat, nx, ny, nz);
        buf.addVertex(mat, (float) q.p2.x, (float) q.p2.y, (float) q.p2.z).setColor(q.argb).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(q.light).setNormal(mat, nx, ny, nz);
        buf.addVertex(mat, (float) q.p3.x, (float) q.p3.y, (float) q.p3.z).setColor(q.argb).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(q.light).setNormal(mat, nx, ny, nz);
    }
}
