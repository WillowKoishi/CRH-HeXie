package willow.train.hexie.bridge.interaction;

import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.core.api.interaction.BlockControlContext;

/** Coordinate frames and input providers shared by surface picking and control dispatch. */
public interface SurfaceInputBridge {
    SurfaceInputSourcesOperations surfaceInputSources();

    interface SurfaceInputSourcesOperations {
        void registerFrame(Function<BlockControlContext, SurfaceInputSourcesFrameView> provider);
        void registerBlocker(BooleanSupplier blocker);
        void registerEye(BiFunction<Player, Float, Vec3> provider);
        void registerEyeOffset(BiFunction<Player, Float, Vec3> provider);
        float partialTick();
        Set<BlockPos> cells(Vec3 from, Vec3 to);
    }

    /** Apply the registered render frame for this world or carrier. */
    default SurfaceInputSourcesRayView localRay(BlockControlContext target, Vec3 from, Vec3 to) {
        return createSurfaceInputSourcesRay(from, to);
    }

    /** Clip captured owners (including occluders) with the carrier-local ray already clipped to terrain. */
    default BlockHitResult pickCarriageBlock(BlockGetter blocks, Iterable<BlockPos> owners, SurfaceInputSourcesRayView ray) {
        return null;
    }

    /** Hit the outward-facing visible mesh in storage coordinates. */
    default BlockHitResult pickSurface(BlockControlContext target, SurfaceInputSourcesRayView localRay) {
        return null;
    }

    SurfaceInputSourcesRayView createSurfaceInputSourcesRay(Vec3 from, Vec3 to);

    interface SurfaceInputSourcesFrameView {
        Vec3 toWorld(Vec3 point);
        SurfaceInputSourcesRayView toLocal(Vec3 from, Vec3 to);
    }

    interface SurfaceInputSourcesQueryView {
        void collectWorld(Vec3 storageFrom, Vec3 storageTo);
        void add(BlockControlContext candidate, BlockHitResult hit);
        void obstruct(int carrierId, BlockPos owner, Vec3 worldPoint);
    }

    interface SurfaceInputSourcesRayView {
        Vec3 from();
        Vec3 to();
    }
}
