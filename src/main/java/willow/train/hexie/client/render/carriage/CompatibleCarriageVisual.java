package willow.train.hexie.client.render.carriage;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import com.simibubi.create.content.contraptions.render.ClientContraption;
import dev.engine_room.flywheel.api.instance.*;
import dev.engine_room.flywheel.api.material.Transparency;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.visualization.*;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.RandomSource;
import net.neoforged.fml.ModList;
import org.joml.Matrix3fc;
import org.joml.Matrix4fc;
import org.joml.Vector4fc;
import willow.train.hexie.Main;

/** Reuse Create's baked meshes; only map OIT materials in affected carriage embeddings. */
public final class CompatibleCarriageVisual {
    private CompatibleCarriageVisual() {}
    public static VisualizationContext context(VisualizationContext original, Supplier<ClientContraption> client) {
        if (!ModList.get().isLoaded("sable") || !ModList.get().isLoaded("veil")) return original;
        return new Context(original, new Selection(client));
    }
    private static final class Selection {
        final Supplier<ClientContraption> source;
        ClientContraption previous;
        int version = Integer.MIN_VALUE;
        boolean enabled;
        Selection(Supplier<ClientContraption> source) { this.source = source; }
        synchronized boolean enabled() {
            var client = source.get();
            if (client == null) return false;
            if (client == previous && version == client.structureVersion()) return enabled;
            previous = client; version = client.structureVersion(); enabled = false;
            var blocks = client.getRenderedBlocks();
            var world = client.getRenderLevel();
            var random = RandomSource.create(42);
            for (var pos : blocks.positions()) {
                var state = blocks.lookup().apply(pos);
                if (!Main.MOD_ID.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace())) continue;
                var model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
                var data = model.getModelData(world,pos,state,world.getModelData(pos));
                random.setSeed(state.getSeed(pos));
                if (model.getRenderTypes(state,random,data).contains(RenderType.translucent())) { enabled = true; break; }
            }
            return enabled;
        }
    }
    private record MappedModel(List<Model.ConfiguredMesh> meshes, Vector4fc boundingSphere) implements Model {}
    private static class Context implements VisualizationContext {
        final VisualizationContext delegate;
        final Selection selection;
        // Values retain meshes, not their source Model; old structure versions can be collected.
        final Map<Model,Model> mapped = new WeakHashMap<>();
        final InstancerProvider provider;
        Context(VisualizationContext delegate, Selection selection) {
            this.delegate = delegate; this.selection = selection;
            provider = new InstancerProvider() {
                @Override public <I extends Instance> Instancer<I> instancer(InstanceType<I> type, Model model, int bias) {
                    return delegate.instancerProvider().instancer(type, selection.enabled() ? map(model) : model, bias);
                }
            };
        }
        private synchronized Model map(Model source) {
            return mapped.computeIfAbsent(source, model -> new MappedModel(model.meshes().stream().map(mesh -> {
                var material = mesh.material();
                if (material.transparency() == Transparency.ORDER_INDEPENDENT)
                    material = SimpleMaterial.builderOf(material).transparency(Transparency.TRANSLUCENT).build();
                return new Model.ConfiguredMesh(material, mesh.mesh());
            }).toList(), model.boundingSphere()));
        }
        @Override public InstancerProvider instancerProvider() { return provider; }
        @Override public Vec3i renderOrigin() { return delegate.renderOrigin(); }
        @Override public VisualEmbedding createEmbedding(Vec3i origin) { return new Embedding(delegate.createEmbedding(origin), selection); }
    }
    private static final class Embedding extends Context implements VisualEmbedding {
        private final VisualEmbedding embedding;
        Embedding(VisualEmbedding embedding, Selection selection) { super(embedding, selection); this.embedding = embedding; }
        @Override public void transforms(Matrix4fc pose, Matrix3fc normal) { embedding.transforms(pose,normal); }
        @Override public void delete() { synchronized (this) { mapped.clear(); } embedding.delete(); }
    }
}
