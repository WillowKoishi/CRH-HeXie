package willow.train.hexie.render.ss7d;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import java.util.function.Consumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.bridge.render.RenderBridge.SS7DWiperVisualView;
import willow.train.hexie.bridge.world.BlockEntityViews.SS7DHeadEntityView;
import willow.train.hexie.render.blockentity.part.CarriagePartInstances;
import willow.train.hexie.render.carriage.CarriageBlockVisual;

/** Flywheel-owned wipers and side plates on an assembled SS7D head. */
public final class SS7DWiperVisual extends CarriageBlockVisual<BlockEntity> implements SS7DWiperVisualView {
    // Draw before the carriage glass so the windshield cannot cover the exterior wipers.
    private final SS7DHeadEntityView head;
    private static final int RENDER_BIAS = -1;

    private final CarriagePartInstances parts;
    private final BlockPos position;
    private final PoseStack pose = new PoseStack();
    private BlockState state;
    private float leftAngle = Float.NaN;
    private float rightAngle = Float.NaN;
    private boolean sidePlates;

    private SS7DWiperVisual(VisualizationContext context, SS7DHeadEntityView head, float partialTick) {
        super(head.asBlockEntity());
        this.head = head;
        position = head.asBlockEntity().getBlockPos().subtract(context.renderOrigin());
        parts = new CarriagePartInstances(context, 3, RENDER_BIAS);
        update(partialTick);
    }

    public static SS7DWiperVisual create(VisualizationContext context, SS7DHeadEntityView head, float partialTick) {
        return new SS7DWiperVisual(context, head, partialTick);
    }

    @Override public void beginFrame(Context frame) {
        update(frame.partialTick());
    }

    @Override public void update(float partialTick) {
        var nextState = blockEntity.getBlockState();
        float nextLeft = head.wiperAngle(0, partialTick);
        float nextRight = head.wiperAngle(1, partialTick);
        boolean nextSidePlates = head.hasSidePlates();
        if (state == nextState && Float.floatToIntBits(leftAngle) == Float.floatToIntBits(nextLeft)
                && Float.floatToIntBits(rightAngle) == Float.floatToIntBits(nextRight)
                && sidePlates == nextSidePlates) return;

        state = nextState;
        leftAngle = nextLeft;
        rightAngle = nextRight;
        sidePlates = nextSidePlates;
        pose.setIdentity();
        pose.translate(position.getX(), position.getY(), position.getZ());
        parts.start();
        SS7DWiperParts.drawWipers(head, partialTick, pose,
                (model, transform) -> parts.draw(model, RenderType.cutoutMipped(), transform, false));
        if (sidePlates)
            SS7DHeadRenderer.drawSidePlates(head, pose,
                    (model, transform) -> parts.draw(model, RenderType.cutoutMipped(), transform, false));
        parts.finish();
    }

    @Override protected boolean rendersEntireBlock() {
        // The BER still renders the number plates and standalone coupler.
        return false;
    }

    @Override public void collectCrumblingInstances(Consumer<Instance> consumer) {
        parts.collect(consumer);
    }

    @Override protected void deleteInstances() {
        parts.delete();
    }
}
