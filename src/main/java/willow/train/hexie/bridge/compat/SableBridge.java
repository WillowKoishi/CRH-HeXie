package willow.train.hexie.bridge.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public interface SableBridge {
    boolean contains(Level level, BlockPos owner);

    SableInteractionCoordinatesFrameView logical(Level level, BlockPos owner);

    SableInteractionCoordinatesFrameView render(Level level, BlockPos owner, float partialTick);

    Vec3 logicalWorld(Level level, Vec3 position);

    Vec3 renderEye(Entity viewer, float partialTick);

    double logicalDistanceSquared(Level level, Vec3 eye, BlockPos owner, Vec3 storageHit);

    interface SableInteractionCoordinatesFrameView {
        Vec3 toStorage(Vec3 world);
        Vec3 toWorld(Vec3 storage);
        SableInteractionCoordinatesRayView ray(Vec3 worldFrom, Vec3 worldTo);
    }

    interface SableInteractionCoordinatesRayView {
        Vec3 from();
        Vec3 to();
    }
}
