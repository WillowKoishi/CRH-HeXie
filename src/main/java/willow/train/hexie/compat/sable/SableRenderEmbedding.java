package willow.train.hexie.compat.sable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import dev.engine_room.flywheel.api.visualization.VisualEmbedding;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.joml.Vector3dc;

/** Plot embeddings for effects, which Sable's block-entity visual integration does not wrap. */
public final class SableRenderEmbedding {
    private SableRenderEmbedding() {}

    /** Resolve an owner once when indexing it, rather than searching for its plot every frame. */
    public static @Nullable Handle find(Level level, BlockPos owner) {
        if (!ModList.get().isLoaded("sable")) return null;
        var api = Loaded.API;
        try {
            Object subLevel = (Object) api.containing.invokeExact(level, (Vec3i) owner);
            if (!api.clientSubLevel.isInstance(subLevel) || (boolean) api.removed.invokeExact(subLevel)) return null;
            Object container = (Object) api.container.invokeExact(level);
            Object plot = (Object) api.plot.invokeExact(subLevel);
            BlockPos origin = (BlockPos) api.centerBlock.invokeExact(plot);
            ChunkPos center = (ChunkPos) api.centerChunk.invokeExact(plot);
            return new Handle(subLevel, container, origin.immutable(), center.getMinBlockX(), center.getMinBlockZ());
        } catch (Throwable failure) {
            throw failed("find plot", failure);
        }
    }

    /** Identity is the plot instance, so all its block owners can share one effect and embedding. */
    public static final class Handle {
        private final Object subLevel;
        private final Object container;
        private final BlockPos origin;
        private final int centerX;
        private final int centerZ;

        private Handle(Object subLevel, Object container, BlockPos origin, int centerX, int centerZ) {
            this.subLevel = subLevel;
            this.container = container;
            this.origin = origin;
            this.centerX = centerX;
            this.centerZ = centerZ;
        }

        public BlockPos origin() { return origin; }

        public boolean removed() {
            try {
                return (boolean) Loaded.API.removed.invokeExact(subLevel);
            } catch (Throwable failure) {
                throw failed("check plot lifetime", failure);
            }
        }

        /** The caller owns this binding and deletes it after deleting its instances. */
        public Binding create(VisualizationContext parent, float partialTick) {
            return new Binding(this, parent, partialTick);
        }

        @Override public boolean equals(Object other) {
            return other instanceof Handle handle && subLevel == handle.subLevel;
        }

        @Override public int hashCode() { return System.identityHashCode(subLevel); }
    }

    /** One mutable transform per plot; updates allocate no matrices, vectors or reflection arguments. */
    public static final class Binding {
        private final Handle handle;
        private final VisualizationContext parent;
        private final VisualEmbedding embedding;
        private final Matrix4f transform = new Matrix4f();
        private final Matrix3f normal = new Matrix3f();
        private final Quaternionf orientation = new Quaternionf();
        private final Matrix4f lightingScene = new Matrix4f();
        private final int lightingSceneId;
        private boolean deleted;

        private Binding(Handle handle, VisualizationContext parent, float partialTick) {
            this.handle = handle;
            this.parent = parent;
            try {
                // Sable keeps this ID for the lifetime of the plot and releases it on removal.
                // Effects can be queued before the plot is removed and created afterwards.
                // Do not allocate a new scene for a plot whose previous scene was already freed.
                lightingSceneId = handle.removed() ? 0
                        : (int) Loaded.API.lightingSceneId.invokeExact(handle.container, handle.subLevel);
            } catch (Throwable failure) {
                throw failed("resolve lighting scene", failure);
            }
            embedding = parent.createEmbedding(handle.origin);
            try {
                if (!Loaded.API.lightingExtension.isInstance(embedding))
                    throw new IllegalStateException("Sable's Flywheel lighting extension is missing from the plot embedding");
                Vec3i localOrigin = embedding.renderOrigin();
                lightingScene.translation(localOrigin.getX() - handle.centerX, localOrigin.getY(),
                        localOrigin.getZ() - handle.centerZ);
                update(partialTick);
            } catch (RuntimeException | Error failure) {
                delete();
                throw failure;
            }
        }

        public VisualEmbedding embedding() { return embedding; }

