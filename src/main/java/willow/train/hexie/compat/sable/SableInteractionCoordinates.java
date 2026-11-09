package willow.train.hexie.compat.sable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/** Coordinates of an already identified block, including Sable's storage plots. */
public final class SableInteractionCoordinates {
    private static final Frame WORLD = new Frame() {
        @Override public Vec3 toStorage(Vec3 world) { return world; }
        @Override public Vec3 toWorld(Vec3 storage) { return storage; }
    };

    private SableInteractionCoordinates() {}

    /** A snapshot for one query; both ray endpoints must use this same frame. */
    public interface Frame {
        Vec3 toStorage(Vec3 world);
        Vec3 toWorld(Vec3 storage);

        default Ray ray(Vec3 worldFrom, Vec3 worldTo) {
            return new Ray(toStorage(worldFrom), toStorage(worldTo));
        }
    }

    public record Ray(Vec3 from, Vec3 to) {}

    /** Whether this storage block is owned by an actual plot, including an identity-pose plot. */
    public static boolean contains(Level level, BlockPos owner) {
        return loaded() && Loaded.contains(level, owner);
    }

    /** Whether this storage-space position belongs to a plot; no transform comparison is needed. */
    public static boolean contains(Level level, Vec3 position) {
        return loaded() && Loaded.contains(level, position);
    }

    /** Server validation and other logical queries use the target block's current physics pose. */
    public static Frame logical(Level level, BlockPos owner) {
        return loaded() ? Loaded.logical(level, owner) : WORLD;
    }

    /** Mouse picking and seated console coordinates use the same pose as the rendered plot. */
    public static Frame render(Level level, BlockPos owner, float partialTick) {
        return loaded() ? Loaded.render(level, owner, partialTick) : WORLD;
    }

    /** An entity eye or another known position may itself still be in a storage plot. */
    public static Vec3 logicalWorld(Level level, Vec3 position) {
        return loaded() ? Loaded.logicalWorld(level, position) : position;
    }

    /** Preserve Sable's interpolation for viewers standing on, or riding in, a moving plot. */
    public static Vec3 renderEye(Entity viewer, float partialTick) {
        return loaded() ? Loaded.renderEye(viewer, partialTick) : viewer.getEyePosition(partialTick);
    }

    /** Compare a block-local/storage hit against an eye position in logical world space. */
    public static double logicalDistanceSquared(Level level, Vec3 eye, BlockPos owner, Vec3 storageHit) {
        return logicalWorld(level, eye).distanceToSqr(logical(level, owner).toWorld(storageHit));
    }

    /**
     * Let the native Level.clip project its own ray through intersecting plots using render poses.
     * Its returned hit keeps storage coordinates, including the owner and face, for native packets.
     * Do not apply this to a per-cell clip: that ray has already been projected by Sable.
     */
    public static <T> T withRenderPose(Level level, float partialTick, Supplier<T> query) {
        return loaded() ? Loaded.withRenderPose(level, partialTick, query) : query.get();
    }

    private static boolean loaded() { return ModList.get().isLoaded("sable"); }

    /** Resolve the verified Sable API once, and only after its mod guard succeeds. */
    private static final class Loaded {
        private static final Api API = new Api();

        private static boolean contains(Level level, BlockPos owner) {
            return invoke(API.containingBlock, API.helper, level, owner) != null;
        }

        private static boolean contains(Level level, Vec3 position) {
            return invoke(API.containingPosition, API.helper, level, position) != null;
        }

        private static Frame logical(Level level, BlockPos owner) {
            var subLevel = invoke(API.containingBlock, API.helper, level, owner);
            return subLevel == null ? WORLD : frame(invoke(API.logicalPose, subLevel));
        }

        private static Frame render(Level level, BlockPos owner, float partialTick) {
            var subLevel = invoke(API.containingBlock, API.helper, level, owner);
            return subLevel == null ? WORLD : frame(renderPose(subLevel, partialTick));
        }

        private static Object renderPose(Object subLevel, float partialTick) {
            return API.clientSubLevel.isInstance(subLevel)
                    ? invoke(API.renderPose, subLevel, partialTick) : invoke(API.logicalPose, subLevel);
        }

