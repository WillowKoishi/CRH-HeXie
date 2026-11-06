package willow.train.hexie.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.util.RendererReloadCache;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint;
import willow.train.hexie.vehicle.assembly.CouplerDataRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.compat.create.SnapshotCouplerAnimation;
import willow.train.hexie.blocks.coupler.CouplerBlock;
import willow.train.hexie.client.render.blockentity.CouplerBlockEntityRenderer;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;

/** Dispatches coupler parts in carriage space, preserving extension renderers that supply their own buffers. */
public final class SnapshotCouplerRenderer {
    private static final java.util.Map<Block, Renderer> RENDERERS = new java.util.IdentityHashMap<>();

    private static final RendererReloadCache<CouplingEndpoint.RenderState, BlockState> MOVING_STATES =
            new RendererReloadCache<>(CouplerDataRegistry::renderState);

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

    private static void mount(PoseStack stack, BlockPos position, double floorOffset, String facing) {
        stack.translate(position.getX() + .5, position.getY() + .5 - floorOffset, position.getZ() + .5);
        float yaw = switch (facing) {
            case "EAST" -> -90; case "SOUTH" -> 180; case "WEST" -> 90; default -> 0;
        };
        stack.mulPose(Axis.YP.rotationDegrees(yaw));
        stack.translate(-.5, -.5, -.5);
    }

    private static void alignMovingModel(PoseStack stack, CouplingEndpoint.Visual visual) {
        var pivot = visual.origin();
        var offset = visual.movingOffset();
        stack.translate(-.5 - pivot.x() + offset.x(), -.5 - pivot.y() + offset.y(), -.5 - pivot.z() + offset.z());
    }

    public static void render(SnapshotCarriageEntity entity, float partialTick, PoseStack stack,
                              MultiBufferSource buffers, int overlay) {
        if (entity.couplerAnimation().frames().isEmpty()) return;
        stack.pushPose();
        entity.applyLocalTransforms(stack, partialTick);
        renderParts(entity, partialTick, stack, buffers, overlay, null);
        stack.popPose();
    }

    /** The visual passes a carriage-local stack; the ordinary renderer supplies the same stack in world space. */
    static void renderParts(SnapshotCarriageEntity entity, float partialTick, PoseStack stack, MultiBufferSource buffers,
                            int overlay, SnapshotCouplerVisual instances) {
        var animation = entity.couplerAnimation();
        if (animation.frames().isEmpty()) return;
        var client = entity.getContraption().getOrCreateClientContraptionLazy();
        boolean instanced = instances != null || VisualizationManager.supportsVisualization(entity.level());
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
            var parts = renderer instanceof CouplerModelParts p ? p : null;
            boolean drawRegistered = renderer != null && (instances != null ? parts != null : !instanced || parts == null);
            boolean hasMoving = visual != null && visual.moving() != null;
            var movingState = hasMoving ? MOVING_STATES.get(visual.moving()) : null;
            boolean baked = hasMoving && movingState.getRenderShape() == RenderShape.MODEL;
            boolean drawMoving = hasMoving && (instances != null ? baked : !instanced || !baked);
            var attachment = parameters == null ? null : parameters.attachment();
            if (!drawRegistered && !drawMoving && (instances != null || attachment == null)) continue;
            int light = SnapshotCarriageRenderer.light(entity, frame, partialTick);
            if (instances != null) instances.light = light;
            if (drawRegistered) {
                stack.pushPose();
                // Bellows remain attached to the body's floor. Unlike an independent coupler base,
                // their mount must not subtract floorOffset when the body height is adjusted.
                mount(stack, frame.position(), 0, facing);
                var blockEntity = client.getBlockEntity(frame.position());
                if (instances != null) parts.attached(block.state(), blockEntity, parameters, partialTick, false, stack, instances);
                else renderer.renderAttachedParts(block.state(), blockEntity, parameters, partialTick,
                        stack, buffers, light, overlay);
                stack.popPose();
            }
            if (drawRegistered && animation.floorOffset() != 0) {
                // The body mesh moved with the floor; this fixed base follows the body's heading at its old height.
                stack.pushPose();
                mount(stack, frame.position(), animation.floorOffset(), facing);
                if (instances != null) parts.base(stack, instances);
                else renderer.renderBase(block.state(), stack, buffers, light, overlay);
                stack.popPose();
            }
            stack.pushPose();
            SnapshotCarriageRenderer.transform(stack, frame);
            if (drawMoving) {
                stack.pushPose();
                alignMovingModel(stack, visual);
                if (instances != null) {
                    instances.moving(client, frame.position(), movingState, stack);
                } else {
                    var dispatcher = Minecraft.getInstance().getBlockRenderer();
                    var world = client.getRenderLevel();
                    var model = dispatcher.getBlockModel(movingState);
                    var data = model.getModelData(world, frame.position(), movingState, world.getModelData(frame.position()));
                    dispatcher.renderSingleBlock(movingState, stack, buffers, light, overlay, data, null);
                }
                stack.popPose();
            } else if (drawRegistered && !hasMoving) {
                if (instances != null) parts.arm(stack, instances);
                else renderer.renderArm(block.state(), parameters, partialTick, stack, buffers, light, overlay);
            }
            if (attachment != null && instances == null) {
                var complete = attachment.complete();
                var pivot = attachment.pivot();
                stack.translate(complete.x() - pivot.x(), complete.y() - pivot.y(), complete.z() - pivot.z());
                CouplerBlockEntityRenderer.renderAdapter(attachment.adapter(), attachment.reversed(), entity.level(),
                        stack, buffers, light, overlay);
            }
            stack.popPose();
        }
    }
}
