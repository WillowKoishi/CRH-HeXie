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

        /** Parts attached to the body, before applying the independent arm's pivot transform. */
        default void renderAttachedParts(BlockState state, SnapshotCouplerAnimation.Parameters parameters,
                float partialTick, PoseStack stack, MultiBufferSource buffers, int light, int overlay) {}

        /** The displayed block entity owns local animations independently of the solved coupler arm. */
        default void renderAttachedParts(BlockState state, net.minecraft.world.level.block.entity.BlockEntity blockEntity,
                SnapshotCouplerAnimation.Parameters parameters, float partialTick, PoseStack stack,
                MultiBufferSource buffers, int light, int overlay) {
            renderAttachedParts(state, parameters, partialTick, stack, buffers, light, overlay);
        }

        /** Free state in the world, where no runtime endpoint or coupling payload exists. */
        default void renderUncoupledAttachedParts(BlockState state, PoseStack stack,
                MultiBufferSource buffers, int light, int overlay) {}

        default void renderUncoupledAttachedParts(BlockState state,
                net.minecraft.world.level.block.entity.BlockEntity blockEntity, float partialTick, PoseStack stack,
                MultiBufferSource buffers, int light, int overlay) {
            renderUncoupledAttachedParts(state, stack, buffers, light, overlay);
        }

        /** Local block-space bounds, with the block facing already applied; null means no extra bounds. */
        default net.minecraft.world.phys.AABB attachedBounds(BlockState state) { return null; }
    }

    private SnapshotCouplerRenderer() {}

    /** Register on the client during setup; no concrete test models are loaded by this renderer. */
    public static void register(Block block, Renderer renderer) {
        RENDERERS.put(java.util.Objects.requireNonNull(block), java.util.Objects.requireNonNull(renderer));
    }

    public static net.minecraft.world.phys.AABB attachedBounds(BlockState state) {
        var renderer = RENDERERS.get(state.getBlock());
        return renderer == null ? null : renderer.attachedBounds(state);
    }

    /** Uses the same registered parts in the world; does not draw the static arm a second time. */
    public static void renderWorldAttachedParts(BlockState state, PoseStack stack,
            MultiBufferSource buffers, int light, int overlay) {
        renderWorldAttachedParts(state, null, 1, stack, buffers, light, overlay);
    }

    public static void renderWorldAttachedParts(BlockState state,
            net.minecraft.world.level.block.entity.BlockEntity blockEntity, float partialTick, PoseStack stack,
            MultiBufferSource buffers, int light, int overlay) {
        var hook = CouplerBlock.of(state);
        var renderer = RENDERERS.get(state.getBlock());
        if (hook == null || renderer == null || hook.assembled(state)) return;
        stack.pushPose();
        stack.translate(.5, .5, .5);
        float yaw = switch (hook.facing(state)) {
            case EAST -> -90; case SOUTH -> 180; case WEST -> 90; default -> 0;
        };
        stack.mulPose(Axis.YP.rotationDegrees(yaw));
        stack.translate(-.5, -.5, -.5);
        renderer.renderUncoupledAttachedParts(state, blockEntity, partialTick, stack, buffers, light, overlay);
        stack.popPose();
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
            var parameters = i < animation.parameters().size() ? animation.parameters(i, partialTick) : null;
            var visual = parameters == null ? null : parameters.visual();
            var hook = block.state().getBlock() instanceof CouplerBlock registered ? registered : null;
            if (visual == null && hook == null) continue;
            String facing = visual != null ? visual.facing().name() : hook.facing(block.state()).name();
            var renderer = RENDERERS.get(block.state().getBlock());
            int light = SnapshotCarriageRenderer.light(entity, frame, partialTick);
            if (renderer != null) {
                stack.pushPose();
                // Bellows remain attached to the body's floor. Unlike an independent coupler base,
                // their mount must not subtract floorOffset when the body height is adjusted.
                stack.translate(frame.position().getX() + .5, frame.position().getY() + .5,
                        frame.position().getZ() + .5);
                float yaw = switch (facing) {
                    case "EAST" -> -90; case "SOUTH" -> 180; case "WEST" -> 90; default -> 0;
                };
                stack.mulPose(Axis.YP.rotationDegrees(yaw));
                stack.translate(-.5, -.5, -.5);
                var blockEntity = entity.getContraption().getOrCreateClientContraptionLazy()
                        .getRenderLevel().getBlockEntity(frame.position());
                renderer.renderAttachedParts(block.state(), blockEntity, parameters, partialTick,
                        stack, buffers, light, overlay);
                stack.popPose();
            }
            if (renderer != null && animation.floorOffset() != 0) {
                // The body mesh moved with the floor; this fixed base follows the body's heading at its old height.
                stack.pushPose();
                stack.translate(frame.position().getX() + .5, frame.position().getY() + .5 - animation.floorOffset(),
                        frame.position().getZ() + .5);
                float baseYaw = switch (facing) {
                    case "EAST" -> -90; case "SOUTH" -> 180; case "WEST" -> 90; default -> 0;
                };
                stack.mulPose(Axis.YP.rotationDegrees(baseYaw));
                stack.translate(-.5, -.5, -.5);
                renderer.renderBase(block.state(), stack, buffers, light, overlay);
                stack.popPose();
            }
            stack.pushPose();
            SnapshotCarriageRenderer.transform(stack, frame);
            if (visual != null && visual.moving() != null) {
                var pivot = visual.origin();
                var offset = visual.movingOffset();
                stack.pushPose();
                stack.translate(-.5 - pivot.x() + offset.x(), -.5 - pivot.y() + offset.y(), -.5 - pivot.z() + offset.z());
                var dispatcher = net.minecraft.client.Minecraft.getInstance().getBlockRenderer();
                var movingState = willow.train.hexie.vehicle.assembly.CouplerDataRegistry.renderState(visual.moving());
                var model = dispatcher.getBlockModel(movingState);
                var world = entity.getContraption().getOrCreateClientContraptionLazy().getRenderLevel();
                var modelData = model.getModelData(world, frame.position(), movingState,
                        world.getModelData(frame.position()));
                dispatcher.renderSingleBlock(movingState, stack, buffers, light, overlay, modelData, null);
                stack.popPose();
            } else if (renderer != null)
                renderer.renderArm(block.state(), parameters, partialTick, stack, buffers, light, overlay);
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
