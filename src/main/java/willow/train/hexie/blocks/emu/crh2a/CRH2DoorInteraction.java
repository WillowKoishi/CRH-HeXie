package willow.train.hexie.blocks.emu.crh2a;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.blockentity.CustomRenderedDoorEntity;
import willow.train.hexie.compat.sable.SableInteractionCoordinates;

/** Validation for the ordinary use-item packet when an opened cab leaf lies in the next cell. */
public final class CRH2DoorInteraction {
    private CRH2DoorInteraction() {}

    public static boolean validExtendedHit(ServerPlayer player, BlockHitResult hit) {
        var level = player.serverLevel();
        BlockPos position = hit.getBlockPos();
        var state = level.getBlockState(position);
        if (!(state.getBlock() instanceof CRH2DoorBlock door) || door.isSlideDoor()) return false;
        BlockPos root = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? position.below() : position;
        if (!level.getBlockState(root).is(door)
                || !(level.getBlockEntity(root) instanceof CustomRenderedDoorEntity entity)) return false;
        var frame = SableInteractionCoordinates.logical(level, root);
        Vec3 worldEye = SableInteractionCoordinates.logicalWorld(level, player.getEyePosition());
        Vec3 eye = frame.toStorage(worldEye);
        Vec3 location = hit.getLocation();
        double distanceSquared = worldEye.distanceToSqr(frame.toWorld(location));
        double reach = player.blockInteractionRange();
        if (distanceSquared > reach * reach) return false;
        Vec3 direction = location.subtract(eye);
        if (direction.lengthSqr() < 1e-12) return false;
        Vec3 target = location.add(direction.normalize().scale(1e-4));
        var shape = door.interactionShape(level.getBlockState(root), entity.crh2InteractionProgress());
        var actual = shape.clip(eye, target, root);
        if (actual == null || actual.getLocation().distanceToSqr(location) > 1e-6) return false;
        var obstruction = level.clip(new ClipContext(eye, location, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, player));
        if (obstruction.getType() != HitResult.Type.MISS
                && !obstruction.getBlockPos().equals(root) && !obstruction.getBlockPos().equals(root.above())
                && SableInteractionCoordinates.logicalDistanceSquared(level, worldEye,
                        obstruction.getBlockPos(), obstruction.getLocation()) + 1e-6 < distanceSquared) return false;
        return true;
    }
}
