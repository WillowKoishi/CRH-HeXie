package willow.train.hexie.client.render;

import dev.engine_room.flywheel.api.material.CardinalLightingMode;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.visual.Effect;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visual.ShaderLightVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.material.LightShaders;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.model.ModelUtil;
import dev.engine_room.flywheel.lib.model.baked.BlockModelBuilder;
import dev.engine_room.flywheel.lib.model.baked.SinglePosVirtualBlockGetter;
import dev.engine_room.flywheel.lib.util.RendererReloadCache;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import willow.train.hexie.client.render.carriage.CompatibleCarriageVisual;

import java.util.List;

/** One world-shell visual for both heads and carriage parts; no extra block entities. */
final class CRH2ShellVisual implements EffectVisual<CRH2ShellVisual.Shell>, ShaderLightVisual {
    private static final boolean COMPATIBLE_TRANSPARENCY = ModList.get().isLoaded("sable") && ModList.get().isLoaded("veil");
    // Same virtual block builder/cache lifecycle as Flywheel Models.block(), with continuous
    // world lighting for models that extend well beyond their owner block.
    private static final RendererReloadCache<BlockState, Model> MODELS = new RendererReloadCache<>(state ->
            new BlockModelBuilder(SinglePosVirtualBlockGetter.createFullDark().blockState(state),
                    List.of(BlockPos.ZERO))
                    .materialFunc((renderType, shaded, ao) -> {
                        var material = ModelUtil.getMaterial(renderType, shaded, ao);
                        if (COMPATIBLE_TRANSPARENCY)
                            material = CompatibleCarriageVisual.withCompatibleTransparency(material, renderType);
                        return material == null ? null : SimpleMaterial.builderOf(material)
                                .light(LightShaders.SMOOTH)
                                .cardinalLightingMode(CardinalLightingMode.OFF)
                                .ambientOcclusion(false).build();
                    }).build());

    record Shell(ClientLevel level, BlockPos pos, BlockState state) implements Effect {
        @Override
        public EffectVisual<?> visualize(VisualizationContext context, float partialTick) {
            return new CRH2ShellVisual(context, this);
        }
    }

    private final TransformedInstance instance;
    private final LongSet lightSections;

    private CRH2ShellVisual(VisualizationContext context, Shell shell) {
        Model model = MODELS.get(shell.state());
        instance = context.instancerProvider().instancer(InstanceTypes.TRANSFORMED, model).createInstance();
        var origin = context.renderOrigin();
        instance.setIdentityTransform().translate(shell.pos().getX() - origin.getX(),
                shell.pos().getY() - origin.getY(), shell.pos().getZ() - origin.getZ()).setChanged();
        // Track the whole model, including neighbouring light samples, not just its root section.
        var sphere = model.boundingSphere();
        lightSections = sections(shell.pos().getX() + sphere.x(), shell.pos().getY() + sphere.y(),
                shell.pos().getZ() + sphere.z(), sphere.w() + 1);
    }

    static LongSet sections(double x, double y, double z, double radius) {
        var sections = new LongOpenHashSet();
        for (int sx = Mth.floor(x - radius) >> 4; sx <= Mth.floor(x + radius) >> 4; sx++)
            for (int sy = Mth.floor(y - radius) >> 4; sy <= Mth.floor(y + radius) >> 4; sy++)
                for (int sz = Mth.floor(z - radius) >> 4; sz <= Mth.floor(z + radius) >> 4; sz++)
                    sections.add(SectionPos.asLong(sx, sy, sz));
        return sections;
    }

    @Override public void setSectionCollector(SectionCollector collector) { collector.sections(lightSections); }
    // State changes replace the immutable Shell; Flywheel recreates visuals on render-origin changes.
    @Override public void update(float partialTick) {}
    @Override public void delete() { instance.delete(); }
}
