package willow.train.hexie.client.render.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import willow.train.hexie.blockentity.DrivingConsoleEntity;
import willow.train.hexie.blocks.locos.CRH2DrivingConsoleBlock;
import willow.train.hexie.blocks.locos.TestDrivingConsoleBlock;
import willow.train.hexie.client.cab.Ctcs2DmiDisplay;

/** Temporary mounting fixture: authored DMI at 1:1 scale, simple movable cube handles. */
public final class TestDrivingConsoleRenderer implements BlockEntityRenderer<DrivingConsoleEntity> {
    public static final ModelResourceLocation SHELL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath("hexie", "component/dmi/lkc_x_2000s"));
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("hexie", "textures/misc/sign_white.png");
    public TestDrivingConsoleRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(DrivingConsoleEntity cab, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!(cab.getBlockState().getBlock() instanceof TestDrivingConsoleBlock)) return;
        var controls = cab.testControls();
        pose.pushPose(); pose.translate(.5, 0, .5);
        pose.mulPose(Axis.YP.rotationDegrees(180 - cab.getBlockState().getValue(CRH2DrivingConsoleBlock.FACING).toYRot()));
        pose.translate(-.5, 0, -.5);
        // Keep the entire operating face above the plinth and inside this block column.
        box(pose, buffers, .02f, 1f, .64f, .98f, 1.72f, .66f, 0xff555555, light);
        pose.pushPose(); pose.translate(.5, 1.52, .85);
        var mc = Minecraft.getInstance();
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderType.solid()),
                cab.getBlockState(), mc.getModelManager().getModel(SHELL), 1, 1, 1, light, overlay, ModelData.EMPTY, RenderType.solid());
        var display = cab.displayState();
        // Unassembled fixture is a stationary bench, not a simulated moving train.
        if (!cab.hasVehicleSource()) display = new willow.train.hexie.api.cab.CabDisplayState(0, java.util.Map.of(),
                new willow.train.hexie.api.cab.DmiState(controls.automatic ? willow.train.hexie.api.cab.DmiState.Mode.AUTOMATIC
                        : willow.train.hexie.api.cab.DmiState.Mode.MANUAL, null,
                        willow.train.hexie.vehicle.control.TrainControl.LINE_SPEED_LIMIT_KMH, 0,
                        controls.brake > 0 || controls.parking || controls.controller.emergencyLatched() ? 0 : controls.power / 10d,
                        controls.parking || controls.controller.emergencyLatched() ? 1 : Math.min(10, controls.brake) / 10d * .9,
                        "静态测试台", "", "", "", ""));
        var lamps = new java.util.HashMap<>(display.lamps());
        lamps.put("dmi.brake.emergency", new willow.train.hexie.api.cab.CabDisplayState.Lamp(
                0xcc0000, controls.controller.emergencyLatched()));
        var reading = new willow.train.hexie.api.cab.CabDisplayState(display.speedKmh(), lamps, display.dmi());
        Ctcs2DmiDisplay.INSTANCE.renderOnShell(() -> reading, new Matrix4f(), pose, buffers);
        pose.popPose();
        box(pose, buffers, .14f, 1.20f, .82f, .21f, 1.62f, .85f, 0xff252525, light);
        box(pose, buffers, .79f, 1.20f, .82f, .86f, 1.62f, .85f, 0xff252525, light);
        float py = 1.22f + controls.power * .032f, by = 1.22f + controls.brake * .029f;
        box(pose, buffers, .08f, py, .85f, .27f, py + .06f, .915f, 0xff999999, light);
        box(pose, buffers, .73f, by, .85f, .92f, by + .06f, .915f,
                controls.brake == 11 ? 0xffb03830 : 0xff777777, light);
        text(pose, buffers, "功率 +", .065f, 1.675f, light);
        text(pose, buffers, "制动 + / 紧急", .69f, 1.675f, light);
        text(pose, buffers, "−  " + controls.power + "/10", .065f, 1.19f, light);
        text(pose, buffers, "−  " + controls.brake + "/11", .71f, 1.19f, light);
        box(pose, buffers, .06f, 1.02f, .85f, .32f, 1.16f, .90f, 0xff555555, light);
        box(pose, buffers, .37f, 1.02f, .85f, .63f, 1.16f, .90f, 0xff305c39, light);
        box(pose, buffers, .68f, 1.02f, .85f, .94f, 1.16f, .90f, controls.parking ? 0xff8a602b : 0xff555555, light);
        text(pose, buffers, controls.reverser == 0 ? "反向器：中" : controls.reverser > 0 ? "反向器：前" : "反向器：后", .065f, 1.11f, light);
        text(pose, buffers, controls.automatic ? "机控" : "人控", .42f, 1.11f, light);
        text(pose, buffers, controls.parking ? "驻车：开" : "驻车：关", .7f, 1.11f, light);
        text(pose, buffers, controls.controller.emergencyLatched() ? "紧急制动锁定" : "临时测试驾驶台", .31f, 1.34f, light);
        text(pose, buffers, "手柄上半增 / 下半减", .28f, 1.27f, light);
        text(pose, buffers, "右键光标  滚轮缩放", .28f, 1.20f, light);
        pose.popPose();
    }
    private static void text(PoseStack pose, MultiBufferSource buffers, String text, float x, float y, int light) {
        pose.pushPose(); pose.translate(x, y, .93); pose.scale(.0045f, -.0045f, .0045f);
        Minecraft.getInstance().font.drawInBatch(text, 0, 0, 0xffeeeeee, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, light);
        pose.popPose();
    }
    private static void box(PoseStack pose, MultiBufferSource buffers, float x, float y, float z,
                            float X, float Y, float Z, int color, int light) {
        var out = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        float[][] points = {{x,y,z},{X,y,z},{X,Y,z},{x,Y,z},{x,y,Z},{X,y,Z},{X,Y,Z},{x,Y,Z}};
        int[][] faces = {{4,5,6,7},{1,0,3,2},{0,4,7,3},{5,1,2,6},{3,7,6,2},{0,1,5,4}};
        for (var face : faces) for (int index : face) {
            var p = points[index];
            out.addVertex(pose.last().pose(), p[0], p[1], p[2]).setColor(color).setUv(.5f,.5f)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(), 0, 0, 1);
        }
    }
    @Override public boolean shouldRenderOffScreen(DrivingConsoleEntity cab) { return true; }
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(DrivingConsoleEntity cab) {
        return new net.minecraft.world.phys.AABB(cab.getBlockPos()).inflate(.75);
    }
}
