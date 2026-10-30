package willow.train.hexie.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.compat.create.SnapshotCouplerAnimation;
import willow.train.hexie.blocks.coupler.CouplerBlock;
import willow.train.hexie.client.render.blockentity.CouplerBlockEntityRenderer;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;

/** Draws the few independent arms with the existing baked-model buffer path, including under Flywheel. */
public final class SnapshotCouplerRenderer {
    private static final java.util.Map<Block, Renderer> RENDERERS = new java.util.IdentityHashMap<>();

    /** Arm origin is the registered pivot; angles and extension are available for custom animation. */
    @FunctionalInterface
    public interface Renderer {
        void renderArm(BlockState state, SnapshotCouplerAnimation.Parameters parameters, float partialTick,
                       PoseStack stack, MultiBufferSource buffers, int light, int overlay);

        /** Base origin is the block's lower corner, rotated to the registered facing. */
        default void renderBase(BlockState state, PoseStack stack, MultiBufferSource buffers, int light, int overlay) {}
    }

    private SnapshotCouplerRenderer() {}

    /** Register on the client during setup; no concrete test models are loaded by this renderer. */
    public static void register(Block block, Renderer renderer) {
        RENDERERS.put(java.util.Objects.requireNonNull(block), java.util.Objects.requireNonNull(renderer));
    }

    public static void render(SnapshotCarriageEntity entity, float partialTick, PoseStack stack,
                              MultiBufferSource buffers, int overlay) {
        var animation = entity.couplerAnimation();
        if (animation.frames().isEmpty()) return;
        stack.pushPose();
        entity.applyLocalTransforms(stack, partialTick);
        for (int i = 0; i < animation.frames().size(); i++) {
            var frame = animation.frame(i, partialTick);
            var block = entity.getContraption().getBlocks().get(frame.position());
            if (block == null) continue;
            var hook = CouplerBlock.of(block.state());
            if (hook == null) continue;
            var renderer = RENDERERS.get(block.state().getBlock());
            int light = SnapshotCarriageRenderer.light(entity, frame, partialTick);
            if (renderer != null && animation.floorOffset() != 0) {
                // The body mesh moved with the floor; this fixed base follows the body's heading at its old height.
                stack.pushPose();
                stack.translate(frame.position().getX() + .5, frame.position().getY() + .5 - animation.floorOffset(),
                        frame.position().getZ() + .5);
                float baseYaw = switch (hook.facing(block.state())) {
                    case EAST -> -90; case SOUTH -> 180; case WEST -> 90; default -> 0;
                };
                stack.mulPose(Axis.YP.rotationDegrees(baseYaw));
                stack.translate(-.5, -.5, -.5);
                renderer.renderBase(block.state(), stack, buffers, light, overlay);
                stack.popPose();
            }
            stack.pushPose();
            SnapshotCarriageRenderer.transform(stack, frame);
            if (hook instanceof willow.train.hexie.vehicle.assembly.CouplerDataRegistry.Definition definition) {
                var pivot = definition.pivot();
                var offset = definition.movingOffset();
                stack.pushPose();
                stack.translate(-.5 - pivot.x() + offset.x(), -.5 - pivot.y() + offset.y(), -.5 - pivot.z() + offset.z());
                var dispatcher = net.minecraft.client.Minecraft.getInstance().getBlockRenderer();
                var model = dispatcher.getBlockModel(definition.movingState());
                var world = entity.getContraption().getOrCreateClientContraptionLazy().getRenderLevel();
                var modelData = model.getModelData(world, frame.position(), definition.movingState(),
                        world.getModelData(frame.position()));
                dispatcher.renderSingleBlock(definition.movingState(), stack, buffers, light, overlay, modelData, null);
                stack.popPose();
            } else if (renderer != null)
                renderer.renderArm(block.state(), i < animation.parameters().size()
                        ? animation.parameters(i, partialTick) : null, partialTick, stack, buffers, light, overlay);
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
