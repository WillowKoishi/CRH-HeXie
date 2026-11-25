package willow.train.hexie.render.door;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import java.util.function.Consumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.bridge.render.DoorRenderBridge.CarriageDoorVisualView;
import willow.train.hexie.bridge.world.BlockEntityViews.CustomRenderedDoorEntityView;
import willow.train.hexie.render.blockentity.part.CarriagePartInstances;
import willow.train.hexie.render.carriage.CarriageBlockVisual;
import willow.train.hexie.render.carriage.CarriageTransparencyCompat;

/** Door instances use the carriage's transform and light volume, including beyond the entity render pass. */
public final class CarriageDoorVisual extends CarriageBlockVisual<BlockEntity>
        implements CarriageDoorParts.PartRenderer, CarriageDoorVisualView {
    private final CustomRenderedDoorEntityView door;
    private final CarriagePartInstances parts;
    private final BlockPos pos;
    private final PoseStack pose = new PoseStack();
    private BlockState state;
    private boolean framed, reversed;
    private float progress;

    private CarriageDoorVisual(VisualizationContext context, CustomRenderedDoorEntityView door, float partialTick) {
        super(door.asBlockEntity());
        this.door = door;
        this.parts = new CarriagePartInstances(context, 3);
        this.pos = door.asBlockEntity().getBlockPos().subtract(context.renderOrigin());
        update(partialTick);
    }

    public static CarriageDoorVisual create(VisualizationContext context, CustomRenderedDoorEntityView door, float partialTick) {
        return new CarriageDoorVisual(context, door, partialTick);
    }

    @Override public void beginFrame(Context frame) {
        update(frame.partialTick());
    }

    @Override public void update(float partialTick) {
        float next = door.crh2Progress(partialTick);
        if (state == blockEntity.getBlockState() && progress == next
                && framed == door.hasPassengerFrame() && reversed == door.hasReverseSlide()) return;
        state = blockEntity.getBlockState();
        progress = next;
        framed = door.hasPassengerFrame();
        reversed = door.hasReverseSlide();
        pose.setIdentity();
        pose.translate(pos.getX(), pos.getY(), pos.getZ());
        parts.start();
        CarriageDoorParts.drawParts(door, progress, pose, this);
        parts.finish();
    }

    @Override public void draw(PartialModel model, RenderType layer, PoseStack transform) {
        if (CarriageTransparencyCompat.required() && layer == RenderType.translucent()) return;
        parts.draw(model, layer, transform, layer != RenderType.cutoutMipped());
    }

    @Override protected boolean rendersEntireBlock() {
        return !CarriageTransparencyCompat.required() || !CarriageDoorParts.isShellDoor(blockEntity.getBlockState());
    }

    @Override public void collectCrumblingInstances(Consumer<Instance> consumer) {
        parts.collect(consumer);
    }

    @Override protected void deleteInstances() {
        parts.delete();
    }
}
