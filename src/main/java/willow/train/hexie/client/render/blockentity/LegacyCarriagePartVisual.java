package willow.train.hexie.client.render.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import willow.train.hexie.blockentity.CR200jFrontBlockEntity;
import willow.train.hexie.blockentity.OBJRenderableEndFaceEntity;
import willow.train.hexie.client.render.carriage.CarriageBlockVisual;

/** Carriage instances for the paired end doors, CR200J fairing and wipers. */
public final class LegacyCarriagePartVisual extends CarriageBlockVisual<BlockEntity> {
    private interface Parts {
        void draw(float partialTick, PoseStack pose, BiConsumer<PartialModel, PoseStack> sink);
    }

    private final BlockPos pos;
    private final PoseStack pose = new PoseStack();
    private final Parts parts;
    private final CarriagePartInstances instances;
    private final BiConsumer<PartialModel, PoseStack> sink;

    private LegacyCarriagePartVisual(VisualizationContext context, BlockEntity entity, RenderType layer,
                                     int count, Parts parts, float partialTick) {
        super(entity);
        this.parts = parts;
        pos = entity.getBlockPos().subtract(context.renderOrigin());
        instances = new CarriagePartInstances(context, count);
        sink = (model, transform) -> instances.draw(model, layer, transform, false);
        update(partialTick);
    }

    public static LegacyCarriagePartVisual create(VisualizationContext context, BlockEntity entity, float partialTick) {
        var dispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();
        if (entity instanceof OBJRenderableEndFaceEntity end && dispatcher.getRenderer(end) instanceof OBJRenderableEndFaceRenderer) {
            return new LegacyCarriagePartVisual(context, end, RenderType.cutout(), 2,
                    (partial, pose, sink) -> OBJRenderableEndFaceRenderer.drawParts(end, partial, pose, sink), partialTick);
        }
        if (entity instanceof CR200jFrontBlockEntity front && dispatcher.getRenderer(front) instanceof CR200jFrontRenderer renderer) {
            return new LegacyCarriagePartVisual(context, front, RenderType.solid(), 4,
                    (partial, pose, sink) -> renderer.drawParts(front, pose, sink), partialTick);
        }
        return null;
    }

    @Override public void beginFrame(Context frame) {
        if (blockEntity instanceof CR200jFrontBlockEntity front) CR200jFrontRenderer.advanceAnimation(front);
        update(frame.partialTick());
    }

    @Override public void update(float partialTick) {
        pose.setIdentity();
        pose.translate(pos.getX(), pos.getY(), pos.getZ());
        instances.start();
        parts.draw(partialTick, pose, sink);
        instances.finish();
    }

    @Override public void collectCrumblingInstances(Consumer<Instance> consumer) {
        instances.collect(consumer);
    }

    @Override protected void deleteInstances() {
        instances.delete();
    }
}
