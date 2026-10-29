package willow.train.hexie.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import willow.train.hexie.Main;
import willow.train.hexie.blocks.coupler.TestCouplerBlock;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;

/** Draws the few independent arms with the existing baked-model buffer path, including under Flywheel. */
public final class SnapshotCouplerRenderer {
    private static final PartialModel TEST_ARM = PartialModel.of(Main.asResource("block/test_coupler_arm"));

    private SnapshotCouplerRenderer() {}

    /** Register the partial model before resources are baked. */
    public static void init() {}

    public static void render(SnapshotCarriageEntity entity, float partialTick, PoseStack stack,
                              MultiBufferSource buffers, int overlay) {
        var animation = entity.couplerAnimation();
        if (animation.frames().isEmpty()) return;
        stack.pushPose();
        entity.applyLocalTransforms(stack, partialTick);
        for (int i = 0; i < animation.frames().size(); i++) {
            var frame = animation.frame(i, partialTick);
            var block = entity.getContraption().getBlocks().get(frame.position());
            if (block == null || !(block.state().getBlock() instanceof TestCouplerBlock)) continue;
            stack.pushPose();
            SnapshotCarriageRenderer.transform(stack, frame);
            // Standard block-model coordinates: registered rear pivot (0, 0, .5) is (8, 8, 16).
            stack.translate(-.5, -.5, -1);
            CachedBuffers.partial(TEST_ARM, block.state()).light(SnapshotCarriageRenderer.light(entity, frame, partialTick))
                    .overlay(overlay).renderInto(stack, buffers.getBuffer(RenderType.cutoutMipped()));
            stack.popPose();
        }
        stack.popPose();
    }
}
