package willow.train.hexie.client.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import willow.train.hexie.Main;
import willow.train.hexie.blocks.locos.head.CRH2HeadBlock;
import willow.train.hexie.blocks.locos.crh2a.CRH2PartBlock;

/** Static chunk meshes, including Sodium's implementation of AddSectionGeometryEvent. */
public final class CRH2StaticSections {
    private record Models(Map<BlockState, CRH2StaticMesh> meshes, int reach) {}
    private record Draw(BlockPos cell, BakedQuad quad, int[] light) {}
    private static volatile Models models = new Models(Map.of(), 0);
    // All world-dependent state is owned by the main client thread.
    private static ClientLevel observedLevel;
    private static Models observedModels;
    private static final Map<BlockPos, Map<BlockPos, BlockState>> observed = new HashMap<>();
    private static final Set<BlockPos> dirty = new HashSet<>();
    private static final Set<BlockPos> scanned = new HashSet<>();
    private static final Map<BlockPos, Map<BlockPos, List<CRH2StaticMesh.Face>>> destinations = new HashMap<>();
    private static final Map<BlockPos, Set<BlockPos>> ownerDestinations = new HashMap<>();

    private CRH2StaticSections() {}

    public static void register() {
        Main.bus.addListener(CRH2StaticSections::bake);
        NeoForge.EVENT_BUS.addListener(CRH2StaticSections::addGeometry);
        NeoForge.EVENT_BUS.addListener(CRH2StaticSections::tick);
        NeoForge.EVENT_BUS.addListener(CRH2StaticSections::chunkLoaded);
        NeoForge.EVENT_BUS.addListener(CRH2StaticSections::chunkUnloaded);
        NeoForge.EVENT_BUS.addListener(CRH2StaticSections::levelUnloaded);
    }

    private static boolean owner(BlockState state) {
        return state.getBlock() instanceof CRH2HeadBlock
                || state.getBlock() instanceof CRH2PartBlock && state.getValue(CRH2PartBlock.PART) == 0;
    }

