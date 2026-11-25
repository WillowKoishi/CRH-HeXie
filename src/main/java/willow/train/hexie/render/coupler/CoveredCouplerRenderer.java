package willow.train.hexie.render.coupler;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import java.util.Objects;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.CreateCarriageBridge.SnapshotCouplerAnimationParametersView;
import willow.train.hexie.bridge.vehicle.CouplingBridge.CouplingEndpointAxisView;
import willow.train.hexie.bridge.world.BlockEntityViews.CouplerBlockEntityView;
import willow.train.hexie.bridge.world.WorldBlockBridge.CouplerGeometryView;

/** Independent coupler arm and body-mounted covers share the authored mounting coordinates. */
public final class CoveredCouplerRenderer implements CouplerModelParts {
    private final PartialModel base, arm, left, right;
    private final Vec3 armOrigin;
    private final CouplingEndpointAxisView coverAxis;
    private final float openingAngle;
    private final CouplerGeometryView geometry;

    public CoveredCouplerRenderer(PartialModel base, PartialModel arm, PartialModel left,
                                  PartialModel right, CouplerGeometryView geometry) {
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
        return geometry.bounds(HexieBridges.couplerBlock().couplerBlock().of(state).facing(state));
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
            SnapshotCouplerAnimationParametersView parameters, float partialTick, boolean inWorld, PoseStack stack, Sink sink) {
        float progress = blockEntity instanceof CouplerBlockEntityView coupler ? coupler.coverProgress(partialTick)
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
