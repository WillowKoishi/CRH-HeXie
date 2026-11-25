package willow.train.hexie.render.coupler;

// split from willow/train/hexie/client/render/PartialCouplerRenderer.java

import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import java.util.List;
import java.util.function.Function;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.compat.CreateCarriageBridge.SnapshotCouplerAnimationParametersView;

import static willow.train.hexie.render.coupler.BellowsGeometry.BellowsDeformation;
import static willow.train.hexie.render.coupler.BellowsGeometry.ContactSpan;

/** Model parameters for the existing coupler render pass, shared by rigid arms and body-mounted bellows. */
public final class PartialCouplerRenderer implements CouplerModelParts {
    /** Model coordinates are relative to the block corner, facing north; frontZ must be less than baseZ. */
    public record BellowsPart(PartialModel model, double baseZ, double frontZ, double maxThickness) {
        public BellowsPart {
            if (!(frontZ < baseZ)) throw new IllegalArgumentException("Bellows must extend toward local north");
            if (!(maxThickness >= baseZ - frontZ))
                throw new IllegalArgumentException("Maximum thickness must include the neutral bellows");
        }
    }

    private final PartialModel arm, base;
    private final Vec3 pivot;
    private final List<BellowsPart> bellows;
    private final double extensionShare;
    private final Function<BlockState, AABB> bounds;
    private final ContactSpan contactSpan;

    /** base may be null when the fixed shell already belongs to the carriage structure model. */
    public PartialCouplerRenderer(PartialModel arm, Vec3 modelPivot, PartialModel base,
            List<BellowsPart> bellows, double extensionShare) {
        this(arm, modelPivot, base, bellows, extensionShare, state -> null);
    }

    public PartialCouplerRenderer(PartialModel arm, Vec3 modelPivot, PartialModel base,
            List<BellowsPart> bellows, double extensionShare, Function<BlockState, AABB> bounds) {
        this(arm, modelPivot, base, bellows, extensionShare, bounds, null);
    }

    public PartialCouplerRenderer(PartialModel arm, Vec3 modelPivot, PartialModel base,
            List<BellowsPart> bellows, double extensionShare, Function<BlockState, AABB> bounds,
            ContactSpan contactSpan) {
        this.arm = java.util.Objects.requireNonNull(arm);
        this.pivot = java.util.Objects.requireNonNull(modelPivot);
        this.base = base;
        this.bellows = List.copyOf(bellows);
        if (!Double.isFinite(extensionShare) || extensionShare < 0)
            throw new IllegalArgumentException("Invalid bellows share of the coupling gap");
        this.extensionShare = extensionShare;
        this.bounds = java.util.Objects.requireNonNull(bounds);
        this.contactSpan = contactSpan;
    }

    @Override public AABB attachedBounds(BlockState state) { return bounds.apply(state); }

    @Override public void arm(PoseStack stack, Sink sink) {
        stack.pushPose();
        stack.translate(-pivot.x, -pivot.y, -pivot.z);
        sink.draw(arm, stack, null);
        stack.popPose();
    }

    @Override public void base(PoseStack stack, Sink sink) {
        if (base != null) sink.draw(base, stack, null);
    }

    @Override public void attached(BlockState state, BlockEntity display, SnapshotCouplerAnimationParametersView parameters,
            float partialTick, boolean uncoupled, PoseStack stack, Sink sink) {
        BellowsDeformation deformation = null;
        for (var part : bellows) {
            // Parts with the same fixed depth share the upper/lower solution.
            if (deformation == null || deformation.baseZ() != part.baseZ()
                    || deformation.frontZ() != part.frontZ() || deformation.maxThickness() != part.maxThickness()) {
                deformation = (uncoupled ? BellowsDeformation.free(part.baseZ(), part.frontZ(), part.maxThickness())
                        : BellowsDeformation.of(pivot, part.baseZ(), part.frontZ(), part.maxThickness(), parameters, extensionShare))
                        .withContactSpan(contactSpan);
            }
            sink.draw(part.model(), stack, deformation);
        }
    }

}
