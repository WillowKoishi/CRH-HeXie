package willow.train.hexie.client.render.bogey;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.trains.bogey.BogeyRenderer;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import com.simibubi.create.content.trains.bogey.BogeyVisual;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import com.simibubi.create.foundation.render.SpecialModels;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import willow.train.hexie.blocks.bogeys.BogeyFloorData;

/** Shares each bogey's animation transforms between Create's renderer and Flywheel visual. */
public abstract class SimpleBogeyRenderer implements BogeyRenderer {
    // Flywheel may update different visuals using the same renderer concurrently.
    private final ThreadLocal<BiConsumer<Part, Matrix4f>> instanceOutput = new ThreadLocal<>();
    private final ThreadLocal<Matrix4f> instanceTransform = ThreadLocal.withInitial(Matrix4f::new);
    private record Part(PartialModel model, boolean reversed) {}
    private final ThreadLocal<Boolean> reversedPass = new ThreadLocal<>();
    private int overlay;

    public abstract BogeySizes.BogeySize getSize();

    public abstract void render(CompoundTag bogeyData, float wheelAngle, PoseStack poseStack,
            int light, VertexConsumer buffer, boolean inContraption);

    @Override
    public final void render(CompoundTag bogeyData, float wheelAngle, float partialTick,
            PoseStack poseStack, MultiBufferSource buffers, int light, int overlay,
            boolean inContraption) {
        this.overlay = overlay;
        poseStack.pushPose();
        try {
            renderParts(bogeyData, wheelAngle, poseStack, light,
                    buffers.getBuffer(RenderType.cutoutMipped()), inContraption);
        } finally {
            poseStack.popPose();
        }
    }

    public final BogeyVisual createVisual(VisualizationContext context, float partialTick,
            boolean inContraption) {
        return new AnimatedVisual(context, inContraption);
    }

    private static void applyFloorPreview(CompoundTag data, PoseStack pose, boolean inContraption) {
        // Wheels keep their rail height; the separate top model follows height and longitudinal settings.
        // Assembled vehicles already apply longitudinal displacement through the real support pivot.
        if (!inContraption) pose.translate(0, 0, BogeyFloorData.LONGITUDINAL_SETTING.blocks(BogeyFloorData.longitudinal(data)));
    }

    private void renderParts(CompoundTag data, float angle, PoseStack pose, int light, VertexConsumer buffer, boolean moving) {
        applyFloorPreview(data, pose, moving);
        boolean reversed = data.getBoolean(BogeyFloorData.REVERSED);
        if (reversed) pose.scale(1, 1, -1);
        reversedPass.set(reversed);
        try {
            render(data, reversed ? -angle : angle, pose, light, buffer, moving);
        } finally {
            reversedPass.remove();
        }
    }

    protected final BogeyModelData getTransform(PartialModel model, PoseStack poseStack,
            boolean instanced) {
        return new BogeyModelData(model);
    }

    protected final BogeyModelData[] getTransform(PartialModel model, PoseStack poseStack,
            boolean instanced, int count) {
        BogeyModelData[] result = new BogeyModelData[count];
        for (int i = 0; i < count; i++) result[i] = new BogeyModelData(model);
        return result;
    }

    public final class BogeyModelData {
        private final PartialModel model;
        private final Matrix4f transform = new Matrix4f();

        private BogeyModelData(PartialModel model) {
            this.model = model;
            // Raise frame and wheel parts once; the static top model keeps its original anchor.
            transform.translate(0, (float) willow.train.hexie.init.track.TrackMaterialInit.RAIL_HEIGHT_INCREASE, 0);
        }

        public BogeyModelData translate(double x, double y, double z) {
            transform.translate((float) x, (float) y, (float) z);
            return this;
        }

        public BogeyModelData rotateX(double degrees) {
            transform.rotateX((float) Math.toRadians(degrees));
            return this;
        }

        public BogeyModelData rotateY(double degrees) {
            transform.rotateY((float) Math.toRadians(degrees));
            return this;
        }

        public void render(PoseStack poseStack, int light, VertexConsumer buffer) {
            boolean reversed = Boolean.TRUE.equals(reversedPass.get());
            // The geometry is already reflected. Cancel its local reflection after the animated transform.
            Matrix4f local = reversed ? new Matrix4f(transform).scale(1, 1, -1) : transform;
            BiConsumer<Part, Matrix4f> output = instanceOutput.get();
            if (output != null) {
                // The instance copies the transform immediately, so this thread-local scratch matrix is reusable.
                output.accept(new Part(model, reversed), instanceTransform.get().set(poseStack.last().pose()).mul(local));
                return;
            }
            poseStack.pushPose();
            poseStack.last().pose().mul(local);
            poseStack.last().normal().mul(new Matrix3f(local));
            (reversed ? BogeyMirror.BUFFERS.get(model) : CachedBuffers.partial(model, Blocks.AIR.defaultBlockState()))
                    .light(light).overlay(overlay).renderInto(poseStack, buffer);
            poseStack.popPose();
        }
    }

    private static final class ModelInstances {
        private final List<TransformedInstance> instances = new ArrayList<>();
        private int used;
    }

    private final class AnimatedVisual implements BogeyVisual {
        private final VisualizationContext context;
        private final boolean inContraption;
        private final Map<Part, ModelInstances> instances = new HashMap<>();
        private final BiConsumer<Part, Matrix4f> output = this::emit;
        private int packedLight;

        private AnimatedVisual(VisualizationContext context, boolean inContraption) {
            this.context = context;
            this.inContraption = inContraption;
        }

        @Override
        public void update(CompoundTag bogeyData, float wheelAngle, PoseStack poseStack) {
            instances.values().forEach(group -> group.used = 0);
            BiConsumer<Part, Matrix4f> previousOutput = instanceOutput.get();
            instanceOutput.set(output);
            poseStack.pushPose();
            try {
                renderParts(bogeyData, wheelAngle, poseStack,
                        packedLight, null, inContraption);
            } finally {
                poseStack.popPose();
                if (previousOutput == null)
                    instanceOutput.remove();
                else
                    instanceOutput.set(previousOutput);
            }
            instances.values().forEach(group -> {
                for (int i = group.used; i < group.instances.size(); i++)
                    group.instances.get(i).setZeroTransform().setChanged();
            });
        }

        private void emit(Part model, Matrix4f transform) {
            ModelInstances group = instances.computeIfAbsent(model, ignored -> new ModelInstances());
            int index = group.used++;
            if (index == group.instances.size()) {
                TransformedInstance instance = context.instancerProvider()
                        .instancer(InstanceTypes.TRANSFORMED, model.reversed() ? BogeyMirror.MODELS.get(model.model()) : SpecialModels.smoothLit(model.model()))
                        .createInstance();
                instance.light(packedLight);
                group.instances.add(instance);
            }
            group.instances.get(index).setTransform(transform).setChanged();
        }

        @Override
        public void hide() {
            forEach(instance -> instance.setZeroTransform().setChanged());
        }

        @Override
        public void updateLight(int packedLight) {
            this.packedLight = packedLight;
            forEach(instance -> instance.light(packedLight).setChanged());
        }

        @Override
        public void collectCrumblingInstances(Consumer<Instance> consumer) {
            forEach(consumer::accept);
        }

        @Override
        public void delete() {
            forEach(Instance::delete);
            instances.clear();
        }

        private void forEach(Consumer<TransformedInstance> action) {
            instances.values().forEach(group -> group.instances.forEach(action));
        }
    }
}
