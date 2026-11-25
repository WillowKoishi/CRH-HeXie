package willow.train.hexie.render.blockentity.console;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.BlockEntityViews.DrivingConsoleEntityView;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH1DrivingConsoleBlockView;
import willow.train.hexie.bridge.world.CarriageBlockBridge.TestDrivingConsoleBlockView;
import willow.train.hexie.core.api.cab.CabSurfaceHost;
import willow.train.hexie.core.api.interaction.ControlSurface;
import willow.train.hexie.render.cab.CabPresentation;
import willow.train.hexie.render.cab.Ctcs2DmiDisplay;
import willow.train.hexie.render.cab.Ctcs2DmiModule;
import willow.train.hexie.render.carriage.CarriageBlockVisual;

public final class DrivingConsoleRenderer implements BlockEntityRenderer<BlockEntity> {
    public DrivingConsoleRenderer(BlockEntityRendererProvider.Context context) {}

    public static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(CarriageConsoleVisual.TEST_BOX);
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

    @Override public void render(BlockEntity blockEntity, float partial, PoseStack pose, MultiBufferSource buffers,
                                 int light, int overlay) {
        if (!(blockEntity instanceof DrivingConsoleEntityView cab)) return;
        var state = cab.asBlockEntity().getBlockState();
        if (!(state.getBlock() instanceof CabSurfaceHost host)) return;
        var surface = host.surfaceGeometry(state);
        if (surface == ControlSurface.EMPTY) return;
        pose.pushPose();
        orient(pose, host.cabFacing(state));
        boolean drawGeometry = !CarriageBlockVisual.ownsParts(cab.asBlockEntity());
        if (state.getBlock() instanceof TestDrivingConsoleBlockView)
            TestDrivingConsoleRenderer.renderPanel(cab, pose, buffers, light, drawGeometry);
        if (drawGeometry) visitModels(cab, surface, (model, transform, layer) -> {
            pose.pushPose();
            pose.mulPose(new Matrix4f(transform));
            var mc = Minecraft.getInstance();
            mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(layer), state,
                    mc.getModelManager().getModel(model), 1, 1, 1, light, overlay, ModelData.EMPTY, layer);
            pose.popPose();
        });
        for (var mount : surface.mounts()) {
            if (mount.module() == HexieBridges.cabControl().ctcs2Panel().instance())
                Ctcs2DmiDisplay.INSTANCE.renderOnShell(cab, mount.transform(), pose, buffers);
        }
        pose.popPose();
    }

    static void orient(PoseStack pose, Direction facing) {
        pose.translate(.5, 0, .5);
        pose.mulPose(Axis.YP.rotationDegrees(ControlSurface.yaw(facing)));
        pose.translate(-.5, 0, -.5);
    }

    interface Models {
        void draw(ModelResourceLocation model, Matrix4fc transform, RenderType layer);
    }

    static void visitModels(DrivingConsoleEntityView cab, ControlSurface surface, Models models) {
        var controls = cab.surfaceState();
        var animation = CabPresentation.animation(cab);
        for (var part : surface.parts()) {
            if (part.model() == null) continue;
            var transform = part.pose() == null ? new Matrix4f() : part.pose().at(controls, animation);
            models.draw(ModelResourceLocation.standalone(part.model()), transform, RenderType.cutout());
        }
        for (var mount : surface.mounts()) {
            if (mount.module() == HexieBridges.cabControl().ctcs2Panel().instance()
                    && !(cab.asBlockEntity().getBlockState().getBlock() instanceof CRH1DrivingConsoleBlockView))
                models.draw(Ctcs2DmiModule.Shell.GREY.model(), mount.transform(), RenderType.solid());
        }
    }

    @Override public boolean shouldRenderOffScreen(BlockEntity cab) { return true; }
    @Override public AABB getRenderBoundingBox(BlockEntity cab) {
        return new AABB(cab.getBlockPos()).inflate(2);
    }
}
