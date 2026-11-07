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

/** The body swings at the rear pin; only the knuckle opens at the forward pin. */
public final class JanneyCouplerRenderer implements CouplerModelParts {
    private final PartialModel base, body, knuckle, bodyPin, knucklePin;
    private final CouplerGeometry geometry;
    private final Vec3 origin;

    public JanneyCouplerRenderer(PartialModel base, PartialModel body, PartialModel knuckle,
            PartialModel bodyPin, PartialModel knucklePin, CouplerGeometry geometry) {
        this.base = Objects.requireNonNull(base);
        this.body = Objects.requireNonNull(body);
        this.knuckle = Objects.requireNonNull(knuckle);
        this.bodyPin = Objects.requireNonNull(bodyPin);
        this.knucklePin = Objects.requireNonNull(knucklePin);
        this.geometry = Objects.requireNonNull(geometry);
        this.origin = geometry.armOrigin();
        if (!geometry.hasKnuckle()) throw new IllegalArgumentException("Janney geometry needs a knuckle");
    }

    @Override public AABB attachedBounds(BlockState state) {
        return geometry.bounds(CouplerBlock.of(state).facing(state));
    }

    @Override public void base(PoseStack pose, Sink sink) { sink.draw(base, pose, null); }

    @Override public void arm(PoseStack pose, Sink sink) { arm(0, pose, sink); }

    @Override public void arm(BlockState state, BlockEntity display, SnapshotCouplerAnimation.Parameters parameters,
            float partialTick, PoseStack pose, Sink sink) {
        arm(progress(state, display, partialTick), pose, sink);
    }

    private void arm(float progress, PoseStack pose, Sink sink) {
        pose.pushPose();
        pose.translate(-origin.x, -origin.y, -origin.z);
        sink.draw(body, pose, null);
        sink.draw(bodyPin, pose, null);
        sink.draw(knucklePin, pose, null);
        knuckle(progress, pose, sink);
        pose.popPose();
    }

    @Override public void attached(BlockState state, BlockEntity display, SnapshotCouplerAnimation.Parameters parameters,
            float partialTick, boolean inWorld, PoseStack pose, Sink sink) {
        // Placed/native Create blocks bake base, body and pins. Snapshot arms draw all moving pieces above.
        if (inWorld) knuckle(progress(state, display, partialTick), pose, sink);
    }

    private static float progress(BlockState state, BlockEntity display, float partialTick) {
        return display instanceof CouplerBlockEntity coupler ? coupler.coverProgress(partialTick)
                : state.getOptionalValue(BlockStateProperties.OPEN).orElse(false) ? 1 : 0;
    }

    private void knuckle(float progress, PoseStack pose, Sink sink) {
        var axis = geometry.knuckleAxis();
        pose.pushPose();
        pose.translate(axis.x(), 0, axis.z());
        pose.mulPose(Axis.YP.rotationDegrees(geometry.knuckleOpeningAngle() * progress));
        pose.translate(-axis.x(), 0, -axis.z());
        sink.draw(knuckle, pose, null);
        pose.popPose();
    }
}
