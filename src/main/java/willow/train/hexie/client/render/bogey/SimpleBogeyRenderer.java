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

/** Shares each bogey's animation transforms between Create's renderer and Flywheel visual. */
public abstract class SimpleBogeyRenderer implements BogeyRenderer {
    private BiConsumer<PartialModel, Matrix4f> instanceOutput;
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
            render(bogeyData, wheelAngle, poseStack, light,
                    buffers.getBuffer(RenderType.cutoutMipped()), inContraption);
        } finally {
            poseStack.popPose();
        }
    }

    public final BogeyVisual createVisual(VisualizationContext context, float partialTick,
            boolean inContraption) {
        return new AnimatedVisual(context, inContraption);
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
            if (instanceOutput != null) {
                instanceOutput.accept(model, new Matrix4f(poseStack.last().pose()).mul(transform));
                return;
            }
            poseStack.pushPose();
            poseStack.last().pose().mul(transform);
            poseStack.last().normal().mul(new Matrix3f(transform));
            CachedBuffers.partial(model, Blocks.AIR.defaultBlockState())
                    .light(light).overlay(overlay).renderInto(poseStack, buffer);
            poseStack.popPose();
        }
    }

    private final class AnimatedVisual implements BogeyVisual {
        private final VisualizationContext context;
        private final boolean inContraption;
        private final Map<PartialModel, List<TransformedInstance>> instances = new HashMap<>();
        private final Map<PartialModel, Integer> used = new HashMap<>();
        private int packedLight;

        private AnimatedVisual(VisualizationContext context, boolean inContraption) {
            this.context = context;
            this.inContraption = inContraption;
        }

        @Override
        public void update(CompoundTag bogeyData, float wheelAngle, PoseStack poseStack) {
            used.clear();
            instanceOutput = this::emit;
            poseStack.pushPose();
            try {
                SimpleBogeyRenderer.this.render(bogeyData, wheelAngle, poseStack,
                        packedLight, null, inContraption);
            } finally {
                poseStack.popPose();
                instanceOutput = null;
            }
            instances.forEach((model, list) -> {
                for (int i = used.getOrDefault(model, 0); i < list.size(); i++)
                    list.get(i).setZeroTransform().setChanged();
            });
        }

        private void emit(PartialModel model, Matrix4f transform) {
            List<TransformedInstance> list = instances.computeIfAbsent(model,
                    ignored -> new ArrayList<>());
            int index = used.merge(model, 1, Integer::sum) - 1;
            if (index == list.size()) {
                list.add(context.instancerProvider()
                        .instancer(InstanceTypes.TRANSFORMED, SpecialModels.smoothLit(model))
                        .createInstance());
            }
            list.get(index).setTransform(transform).light(packedLight).setChanged();
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
            instances.values().forEach(list -> list.forEach(action));
        }
    }
}
