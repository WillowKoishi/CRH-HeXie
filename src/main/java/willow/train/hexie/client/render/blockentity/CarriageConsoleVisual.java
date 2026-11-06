package willow.train.hexie.client.render.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import willow.train.hexie.Main;
import willow.train.hexie.api.cab.CabSurfaceHost;
import willow.train.hexie.blockentity.DrivingConsoleEntity;
import willow.train.hexie.blocks.emu.TestDrivingConsoleBlock;
import willow.train.hexie.client.render.carriage.CarriageBlockVisual;
import willow.train.hexie.vehicle.control.TestCabControls;

/** Physical controls and display shells follow the carriage; the existing renderer draws the LCD and labels. */
public final class CarriageConsoleVisual extends CarriageBlockVisual<DrivingConsoleEntity>
        implements DrivingConsoleRenderer.Models, TestDrivingConsoleRenderer.Boxes {
    static final ModelResourceLocation TEST_BOX = ModelResourceLocation.standalone(Main.asResource("component/cab/test_box"));
    private final CarriagePartInstances parts;
    private final PoseStack pose = new PoseStack();
    private final BlockPos position;

    private CarriageConsoleVisual(VisualizationContext context, DrivingConsoleEntity cab) {
        super(cab);
        position = cab.getBlockPos().subtract(context.renderOrigin());
        parts = new CarriagePartInstances(context, 4);
        updateParts();
    }

    public static CarriageConsoleVisual create(VisualizationContext context, DrivingConsoleEntity cab) {
        var state = cab.getBlockState();
        if (!(state.getBlock() instanceof CabSurfaceHost host)) return null;
        var surface = host.surfaceGeometry(state);
        if (surface.parts().isEmpty() && surface.mounts().isEmpty() && !(state.getBlock() instanceof TestDrivingConsoleBlock)) return null;
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
            if (state.getBlock() instanceof TestDrivingConsoleBlock)
                TestDrivingConsoleRenderer.drawBoxes(TestCabControls.view(blockEntity.controls()), this);
            DrivingConsoleRenderer.visitModels(blockEntity, host.surfaceGeometry(state), this);
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
