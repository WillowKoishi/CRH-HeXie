package dev.hexie.renderer.neoforge;

import dev.hexie.renderer.*;
import dev.hexie.renderer.mc.*;
import dev.hexie.renderer.sable.ShipSpace;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.Tags;

/**
 * Per-section incrementally cached terrain VoxelShape casters (16x16x16 section cache so placing/breaking
 * a light only rescans ONE section in ~0.2ms instead of 228k blocks!), full multi-light emitter extraction
 * (up to 128 individual lights, zero 4x4x4 light deletion, zero emitter self-occlusion), and cached
 * exact world-space triangles for static carriage shells, custom-cut windows, and moving Create trains.
 */
final class WorldCasters {
    private static final int MAX_FORWARD_LAMPS = 128;
    private static final int MAX_BOXES = 8192;
    private static final int MAX_TRIANGLES = 16384;
    private static final int SECTION_RADIUS_XZ = 2; // 5x5 chunks = 80x80 blocks
    private static final int SECTION_RADIUS_Y = 1;  // 3 sections = 48 blocks tall

    private record CachedSection(int secX, int secY, int secZ,
                                 List<ForwardLight.Box> boxes,
                                 List<ForwardLight.Lamp> emitters,
                                 List<ForwardLight.CasterTri> extraTris) {
        static CachedSection empty(int sx, int sy, int sz) {
            return new CachedSection(sx, sy, sz, List.of(), List.of(), List.of());
        }
    }

    private final Map<Long, CachedSection> sectionCache = new HashMap<>();
    private final Set<Long> dirtySections = new HashSet<>();
    private List<ForwardLight.Lamp> emitters = List.of(), nearest = List.of();
    private List<ForwardLight.CasterTri> terrainExtraTris = List.of();
    private Vec rankedCenter;
    private ForwardLight.BoxTree terrainTree = ForwardLight.buildBoxTree(List.of());
    private ForwardLight.TriTree cachedTriTree = ForwardLight.EMPTY_TRI_TREE;
    private long cachedTriSignature = Long.MIN_VALUE;
    private ClientLevel previous;
    private int lastCenterSecX = Integer.MIN_VALUE, lastCenterSecY = Integer.MIN_VALUE, lastCenterSecZ = Integer.MIN_VALUE;
    private long lastSectionRevision = Long.MIN_VALUE;
    private long lastMemoryBytes;