        private static Frame frame(Object current) {
            final Object pose;
            try {
                pose = API.copyPose.newInstance(current);
            } catch (ReflectiveOperationException failure) {
                throw failed("copy pose", failure);
            }
            return new Frame() {
                @Override public Vec3 toStorage(Vec3 world) { return (Vec3) invoke(API.toStorage, pose, world); }
                @Override public Vec3 toWorld(Vec3 storage) { return (Vec3) invoke(API.toWorld, pose, storage); }
            };
        }

        private static Vec3 logicalWorld(Level level, Vec3 position) {
            var subLevel = invoke(API.containingPosition, API.helper, level, position);
            return subLevel == null ? position
                    : (Vec3) invoke(API.toWorld, invoke(API.logicalPose, subLevel), position);
        }

        private static Vec3 renderEye(Entity viewer, float partialTick) {
            return (Vec3) invoke(API.renderEye, API.helper, viewer, partialTick);
        }

        private static <T> T withRenderPose(Level level, float partialTick, Supplier<T> query) {
            if (!API.poseProvider.isInstance(level)) return query.get();
            it.unimi.dsi.fastutil.Function<Object, Object> supplier = subLevel -> renderPose(subLevel, partialTick);
            invoke(API.pushPose, level, supplier);
            try {
                return query.get();
            } finally {
                invoke(API.popPose, level);
            }
        }

        private static Object invoke(Method method, Object receiver, Object... arguments) {
            try {
                return method.invoke(receiver, arguments);
            } catch (ReflectiveOperationException failure) {
                throw failed(method.getName(), failure);
            }
        }

        private static RuntimeException failed(String operation, ReflectiveOperationException failure) {
            Throwable cause = failure instanceof InvocationTargetException invocation ? invocation.getCause() : failure;
            if (cause instanceof RuntimeException runtime) return runtime;
            if (cause instanceof Error error) throw error;
            return new IllegalStateException("Sable interaction coordinate call failed: " + operation, cause);
        }

        /** Signatures checked against Sable 2.0.6 and its bundled Companion 1.6.0. */
        private static final class Api {
            final Object helper;
            final Class<?> clientSubLevel, poseProvider;
            final Constructor<?> copyPose;
            final Method containingBlock, containingPosition, logicalPose, renderPose;
            final Method toStorage, toWorld, renderEye, pushPose, popPose;

            Api() {
                try {
                    var loader = SableInteractionCoordinates.class.getClassLoader();
                    var sable = Class.forName("dev.ryanhcode.sable.Sable", true, loader);
                    var subLevel = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel", false, loader);
                    var pose = Class.forName("dev.ryanhcode.sable.companion.math.Pose3dc", false, loader);
                    var mutablePose = Class.forName("dev.ryanhcode.sable.companion.math.Pose3d", false, loader);
                    clientSubLevel = Class.forName("dev.ryanhcode.sable.companion.ClientSubLevelAccess", false, loader);
                    poseProvider = Class.forName("dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension", false, loader);
                    helper = sable.getField("HELPER").get(null);
                    containingBlock = helper.getClass().getMethod("getContaining", Level.class, Vec3i.class);
                    containingPosition = helper.getClass().getMethod("getContaining", Level.class, Position.class);
                    logicalPose = subLevel.getMethod("logicalPose");
                    renderPose = clientSubLevel.getMethod("renderPose", float.class);
                    copyPose = mutablePose.getConstructor(pose);
                    toStorage = pose.getMethod("transformPositionInverse", Vec3.class);
                    toWorld = pose.getMethod("transformPosition", Vec3.class);
                    renderEye = helper.getClass().getMethod("getEyePositionInterpolated", Entity.class, float.class);
                    pushPose = poseProvider.getMethod("sable$pushPoseSupplier", it.unimi.dsi.fastutil.Function.class);
                    popPose = poseProvider.getMethod("sable$popPoseSupplier");
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException("Cannot bind Sable's interaction coordinate API", failure);
                }
            }
        }
    }
}
