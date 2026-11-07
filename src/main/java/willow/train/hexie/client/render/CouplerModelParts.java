package willow.train.hexie.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.compat.create.SnapshotCouplerAnimation;

/** Both renderers consume the same part selection, model-space poses and deformation. */
interface CouplerModelParts extends SnapshotCouplerRenderer.Renderer {
    @FunctionalInterface
    interface Sink {
        void draw(PartialModel model, PoseStack pose, PartialCouplerRenderer.BellowsDeformation deformation);
        default void cable(PartialModel model, PoseStack pose, CableDeformation deformation) {
            throw new UnsupportedOperationException("This coupler sink does not support deforming cables");
        }
    }

    void arm(PoseStack pose, Sink sink);
    default void arm(BlockState state, BlockEntity display, SnapshotCouplerAnimation.Parameters parameters,
                     float partialTick, PoseStack pose, Sink sink) { arm(pose, sink); }
    default void base(PoseStack pose, Sink sink) {}
    void attached(BlockState state, BlockEntity display, SnapshotCouplerAnimation.Parameters parameters,
                  float partialTick, boolean inWorld, PoseStack pose, Sink sink);

    private static Sink buffered(BlockState state, MultiBufferSource buffers, int light, int overlay) {
        return new Sink() {
            @Override public void draw(PartialModel model, PoseStack pose, PartialCouplerRenderer.BellowsDeformation deformation) {
                var buffer = CachedBuffers.partial(model, state).light(light).overlay(overlay);
                var consumer = buffers.getBuffer(RenderType.cutoutMipped());
                if (deformation == null) {
                    buffer.renderInto(pose, consumer);
                } else {
                    buffer.renderInto(new PoseStack(), new PartialCouplerRenderer.DeformingConsumer(consumer, pose.last(), deformation));
                }
            }
            @Override public void cable(PartialModel model, PoseStack pose, CableDeformation deformation) {
                CachedBuffers.partial(model, state).light(light).overlay(overlay).renderInto(new PoseStack(),
                        new CableDeformingConsumer(buffers.getBuffer(RenderType.cutoutMipped()), pose.last(), deformation));
            }
        };
    }

    @Override default void renderArm(BlockState state, SnapshotCouplerAnimation.Parameters parameters, float partialTick,
                                     PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        renderArm(state, null, parameters, partialTick, pose, buffers, light, overlay);
    }

    @Override default void renderArm(BlockState state, BlockEntity display,
            SnapshotCouplerAnimation.Parameters parameters, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        arm(state, display, parameters, partialTick, pose, buffered(state, buffers, light, overlay));
    }

    @Override default void renderBase(BlockState state, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        base(pose, buffered(state, buffers, light, overlay));
    }

    @Override default void renderAttachedParts(BlockState state, SnapshotCouplerAnimation.Parameters parameters,
            float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        renderAttachedParts(state, null, parameters, partialTick, pose, buffers, light, overlay);
    }

    @Override default void renderAttachedParts(BlockState state, BlockEntity display, SnapshotCouplerAnimation.Parameters parameters,
            float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        attached(state, display, parameters, partialTick, false, pose, buffered(state, buffers, light, overlay));
    }

    @Override default void renderUncoupledAttachedParts(BlockState state, PoseStack pose, MultiBufferSource buffers,
                                                       int light, int overlay) {
        renderUncoupledAttachedParts(state, null, 1, pose, buffers, light, overlay);
    }

    @Override default void renderUncoupledAttachedParts(BlockState state, BlockEntity display, float partialTick,
            PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        attached(state, display, null, partialTick, true, pose, buffered(state, buffers, light, overlay));
    }
}
