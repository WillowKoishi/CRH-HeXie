package willow.train.hexie.client.render.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import willow.train.hexie.api.cab.CabSurfaceHost;
import willow.train.hexie.api.cab.CabSurfaceGeometry;
import willow.train.hexie.blockentity.DrivingConsoleEntity;
import willow.train.hexie.blocks.emu.TestDrivingConsoleBlock;
import willow.train.hexie.client.cab.CabPresentation;
import willow.train.hexie.client.cab.Ctcs2DmiDisplay;
import willow.train.hexie.client.cab.Ctcs2DmiModule;
import willow.train.hexie.vehicle.control.Ctcs2Panel;

public final class DrivingConsoleRenderer implements BlockEntityRenderer<DrivingConsoleEntity> {
    public DrivingConsoleRenderer(BlockEntityRendererProvider.Context context) {}

    public static void registerModels(ModelEvent.RegisterAdditional event) {
        var registered = new HashSet<ResourceLocation>();
        for (var block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof CabSurfaceHost host)) continue;
            for (var state : block.getStateDefinition().getPossibleStates()) {
                for (var part : host.surfaceGeometry(state).parts()) {
                    if (part.model() != null && registered.add(part.model()))
                        event.register(ModelResourceLocation.standalone(part.model()));
                }
            }
        }
    }

    @Override public void render(DrivingConsoleEntity cab, float partial, PoseStack pose, MultiBufferSource buffers,
                                 int light, int overlay) {
        var state = cab.getBlockState();
        if (!(state.getBlock() instanceof CabSurfaceHost host)) return;
        var surface = host.surfaceGeometry(state);
        if (surface == CabSurfaceGeometry.EMPTY) return;
        var controls = cab.surfaceState();
        var animation = CabPresentation.animation(cab);
        pose.pushPose();
        pose.translate(.5, 0, .5);
        pose.mulPose(Axis.YP.rotationDegrees(CabSurfaceGeometry.yaw(host.cabFacing(state))));
        pose.translate(-.5, 0, -.5);
        if (state.getBlock() instanceof TestDrivingConsoleBlock)
            TestDrivingConsoleRenderer.renderPanel(cab, pose, buffers, light);
        for (var part : surface.parts()) {
            if (part.model() == null) continue;
            var transform = part.pose() == null ? new Matrix4f() : part.pose().at(controls, animation);
            draw(part.model(), transform, state, pose, buffers, light, overlay);
        }
        for (var mount : surface.mounts()) {
            if (mount.module() == Ctcs2Panel.INSTANCE)
                Ctcs2DmiModule.render(cab, Ctcs2DmiModule.Shell.GREY, Ctcs2DmiDisplay.INSTANCE,
                        mount.transform(), state, pose, buffers, light, overlay);
        }
        pose.popPose();
    }

    private static void draw(ResourceLocation model, Matrix4f transform, BlockState state, PoseStack pose,
                             MultiBufferSource buffers, int light, int overlay) {
        var mc = Minecraft.getInstance();
        var layer = RenderType.cutout();
        pose.pushPose();
        pose.mulPose(transform);
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(layer), state,
                mc.getModelManager().getModel(ModelResourceLocation.standalone(model)), 1, 1, 1,
                light, overlay, ModelData.EMPTY, layer);
        pose.popPose();
    }

    @Override public boolean shouldRenderOffScreen(DrivingConsoleEntity cab) { return true; }
    @Override public AABB getRenderBoundingBox(DrivingConsoleEntity cab) {
        return new AABB(cab.getBlockPos()).inflate(2);
    }
}
