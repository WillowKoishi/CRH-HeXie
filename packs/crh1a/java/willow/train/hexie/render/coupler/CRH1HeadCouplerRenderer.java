package willow.train.hexie.render.coupler;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.CreateCarriageBridge.SnapshotCouplerAnimationParametersView;
import willow.train.hexie.bridge.world.BlockEntityViews.CRH1HeadEntityView;
import willow.train.hexie.bridge.world.CarriageBlockBridge;
import willow.train.hexie.render.init.PartialModelInit;

/** Covers remain on the body; the two coupler pieces share the solver's vertical axis. */
public final class CRH1HeadCouplerRenderer implements CouplerModelParts {
    @Override public AABB attachedBounds(BlockState state) { return HexieBridges.carriageBlock().crh1HeadGeometry().bounds(state.getValue(BlockStateProperties.HORIZONTAL_FACING)); }
    @Override public void arm(PoseStack pose, Sink sink) { armParts(pose, sink, 1, true); }
    @Override public void arm(BlockState state, BlockEntity display, SnapshotCouplerAnimationParametersView parameters,
                              float partialTick, PoseStack pose, Sink sink) {
        armParts(pose, sink, progress(state, display, partialTick), true);
    }
    @Override public void attached(BlockState state, BlockEntity display, SnapshotCouplerAnimationParametersView parameters,
                                   float partialTick, boolean inWorld, PoseStack pose, Sink sink) {
        float progress = progress(state, display, partialTick);
        float open = HexieBridges.carriageBlock().crh1HeadAnimation().open(progress), back = HexieBridges.carriageBlock().crh1HeadAnimation().withdraw(progress);
        var lower = HexieBridges.carriageBlock().crh1HeadGeometry().lowerDelta();
        pose.pushPose();
        pose.translate(lower.x * open, lower.y * open, back / 16d + (lower.z - 1 / 16d) * open);
        sink.drawShell(PartialModelInit.CRH1_HEAD_COVER_LOWER, pose);
        pose.popPose();
        var upper = HexieBridges.carriageBlock().crh1HeadGeometry().upperDelta();
        var pivot = HexieBridges.carriageBlock().crh1HeadGeometry().upperPivot();
        pose.pushPose();
        pose.translate(upper.x * open, upper.y * open, back / 16d + (upper.z - 1 / 16d) * open);
        pose.translate(pivot.x, pivot.y, pivot.z);
        pose.mulPose(Axis.XP.rotationDegrees(HexieBridges.carriageBlock().crh1HeadGeometry().upperAngle() * open));
        pose.translate(-pivot.x, -pivot.y, -pivot.z);
        sink.drawShell(PartialModelInit.CRH1_HEAD_COVER_UPPER, pose);
        pose.popPose();
        if (inWorld) armParts(pose, sink, progress, false);
    }
    private static void armParts(PoseStack pose, Sink sink, float progress, boolean originAtAxis) {
        pose.pushPose();
        if (originAtAxis) {
            var axis = HexieBridges.carriageBlock().crh1HeadGeometry().armOrigin();
            pose.translate(-axis.x, -axis.y, -axis.z);
        }
        sink.draw(PartialModelInit.CRH1_HEAD_COUPLER_REAR, pose, null);
        pose.translate(0, 0, -CarriageBlockBridge.CRH1HeadAnimationOperations.EXTENSION * HexieBridges.carriageBlock().crh1HeadAnimation().extension(progress));
        sink.draw(PartialModelInit.CRH1_HEAD_COUPLER_FRONT, pose, null);
        pose.popPose();
    }
    private static float progress(BlockState state, BlockEntity entity, float partialTick) {
        return entity instanceof CRH1HeadEntityView head ? head.animationProgress(partialTick)
                : state.getValue(BlockStateProperties.OPEN) ? 1 : 0;
    }
}
