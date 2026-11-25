package willow.train.hexie.compat.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.compat.SableBridge;
import willow.train.hexie.compat.sable.SableInteractionCoordinates;

final class SablePorts implements SableBridge {
    @Override public boolean contains(Level level, BlockPos owner) {
        return SableInteractionCoordinates.contains(level, owner);
    }

    @Override public SableInteractionCoordinatesFrameView logical(Level level, BlockPos owner) {
        return SableInteractionCoordinates.logical(level, owner);
    }

    @Override public SableInteractionCoordinatesFrameView render(Level level, BlockPos owner, float partialTick) {
        return SableInteractionCoordinates.render(level, owner, partialTick);
    }

    @Override public Vec3 logicalWorld(Level level, Vec3 position) {
        return SableInteractionCoordinates.logicalWorld(level, position);
    }

    @Override public Vec3 renderEye(Entity viewer, float partialTick) {
        return SableInteractionCoordinates.renderEye(viewer, partialTick);
    }

    @Override public double logicalDistanceSquared(Level level, Vec3 eye, BlockPos owner, Vec3 storageHit) {
        return SableInteractionCoordinates.logicalDistanceSquared(level, eye, owner, storageHit);
    }
}
