package willow.train.hexie.compat.simurail.client;

import com.crystaelix.simurail.content.bogey.PhysicsBogeyBlockEntity;
import com.crystaelix.simurail.content.bogey.PhysicsBogeyRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.compat.simurail.PhysicalHeadBogeyBlockEntity;

/** A camera near the wheel mount remains in range even when the rear controller is farther away. */
public final class PhysicalHeadBogeyRenderer extends PhysicsBogeyRenderer {
    public PhysicalHeadBogeyRenderer(BlockEntityRendererProvider.Context context) { super(context); }

    @Override protected void renderSafe(PhysicsBogeyBlockEntity entity, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        if (!(entity instanceof PhysicalHeadBogeyBlockEntity head)) {
            super.renderSafe(entity, partialTick, pose, buffers, light, overlay);
            return;
        }
        if (VisualizationManager.supportsVisualization(head.getLevel())) return;
        if (head.isCarriageView() || !head.headBogeyEnabled()) {
            if (!head.isUnpowered()) {
                var shaft = getRenderedBlockState(head);
                renderRotatingKineticBlock(head, shaft, pose, buffers.getBuffer(getRenderType(head, shaft)), light);
            }
            return;
        }

        var offset = head.attachmentOffset();
        pose.pushPose();
        try {
            pose.translate(offset.x, offset.y, offset.z);
            super.renderSafe(head, partialTick, pose, buffers, light, overlay);
        } finally {
            // SimuRail also translates this pose while drawing the back connector.
            pose.popPose();
        }
    }

    @Override protected SuperByteBuffer getRotatedModel(PhysicsBogeyBlockEntity entity, BlockState state) {
        var shaft = super.getRotatedModel(entity, state);
        if (entity instanceof PhysicalHeadBogeyBlockEntity head) {
            // Only the kinetic shaft uses this model. Undo the wheel translation before its native rotation.
            var offset = head.attachmentOffset();
            shaft.translate(-offset.x, -offset.y, -offset.z);
        }
        return shaft;
    }

    @Override public boolean shouldRender(PhysicsBogeyBlockEntity entity, Vec3 camera) {
        if (super.shouldRender(entity, camera)) return true;
        if (!(entity instanceof PhysicalHeadBogeyBlockEntity head)) return false;
        var anchor = head.attachmentCenter();
        double distance = getViewDistance();
        return camera.distanceToSqr(anchor.x(), anchor.y(), anchor.z()) < distance * distance;
    }
}
