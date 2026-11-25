package willow.train.hexie.render.ss7d;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.CreateCarriageBridge.SnapshotCouplerAnimationParametersView;
import willow.train.hexie.render.coupler.SnapshotCouplerRenderer;
import willow.train.hexie.render.init.PartialModelInit;

import static willow.train.hexie.render.ss7d.SS7DHeadRenderer.draw;

// split from willow/train/hexie/client/render/ss7d/SS7DHeadRenderer.java, lines 30, 158-181
public final class SS7DHeadCoupler {
    private static final Coupler COUPLER = new Coupler();

    public static void hook(BlockState state, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var geometry = HexieBridges.carriageBlock().ss7DHeadGeometry().coupler();
        draw(PartialModelInit.SS7D_HEAD_HOOK, state, pose, buffers, light, overlay);
        var axis = geometry.knuckleAxis();
        pose.pushPose();
        pose.translate(axis.x(), 0, axis.z());
        if (state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN))
            pose.mulPose(Axis.YP.rotationDegrees(geometry.knuckleOpeningAngle()));
        pose.translate(-axis.x(), 0, -axis.z());
        draw(PartialModelInit.SS7D_HEAD_KNUCKLE, state, pose, buffers, light, overlay);
        pose.popPose();
    }

    public static SnapshotCouplerRenderer.Renderer coupler() { return COUPLER; }
    private static final class Coupler implements SnapshotCouplerRenderer.Renderer {
        @Override public void renderArm(BlockState state, SnapshotCouplerAnimationParametersView parameters,
                float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            var origin = HexieBridges.carriageBlock().ss7DHeadGeometry().coupler().armOrigin();
            pose.pushPose();
            pose.translate(-origin.x, -origin.y, -origin.z);
            hook(state, pose, buffers, light, overlay);
            pose.popPose();
        }
    }
}
