package willow.train.hexie.client.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import dev.engine_room.flywheel.api.visual.Effect;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import willow.train.hexie.Main;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.level.block.Block;
import willow.train.hexie.compat.sable.SableRenderEmbedding;

/** Shared world-shell index: Flywheel instances with vanilla/Sodium section meshes as fallback. */
final class StaticShellSections {
    private record Models(Map<BlockState, StaticShellMesh> meshes) {}
    private record Draw(BlockPos cell, BakedQuad quad, RenderType renderType, int[] light) {}
    private volatile Models models = new Models(Map.of());
    // All world-dependent state is owned by the main client thread.
    private ClientLevel observedLevel;
    private Models observedModels;
    private VisualizationManager visualizationManager;
    private final Map<BlockPos, Effect> shells = new HashMap<>();
    private final Map<SableRenderEmbedding.Handle, StaticShellPlotVisual.Parts> plotShells = new HashMap<>();
    private final Map<BlockPos, Map<BlockPos, BlockState>> observed = new HashMap<>();
    private final Set<BlockPos> dirty = new HashSet<>();
    // Geometry membership changes only when owners/chunks/models change, never for lighting.
    private final Map<BlockPos, Map<BlockPos, List<StaticShellMesh.Face>>> destinations = new HashMap<>();
    private final Map<BlockPos, Set<BlockPos>> ownerDestinations = new HashMap<>();

    private final Predicate<Block> blocks;
    private final Predicate<BlockState> owners;
    private final Predicate<BlockState> worldTerrain;
    private final BiFunction<BakedModel, BlockState, BakedModel> prepare;
    private final boolean unshadedVirtual;

    StaticShellSections(Predicate<Block> blocks, Predicate<BlockState> owners,
                        Predicate<BlockState> worldTerrain,
                        BiFunction<BakedModel, BlockState, BakedModel> prepare, boolean unshadedVirtual) {
        this.blocks = blocks;
        this.owners = owners;
        this.worldTerrain = worldTerrain;
        this.prepare = prepare;
        this.unshadedVirtual = unshadedVirtual;
    }

