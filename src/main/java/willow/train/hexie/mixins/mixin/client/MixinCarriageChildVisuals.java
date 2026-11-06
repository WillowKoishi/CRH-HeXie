package willow.train.hexie.mixins.mixin.client;

import java.util.List;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.render.ContraptionVisual;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualEmbedding;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.api.visualization.VisualizerRegistry;
import dev.engine_room.flywheel.lib.task.PlanMap;
import dev.engine_room.flywheel.lib.visual.AbstractEntityVisual;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import willow.train.hexie.blockentity.CustomRenderedDoorEntity;
import willow.train.hexie.blockentity.DrivingConsoleEntity;
import willow.train.hexie.client.render.blockentity.CarriageDoorVisual;
import willow.train.hexie.client.render.blockentity.CarriageConsoleVisual;
import willow.train.hexie.client.render.blockentity.CustomRenderedDoorRenderer;
import willow.train.hexie.client.render.blockentity.DrivingConsoleRenderer;
import willow.train.hexie.client.render.blockentity.LegacyCarriagePartVisual;
import willow.train.hexie.client.render.carriage.CarriageBlockVisual;

@Mixin(value = ContraptionVisual.class, remap = false)
public abstract class MixinCarriageChildVisuals extends AbstractEntityVisual<AbstractContraptionEntity> {
    @Shadow @Final protected VisualEmbedding embedding;
    @Shadow @Final protected List<BlockEntityVisual<?>> children;
    @Shadow @Final protected PlanMap<DynamicVisual, DynamicVisual.Context> dynamicVisuals;

    protected MixinCarriageChildVisuals(VisualizationContext context, AbstractContraptionEntity entity, float partial) {
        super(context, entity, partial);
    }

    @Inject(method = "setupVisualizer", at = @At("HEAD"))
    private void hexie$attachCarriagePart(BlockEntity block, float partial, CallbackInfo ci) {
        if (!(entity instanceof CarriageContraptionEntity) || VisualizerRegistry.getVisualizer(block.getType()) != null) return;
        var dispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();
        CarriageBlockVisual<?> visual;
        if (block instanceof CustomRenderedDoorEntity door && door.isLowerSide(door.getBlockState())) {
            var renderer = dispatcher.getRenderer(door);
            if (renderer == null || renderer.getClass() != CustomRenderedDoorRenderer.class) return;
            visual = CarriageDoorVisual.create(embedding, door, partial);
        } else if (block instanceof DrivingConsoleEntity console) {
            if (!(dispatcher.getRenderer(console) instanceof DrivingConsoleRenderer)) return;
            visual = CarriageConsoleVisual.create(embedding, console);
        } else {
            visual = LegacyCarriagePartVisual.create(embedding, block, partial);
        }
        if (visual == null) return;
        children.add(visual);
        dynamicVisuals.add(visual, visual.planFrame());
        visual.attach();
    }
}
