package willow.train.hexie.api.bogey.client;

import java.util.Objects;
import java.util.OptionalDouble;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/**
 * One read-only rendering sample. Matrices and saved data are defensive copies.
 * Part callbacks do not own the vehicle's pose, state synchronization or rendering lifetime.
 */
public final class BogeyAnimationContext {
    private final ResourceLocation bogeyId;
    private final CompoundTag data;
    private final Matrix4f topInFrame;
    private final boolean inContraption, reversed;
    private final double floorOffsetBlocks, longitudinalOffsetBlocks, wheelAngleDegrees;
    private final OptionalDouble speedBlocksPerSecond, signedTravelPhaseBlocks, partialTick;

    public BogeyAnimationContext(ResourceLocation bogeyId, CompoundTag data, Matrix4fc topInFrame,
            boolean inContraption, boolean reversed, double floorOffsetBlocks, double longitudinalOffsetBlocks,
            double wheelAngleDegrees, OptionalDouble speedBlocksPerSecond,
            OptionalDouble signedTravelPhaseBlocks, OptionalDouble partialTick) {
        this.bogeyId = Objects.requireNonNull(bogeyId);
        this.data = Objects.requireNonNull(data).copy();
        this.topInFrame = new Matrix4f(Objects.requireNonNull(topInFrame));
        if (!this.topInFrame.isFinite()) throw new IllegalArgumentException("Non-finite bogey attachment pose");
        this.inContraption = inContraption;
        this.reversed = reversed;
        this.floorOffsetBlocks = finite(floorOffsetBlocks);
        this.longitudinalOffsetBlocks = finite(longitudinalOffsetBlocks);
        this.wheelAngleDegrees = finite(wheelAngleDegrees);
        this.speedBlocksPerSecond = finite(speedBlocksPerSecond);
        this.signedTravelPhaseBlocks = finite(signedTravelPhaseBlocks);
        this.partialTick = finite(partialTick);
    }

    public ResourceLocation bogeyId() { return bogeyId; }
    public CompoundTag data() { return data.copy(); }

    /**
     * Mechanical top coordinates in the moving frame's model coordinates. Excludes the configured
     * floor-height displacement and already accounts for model reversal; do not mirror it again.
     * The renderer has applied the whole frame pose before invoking the callback.
     */
    public Matrix4f topInFrame() { return new Matrix4f(topInFrame); }

    /** The existing backend render flag; false alone does not establish that a bogey is stationary. */
    public boolean inContraption() { return inContraption; }
    public boolean reversed() { return reversed; }
    public double floorOffsetBlocks() { return floorOffsetBlocks; }
    public double longitudinalOffsetBlocks() { return longitudinalOffsetBlocks; }

    /** Existing renderer angle, in degrees, including model reversal. Use is optional. */
    public double wheelAngleDegrees() { return wheelAngleDegrees; }

    /** Signed speed along the model's forward direction, in blocks/second; absent when not sampled. */
    public OptionalDouble speedBlocksPerSecond() { return speedBlocksPerSecond; }

    /**
     * Continuous animation phase expressed in signed blocks, reconstructed from HeXie's existing
     * unwrapped wheel phase and registered radius. It is not a persistent odometer; it follows the
     * existing animation's initialization/reload phase. Missing for native paths without that sample.
     */
    public OptionalDouble signedTravelPhaseBlocks() { return signedTravelPhaseBlocks; }

    /** The actual interpolation fraction for this sample, when the invoking render path supplies it. */
    public OptionalDouble partialTick() { return partialTick; }

    private static double finite(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite bogey animation input");
        return value;
    }

    private static OptionalDouble finite(OptionalDouble value) {
        Objects.requireNonNull(value).ifPresent(BogeyAnimationContext::finite);
        return value;
    }
}