    void prepare(ClientLevel level, RendererBackend.Snapshot scene, WorldLightVolume volume,
                 RenderSettings settings, boolean sable, int rayBudget) {
        if (level == null) {
            ForwardLight.clear();
            return;
        }
        int maxActive = Math.min(MAX_FORWARD_LAMPS, settings.get(RenderSettings.Field.ACTIVE_LIGHTS));
        if (maxActive <= 0) {
            ForwardLight.clear();
            return;
        }

        var mc = Minecraft.getInstance();
        Vec focus = Vec.ZERO;
        if (mc != null && mc.gameRenderer != null && mc.gameRenderer.getMainCamera() != null && mc.gameRenderer.getMainCamera().isInitialized()) {
            var cp = mc.gameRenderer.getMainCamera().getPosition();
            focus = new Vec(cp.x, cp.y, cp.z);
        } else if (mc != null && mc.player != null) {
            var pp = mc.player.position();
            focus = new Vec(pp.x, pp.y, pp.z);
        }

        long memoryBytes = (long) settings.get(RenderSettings.Field.MEMORY_MIB) << 20;
        int boxLimit = (int) Math.min(MAX_BOXES, Math.max(1024L, memoryBytes / 64));

        int centerSecX = (int) Math.floor(focus.x()) >> 4;
        int centerSecY = Math.clamp((int) Math.floor(focus.y()) >> 4, level.getMinSection(), level.getMaxSection() - 1);
        int centerSecZ = (int) Math.floor(focus.z()) >> 4;

        boolean levelChanged = level != previous || memoryBytes != lastMemoryBytes;
        if (levelChanged) {
            sectionCache.clear();
            dirtySections.clear();
            previous = level;
            lastMemoryBytes = memoryBytes;
            lastCenterSecX = Integer.MIN_VALUE;
            cachedTriSignature = Long.MIN_VALUE;
        }

        dirtySections.clear();
        ClientRenderState.drainDirtyCasterSections(dirtySections);
        boolean windowChanged = centerSecX != lastCenterSecX || centerSecY != lastCenterSecY || centerSecZ != lastCenterSecZ;
        boolean revBumpedWithoutDirty = lastSectionRevision != ClientRenderState.sectionRevision && dirtySections.isEmpty();

        if (windowChanged) {
            lastCenterSecX = centerSecX;
            lastCenterSecY = centerSecY;
            lastCenterSecZ = centerSecZ;
            int minX = (centerSecX - SECTION_RADIUS_XZ) << 4;
            int minY = Math.max(level.getMinBuildHeight(), (centerSecY - SECTION_RADIUS_Y) << 4);
            int minZ = (centerSecZ - SECTION_RADIUS_XZ) << 4;
            int maxX = ((centerSecX + SECTION_RADIUS_XZ) << 4) + 15;
            int maxY = Math.min(level.getMaxBuildHeight() - 1, ((centerSecY + SECTION_RADIUS_Y) << 4) + 15);
            int maxZ = ((centerSecZ + SECTION_RADIUS_XZ) << 4) + 15;
            volume.trackCasters(new Vec(minX, minY, minZ), new Vec(maxX, maxY, maxZ));
        }

        boolean cacheChanged = levelChanged || windowChanged;
        if (!dirtySections.isEmpty()) {
            for (long key : dirtySections) {
                if (sectionCache.remove(key) != null) cacheChanged = true;
            }
        } else if (revBumpedWithoutDirty) {
            // e.g. custom-window edit listener bumped sectionRevision directly: refresh only center 3x3x3 sections
            for (int sx = centerSecX - 1; sx <= centerSecX + 1; sx++) {
                for (int sy = centerSecY - 1; sy <= centerSecY + 1; sy++) {
                    for (int sz = centerSecZ - 1; sz <= centerSecZ + 1; sz++) {
                        sectionCache.remove(ClientRenderState.packSectionKey(sx, sy, sz));
                    }
                }
            }
            cacheChanged = true;
        }
        lastSectionRevision = ClientRenderState.sectionRevision;

        int minSecY = Math.max(level.getMinSection(), centerSecY - SECTION_RADIUS_Y);
        int maxSecY = Math.min(level.getMaxSection() - 1, centerSecY + SECTION_RADIUS_Y);

        // Scan ONLY missing or dirty 16x16x16 sections (placing/breaking 1 torch rescans ONLY 1 section!)
        for (int sx = centerSecX - SECTION_RADIUS_XZ; sx <= centerSecX + SECTION_RADIUS_XZ; sx++) {
            for (int sz = centerSecZ - SECTION_RADIUS_XZ; sz <= centerSecZ + SECTION_RADIUS_XZ; sz++) {
                if (!level.hasChunk(sx, sz)) continue;
                LevelChunkSection[] sections = level.getChunk(sx, sz).getSections();
                for (int sy = minSecY; sy <= maxSecY; sy++) {
                    long key = ClientRenderState.packSectionKey(sx, sy, sz);
                    if (sectionCache.containsKey(key)) continue;
                    int idx = level.getSectionIndexFromSectionY(sy);
                    LevelChunkSection sec = (idx >= 0 && idx < sections.length) ? sections[idx] : null;
                    CachedSection built = scanSection(level, sec, sx, sy, sz);
                    sectionCache.put(key, built);
                    cacheChanged = true;
                }
            }
        }

        // Evict sections far outside the active window
        if (windowChanged && sectionCache.size() > 128) {
            sectionCache.entrySet().removeIf(e -> {
                CachedSection s = e.getValue();
                return Math.abs(s.secX - centerSecX) > SECTION_RADIUS_XZ + 1
                        || Math.abs(s.secY - centerSecY) > SECTION_RADIUS_Y + 1
                        || Math.abs(s.secZ - centerSecZ) > SECTION_RADIUS_XZ + 1;
            });
        }

        if (cacheChanged) {
            ClientRenderState.timings.terrainScans++;
            // Order active sections nearest-first so even in dense stations, the closest boxes & emitters always win!
            var activeSections = new ArrayList<CachedSection>(75);
            for (int sx = centerSecX - SECTION_RADIUS_XZ; sx <= centerSecX + SECTION_RADIUS_XZ; sx++) {
                for (int sz = centerSecZ - SECTION_RADIUS_XZ; sz <= centerSecZ + SECTION_RADIUS_XZ; sz++) {
                    for (int sy = minSecY; sy <= maxSecY; sy++) {
                        CachedSection cs = sectionCache.get(ClientRenderState.packSectionKey(sx, sy, sz));
                        if (cs != null && (!cs.boxes.isEmpty() || !cs.emitters.isEmpty() || !cs.extraTris.isEmpty())) {
                            activeSections.add(cs);
                        }
                    }
                }
            }
            final double fx = focus.x(), fy = focus.y(), fz = focus.z();
            activeSections.sort(Comparator.comparingDouble(s -> {
                double dx = (s.secX << 4) + 8 - fx;
                double dy = (s.secY << 4) + 8 - fy;
                double dz = (s.secZ << 4) + 8 - fz;
                return dx * dx + dy * dy + dz * dz;
            }));

            var allBoxes = new ArrayList<ForwardLight.Box>(1024);
            var allEmitters = new ArrayList<ForwardLight.Lamp>(64);
            var allExtraTris = new ArrayList<ForwardLight.CasterTri>(64);
            for (var cs : activeSections) {
                if (allBoxes.size() < boxLimit) {
                    int take = Math.min(cs.boxes.size(), boxLimit - allBoxes.size());
                    for (int i = 0; i < take; i++) allBoxes.add(cs.boxes.get(i));
                }
                allEmitters.addAll(cs.emitters);
                if (allExtraTris.size() < MAX_TRIANGLES) {
                    int take = Math.min(cs.extraTris.size(), MAX_TRIANGLES - allExtraTris.size());
                    for (int i = 0; i < take; i++) allExtraTris.add(cs.extraTris.get(i));
                }
            }
            terrainTree = ForwardLight.buildBoxTree(allBoxes);
            emitters = allEmitters.isEmpty() ? List.of() : List.copyOf(allEmitters);
            boolean extraChanged = allExtraTris.size() != terrainExtraTris.size() || !allExtraTris.equals(terrainExtraTris);
            if (extraChanged) {
                terrainExtraTris = allExtraTris.isEmpty() ? List.of() : List.copyOf(allExtraTris);
                cachedTriSignature = Long.MIN_VALUE;
            }
            rankedCenter = null;
        }

        var lamps = new ArrayList<ForwardLight.Lamp>();
        if (scene != null) {
            for (var l : scene.frame().lights()) {
                if (!l.enabled() || l.intensity() <= 0 || l.support(64) != LightState.Support.SUPPORTED) continue;
                var ownerPose = scene.world().get(l.attachment());
                if (ownerPose == null) continue;
                Vec pos = ownerPose.apply(l.localPosition());
                lamps.add(new ForwardLight.Lamp(pos, l));
                if (lamps.size() >= maxActive) break;
            }
        }
        float partial = scene != null ? scene.frame().partialTick() : 0.0f;
        var sun = daylight(level, partial);

        var dynamicTris = new ArrayList<ForwardLight.CasterTri>();
        var dynamicLamps = new ArrayList<ForwardLight.Lamp>();
        long dynSig = ClientRenderState.collectDynamicCasterTriangles(level, focus, partial, MAX_TRIANGLES, dynamicTris, dynamicLamps);
        for (var dl : dynamicLamps) {
            if (lamps.size() >= maxActive) break;
            lamps.add(dl);
        }

        if (lamps.size() < maxActive && !emitters.isEmpty()) {
            Vec quantizedFocus = new Vec(Math.floor(focus.x() / 4.0) * 4.0, Math.floor(focus.y() / 4.0) * 4.0, Math.floor(focus.z() / 4.0) * 4.0);
            if (!quantizedFocus.equals(rankedCenter)) {
                record Candidate(ForwardLight.Lamp lamp, double d2) {}
                nearest = emitters.stream().map(l -> {
                    Vec d = l.position().minus(quantizedFocus);
                    return new Candidate(l, d.dot(d));
                }).sorted(Comparator.comparingDouble(Candidate::d2))
                        .limit(MAX_FORWARD_LAMPS).map(Candidate::lamp).toList();
                rankedCenter = quantizedFocus;
            }
            lamps.addAll(nearest.subList(0, Math.min(nearest.size(), maxActive - lamps.size())));
        }
        if (lamps.isEmpty() && sun == ForwardLight.Sun.OFF) {
            ForwardLight.clear();
            return;
        }

        double winMinX = (centerSecX - SECTION_RADIUS_XZ) << 4;
        double winMinY = minSecY << 4;
        double winMinZ = (centerSecZ - SECTION_RADIUS_XZ) << 4;
        double winMaxX = ((centerSecX + SECTION_RADIUS_XZ + 1) << 4);
        double winMaxY = ((maxSecY + 1) << 4);
        double winMaxZ = ((centerSecZ + SECTION_RADIUS_XZ + 1) << 4);

        ForwardLight.TriTree triTree = ForwardLight.EMPTY_TRI_TREE;
        boolean hasSections = scene != null && !scene.sections().isEmpty();
        if (hasSections || !terrainExtraTris.isEmpty() || !dynamicTris.isEmpty()) {
            long triSig = 17L;
            triSig = triSig * 31L + centerSecX;
            triSig = triSig * 31L + centerSecY;
            triSig = triSig * 31L + centerSecZ;
            triSig = triSig * 31L + terrainExtraTris.size();
            triSig = triSig * 31L + dynSig;
            record SectionCand(long group, RendererBackend.Section section, Pose pose, double dist2) {}
            var cands = new ArrayList<SectionCand>();
            var focusVec3 = new Vec3(focus.x(), focus.y(), focus.z());
            if (hasSections) {
                for (var entry : scene.sections().entrySet()) {
                    long group = entry.getKey();
                    var pose = scene.world().get(group);
                    if (pose == null) continue;
                    AABB box = volume.groupBounds(scene, group);
                    if (box != null && (box.maxX < winMinX || box.minX > winMaxX
                            || box.maxY < winMinY || box.minY > winMaxY
                            || box.maxZ < winMinZ || box.minZ > winMaxZ)) {
                        continue;
                    }
                    double d2 = box == null ? 0.0 : box.distanceToSqr(focusVec3);
                    cands.add(new SectionCand(group, entry.getValue(), pose, d2));
                    triSig = triSig * 31L + group;
                    triSig = triSig * 31L + entry.getValue().revision().revision();
                    triSig = triSig * 31L + pose.hashCode();
                }
            }
            if (triSig != cachedTriSignature) {
                cands.sort(Comparator.comparingDouble(SectionCand::dist2));
                var triangles = new ArrayList<ForwardLight.CasterTri>(Math.min(MAX_TRIANGLES, dynamicTris.size() + terrainExtraTris.size() + 256));
                for (var dt : dynamicTris) {
                    if (triangles.size() >= MAX_TRIANGLES) break;
                    triangles.add(dt);
                }
                for (var et : terrainExtraTris) {
                    if (triangles.size() >= MAX_TRIANGLES) break;
                    triangles.add(et);
                }
                for (var cand : cands) {
                    var tris = scene.triangles(cand.group);
                    for (var mesh : cand.section.submeshes()) {
                        if (mesh.translucent() || mesh.material().equals("translucent")
                                || mesh.material().endsWith(":translucent") || mesh.material().startsWith("decal:")) {
                            continue;
                        }
                        boolean cutout = mesh.material().equals("cutout");
                        for (int i = mesh.start(); i < mesh.end(); i++) {
                            if (triangles.size() >= MAX_TRIANGLES) break;
                            int mode = cutout ? ((((i - mesh.start()) & 1) == 0) ? 1 : 2) : 0;
                            triangles.add(new ForwardLight.CasterTri(tris.get(i).transform(cand.pose), mode));
                        }
                        if (triangles.size() >= MAX_TRIANGLES) break;
                    }
                    if (triangles.size() >= MAX_TRIANGLES) break;
                }
                cachedTriTree = ForwardLight.buildTriTree(triangles);
                cachedTriSignature = triSig;
            }
            triTree = cachedTriTree;
        }

        ForwardLight.BoxTree boxTree = terrainTree;
        if (sable && scene != null) {
            var shipBoxes = new ArrayList<ForwardLight.Box>();
            Vec center = new Vec((winMinX + winMaxX) * 0.5, (winMinY + winMaxY) * 0.5, (winMinZ + winMaxZ) * 0.5);
            double combinedRadius = 48.0;
            if (ShipSpace.appendCasters(level, center, combinedRadius, scene.frame().partialTick(), shipBoxes,
                    Math.max(0, boxLimit - terrainTree.boxCount())) && !shipBoxes.isEmpty()) {
                var combined = new ArrayList<>(terrainTree.boxes());
                combined.addAll(shipBoxes);
                boxTree = ForwardLight.buildBoxTree(combined);
            }
        }
        ForwardLight.publish(lamps, boxTree, triTree, rayBudget, sun, new Vec(winMinX, winMinY, winMinZ));
    }