        /** Returns false when the plot or this binding is gone; the caller can remove its instances. */
        public boolean update(float partialTick) {
            if (deleted || handle.removed()) return false;
            var api = Loaded.API;
            try {
                Object pose = (Object) api.renderPose.invokeExact(handle.subLevel, partialTick);
                Vector3dc position = (Vector3dc) api.position.invokeExact(pose);
                Vector3dc rotationPoint = (Vector3dc) api.rotationPoint.invokeExact(pose);
                Quaterniondc rotation = (Quaterniondc) api.orientation.invokeExact(pose);
                // The parent's origin can change when Flywheel rebases the world renderer.
                Vec3i parentOrigin = parent.renderOrigin();
                Vec3i localOrigin = embedding.renderOrigin();
                transform.translation((float) (position.x() - parentOrigin.getX()),
                        (float) (position.y() - parentOrigin.getY()), (float) (position.z() - parentOrigin.getZ()));
                transform.rotate(orientation.set(rotation));
                transform.translate((float) (localOrigin.getX() - rotationPoint.x()),
                        (float) (localOrigin.getY() - rotationPoint.y()), (float) (localOrigin.getZ() - rotationPoint.z()));
                transform.normal(normal);
                embedding.transforms(transform, normal);
                int skyLight = (int) api.skyLight.invokeExact(handle.subLevel);
                api.setLighting.invokeExact((Object) embedding, (Matrix4fc) lightingScene, lightingSceneId, skyLight / 15.0f);
                return true;
            } catch (Throwable failure) {
                throw failed("update plot embedding", failure);
            }
        }

        public void delete() {
            if (deleted) return;
            deleted = true;
            embedding.delete();
        }
    }

    private static RuntimeException failed(String operation, Throwable failure) {
        if (failure instanceof Error error) throw error;
        return new IllegalStateException("Sable render embedding failed: " + operation, failure);
    }

    /** No Sable types are linked until the mod guard succeeds. */
    private static final class Loaded {
        private static final Api API = new Api();
    }

    /** Public signatures verified against Sable 2.0.6 and Companion 1.6.0. */
    private static final class Api {
        final Class<?> clientSubLevel;
        final Class<?> lightingExtension;
        final MethodHandle containing, container, plot, centerBlock, centerChunk, removed;
        final MethodHandle renderPose, position, rotationPoint, orientation, skyLight, lightingSceneId, setLighting;

        Api() {
            try {
                var loader = SableRenderEmbedding.class.getClassLoader();
                var lookup = MethodHandles.publicLookup();
                var sable = Class.forName("dev.ryanhcode.sable.Sable", true, loader);
                var subLevel = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel", false, loader);
                clientSubLevel = Class.forName("dev.ryanhcode.sable.sublevel.ClientSubLevel", false, loader);
                var containerType = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer", false, loader);
                var clientContainer = Class.forName("dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer", false, loader);
                var plotType = Class.forName("dev.ryanhcode.sable.sublevel.plot.LevelPlot", false, loader);
                var pose = Class.forName("dev.ryanhcode.sable.companion.math.Pose3dc", false, loader);
                lightingExtension = Class.forName("dev.ryanhcode.sable.neoforge.mixinterface.compatibility.flywheel.EmbeddedEnvironmentExtension", false, loader);
                Object helper = sable.getField("HELPER").get(null);
                containing = lookup.unreflect(helper.getClass().getMethod("getContaining", Level.class, Vec3i.class))
                        .bindTo(helper).asType(MethodType.methodType(Object.class, Level.class, Vec3i.class));
                container = lookup.unreflect(containerType.getMethod("getContainer", Level.class))
                        .asType(MethodType.methodType(Object.class, Level.class));
                plot = getter(lookup, subLevel, "getPlot", Object.class);
                centerBlock = getter(lookup, plotType, "getCenterBlock", BlockPos.class);
                centerChunk = getter(lookup, plotType, "getCenterChunk", ChunkPos.class);
                removed = getter(lookup, subLevel, "isRemoved", boolean.class);
                renderPose = lookup.unreflect(clientSubLevel.getMethod("renderPose", float.class))
                        .asType(MethodType.methodType(Object.class, Object.class, float.class));
                position = getter(lookup, pose, "position", Vector3dc.class);
                rotationPoint = getter(lookup, pose, "rotationPoint", Vector3dc.class);
                orientation = getter(lookup, pose, "orientation", Quaterniondc.class);
                skyLight = getter(lookup, clientSubLevel, "getLatestSkyLightScale", int.class);
                lightingSceneId = lookup.unreflect(clientContainer.getMethod("getLightingSceneId", clientSubLevel))
                        .asType(MethodType.methodType(int.class, Object.class, Object.class));
                setLighting = lookup.unreflect(lightingExtension.getMethod("sable$setLightingInfo", Matrix4fc.class, int.class, float.class))
                        .asType(MethodType.methodType(void.class, Object.class, Matrix4fc.class, int.class, float.class));
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot bind Sable's render embedding API", failure);
            }
        }

        private static MethodHandle getter(MethodHandles.Lookup lookup, Class<?> owner, String name, Class<?> result)
                throws ReflectiveOperationException {
            return lookup.unreflect(owner.getMethod(name)).asType(MethodType.methodType(result, Object.class));
        }
    }
}
