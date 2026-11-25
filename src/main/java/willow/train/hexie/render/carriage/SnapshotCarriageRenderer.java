package willow.train.hexie.render.carriage;

// split from willow/train/hexie/client/render/SnapshotCarriageRenderer.java

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.SnapshotCarriageView;
import willow.train.hexie.render.bogey.BogeyRenderData;
import willow.train.hexie.render.coupler.CouplerAdapterBounds;
import willow.train.hexie.render.coupler.SnapshotCouplerRenderer;

import static willow.train.hexie.render.carriage.SnapshotBogeyRenderInputs.data;
import static willow.train.hexie.render.carriage.SnapshotBogeyRenderInputs.light;
import static willow.train.hexie.render.carriage.SnapshotBogeyTransforms.suspensionPose;
import static willow.train.hexie.render.carriage.SnapshotBogeyTransforms.transform;

/** Reuses Create's structure renderer and each registered bogey renderer, without its two-support limit. */
public final class SnapshotCarriageRenderer extends ContraptionEntityRenderer<CarriageContraptionEntity> {
    public SnapshotCarriageRenderer(EntityRendererProvider.Context context) { super(context); }

    @Override public boolean shouldRender(CarriageContraptionEntity entity,
            net.minecraft.client.renderer.culling.Frustum frustum, double cameraX, double cameraY, double cameraZ) {
        if (super.shouldRender(entity, frustum, cameraX, cameraY, cameraZ)) return true;
        // Keep Create's readiness and vanilla's distance checks; only extend the visible mesh envelope.
        if (entity.getContraption() == null || !entity.isAliveOrStale() || !entity.isReadyForRender()
                || !entity.shouldRender(cameraX, cameraY, cameraZ)) return false;
        float partial = net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        var extra = CouplerAdapterBounds.adapterBounds((SnapshotCarriageView) entity, partial);
        return extra != null && frustum.isVisible(extra.inflate(.5));
    }

    @Override
    public void render(CarriageContraptionEntity entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        super.render(entity, yaw, partialTick, poseStack, buffers, packedLight);
        if (entity.getContraption() == null) return;
        int overlay = OverlayTexture.NO_OVERLAY;
        SnapshotCouplerRenderer.render((SnapshotCarriageView) entity, partialTick, poseStack, buffers, overlay);
        // Buffered bogeys
        if (VisualizationManager.supportsVisualization(entity.level())) return;
        var client = entity.getContraption().getOrCreateClientContraptionLazy();
        var animation = ((SnapshotCarriageView) entity).bogeyAnimation();
        poseStack.pushPose();
        HexieBridges.Client.createCarriageClient().snapshotCarriageEntity().applyLocalTransforms((SnapshotCarriageView) entity, poseStack, partialTick);
        for (int i = 0; i < animation.frames().size(); i++) {
            var frame = animation.frame(i, partialTick);
            var bogey = BogeyRenderData.resolve(client.getRenderLevel(), frame.position());
            if (bogey == null) continue;
            poseStack.pushPose();
            transform(poseStack, frame);
            try (var suspension = suspensionPose(bogey, frame);
                 var sample = HexieBridges.Client.createTrainClient().createAddonBogeyRenderer().snapshotSample(
                         frame.wheelAngle(), frame.speed(), partialTick)) {
                bogey.style().render(bogey.block().getSize(), partialTick, poseStack, buffers,
                        light((SnapshotCarriageView) entity, frame, partialTick), overlay, (float) (frame.wheelAngle() % 360),
                        data(bogey, frame), true);
            }
            poseStack.popPose();
        }
        poseStack.popPose();
    }

}
