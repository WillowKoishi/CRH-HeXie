package willow.train.hexie.client.render.track;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.visual.Effect;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visual.ShaderLightVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

/** Immutable geometry; Flywheel owns its draw, visibility and section lighting. */
record CurveBedEffect(ClientLevel level, BlockPos pos, Model model, BallastModels.Material texture,
                      boolean repeating, LongSet lightSections) implements Effect {
    @Override public EffectVisual<?> visualize(VisualizationContext context, float partialTick) {
        return new Visual(context, this);
    }

    private static final class Visual implements EffectVisual<CurveBedEffect>, ShaderLightVisual {
        private final Instance instance;
        private final LongSet sections;

        Visual(VisualizationContext context, CurveBedEffect effect) {
            var pos = effect.pos.subtract(context.renderOrigin());
            sections = effect.lightSections;
            instance = CurveBedInstance.create(context, effect.model, pos.getX(), pos.getY(), pos.getZ(),
                    effect.texture, effect.repeating);
        }

        @Override public void setSectionCollector(SectionCollector collector) { collector.sections(sections); }
        @Override public void update(float partialTick) {}
        @Override public void delete() { instance.delete(); }
    }
}
