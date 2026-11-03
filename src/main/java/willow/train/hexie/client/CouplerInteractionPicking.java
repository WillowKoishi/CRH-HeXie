package willow.train.hexie.client;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.blockentity.CouplerBlockEntity;
import willow.train.hexie.blocks.coupler.CouplerInteraction;
import willow.train.hexie.compat.create.CarriagePlayerSupport;
import willow.train.hexie.compat.create.SnapshotCouplerModelInteraction;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;

/** Finds the authored surfaces outside the mounting cell, including independently swinging heads. */
public final class CouplerInteractionPicking {
    private CouplerInteractionPicking() {}

    public static HitResult world(Entity viewer, double reach, float partial, boolean fluids, HitResult nativeHit) {
        Vec3 origin = viewer.getEyePosition(partial);
        Vec3 target = origin.add(viewer.getViewVector(partial).scale(reach));
        if (nativeHit instanceof BlockHitResult blockHit && nativeHit.getType() != HitResult.Type.MISS
                && CouplerInteraction.hasGeometry(viewer.level().getBlockState(blockHit.getBlockPos()))) {
            // An AABB/owner-cell hit may lie in the empty space between actual model surfaces.
            nativeHit = viewer.level().clip(new ClipContext(origin, target, ClipContext.Block.OUTLINE,
                    fluids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE, viewer) {
                @Override public VoxelShape getBlockShape(BlockState state, BlockGetter level, BlockPos position) {
                    return CouplerInteraction.hasGeometry(state) ? Shapes.empty()
                            : super.getBlockShape(state, level, position);
                }
            });
        }
        double closest = nativeHit.getType() == HitResult.Type.MISS ? reach * reach
                : nativeHit.getLocation().distanceToSqr(origin);
        if (nativeHit.getType() != HitResult.Type.MISS) target = nativeHit.getLocation();
        HitResult result = nativeHit;
        AABB search = new AABB(origin, target).inflate(3);
        for (BlockPos position : BlockPos.betweenClosed(BlockPos.containing(search.minX, search.minY, search.minZ),
                BlockPos.containing(search.maxX, search.maxY, search.maxZ))) {
            if (!viewer.level().hasChunkAt(position)) continue;
            var state = viewer.level().getBlockState(position);
            if (!CouplerInteraction.hasGeometry(state)) continue;
            float progress = viewer.level().getBlockEntity(position) instanceof CouplerBlockEntity coupler
                    ? coupler.coverProgress(partial) : CouplerInteraction.coverProgress(state);
            var hit = CouplerInteraction.clipWorld(state, position, progress, origin, target);
            if (hit == null || hit.point().distanceToSqr(origin) >= closest) continue;
            closest = hit.point().distanceToSqr(origin);
            result = new BlockHitResult(hit.point(), hit.face(), position.immutable(), false);
        }
        return result;
    }

    public static BlockHitResult contraption(AbstractContraptionEntity entity, Vec3 origin, Vec3 target,
            BlockHitResult nativeHit) {
        var contraption = entity.getContraption();
        if (contraption == null) return nativeHit;
        float partial = CarriageInteractionCoordinates.partialTick();
        Vec3 from, to;
        if (entity instanceof SnapshotCarriageEntity carriage) {
            var frame = CarriagePlayerSupport.frame(carriage, partial);
            from = frame.local(origin); to = frame.local(target);
        } else {
            from = entity.toLocalVector(origin, 1); to = entity.toLocalVector(target, 1);
        }
        if (nativeHit != null && contraption.getBlocks().get(nativeHit.getBlockPos()) != null
                && CouplerInteraction.hasGeometry(contraption.getBlocks().get(nativeHit.getBlockPos()).state())) {
            nativeHit = null;
            double closest = from.distanceToSqr(to);
            for (var info : contraption.getBlocks().values()) {
                var position = info.pos();
                var state = info.state();
                if (CouplerInteraction.hasGeometry(state) || contraption.isHiddenInPortal(position)) continue;
                var obstruction = DoorInteractionPicking.blockHit(contraption, position, from, to);
                if (obstruction == null || obstruction.getLocation().distanceToSqr(from) >= closest) continue;
                closest = obstruction.getLocation().distanceToSqr(from);
                nativeHit = obstruction;
            }
        }
        double closest = nativeHit == null ? from.distanceToSqr(to) : nativeHit.getLocation().distanceToSqr(from);
        BlockHitResult result = nativeHit;
        for (var info : contraption.getBlocks().values()) {
            var position = info.pos();
            var state = info.state();
            if (!CouplerInteraction.hasGeometry(state) || contraption.isHiddenInPortal(position)) continue;
            float progress = contraption.getBlockEntityClientSide(position) instanceof CouplerBlockEntity be
                    ? be.coverProgress(partial) : CouplerInteraction.coverProgress(state);
            var hit = entity instanceof SnapshotCarriageEntity carriage
                    ? SnapshotCouplerModelInteraction.clip(carriage, position, state, progress, partial, from, to)
                    : CouplerInteraction.clipWorld(state, position, progress, from, to);
            if (hit == null || hit.point().distanceToSqr(from) >= closest) continue;
            closest = hit.point().distanceToSqr(from);
            result = new BlockHitResult(hit.point(), hit.face(), position, false);
        }
        return result;
    }
}
