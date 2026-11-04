package willow.train.hexie.client.render.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import willow.train.hexie.blockentity.DrivingConsoleEntity;
import willow.train.hexie.blocks.emu.CRH2CabGeometry;
import willow.train.hexie.blocks.emu.CRH2DrivingConsoleBlock;
import willow.train.hexie.blocks.emu.TestDrivingConsoleBlock;
import willow.train.hexie.init.block.BlockInit;
import willow.train.hexie.client.cab.Ctcs2DmiDisplay;
import willow.train.hexie.client.cab.Crh2CabPresentation;
import willow.train.hexie.client.cab.Ctcs2DmiModule;
import willow.train.hexie.vehicle.control.Crh2CabHandle;

public final class DrivingConsoleRenderer implements BlockEntityRenderer<DrivingConsoleEntity> {
    public static final Map<Crh2CabHandle, ModelResourceLocation> MODELS;
    public static final ModelResourceLocation BRAKE_DOME = model("brake_dome");
    static {
        var models = new EnumMap<Crh2CabHandle, ModelResourceLocation>(Crh2CabHandle.class);
        for (var handle : Crh2CabHandle.values())
            models.put(handle, model(handle.name().toLowerCase(Locale.ROOT)));
        MODELS = Map.copyOf(models);
    }
    private final TestDrivingConsoleRenderer testCab;

    private static ModelResourceLocation model(String name) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath("hexie",
                "block/crh2a/nonstandard_driving_console/" + name));
    }

    public DrivingConsoleRenderer(BlockEntityRendererProvider.Context context) {
        testCab = new TestDrivingConsoleRenderer(context);
    }

    @Override public void render(DrivingConsoleEntity cab, float partial, PoseStack pose, MultiBufferSource buffers,
                                 int light, int overlay) {
        var state = cab.getBlockState();
        if (state.getBlock() instanceof TestDrivingConsoleBlock) {
            testCab.render(cab, partial, pose, buffers, light, overlay);
            return;
        }
        if (!state.is(BlockInit.CRH2A_NONSTANDARD_DRIVING_CONSOLE.get())) return;
        var controls = cab.controls();
        double brake = Crh2CabPresentation.brake(cab, controls);
        pose.pushPose();
        pose.translate(.5, 0, .5);
        pose.mulPose(Axis.YP.rotationDegrees(CRH2CabGeometry.yaw(state.getValue(CRH2DrivingConsoleBlock.FACING))));
        pose.translate(-.5, 0, -.5);
        draw(BRAKE_DOME, CRH2CabGeometry.brakeDomePose(brake), state, pose, buffers, light, overlay);
        for (var entry : MODELS.entrySet()) {
            var handle = entry.getKey();
            double position = handle == Crh2CabHandle.BRAKE ? brake : handle.notch(controls);
            draw(entry.getValue(), CRH2CabGeometry.handlePose(handle, position), state, pose, buffers, light, overlay);
        }
        Ctcs2DmiModule.render(cab, Ctcs2DmiModule.Shell.GREY, Ctcs2DmiDisplay.INSTANCE,
                CRH2CabGeometry.dmiMount(), state, pose, buffers, light, overlay);
        pose.popPose();
    }

    private static void draw(ModelResourceLocation model, Matrix4f transform, BlockState state, PoseStack pose,
                             MultiBufferSource buffers, int light, int overlay) {
        var mc = Minecraft.getInstance();
        var layer = RenderType.cutout();
        pose.pushPose();
        pose.mulPose(transform);
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(layer), state,
                mc.getModelManager().getModel(model), 1, 1, 1, light, overlay, ModelData.EMPTY, layer);
        pose.popPose();
    }

    @Override public boolean shouldRenderOffScreen(DrivingConsoleEntity cab) { return true; }
    @Override public AABB getRenderBoundingBox(DrivingConsoleEntity cab) {
        return new AABB(cab.getBlockPos()).inflate(2);
    }
}
