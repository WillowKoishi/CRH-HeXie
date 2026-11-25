package willow.train.hexie.render.blockentity.console;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.render.BlockEntityRenderBridge.CarriageConsoleVisualView;
import willow.train.hexie.bridge.world.BlockEntityViews.DrivingConsoleEntityView;
import willow.train.hexie.bridge.world.CarriageBlockBridge.TestDrivingConsoleBlockView;
import willow.train.hexie.core.HexieConstants;
import willow.train.hexie.core.api.cab.CabSurfaceHost;
import willow.train.hexie.render.blockentity.part.CarriagePartInstances;
import willow.train.hexie.render.carriage.CarriageBlockVisual;

/** Physical controls and display shells follow the carriage; the existing renderer draws the LCD and labels. */
public final class CarriageConsoleVisual extends CarriageBlockVisual<BlockEntity>
        implements DrivingConsoleRenderer.Models, TestDrivingConsoleRenderer.Boxes, CarriageConsoleVisualView {
    static final ModelResourceLocation TEST_BOX = ModelResourceLocation.standalone(HexieConstants.asResource("component/cab/test_box"));
    private final DrivingConsoleEntityView cab;
    private final CarriagePartInstances parts;
    private final PoseStack pose = new PoseStack();
    private final BlockPos position;

    private CarriageConsoleVisual(VisualizationContext context, DrivingConsoleEntityView cab) {
        super(cab.asBlockEntity());
        this.cab = cab;
        position = cab.asBlockEntity().getBlockPos().subtract(context.renderOrigin());
        parts = new CarriagePartInstances(context, 4);
        updateParts();
    }

    public static CarriageConsoleVisual create(VisualizationContext context, DrivingConsoleEntityView cab) {
        var state = cab.asBlockEntity().getBlockState();
        if (!(state.getBlock() instanceof CabSurfaceHost host)) return null;
        var surface = host.surfaceGeometry(state);
        if (surface.parts().isEmpty() && surface.mounts().isEmpty() && !(state.getBlock() instanceof TestDrivingConsoleBlockView)) return null;
        return new CarriageConsoleVisual(context, cab);
    }

    @Override public void beginFrame(Context frame) { updateParts(); }

    private void updateParts() {
        var state = blockEntity.getBlockState();
        parts.start();
        if (state.getBlock() instanceof CabSurfaceHost host) {
            pose.setIdentity();
            pose.translate(position.getX(), position.getY(), position.getZ());
            DrivingConsoleRenderer.orient(pose, host.cabFacing(state));
            if (state.getBlock() instanceof TestDrivingConsoleBlockView)
                TestDrivingConsoleRenderer.drawBoxes(HexieBridges.cabControl().testCabControls().view(cab.controls()), this);
            DrivingConsoleRenderer.visitModels(cab, host.surfaceGeometry(state), this);
        }
        parts.finish();
    }

    @Override public void box(float x, float y, float z, float endX, float endY, float endZ, int color) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.scale(endX - x, endY - y, endZ - z);
        parts.draw(Minecraft.getInstance().getModelManager().getModel(TEST_BOX), RenderType.cutout(), pose, true, color);
        pose.popPose();
    }

    @Override public void draw(ModelResourceLocation model, Matrix4fc transform, RenderType layer) {
        pose.pushPose();
        pose.mulPose(new Matrix4f(transform));
        parts.draw(Minecraft.getInstance().getModelManager().getModel(model), layer, pose, false, 0xffffffff);
        pose.popPose();
    }

    @Override protected boolean rendersEntireBlock() { return false; }
    @Override protected void deleteInstances() { parts.delete(); }
}
