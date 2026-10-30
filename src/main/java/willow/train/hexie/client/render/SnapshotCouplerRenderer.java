package willow.train.hexie.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import willow.train.hexie.Main;
import willow.train.hexie.blocks.coupler.TestCouplerBlock;
import willow.train.hexie.blocks.coupler.CouplerBlock;
import willow.train.hexie.client.render.blockentity.CouplerBlockEntityRenderer;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;

/** Draws the few independent arms with the existing baked-model buffer path, including under Flywheel. */
public final class SnapshotCouplerRenderer {
    private static final PartialModel TEST_ARM = PartialModel.of(Main.asResource("block/test_coupler_arm"));
    private static final PartialModel TEST_BASE = PartialModel.of(Main.asResource("block/test_coupler_base"));

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
            if (block == null || !(block.state().getBlock() instanceof CouplerBlock hook)) continue;
            int light = SnapshotCarriageRenderer.light(entity, frame, partialTick);
            if (hook instanceof TestCouplerBlock && animation.floorOffset() != 0) {
                // The body mesh moved with the floor; this fixed base follows the body's heading at its old height.
                stack.pushPose();
                stack.translate(frame.position().getX() + .5, frame.position().getY() + .5 - animation.floorOffset(),
                        frame.position().getZ() + .5);
                float baseYaw = switch (hook.facing(block.state())) {
                    case EAST -> -90; case SOUTH -> 180; case WEST -> 90; default -> 0;
                };
                stack.mulPose(Axis.YP.rotationDegrees(baseYaw));
                stack.translate(-.5, -.5, -.5);
                CachedBuffers.partial(TEST_BASE, block.state()).light(light)
                        .overlay(overlay).renderInto(stack, buffers.getBuffer(RenderType.cutoutMipped()));
                stack.popPose();
            }
            stack.pushPose();
            SnapshotCarriageRenderer.transform(stack, frame);
            if (hook instanceof TestCouplerBlock) {
                stack.pushPose();
                // Standard block-model coordinates: registered rear pivot (0, 0, .5) is (8, 8, 16).
                stack.translate(-.5, -.5, -1);
                CachedBuffers.partial(TEST_ARM, block.state()).light(light)
                        .overlay(overlay).renderInto(stack, buffers.getBuffer(RenderType.cutoutMipped()));
                stack.popPose();
            }
            if (i < animation.parameters().size()) {
                var attachment = animation.parameters(i, partialTick).attachment();
                if (attachment != null) {
                    var complete = attachment.complete();
                    var pivot = attachment.pivot();
                    stack.translate(complete.x() - pivot.x(), complete.y() - pivot.y(), complete.z() - pivot.z());
                    CouplerBlockEntityRenderer.renderAdapter(attachment.adapter(), attachment.reversed(), entity.level(),
                            stack, buffers, light, overlay);
                }
            }
            stack.popPose();
        }
        stack.popPose();
    }
}
