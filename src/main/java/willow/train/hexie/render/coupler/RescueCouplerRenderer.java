package willow.train.hexie.render.coupler;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.Blocks;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.CouplingBridge.CouplerAdapterView;
import willow.train.hexie.render.init.PartialModelInit;

/** Authored adapter geometry, relative to its CRH2 mating point, following the existing host arm. */
public final class RescueCouplerRenderer {
    private RescueCouplerRenderer() {}

    public static void render(CouplerAdapterView adapter,
            boolean reversed, float progress, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        parts(adapter, reversed, progress, pose, (model, partPose, deformation) ->
                CachedBuffers.partial(model, Blocks.AIR.defaultBlockState()).light(light).overlay(overlay)
                        .renderInto(partPose, buffers.getBuffer(RenderType.cutoutMipped())));
    }

    static void parts(CouplerAdapterView adapter,
            boolean reversed, float progress, PoseStack pose, CouplerModelParts.Sink sink) {
        var geometry = HexieBridges.toolOps().rescueCouplerItem().geometry();
        pose.pushPose();
        if (reversed) {
            // Keep the second mating point at the host, then face the CRH2 end outwards.
            var contact = adapter.offset();
            pose.mulPose(Axis.YP.rotationDegrees(180));
            pose.translate(-contact.x(), -contact.y(), -contact.z());
        }
        sink.draw(PartialModelInit.RESCUE_COUPLER_BODY, pose, null);
        var pin = geometry.knuckleAxis();
        pose.translate(pin.x(), 0, pin.z());
        pose.mulPose(Axis.YP.rotationDegrees(geometry.knuckleOpeningAngle() * progress));
        pose.translate(-pin.x(), 0, -pin.z());
        sink.draw(PartialModelInit.RESCUE_COUPLER_KNUCKLE, pose, null);
        pose.popPose();
    }
}