    private static void bake(ModelEvent.ModifyBakingResult event) {
        var meshes = new HashMap<BlockState, CRH2StaticMesh>();
        int reach = 0;
        for (var block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof CRH2HeadBlock) && !(block instanceof CRH2PartBlock)) continue;
            for (var state : block.getStateDefinition().getPossibleStates()) {
                if (!owner(state)) continue;
                var key = BlockModelShaper.stateToModelLocation(state);
                var original = event.getModels().get(key);
                if (original == null) continue;
                var mesh = new CRH2StaticMesh(original, state);
                meshes.put(state, mesh);
                reach = Math.max(reach, mesh.reach);
                event.getModels().put(key, new CRH2StaticModel(original));
            }
        }
        models = new Models(Map.copyOf(meshes), reach);
    }

    private static void select(ClientLevel level) {
        if (observedLevel == level && observedModels == models) return;
        observed.clear(); dirty.clear(); scanned.clear(); destinations.clear(); ownerDestinations.clear();
        observedLevel = level; observedModels = models;
    }

    private static BlockPos section(BlockPos p) {
        return new BlockPos(p.getX() >> 4, p.getY() >> 4, p.getZ() >> 4);
    }

    private static void index(BlockPos root, BlockState state) {
        var previous = ownerDestinations.remove(root);
        if (previous != null) for (var section : previous) {
            var entries = destinations.get(section);
            if (entries != null) { entries.remove(root); if (entries.isEmpty()) destinations.remove(section); }
            dirty.add(section);
        }
        var mesh = models.meshes.get(state);
        if (mesh == null) return;
        var groups = new HashMap<BlockPos, List<CRH2StaticMesh.Face>>();
        for (var face : mesh.faces)
            groups.computeIfAbsent(section(root.offset(face.cell())), ignored -> new ArrayList<>()).add(face);
        groups.forEach((section, faces) -> {
            destinations.computeIfAbsent(section, ignored -> new HashMap<>()).put(root, List.copyOf(faces));
            dirty.add(section);
        });
        ownerDestinations.put(root, Set.copyOf(groups.keySet()));
    }

    private static Map<BlockPos, BlockState> roots(ClientLevel level, int sx, int sy, int sz) {
        var key = new BlockPos(sx, sy, sz);
        if (scanned.contains(key)) return observed.getOrDefault(key, Map.of());
        var found = new HashMap<BlockPos, BlockState>();
        var chunk = level.getChunk(sx, sz, ChunkStatus.FULL, false);
        int index = level.getSectionIndexFromSectionY(sy);
        if (chunk == null) return Map.of();
        scanned.add(key);
        if (index >= 0 && index < chunk.getSections().length) {
            var source = chunk.getSection(index);
            // Skip whole palettes without train owners; no air-volume scan each frame.
            if (source.maybeHas(CRH2StaticSections::owner)) {
                for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                    var state = source.getBlockState(x, y, z);
                    if (owner(state)) found.put(new BlockPos(sx*16+x, sy*16+y, sz*16+z), state);
                }
            }
        }
        var previous = observed.getOrDefault(key, Map.of());
        if (!previous.equals(found)) {
            for (var old : previous.keySet()) if (!found.containsKey(old)) index(old, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            found.forEach(CRH2StaticSections::index);
            if (found.isEmpty()) observed.remove(key);
            else observed.put(key, Map.copyOf(found));
        }
        return found;
    }

    private static void addGeometry(AddSectionGeometryEvent event) {
        if (!(event.getLevel() instanceof ClientLevel level)) return;
        select(level);
        var current = models;
        if (current.meshes.isEmpty()) return;
        var origin = event.getSectionOrigin();
        var destination = section(origin);
        int reach = current.reach;
        var draws = new ArrayList<Draw>();
        var lights = new HashMap<BlockPos, Integer>();
        for (int sx = (origin.getX()-reach) >> 4; sx <= (origin.getX()+15+reach) >> 4; sx++)
            for (int sz = (origin.getZ()-reach) >> 4; sz <= (origin.getZ()+15+reach) >> 4; sz++)
                for (int sy = (origin.getY()-reach) >> 4; sy <= (origin.getY()+15+reach) >> 4; sy++) {
                    roots(level, sx, sy, sz);
                }
        for (var entry : destinations.getOrDefault(destination, Map.of()).entrySet()) {
            for (var face : entry.getValue()) {
                var cell = entry.getKey().offset(face.cell());
                int[] light = new int[4];
                int[] vertices = face.quad().getVertices();
                for (int i = 0; i < 4; i++) {
                    int p = i*8;
                    light[i] = lightAt(level, lights,
                            cell.getX()+Float.intBitsToFloat(vertices[p]),
                            cell.getY()+Float.intBitsToFloat(vertices[p+1]),
                            cell.getZ()+Float.intBitsToFloat(vertices[p+2]));
                }
                draws.add(new Draw(cell.subtract(origin), face.quad(), light));
            }
        }
        // This rebuild already contains its latest data. Other affected sections are queued below.
        dirty.remove(destination);
        if (draws.isEmpty()) return;
        var snapshot = List.copyOf(draws);
        event.addRenderer(context -> {
            var pose = context.getPoseStack();
            var buffer = context.getOrCreateChunkBuffer(RenderType.cutout());
            float[] brightness = {1, 1, 1, 1};
            for (var draw : snapshot) {
                pose.pushPose();
                pose.translate(draw.cell.getX(), draw.cell.getY(), draw.cell.getZ());
                // Supply the actual vertex lightmap directly: no owner-block lighting pass afterwards.
                buffer.putBulkData(pose.last(), draw.quad, brightness, 1, 1, 1, 1,
                        draw.light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, true);
                pose.popPose();
            }
        });
    }

    /** Same continuous light field for both shells, independent of anchor and face direction. */
    private static int lightAt(ClientLevel level, Map<BlockPos, Integer> cache, double x, double y, double z) {
        x -= .5; y -= .5; z -= .5;
        int bx = (int)Math.floor(x), by = (int)Math.floor(y), bz = (int)Math.floor(z);
        double fx = x-bx, fy = y-by, fz = z-bz, block = 0, sky = 0;
        for (int dx = 0; dx < 2; dx++) for (int dy = 0; dy < 2; dy++) for (int dz = 0; dz < 2; dz++) {
            var at = new BlockPos(bx+dx, by+dy, bz+dz);
            int light = cache.computeIfAbsent(at, p -> LevelRenderer.getLightColor(level, p));
            double weight = (dx == 0 ? 1-fx : fx)*(dy == 0 ? 1-fy : fy)*(dz == 0 ? 1-fz : fz);
            block += (light & 65535)*weight;
            sky += (light >>> 16)*weight;
        }
        return (int)Math.round(block) | ((int)Math.round(sky) << 16);
    }

    private static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) { observed.clear(); dirty.clear(); scanned.clear(); destinations.clear(); ownerDestinations.clear(); observedLevel = null; return; }
        select(mc.level);
        // Removal replaces an owner with air, whose block callback cannot notify us.
        // Inspect only known owners, never rescan all loaded blocks on a tick.
        var iterator = observed.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Map<BlockPos, BlockState> replacement = null;
            for (var root : entry.getValue().entrySet()) {
                var actual = mc.level.getBlockState(root.getKey());
                if (actual == root.getValue()) continue;
                index(root.getKey(), actual);
                if (replacement == null) replacement = new HashMap<>(entry.getValue());
                replacement.remove(root.getKey());
                if (owner(actual)) {
                    replacement.put(root.getKey(), actual);

                }
            }
            if (replacement != null) {
                if (replacement.isEmpty()) iterator.remove();
                else entry.setValue(Map.copyOf(replacement));
            }
        }
        if (dirty.isEmpty()) return;
        var pending = Set.copyOf(dirty); dirty.clear();
        for (var section : pending)
            mc.levelRenderer.setSectionDirty(section.getX(), section.getY(), section.getZ());
    }

    private static void chunkLoaded(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ClientLevel level)) return;
        // Read only the supplied chunk. Dirty notifications run at the end of the tick,
        // after it has entered the client cache; do not query the level during loading.
        var chunk = event.getChunk();
        select(level);
        scanned.removeIf(key -> key.getX() == chunk.getPos().x && key.getZ() == chunk.getPos().z);
        int radius = (models.reach+15)/16;
        for (int i = 0; i < chunk.getSections().length; i++) {
            if (!chunk.getSection(i).maybeHas(CRH2StaticSections::owner)) continue;
            int sy = level.getSectionYFromSectionIndex(i);
            for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++)
                for (int y = -radius; y <= radius; y++)
                    dirty.add(new BlockPos(chunk.getPos().x+x, sy+y, chunk.getPos().z+z));
        }
    }

    public static void changed(net.minecraft.world.level.LevelReader world, BlockPos pos,
                               BlockState oldState, BlockState newState) {
        if (!(world instanceof ClientLevel level)) return;
        select(level);
        index(pos, newState);
        var source = section(pos);
        {
            var roots = new HashMap<>(observed.getOrDefault(source, Map.of()));
            roots.remove(pos);
            if (owner(newState)) roots.put(pos.immutable(), newState);
            if (roots.isEmpty()) observed.remove(source); else observed.put(source, Map.copyOf(roots));
        }
    }

    private static void chunkUnloaded(ChunkEvent.Unload event) {
        if (event.getLevel() != observedLevel) return;
        var pos = event.getChunk().getPos();
        scanned.removeIf(key -> key.getX() == pos.x && key.getZ() == pos.z);
        var iterator = observed.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getKey().getX() == pos.x && entry.getKey().getZ() == pos.z) {
                for (var root : entry.getValue().keySet()) index(root, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                iterator.remove();
            }
        }
    }

    private static void levelUnloaded(LevelEvent.Unload event) {
        if (event.getLevel() == observedLevel) {
            observed.clear(); dirty.clear(); scanned.clear(); destinations.clear(); ownerDestinations.clear(); observedLevel = null;
        }
    }
}
