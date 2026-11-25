package willow.train.hexie.render.carriage;

// split from willow/train/hexie/client/render/StaticShellSections.java

import dev.engine_room.flywheel.api.visual.Effect;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.render.SableClientBridge.SableRenderEmbeddingHandleView;
import willow.train.hexie.core.sticker.StickerEntry;
import willow.train.hexie.render.rendererbridge.HexieRendererBridge;
import willow.train.hexie.render.sticker.BlockModelCutter;

/**
 * Shared CRH1/CRH2 shell index: renders all world shells natively through HeXie's embedded
 * hexie-renderer engine (StructureRevision batched per chunk section, replacing Flywheel StaticShellVisual
 * and StaticShellSectionGeometry), while retaining Sable plot visuals for moving physics plots.
 */
final class StaticShellSections {
    private static final AtomicInteger FAMILY_SEQ = new AtomicInteger(1);

    private record Models(Map<BlockState, StaticShellMesh> meshes, Map<BlockState, String> modelKeys) {}
    private volatile Models models = new Models(Map.of(), Map.of());
    private final String familyId = "shell-" + FAMILY_SEQ.getAndIncrement();

    // All world-dependent state is owned by the main client thread.
    private ClientLevel observedLevel;
    private Models observedModels;
    private VisualizationManager visualizationManager;
    final Map<BlockPos, Effect> shells = new HashMap<>();
    private final Map<SableRenderEmbeddingHandleView, StaticShellPlotVisual.Parts> plotShells = new HashMap<>();
    private final Map<BlockPos, Map<BlockPos, BlockState>> observed = new HashMap<>();
    private final Map<BlockPos, Map<BlockPos, String>> rendererSections = new HashMap<>();
    private final Set<BlockPos> dirtyRendererSections = new HashSet<>();

