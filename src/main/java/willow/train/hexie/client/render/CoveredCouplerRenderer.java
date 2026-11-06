package willow.train.hexie.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import java.util.Objects;
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
public final class CoveredCouplerRenderer implements CouplerModelParts {
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

    @Override public void arm(PoseStack stack, Sink sink) {
        stack.pushPose();
        stack.translate(-armOrigin.x, -armOrigin.y, -armOrigin.z);
        sink.draw(arm, stack, null);
        stack.popPose();
    }

    @Override public void base(PoseStack stack, Sink sink) {
        sink.draw(base, stack, null);
    }

    @Override public void attached(BlockState state, BlockEntity blockEntity,
            SnapshotCouplerAnimation.Parameters parameters, float partialTick, boolean inWorld, PoseStack stack, Sink sink) {
        float progress = blockEntity instanceof CouplerBlockEntity coupler ? coupler.coverProgress(partialTick)
                : state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN) ? 1 : 0;
        cover(left, openingAngle * progress, stack, sink);
        cover(right, -openingAngle * progress, stack, sink);
    }

    private void cover(PartialModel model, float angle, PoseStack stack, Sink sink) {
        stack.pushPose();
        stack.translate(coverAxis.x(), 0, coverAxis.z());
        stack.mulPose(Axis.YP.rotationDegrees(angle));
        stack.translate(-coverAxis.x(), 0, -coverAxis.z());
        sink.draw(model, stack, null);
        stack.popPose();
    }
}
