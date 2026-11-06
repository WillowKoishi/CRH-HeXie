package willow.train.hexie.client.render.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import java.util.function.Consumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.blockentity.CustomRenderedDoorEntity;
import willow.train.hexie.client.render.carriage.CarriageBlockVisual;

/** Door instances use the carriage's transform and light volume, including beyond the entity render pass. */
public final class CarriageDoorVisual extends CarriageBlockVisual<CustomRenderedDoorEntity>
        implements CustomRenderedDoorRenderer.PartRenderer {
    private final CarriagePartInstances parts;
    private final BlockPos pos;
    private final PoseStack pose = new PoseStack();
    private BlockState state;
    private boolean framed, reversed;
    private float progress;

    private CarriageDoorVisual(VisualizationContext context, CustomRenderedDoorEntity door, float partialTick) {
        super(door);
        this.parts = new CarriagePartInstances(context, 3);
        this.pos = door.getBlockPos().subtract(context.renderOrigin());
        update(partialTick);
    }

    public static CarriageDoorVisual create(VisualizationContext context, CustomRenderedDoorEntity door, float partialTick) {
        return new CarriageDoorVisual(context, door, partialTick);
    }

    @Override public void beginFrame(Context frame) {
        update(frame.partialTick());
    }

    @Override public void update(float partialTick) {
        float next = blockEntity.crh2Progress(partialTick);
        if (state == blockEntity.getBlockState() && progress == next
                && framed == blockEntity.hasPassengerFrame() && reversed == blockEntity.hasReverseSlide()) return;
        state = blockEntity.getBlockState();
        progress = next;
        framed = blockEntity.hasPassengerFrame();
        reversed = blockEntity.hasReverseSlide();
        pose.setIdentity();
        pose.translate(pos.getX(), pos.getY(), pos.getZ());
        parts.start();
        CustomRenderedDoorRenderer.drawParts(blockEntity, progress, pose, this);
        parts.finish();
    }

    @Override public void draw(PartialModel model, RenderType layer, PoseStack transform) {
        parts.draw(model, layer, transform, layer != RenderType.cutoutMipped());
    }

    @Override public void collectCrumblingInstances(Consumer<Instance> consumer) {
        parts.collect(consumer);
    }

    @Override protected void deleteInstances() {
        parts.delete();
    }
}