    // Fallback section-geometry index (used only for moving Sable plots without Flywheel or with cuts).
    final Set<BlockPos> dirty = new HashSet<>();
    final Map<BlockPos, Map<BlockPos, List<StaticShellMesh.Face>>> destinations = new HashMap<>();
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
        HexieBridges.modBootstrap().modBus().addListener(this::bake);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.AddSectionGeometryEvent event) -> StaticShellSectionGeometry.addGeometry(this, event));
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::chunkLoaded);
        NeoForge.EVENT_BUS.addListener(this::chunkUnloaded);
        NeoForge.EVENT_BUS.addListener(this::levelUnloaded);
        BlockModelCutter.INSTANCE.addCutChangedListener(this::onWindowCutsChanged);
    }

    StaticShellMesh meshFor(BlockState state) {
        return models.meshes.get(state);
    }

    Set<BlockPos> cellsForOwner(BlockPos root, StaticShellMesh mesh) {
        var cells = new HashSet<BlockPos>();
        cells.add(root);
        if (mesh != null) {
            for (var face : mesh.faces) cells.add(root.offset(face.cell()));
        }
        return cells;
    }

    private boolean hasAnyWindowCut(BlockPos root, StaticShellMesh mesh) {
        if (BlockModelCutter.INSTANCE.hasWindowCutAt(root)) return true;
        if (mesh != null) {
            for (var face : mesh.faces) {
                if (BlockModelCutter.INSTANCE.hasWindowCutAt(root.offset(face.cell()))) return true;
            }
        }
        return false;
    }

    private void onWindowCutsChanged(Set<BlockPos> dirtyBlocks) {
        if (observedLevel == null || observed.isEmpty()) return;
        for (var sectionEntry : observed.entrySet()) {
            BlockPos secPos = sectionEntry.getKey();
            for (var entry : sectionEntry.getValue().entrySet()) {
                BlockPos root = entry.getKey();
                BlockState state = entry.getValue();
                StaticShellMesh mesh = models.meshes.get(state);
                if (mesh == null) continue;
                boolean hasCut = hasAnyWindowCut(root, mesh);
                boolean touches = hasCut || touchesAny(root, mesh, dirtyBlocks);
                if (!touches) continue;

                // World shells live in HeXie's embedded renderer — re-publish their section immediately!
                if (rendererSections.containsKey(secPos) && rendererSections.get(secPos).containsKey(root)) {
                    dirtyRendererSections.add(secPos);
                    continue;
                }
                // Moving Sable plot fallback: toggle Flywheel plot visual if cut state changed
                boolean instanced = shells.containsKey(root);
                boolean shouldUsePlotFlywheel = visualizationManager != null && !hasCut
                        && HexieBridges.Client.sableClient().sableRenderEmbedding().find(observedLevel, root) != null;
                if (instanced != shouldUsePlotFlywheel) {
                    index(root, state);
                } else {
                    var dests = ownerDestinations.get(root);
                    if (dests != null) dirty.addAll(dests);
                }
            }
        }
        flushDirtyRendererSections();
    }

    private static boolean touchesAny(BlockPos root, StaticShellMesh mesh, Set<BlockPos> dirtyBlocks) {
        if (dirtyBlocks.contains(root)) return true;
        for (var face : mesh.faces) {
            if (dirtyBlocks.contains(root.offset(face.cell()))) return true;
        }
        return false;
    }

    private boolean owner(BlockState state) { return owners.test(state); }

    private void bake(ModelEvent.ModifyBakingResult event) {
        var meshes = new HashMap<BlockState, StaticShellMesh>();
        var keys = new HashMap<BlockState, String>();
        int idx = 0;
        for (var block : BuiltInRegistries.BLOCK) {
            if (!blocks.test(block)) continue;
            for (var state : block.getStateDefinition().getPossibleStates()) {
                if (!owner(state)) continue;
                var key = BlockModelShaper.stateToModelLocation(state);
                var original = event.getModels().get(key);
                if (original == null) continue;
                original = prepare.apply(original, state);
                var mesh = new StaticShellMesh(original, state);
                String modelKey = "hexie:" + familyId + ":" + (++idx) + ":" + key;
                HexieRendererBridge.registerShellModel(modelKey, original, state);
                meshes.put(state, mesh);
                keys.put(state, modelKey);
                event.getModels().put(key, new StaticShellModel(original, unshadedVirtual));
            }
        }
        models = new Models(Map.copyOf(meshes), Map.copyOf(keys));
    }

    void select(ClientLevel level) {
        if (observedLevel != level) {
            clear();
            observedLevel = level;
            observedModels = models;
        }
        var manager = VisualizationManager.get(level);
        if (manager != visualizationManager || observedModels != models) {
            clearVisuals();
            HexieRendererBridge.clearShellFamily(familyId);
            rendererSections.clear();
            dirtyRendererSections.clear();
            visualizationManager = manager;
            observedModels = models;
            dirty.addAll(destinations.keySet());
            destinations.clear(); ownerDestinations.clear();
            for (var roots : observed.values()) roots.forEach(this::index);
            flushDirtyRendererSections();
        }
    }

    /** Render-thread query for Sable's separate single-block model path. */
    boolean instanced(BlockPos root) { return shells.containsKey(root); }

    private void clear() {
        clearVisuals();
        HexieRendererBridge.clearShellFamily(familyId);
        rendererSections.clear();
        dirtyRendererSections.clear();
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

    private void addPlotVisual(BlockPos root, BlockState state, SableRenderEmbeddingHandleView plot) {
        StaticShellMesh mesh = models.meshes.get(state);
        if (visualizationManager == null || mesh == null) return;
        if (hasAnyWindowCut(root, mesh)) return;
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
    }

    static BlockPos section(BlockPos p) {
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
        BlockPos secPos = section(root);
        var secOwners = rendererSections.get(secPos);
        if (secOwners != null && secOwners.remove(root) != null) {
            if (secOwners.isEmpty()) rendererSections.remove(secPos);
            dirtyRendererSections.add(secPos);
        }

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
        String modelKey = models.modelKeys.get(state);
        if (mesh == null || modelKey == null) return;

        var plot = HexieBridges.Client.sableClient().sableRenderEmbedding().find(observedLevel, root);
        if (plot == null) {
            // Ordinary-world CRH1/CRH2 shells render directly via HeXie's built-in hexie-renderer engine!
            rendererSections.computeIfAbsent(secPos, k -> new LinkedHashMap<>()).put(root.immutable(), modelKey);
            dirtyRendererSections.add(secPos);
            return;
        }

        // Moving Sable plot: use Sable plot visual (or fallback section geometry if cut / no Flywheel).
        addPlotVisual(root, state, plot);
        if (shells.containsKey(root)) return;
        var groups = new HashMap<BlockPos, List<StaticShellMesh.Face>>();
        for (var face : mesh.faces)
            groups.computeIfAbsent(secPos, ignored -> new ArrayList<>()).add(face);
        groups.forEach((target, faces) -> {
            destinations.computeIfAbsent(target, ignored -> new HashMap<>()).put(root, List.copyOf(faces));
            dirty.add(target);
        });
        ownerDestinations.put(root, Set.copyOf(groups.keySet()));
    }

    private void flushDirtyRendererSections() {
        if (observedLevel == null || dirtyRendererSections.isEmpty()) return;
        var pending = List.copyOf(dirtyRendererSections);
        dirtyRendererSections.clear();
        for (BlockPos secPos : pending) {
            var ownersInSec = rendererSections.get(secPos);
            if (ownersInSec == null || ownersInSec.isEmpty()) {
                HexieRendererBridge.publishShellSection(observedLevel, familyId, secPos, Map.of(), List.of());
                continue;
            }
            var secObserved = observed.getOrDefault(secPos, Map.of());
            var sectionCells = new LinkedHashSet<BlockPos>();
            for (BlockPos root : ownersInSec.keySet()) {
                sectionCells.add(root);
                BlockState st = secObserved.get(root);
                if (st == null) st = observedLevel.getBlockState(root);
                StaticShellMesh mesh = models.meshes.get(st);
                if (mesh != null) {
                    for (var face : mesh.faces) sectionCells.add(root.offset(face.cell()));
                }
            }
            List<StickerEntry> windows = BlockModelCutter.INSTANCE.windowsForOwnerCells(sectionCells);
            HexieRendererBridge.publishShellSection(observedLevel, familyId, secPos, ownersInSec, windows);
        }
    }

    private void indexChunk(ClientLevel level, ChunkAccess chunk) {
        int sx = chunk.getPos().x, sz = chunk.getPos().z;
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
        flushDirtyRendererSections();
    }

    private void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) { clear(); return; }
        select(mc.level);
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
        flushDirtyRendererSections();
        if (dirty.isEmpty()) return;
        var pending = Set.copyOf(dirty); dirty.clear();
        for (var section : pending)
            mc.levelRenderer.setSectionDirty(section.getX(), section.getY(), section.getZ());
    }

    private void chunkLoaded(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ClientLevel level)) return;
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
        flushDirtyRendererSections();
    }

    private void chunkUnloaded(ChunkEvent.Unload event) {
        if (event.getLevel() != observedLevel) return;
        var pos = event.getChunk().getPos();
        for (int i = 0; i < event.getChunk().getSections().length; i++) {
            var key = new BlockPos(pos.x, observedLevel.getSectionYFromSectionIndex(i), pos.z);
            var roots = observed.remove(key);
            if (roots != null) for (var root : roots.keySet())
                index(root, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
        flushDirtyRendererSections();
    }

    private void levelUnloaded(LevelEvent.Unload event) {
        if (event.getLevel() == observedLevel) {
            clear();
        }
    }
}
