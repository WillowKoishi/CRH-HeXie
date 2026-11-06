package willow.train.hexie.client.render.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.material.LightShaders;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.model.ModelUtil;
import dev.engine_room.flywheel.lib.model.baked.BakedModelBuilder;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.util.RendererReloadCache;
import java.util.Arrays;
import java.util.function.Consumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import willow.train.hexie.client.render.carriage.CarriageTransparencyCompat;
import willow.train.hexie.client.render.carriage.CompatibleCarriageVisual;

/** One transformed instance per authored moving part, lit by the carriage embedding. */
final class CarriagePartInstances {
    private record Part(BakedModel model, RenderType layer, boolean disableDiffuse) {
        Model bake() {
            return new BakedModelBuilder(model).materialFunc((original, shaded, ao) -> {
                var material = ModelUtil.getMaterial(layer, shaded && !disableDiffuse, false);
                if (CarriageTransparencyCompat.required())
                    material = CompatibleCarriageVisual.withCompatibleTransparency(material, layer);
                return SimpleMaterial.builderOf(material).light(LightShaders.SMOOTH).build();
            }).build();
        }
    }

    private static final RendererReloadCache<Part, Model> MODELS = new RendererReloadCache<>(Part::bake);
    private final VisualizationContext context;
    private Part[] parts;
    private TransformedInstance[] instances;
    private int next;

    CarriagePartInstances(VisualizationContext context, int count) {
        this.context = context;
        parts = new Part[count];
        instances = new TransformedInstance[count];
    }

    void start() {
        next = 0;
    }

    void draw(PartialModel model, RenderType layer, PoseStack transform, boolean disableDiffuse) {
        draw(model.get(), layer, transform, disableDiffuse, 0xffffffff);
    }

    void draw(BakedModel model, RenderType layer, PoseStack transform, boolean disableDiffuse, int color) {
        int index = next++;
        if (index == parts.length) {
            int capacity = Math.max(4, index * 2);
            parts = Arrays.copyOf(parts, capacity);
            instances = Arrays.copyOf(instances, capacity);
        }
        var part = parts[index];
        boolean changed = part == null || part.model != model || part.layer != layer || part.disableDiffuse != disableDiffuse;
        if (changed) {
            part = new Part(model, layer, disableDiffuse);
            var instancer = context.instancerProvider().instancer(InstanceTypes.TRANSFORMED, MODELS.get(part));
            if (instances[index] == null) instances[index] = instancer.createInstance();
            else instancer.stealInstance(instances[index]);
            parts[index] = part;
        }
        var instance = instances[index];
        if (changed || !instance.pose.equals(transform.last().pose())
                || instance.red != (byte)(color >> 16) || instance.green != (byte)(color >> 8)
                || instance.blue != (byte)color || instance.alpha != (byte)(color >> 24)) {
            instance.setTransform(transform).colorArgb(color).setChanged();
        }
    }

    void finish() {
        for (int i = next; i < instances.length; i++) {
            if (instances[i] != null) {
                instances[i].delete();
                instances[i] = null;
                parts[i] = null;
            }
        }
    }

    void collect(Consumer<Instance> consumer) {
        for (var instance : instances) if (instance != null) consumer.accept(instance);
    }

    void delete() {
        next = 0;
        finish();
    }
}
