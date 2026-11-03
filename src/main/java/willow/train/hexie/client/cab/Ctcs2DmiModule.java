package willow.train.hexie.client.cab;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import willow.train.hexie.api.cab.CabDisplaySource;

/** The authored fixed-size shell and LCD share one rigid installation marker on any cab. */
public final class Ctcs2DmiModule {
    public enum Shell {
        GREY("lkc_x_2000s"), BLACK("lkc_x_2000s_ab");
        private final ModelResourceLocation model;
        Shell(String name) { model = ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath("hexie", "component/dmi/" + name)); }
        public ModelResourceLocation model() { return model; }
    }
    private Ctcs2DmiModule() {}
    public static void render(CabDisplaySource source, Shell shell, Ctcs2DmiDisplay display,
                              Matrix4fc shellToModel, BlockState lightingState, PoseStack pose,
                              MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        pose.mulPose(new Matrix4f(shellToModel));
        var mc = Minecraft.getInstance();
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderType.solid()),
                lightingState, mc.getModelManager().getModel(shell.model()), 1, 1, 1, light, overlay,
                ModelData.EMPTY, RenderType.solid());
        display.renderOnShell(source, new Matrix4f(), pose, buffers);
        pose.popPose();
    }
}