    void register() {
        Main.bus.addListener(this::bake);
        NeoForge.EVENT_BUS.addListener(this::addGeometry);
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::chunkLoaded);
        NeoForge.EVENT_BUS.addListener(this::chunkUnloaded);
        NeoForge.EVENT_BUS.addListener(this::levelUnloaded);
    }

    private boolean owner(BlockState state) { return owners.test(state); }

    private void bake(ModelEvent.ModifyBakingResult event) {
        var meshes = new HashMap<BlockState, StaticShellMesh>();
        for (var block : BuiltInRegistries.BLOCK) {
            if (!blocks.test(block)) continue;
            for (var state : block.getStateDefinition().getPossibleStates()) {
                if (!owner(state)) continue;
                var key = BlockModelShaper.stateToModelLocation(state);
                var original = event.getModels().get(key);
                if (original == null) continue;
                original = prepare.apply(original, state);
                var mesh = new StaticShellMesh(original, state);
                meshes.put(state, mesh);
                event.getModels().put(key, new StaticShellModel(original, unshadedVirtual));
            }
        }
        models = new Models(Map.copyOf(meshes));
    }

    private void select(ClientLevel level) {
        if (observedLevel != level) {
            clear();
            observedLevel = level;
            observedModels = models;
        }
        // Manager identity also changes on renderer/resource reload and backend switches.
        var manager = VisualizationManager.get(level);
        if (manager != visualizationManager || observedModels != models) {
            clearVisuals();
            visualizationManager = manager;
            observedModels = models;
            dirty.addAll(destinations.keySet());
            destinations.clear(); ownerDestinations.clear();
            // Switching off builds the fallback index; switching on discards it.
            for (var roots : observed.values()) roots.forEach(this::index);
        }
    }

    /** Render-thread query for Sable's separate single-block model path. */
    boolean instanced(BlockPos root) { return shells.containsKey(root); }

    private void clear() {
        clearVisuals();
        observed.clear(); dirty.clear(); destinations.clear(); ownerDestinations.clear();
        observedLevel = null; observedModels = null;
    }

    private void clearVisuals() {
        if (visualizationManager != null)
            new HashSet<>(shells.values()).forEach(visualizationManager.effects()::queueRemove);
        shells.clear();
        plotShells.clear();
        visualizationManager = null;
    }

    private void addVisual(BlockPos root, BlockState state) {
        if (visualizationManager == null || !models.meshes.containsKey(state)) return;
        var plot = SableRenderEmbedding.find(observedLevel, root);
        // Ordinary-world panels are static terrain. Use the existing clipped section
        // mesh path so placing the first panel does not create Flywheel's smooth-light
        // and OIT programs on the render thread. Moving plots keep their own effects.
        if (plot == null && worldTerrain.test(state)) return;
        if (plot != null) {
            var parts = plotShells.get(plot);
            if (parts == null) {
                parts = new StaticShellPlotVisual.Parts(observedLevel, plot);
                plotShells.put(plot, parts);
                parts.put(root, state);
                visualizationManager.effects().queueAdd(parts);
            } else if (parts.put(root, state)) {
                visualizationManager.effects().queueUpdate(parts);
            }
            shells.put(root, parts);
            return;
        }
        var shell = new StaticShellVisual.Shell(observedLevel, root.immutable(), state);
        shells.put(root, shell);
        visualizationManager.effects().queueAdd(shell);
    }

    private static BlockPos section(BlockPos p) {
        return new BlockPos(p.getX() >> 4, p.getY() >> 4, p.getZ() >> 4);
    }

    private void removeVisual(BlockPos root) {
        var oldShell = shells.remove(root);
        if (oldShell == null || visualizationManager == null) return;
        if (oldShell instanceof StaticShellPlotVisual.Parts parts) {
            boolean update = parts.remove(root);
            if (parts.isEmpty()) {
                plotShells.remove(parts.plot);
                visualizationManager.effects().queueRemove(parts);
            } else if (update) {
                visualizationManager.effects().queueUpdate(parts);
            }
        } else {
            visualizationManager.effects().queueRemove(oldShell);
        }
    }

    private void index(BlockPos root, BlockState state) {
        removeVisual(root);
        var previous = ownerDestinations.remove(root);
        if (previous != null) for (var target : previous) {
            var entries = destinations.get(target);
            if (entries != null) {
                entries.remove(root);
                if (entries.isEmpty()) destinations.remove(target);
            }
            dirty.add(target);
        }
        var mesh = models.meshes.get(state);
        if (mesh == null) return;
        addVisual(root, state);
        // An instance needs no per-owner copies of the clipped fallback face lists.
        if (shells.containsKey(root)) return;
        // Plots only allocate render sections inside their block bounds. Keep even
        // boundary faces in the owner's section; their cell offsets stay unchanged.
        var plotSection = SableRenderEmbedding.find(observedLevel, root) != null ? section(root) : null;
        var groups = new HashMap<BlockPos, List<StaticShellMesh.Face>>();
        for (var face : mesh.faces)
            groups.computeIfAbsent(plotSection != null ? plotSection : section(root.offset(face.cell())),
                    ignored -> new ArrayList<>()).add(face);
        groups.forEach((target, faces) -> {
            destinations.computeIfAbsent(target, ignored -> new HashMap<>()).put(root, List.copyOf(faces));
            dirty.add(target);
        });
        ownerDestinations.put(root, Set.copyOf(groups.keySet()));
    }

    private void indexChunk(ClientLevel level, ChunkAccess chunk) {
        int sx = chunk.getPos().x, sz = chunk.getPos().z;
        // Inspect only the delivered chunk, once per load/replacement. No neighbour queries.
        for (int i = 0; i < chunk.getSections().length; i++) {
            int sy = level.getSectionYFromSectionIndex(i);
            var key = new BlockPos(sx, sy, sz);
            var source = chunk.getSection(i);
            var previous = observed.getOrDefault(key, Map.of());
            if (!source.maybeHas(this::owner)) {
                for (var root : previous.keySet()) index(root, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                observed.remove(key);
                continue;
            }
            var found = new HashMap<BlockPos, BlockState>();
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                var state = source.getBlockState(x, y, z);
                if (owner(state)) found.put(new BlockPos(sx * 16 + x, sy * 16 + y, sz * 16 + z), state);
            }
            for (var root : previous.keySet())
                if (!found.containsKey(root)) index(root, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            found.forEach((root, state) -> {
                if (previous.get(root) != state) index(root, state);
            });
            if (found.isEmpty()) observed.remove(key);
            else observed.put(key, Map.copyOf(found));
        }
    }

    private void addGeometry(AddSectionGeometryEvent event) {
        if (!(event.getLevel() instanceof ClientLevel level)) return;
        select(level);
        var origin = event.getSectionOrigin();
        var destination = section(origin);
        var entries = destinations.get(destination);
        dirty.remove(destination);
        // Ordinary terrain sections do no train lookups or lighting work.
        if (entries == null) return;
        var draws = new ArrayList<Draw>();
        var lights = new HashMap<BlockPos, Integer>();
        for (var entry : entries.entrySet()) {
            // World and plot effects own the instanced path. Section geometry is only
            // the fallback for owners without a Flywheel effect.
            if (shells.containsKey(entry.getKey())) continue;
            for (var face : entry.getValue()) {
                var cell = entry.getKey().offset(face.cell());
                int[] light = new int[4];
                int[] vertices = face.quad().getVertices();
                for (int i = 0; i < 4; i++) {
                    int p = i * 8;
                    light[i] = lightAt(level, lights,
                            cell.getX()+Float.intBitsToFloat(vertices[p]),
                            cell.getY()+Float.intBitsToFloat(vertices[p+1]),
                            cell.getZ()+Float.intBitsToFloat(vertices[p+2]));
                }
                draws.add(new Draw(cell.subtract(origin), face.quad(), face.renderType(), light));
            }
        }
        if (draws.isEmpty()) return;
        var snapshot = List.copyOf(draws);
        event.addRenderer(context -> {
            var pose = context.getPoseStack();
            float[] brightness = {1, 1, 1, 1};
            for (var draw : snapshot) {
                var buffer = context.getOrCreateChunkBuffer(draw.renderType());
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
    static int lightAt(net.minecraft.world.level.BlockAndTintGetter level, Map<BlockPos, Integer> cache,
                              double x, double y, double z) {
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

    private void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) { clear(); return; }
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

    private void chunkLoaded(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ClientLevel level)) return;
        // Read only the supplied chunk. Dirty notifications run at the end of the tick,
        // after it has entered the client cache; do not query the level during loading.
        var chunk = event.getChunk();
        select(level);
        indexChunk(level, chunk);
    }

    void changed(net.minecraft.world.level.LevelReader world, BlockPos pos,
                               BlockState oldState, BlockState newState) {
        if (!(world instanceof ClientLevel level)) return;
        select(level);
        if (!owner(oldState) && !owner(newState)) return;
        pos = pos.immutable();
        var source = section(pos);
        var previous = observed.getOrDefault(source, Map.of());
        if (previous.get(pos) == newState || !previous.containsKey(pos) && !owner(newState)) return;
        index(pos, newState);
        var roots = new HashMap<>(previous);
        if (owner(newState)) roots.put(pos, newState);
        else roots.remove(pos);
        if (roots.isEmpty()) observed.remove(source);
        else observed.put(source, Map.copyOf(roots));
    }

    private void chunkUnloaded(ChunkEvent.Unload event) {
        if (event.getLevel() != observedLevel) return;
        var pos = event.getChunk().getPos();
        // Remove this chunk's owners directly; never sweep the world's index.
        for (int i = 0; i < event.getChunk().getSections().length; i++) {
            var key = new BlockPos(pos.x, observedLevel.getSectionYFromSectionIndex(i), pos.z);
            var roots = observed.remove(key);
            if (roots != null) for (var root : roots.keySet())
                index(root, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
    }

    private void levelUnloaded(LevelEvent.Unload event) {
        if (event.getLevel() == observedLevel) {
            clear();
        }
    }
}
