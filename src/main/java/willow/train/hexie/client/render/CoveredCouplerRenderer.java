package willow.train.hexie.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import java.util.Objects;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.blockentity.CouplerBlockEntity;
import willow.train.hexie.blocks.coupler.CouplerBlock;
import willow.train.hexie.blocks.coupler.CouplerGeometry;
import willow.train.hexie.compat.create.SnapshotCouplerAnimation;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint;

/** Independent coupler arm and body-mounted covers share the authored mounting coordinates. */
public final class CoveredCouplerRenderer implements SnapshotCouplerRenderer.Renderer {
    private final PartialModel base, arm, left, right;
    private final Vec3 armOrigin;
    private final CouplingEndpoint.Axis coverAxis;
    private final float openingAngle;
    private final CouplerGeometry geometry;

    public CoveredCouplerRenderer(PartialModel base, PartialModel arm, PartialModel left,
                                  PartialModel right, CouplerGeometry geometry) {
        this.base = Objects.requireNonNull(base);
        this.arm = Objects.requireNonNull(arm);
        this.left = Objects.requireNonNull(left);
        this.right = Objects.requireNonNull(right);
        this.geometry = Objects.requireNonNull(geometry);
        this.armOrigin = geometry.armOrigin();
        this.coverAxis = geometry.coverAxis();
        this.openingAngle = geometry.openingAngle();
    }

    @Override public AABB attachedBounds(BlockState state) {
        return geometry.bounds(CouplerBlock.of(state).facing(state));
    }

    @Override public void renderArm(BlockState state, SnapshotCouplerAnimation.Parameters parameters,
            float partialTick, PoseStack stack, MultiBufferSource buffers, int light, int overlay) {
        // The existing coupler pass already applies the solved arm origin and swing.
        stack.pushPose();
        stack.translate(-armOrigin.x, -armOrigin.y, -armOrigin.z);
        draw(arm, state, stack, buffers, light, overlay);
        stack.popPose();
    }

    @Override public void renderBase(BlockState state, PoseStack stack,
            MultiBufferSource buffers, int light, int overlay) {
        draw(base, state, stack, buffers, light, overlay);
    }

    @Override public void renderAttachedParts(BlockState state, BlockEntity blockEntity,
            SnapshotCouplerAnimation.Parameters parameters, float partialTick, PoseStack stack,
            MultiBufferSource buffers, int light, int overlay) {
        covers(state, blockEntity, partialTick, stack, buffers, light, overlay);
    }

    @Override public void renderUncoupledAttachedParts(BlockState state, BlockEntity blockEntity,
            float partialTick, PoseStack stack, MultiBufferSource buffers, int light, int overlay) {
        covers(state, blockEntity, partialTick, stack, buffers, light, overlay);
    }

    private void covers(BlockState state, BlockEntity blockEntity, float partialTick, PoseStack stack,
            MultiBufferSource buffers, int light, int overlay) {
        float progress = blockEntity instanceof CouplerBlockEntity coupler ? coupler.coverProgress(partialTick)
                : state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN) ? 1 : 0;
        cover(left, openingAngle * progress, state, stack, buffers, light, overlay);
        cover(right, -openingAngle * progress, state, stack, buffers, light, overlay);
    }

    private void cover(PartialModel model, float angle, BlockState state, PoseStack stack,
            MultiBufferSource buffers, int light, int overlay) {
        stack.pushPose();
        stack.translate(coverAxis.x(), 0, coverAxis.z());
        stack.mulPose(Axis.YP.rotationDegrees(angle));
        stack.translate(-coverAxis.x(), 0, -coverAxis.z());
        draw(model, state, stack, buffers, light, overlay);
        stack.popPose();
    }

    private static void draw(PartialModel model, BlockState state, PoseStack stack,
            MultiBufferSource buffers, int light, int overlay) {
        CachedBuffers.partial(model, state).light(light).overlay(overlay)
                .renderInto(stack, buffers.getBuffer(RenderType.cutoutMipped()));
    }

}
