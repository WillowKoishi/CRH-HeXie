package willow.train.hexie.client.render;

import dev.engine_room.flywheel.api.visual.Effect;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visual.ShaderLightVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.compat.sable.SableRenderEmbedding;

import java.util.HashMap;
import java.util.Map;

/** Shared shell models under one moving, lit embedding per Sable plot. */
final class CRH2PlotShellVisual implements EffectVisual<CRH2PlotShellVisual.Parts>, SimpleDynamicVisual, ShaderLightVisual {
    /** Stable effect identity; publish one immutable snapshot for a batch of owner changes. */
    static final class Parts implements Effect {
        private final ClientLevel level;
        final SableRenderEmbedding.Handle plot;
        private final Map<BlockPos, BlockState> parts = new HashMap<>();
        private Map<BlockPos, BlockState> snapshot = Map.of();
        private boolean changed, updateQueued;

        Parts(ClientLevel level, SableRenderEmbedding.Handle plot) { this.level = level; this.plot = plot; }

        @Override public ClientLevel level() { return level; }

        synchronized boolean put(BlockPos pos, BlockState state) {
            if (parts.put(pos.immutable(), state) == state) return false;
            return changed();
        }

        synchronized boolean remove(BlockPos pos) {
            if (parts.remove(pos) == null) return false;
            return changed();
        }

        private boolean changed() {
            changed = true;
            if (updateQueued) return false;
            updateQueued = true;
            return true;
        }

        synchronized boolean isEmpty() { return parts.isEmpty(); }

        synchronized Map<BlockPos, BlockState> snapshot() {
            if (changed) {
                snapshot = Map.copyOf(parts);
                changed = false;
            }
            updateQueued = false;
            return snapshot;
        }

        @Override public EffectVisual<?> visualize(VisualizationContext context, float partialTick) {
            return new CRH2PlotShellVisual(context, this, partialTick);
        }
    }

    private record Part(BlockState state, TransformedInstance instance, LongSet lightSections) {}

    private final Parts effect;
    private final SableRenderEmbedding.Binding binding;
    private final Map<BlockPos, Part> instances = new HashMap<>();
    private Map<BlockPos, BlockState> applied;
    private LongSet lightSections = new LongOpenHashSet();
    private SectionCollector collector;
    private boolean deleted;

    private CRH2PlotShellVisual(VisualizationContext context, Parts effect, float partialTick) {
        this.effect = effect;
        binding = effect.plot.create(context, partialTick);
        if (effect.plot.removed()) delete();
        else update(partialTick);
    }

    @Override public void update(float partialTick) {
        if (deleted) return;
        var snapshot = effect.snapshot();
        if (snapshot == applied) return;
        applied = snapshot;
        var iterator = instances.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (snapshot.get(entry.getKey()) != entry.getValue().state()) {
                entry.getValue().instance().delete();
                iterator.remove();
            }
        }
        var context = binding.embedding();
        var origin = context.renderOrigin();
        for (var entry : snapshot.entrySet()) {
            if (instances.containsKey(entry.getKey())) continue;
            var pos = entry.getKey();
            var model = CRH2ShellVisual.model(entry.getValue());
            var instance = context.instancerProvider().instancer(InstanceTypes.TRANSFORMED, model).createInstance();
            instance.setIdentityTransform().translate(pos.getX() - origin.getX(),
                    pos.getY() - origin.getY(), pos.getZ() - origin.getZ()).setChanged();
            var bounds = model.boundingSphere();
            var sections = CRH2ShellVisual.sections(pos.getX() + bounds.x(), pos.getY() + bounds.y(),
                    pos.getZ() + bounds.z(), bounds.w() + 1);
            instances.put(pos, new Part(entry.getValue(), instance, sections));
        }
        lightSections = new LongOpenHashSet();
        instances.values().forEach(part -> lightSections.addAll(part.lightSections()));
        if (collector != null) collector.sections(lightSections);
    }

    @Override public void beginFrame(Context frame) {
        if (!deleted && !binding.update(frame.partialTick())) delete();
    }

    @Override public void setSectionCollector(SectionCollector collector) {
        this.collector = collector;
        collector.sections(lightSections);
    }

    @Override public void delete() {
        if (deleted) return;
        deleted = true;
        instances.values().forEach(part -> part.instance().delete());
        instances.clear();
        binding.delete();
        lightSections = new LongOpenHashSet();
        if (collector != null) collector.sections(lightSections);
    }
}