    /** Scans a single 16x16x16 chunk section (~0.2ms): extracts all emitters, greedy-merged opaque boxes, and partial boxes. */
    private static CachedSection scanSection(ClientLevel level, LevelChunkSection section, int secX, int secY, int secZ) {
        if (section == null || section.hasOnlyAir()) return CachedSection.empty(secX, secY, secZ);
        int x0 = secX << 4, y0 = secY << 4, z0 = secZ << 4;
        boolean[] occ = new boolean[4096];
        var partial = new ArrayList<ForwardLight.Box>();
        var emittersOut = new ArrayList<ForwardLight.Lamp>();
        var extraTris = new ArrayList<ForwardLight.CasterTri>();
        var cell = new BlockPos.MutableBlockPos();
        boolean anyFull = false;

        for (int ly = 0; ly < 16; ly++) {
            int wy = y0 + ly;
            for (int lz = 0; lz < 16; lz++) {
                int wz = z0 + lz;
                int row = (ly * 16 + lz) * 16;
                for (int lx = 0; lx < 16; lx++) {
                    BlockState state = section.getBlockState(lx, ly, lz);
                    if (state.isAir()) continue;
                    int wx = x0 + lx;
                    int emission = state.getLightEmission(level, cell.set(wx, wy, wz));
                    if (emission > 0) {
                        emittersOut.add(makeEmitter(state, cell, emission));
                        // Light-emitting blocks (glowstone, sea lantern, lantern, ceiling lamp, torch)
                        // never trap their own light inside their own voxel box!
                        continue;
                    }
                    if (collectCaster(level, state, cell, wx, wy, wz, partial, extraTris)) {
                        occ[row + lx] = true;
                        anyFull = true;
                    }
                }
            }
        }

        var boxes = new ArrayList<ForwardLight.Box>();
        if (anyFull) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (!occ[(y * 16 + z) * 16 + x]) continue;
                        int wx = 1;
                        while (x + wx < 16 && occ[(y * 16 + z) * 16 + x + wx]) wx++;
                        int wz = 1;
                        outerZ:
                        while (z + wz < 16) {
                            int r = (y * 16 + z + wz) * 16 + x;
                            for (int dx = 0; dx < wx; dx++) if (!occ[r + dx]) break outerZ;
                            wz++;
                        }
                        int wy = 1;
                        outerY:
                        while (y + wy < 16) {
                            for (int dz = 0; dz < wz; dz++) {
                                int r = ((y + wy) * 16 + z + dz) * 16 + x;
                                for (int dx = 0; dx < wx; dx++) if (!occ[r + dx]) break outerY;
                            }
                            wy++;
                        }
                        for (int dy = 0; dy < wy; dy++) {
                            for (int dz = 0; dz < wz; dz++) {
                                int r = ((y + dy) * 16 + z + dz) * 16 + x;
                                for (int dx = 0; dx < wx; dx++) occ[r + dx] = false;
                            }
                        }
                        boxes.add(new ForwardLight.Box(
                                new Vec(x0 + x, y0 + y, z0 + z),
                                new Vec(x0 + x + wx, y0 + y + wy, z0 + z + wz)));
                    }
                }
            }
        }
        if (!partial.isEmpty()) {
            boxes.addAll(partial.size() <= 48 ? mergeExactBoxes(partial) : partial);
        }
        return new CachedSection(
                secX, secY, secZ,
                boxes.isEmpty() ? List.of() : List.copyOf(boxes),
                emittersOut.isEmpty() ? List.of() : List.copyOf(emittersOut),
                extraTris.isEmpty() ? List.of() : List.copyOf(extraTris));
    }

    private static ForwardLight.Sun daylight(ClientLevel level, float partial) {
        if (!level.dimensionType().hasSkyLight()) return ForwardLight.Sun.OFF;
        double tod = Math.floor(level.getTimeOfDay(partial) * 2400.0) / 2400.0;
        double angle = tod * (2 * Math.PI);
        double y = Math.cos(angle), sign = y >= 0 ? 1 : -1;
        Vec direction = new Vec(-sign * Math.sin(angle), Math.abs(y), 0);
        double t = Math.clamp((direction.y() - .04) / .28, 0, 1);
        double rain = Math.floor(level.getRainLevel(partial) * 20.0) / 20.0;
        double thunder = Math.floor(level.getThunderLevel(partial) * 20.0) / 20.0;
        double strength = t * t * (3 - 2 * t) * (1 - .75 * rain) * (1 - .75 * thunder);
        if (strength == 0) return ForwardLight.Sun.OFF;
        Vec color = sign > 0 ? new Vec(.55, .50, .43) : new Vec(.035, .05, .085);
        return new ForwardLight.Sun(direction, color.times(strength));
    }

    private static ForwardLight.Lamp makeEmitter(BlockState state, BlockPos pos, int emission) {
        double x = pos.getX() + .5, y = pos.getY() + .5, z = pos.getZ() + .5;
        if (state.getBlock() instanceof TorchBlock) {
            if (state.hasProperty(WallTorchBlock.FACING)) {
                var face = state.getValue(WallTorchBlock.FACING);
                x -= .27 * face.getStepX();
                z -= .27 * face.getStepZ();
                y = pos.getY() + .92;
            } else y = pos.getY() + .7;
        } else if (state.getBlock() instanceof LanternBlock) {
            y = pos.getY() + (state.getValue(LanternBlock.HANGING) ? .4375 : .3125);
        } else if (state.getBlock().getDescriptionId().contains("ceilin")) {
            y = pos.getY() + .35;
        }
        Vec p = new Vec(x, y, z);
        Vec color;
        if (state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH) || state.is(Blocks.SOUL_LANTERN)
                || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.SOUL_FIRE)) color = new Vec(.22, .72, 1);
        else if (state.is(Blocks.SEA_LANTERN) || state.is(Blocks.END_ROD) || state.is(Blocks.BEACON)) color = new Vec(.78, .91, 1);
        else if (state.is(Blocks.REDSTONE_TORCH) || state.is(Blocks.REDSTONE_WALL_TORCH)) color = new Vec(1, .16, .07);
        else if (state.getBlock().getDescriptionId().contains("ceilin") || state.getBlock().getDescriptionId().contains("lamp")
                || state.getBlock().getDescriptionId().contains("light")) color = new Vec(1.0, 0.94, 0.84);
        else color = new Vec(1, .64, .30);
        var source = new LightState(pos.asLong(), 0, 0, Vec.ZERO, color, 2.8 * emission / 15.0, emission,
                true, LightState.LightKind.POINT, 0, 0);
        return new ForwardLight.Lamp(p, source, ForwardLight.TARGET_ALL);
    }

    /** Append partial shapes and flag full cubes for the 16x16x16 greedy merge, accurately transmitting light through
     * glass, panes, invisible multi-block proxies, StaticShell carriage parts, and custom-cut windows. */
    private static boolean collectCaster(ClientLevel level, BlockState state, BlockPos.MutableBlockPos cell,
                                         int x, int y, int z, List<ForwardLight.Box> partial,
                                         List<ForwardLight.CasterTri> extraTris) {
        if (state.getRenderShape() == RenderShape.INVISIBLE) return false;
        if (ClientRenderState.skipVoxelBox(level, x, y, z, state)) {
            if (extraTris.size() < MAX_TRIANGLES) {
                ClientRenderState.appendExtraCasterTriangles(level, x, y, z, state, extraTris);
            }
            return false;
        }
        if (state.is(Blocks.IRON_BARS)) {
            appendIronBars(state, x, y, z, partial);
            return false;
        }
        if (!state.getFluidState().isEmpty() || state.getBlock() instanceof LeavesBlock) return false;
        if (state.getBlock() instanceof StainedGlassPaneBlock || state.is(Blocks.GLASS_PANE)
                || state.is(Tags.Blocks.GLASS_PANES)) {
            return false;
        }
        cell.set(x, y, z);
        if (state.getBlock() instanceof HalfTransparentBlock || state.is(Tags.Blocks.GLASS_BLOCKS)) {
            if (state.getLightBlock(level, cell) < 15) return false;
        }
        VoxelShape shape;
        if (state.canOcclude()) {
            shape = state.getOcclusionShape(level, cell);
            if (shape.isEmpty()) shape = state.getShape(level, cell);
        } else {
            if (state.getBlock() instanceof IronBarsBlock) {
                appendShape(state.getShape(level, cell), x, y, z, partial);
                return false;
            }
            if (state.getVisualShape(level, cell, CollisionContext.empty()).isEmpty()) return false;
            shape = state.getShape(level, cell);
        }
        if (Block.isShapeFullBlock(shape)) return true;
        appendShape(shape, x, y, z, partial);
        return false;
    }

    /** Exact perforated metal-bar sub-boxes matching Minecraft's 16x16 iron_bars.png texture. */
    private static void appendIronBars(BlockState state, int wx, int wy, int wz, List<ForwardLight.Box> out) {
        final double P = 1.0 / 16.0;
        out.add(new ForwardLight.Box(new Vec(wx + 7 * P, wy, wz + 7 * P), new Vec(wx + 9 * P, wy + 1, wz + 9 * P)));
        if (state.getValue(CrossCollisionBlock.NORTH)) {
            out.add(new ForwardLight.Box(new Vec(wx + 7 * P, wy, wz + 2 * P), new Vec(wx + 9 * P, wy + 1, wz + 4 * P)));
            out.add(new ForwardLight.Box(new Vec(wx + 7 * P, wy + 12 * P, wz + 2 * P), new Vec(wx + 9 * P, wy + 14 * P, wz + 7 * P)));
            out.add(new ForwardLight.Box(new Vec(wx + 7 * P, wy + 7 * P, wz), new Vec(wx + 9 * P, wy + 9 * P, wz + 4 * P)));
        }
        if (state.getValue(CrossCollisionBlock.SOUTH)) {
            out.add(new ForwardLight.Box(new Vec(wx + 7 * P, wy, wz + 12 * P), new Vec(wx + 9 * P, wy + 1, wz + 14 * P)));
            out.add(new ForwardLight.Box(new Vec(wx + 7 * P, wy + 2 * P, wz + 9 * P), new Vec(wx + 9 * P, wy + 4 * P, wz + 14 * P)));
            out.add(new ForwardLight.Box(new Vec(wx + 7 * P, wy + 7 * P, wz + 12 * P), new Vec(wx + 9 * P, wy + 9 * P, wz + 1)));
        }
        if (state.getValue(CrossCollisionBlock.WEST)) {
            out.add(new ForwardLight.Box(new Vec(wx + 2 * P, wy, wz + 7 * P), new Vec(wx + 4 * P, wy + 1, wz + 9 * P)));
            out.add(new ForwardLight.Box(new Vec(wx + 2 * P, wy + 12 * P, wz + 7 * P), new Vec(wx + 7 * P, wy + 14 * P, wz + 9 * P)));
            out.add(new ForwardLight.Box(new Vec(wx, wy + 7 * P, wz + 7 * P), new Vec(wx + 4 * P, wy + 9 * P, wz + 9 * P)));
        }
        if (state.getValue(CrossCollisionBlock.EAST)) {
            out.add(new ForwardLight.Box(new Vec(wx + 12 * P, wy, wz + 7 * P), new Vec(wx + 14 * P, wy + 1, wz + 9 * P)));
            out.add(new ForwardLight.Box(new Vec(wx + 9 * P, wy + 2 * P, wz + 7 * P), new Vec(wx + 14 * P, wy + 4 * P, wz + 9 * P)));
            out.add(new ForwardLight.Box(new Vec(wx + 12 * P, wy + 7 * P, wz + 7 * P), new Vec(wx + 1, wy + 9 * P, wz + 9 * P)));
        }
    }

    private static void appendShape(VoxelShape shape, int wx, int wy, int wz, List<ForwardLight.Box> out) {
        if (shape.isEmpty()) return;
        for (AABB aabb : shape.toAabbs()) {
            // Skip paper-thin flat floor decorations (rails, carpets, snow layers <= 2px) that bloat BVH without real shadows
            if (aabb.getXsize() <= 1e-6 || aabb.getYsize() <= 1e-6 || aabb.getZsize() <= 1e-6) continue;
            if (aabb.minY <= 1e-4 && aabb.maxY <= 0.13) continue;
            out.add(new ForwardLight.Box(
                    new Vec(wx + aabb.minX, wy + aabb.minY, wz + aabb.minZ),
                    new Vec(wx + aabb.maxX, wy + aabb.maxY, wz + aabb.maxZ)));
        }
    }

    /** Exact merge of AABBs sharing two axes identically and touching/overlapping on the third. */
    private static List<ForwardLight.Box> mergeExactBoxes(List<ForwardLight.Box> input) {
        var list = new ArrayList<ForwardLight.Box>(new LinkedHashSet<>(input));
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < list.size(); i++) {
                var a = list.get(i);
                for (int j = i + 1; j < list.size(); j++) {
                    var b = list.get(j);
                    ForwardLight.Box merged = tryMerge(a, b);
                    if (merged != null) {
                        list.set(i, merged);
                        list.remove(j);
                        a = merged;
                        j--;
                        changed = true;
                    }
                }
            }
        }
        return list;
    }

    private static boolean eq(double a, double b) { return Math.abs(a - b) <= 1e-9; }

    private static ForwardLight.Box tryMerge(ForwardLight.Box a, ForwardLight.Box b) {
        boolean sameX = eq(a.low().x(), b.low().x()) && eq(a.high().x(), b.high().x());
        boolean sameY = eq(a.low().y(), b.low().y()) && eq(a.high().y(), b.high().y());
        boolean sameZ = eq(a.low().z(), b.low().z()) && eq(a.high().z(), b.high().z());
        if (sameX && sameY && a.low().z() <= b.high().z() + 1e-9 && b.low().z() <= a.high().z() + 1e-9)
            return new ForwardLight.Box(Vec.min(a.low(), b.low()), Vec.max(a.high(), b.high()));
        if (sameX && sameZ && a.low().y() <= b.high().y() + 1e-9 && b.low().y() <= a.high().y() + 1e-9)
            return new ForwardLight.Box(Vec.min(a.low(), b.low()), Vec.max(a.high(), b.high()));
        if (sameY && sameZ && a.low().x() <= b.high().x() + 1e-9 && b.low().x() <= a.high().x() + 1e-9)
            return new ForwardLight.Box(Vec.min(a.low(), b.low()), Vec.max(a.high(), b.high()));
        return null;
    }
}
