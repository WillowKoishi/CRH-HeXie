package willow.train.hexie.client;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.blockentity.CustomRenderedDoorEntity;
import willow.train.hexie.blocks.locos.crh2a.CRH2DoorBlock;
import willow.train.hexie.compat.create.CarriagePlayerSupport;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;

/** Supplements native cell traversal for door leaves that swing outside their installation cells. */
public final class DoorInteractionPicking {
    private DoorInteractionPicking() {}

    public static HitResult world(Entity viewer, double reach, float partial, HitResult nativeHit) {
        Vec3 origin = viewer.getEyePosition(partial);
        Vec3 target = origin.add(viewer.getViewVector(partial).scale(reach));
        double closest = nativeHit.getType() == HitResult.Type.MISS ? reach * reach
                : nativeHit.getLocation().distanceToSqr(origin);
        if (nativeHit.getType() != HitResult.Type.MISS) target = nativeHit.getLocation();
        HitResult result = nativeHit;
        // Cab swing extends less than one cell laterally/deeply and two cells upward.
        // Only loaded blocks are inspected; the world itself remains unchanged.
        AABB search = new AABB(origin, target).inflate(2);
        for (BlockPos candidate : BlockPos.betweenClosed(BlockPos.containing(search.minX, search.minY, search.minZ),
                BlockPos.containing(search.maxX, search.maxY, search.maxZ))) {
            if (!viewer.level().hasChunkAt(candidate)) continue;
            BlockState state = viewer.level().getBlockState(candidate);
            if (!(state.getBlock() instanceof CRH2DoorBlock door)
                    || state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) continue;
            float progress = viewer.level().getBlockEntity(candidate) instanceof CustomRenderedDoorEntity entity
                    ? entity.crh2InteractionProgress() : (state.getValue(DoorBlock.OPEN) ? 1 : 0);
            BlockHitResult hit = door.interactionShape(state, progress).clip(origin, target, candidate);
            if (hit == null) continue;
            double distance = hit.getLocation().distanceToSqr(origin);
            if (distance >= closest) continue;
            closest = distance;
            result = hit.withPosition(candidate.above(hit.getLocation().y - candidate.getY() >= 1 ? 1 : 0));
        }
        return result;
    }

    public static BlockHitResult contraption(AbstractContraptionEntity entity, Vec3 origin, Vec3 target,
                                             BlockHitResult nativeHit) {
        var contraption = entity.getContraption();
        if (contraption == null) return nativeHit;
        float partial = CarriageInteractionCoordinates.partialTick();
        Vec3 localOrigin, localTarget;
        if (entity instanceof SnapshotCarriageEntity carriage) {
            var frame = CarriagePlayerSupport.frame(carriage, partial);
            localOrigin = frame.local(origin);
            localTarget = frame.local(target);
        } else {
            localOrigin = entity.toLocalVector(origin, 1);
            localTarget = entity.toLocalVector(target, 1);
        }
        // Create's native lookup passes BlockPos.ZERO.below() to getShape, so a door
        // can be picked at its requested OPEN endpoint before the transition finishes.
        // If that happened, recover the nearest ordinary obstruction behind it first.
        if (nativeHit != null && contraption.getBlocks().get(nativeHit.getBlockPos()) != null
                && contraption.getBlocks().get(nativeHit.getBlockPos()).state().getBlock() instanceof CRH2DoorBlock) {
            nativeHit = null;
            double obstructionDistance = localOrigin.distanceToSqr(localTarget);
            for (var entry : contraption.getBlocks().entrySet()) {
                var position = entry.getKey();
                var state = entry.getValue().state();
                if (state.getBlock() instanceof CRH2DoorBlock || contraption.isHiddenInPortal(position)) continue;
                var obstruction = state.getShape(contraption.getContraptionWorld(), BlockPos.ZERO.below())
                        .clip(localOrigin, localTarget, position);
                if (obstruction == null) continue;
                double distance = obstruction.getLocation().distanceToSqr(localOrigin);
                if (distance >= obstructionDistance) continue;
                obstructionDistance = distance;
                nativeHit = obstruction;
            }
        }
        double closest = nativeHit == null ? localOrigin.distanceToSqr(localTarget)
                : nativeHit.getLocation().distanceToSqr(localOrigin);
        BlockHitResult result = nativeHit;
        for (var entry : contraption.getBlocks().entrySet()) {
            var position = entry.getKey();
            var state = entry.getValue().state();
            if (!(state.getBlock() instanceof CRH2DoorBlock door)
                    || state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER
                    || contraption.isHiddenInPortal(position)) continue;
            var hit = blockHit(contraption, position, localOrigin, localTarget);
            if (hit == null) continue;
            double distance = hit.getLocation().distanceToSqr(localOrigin);
            if (distance >= closest) continue;
            closest = distance;
            result = hit;
        }
        return result;
    }

    /** Queries one visible block, retaining the door leaf's current animation and owner half. */
    public static BlockHitResult blockHit(Contraption contraption, BlockPos position, Vec3 from, Vec3 to) {
        var info = contraption.getBlocks().get(position);
        if (info == null || contraption.isHiddenInPortal(position)) return null;
        var state = info.state();
        if (!(state.getBlock() instanceof CRH2DoorBlock door))
            return state.getShape(contraption.getContraptionWorld(), BlockPos.ZERO.below()).clip(from, to, position);
        if (state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) return null;
        float progress = contraption.getBlockEntityClientSide(position) instanceof CustomRenderedDoorEntity entity
                ? entity.crh2InteractionProgress() : (state.getValue(DoorBlock.OPEN) ? 1 : 0);
        var hit = door.interactionShape(state, progress).clip(from, to, position);
        return hit == null ? null : hit.withPosition(position.above(hit.getLocation().y - position.getY() >= 1 ? 1 : 0));
    }
}
