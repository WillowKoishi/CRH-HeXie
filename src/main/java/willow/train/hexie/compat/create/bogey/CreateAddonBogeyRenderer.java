package willow.train.hexie.compat.create.bogey;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import com.simibubi.create.content.trains.bogey.BogeyStyle;
import java.util.Objects;
import java.util.OptionalDouble;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import willow.train.hexie.api.bogey.BogeyRegistry;
import willow.train.hexie.api.bogey.client.BogeyAnimationContext;
import willow.train.hexie.bogey.client.BogeyAnimations;
import willow.train.hexie.blocks.bogeys.BogeyFloorData;
import willow.train.hexie.client.render.bogey.BogeySuspensionPose;
import willow.train.hexie.client.render.bogey.SimpleBogeyRenderer;

/** Invokes add-on-owned part animation through HeXie's existing ordinary and instanced lifecycles. */
public final class CreateAddonBogeyRenderer {
    private record Sample(double wheelAngle, double speedBlocksPerTick, float partialTick) {}
    private static final ThreadLocal<Sample> SAMPLE = new ThreadLocal<>();
    private CreateAddonBogeyRenderer() {}

    public static BogeyStyle.SizeRenderer create(ResourceLocation id) {
        var renderer = new Renderer(Objects.requireNonNull(id));
        return new BogeyStyle.SizeRenderer(renderer, renderer::createVisual);
    }

    /** Temporary rendering inputs; never modifies saved bogey data or the network animation format. */
    public static SampleScope snapshotSample(double wheelAngle, double speedBlocksPerTick, float partialTick) {
        if (!Double.isFinite(wheelAngle) || !Double.isFinite(speedBlocksPerTick) || !Float.isFinite(partialTick))
            throw new IllegalArgumentException("Non-finite snapshot bogey animation sample");
        Sample previous = SAMPLE.get();
        SAMPLE.set(new Sample(wheelAngle, speedBlocksPerTick, partialTick));
        return new SampleScope(previous);
    }

    public static final class SampleScope implements AutoCloseable {
        private final Sample previous;
        private SampleScope(Sample previous) { this.previous = previous; }
        @Override public void close() {
            if (previous == null) SAMPLE.remove();
            else SAMPLE.set(previous);
        }
    }

    private static final class Renderer extends SimpleBogeyRenderer {
        private final ResourceLocation id;
        private volatile double radius = Double.NaN;
        private Renderer(ResourceLocation id) { this.id = id; }

        @Override public BogeySizes.BogeySize getSize() { return BogeySizes.SMALL; }

        @Override public void render(CompoundTag data, float wheelAngle, PoseStack pose,
                int light, VertexConsumer buffer, boolean inContraption) {
            var registration = BogeyAnimations.find(id).orElseThrow(() ->
                    new IllegalStateException("Missing client bogey animation registration: " + id));
            boolean reversed = data.getBoolean(BogeyFloorData.REVERSED);
            double direction = reversed ? -1 : 1;
            Sample sample = SAMPLE.get();
            var speed = sample != null ? OptionalDouble.of(sample.speedBlocksPerTick * 20 * direction)
                    : OptionalDouble.empty();
            var phase = sample != null
                    ? OptionalDouble.of(-Math.toRadians(sample.wheelAngle) * radius() * direction)
                    : OptionalDouble.empty();
            var context = new BogeyAnimationContext(id, data, BogeySuspensionPose.inModelSpace(data, 0),
                    inContraption, reversed, BogeyFloorData.offset(BogeyFloorData.heightFromBogeyData(data)),
                    BogeyFloorData.LONGITUDINAL_SETTING.blocks(BogeyFloorData.longitudinal(data)), wheelAngle,
                    speed, phase, sample == null ? OptionalDouble.empty() : OptionalDouble.of(sample.partialTick));
            registration.animator().render(context, (model, transform) -> {
                if (!registration.models().contains(Objects.requireNonNull(model)))
                    throw new IllegalArgumentException("Bogey " + id + " did not declare part model " + model);
                if (!Objects.requireNonNull(transform).isFinite())
                    throw new IllegalArgumentException("Non-finite part transform for bogey " + id);
                getTransform(CreateBogeyAnimationBackend.model(model), pose, buffer == null)
                        .transform(transform).render(pose, light, buffer);
            });
        }

        private double radius() {
            double value = radius;
            if (Double.isNaN(value)) {
                value = BogeyRegistry.entries().stream().filter(handle -> handle.id().equals(id)).findFirst()
                        .orElseThrow(() -> new IllegalStateException("Missing common bogey registration: " + id))
                        .definition().model().geometry().wheelRadius();
                radius = value;
            }
            return value;
        }
    }
}
