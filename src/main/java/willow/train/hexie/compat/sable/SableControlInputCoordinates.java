package willow.train.hexie.compat.sable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.api.interaction.BlockControlContext;
import willow.train.hexie.interaction.ControlInputTargets;

/** Composes a target's storage position with Sable's current logical plot pose. */
public final class SableControlInputCoordinates implements ControlInputTargets.Coordinates {
    private SableControlInputCoordinates() {}

    public static void register() { ControlInputTargets.registerCoordinates(new SableControlInputCoordinates()); }

    private BlockPos owner(BlockControlContext target) {
        return target.carrier() == null ? target.position() : target.carrier().blockPosition();
    }

    @Override public boolean supports(BlockControlContext target, Player player) {
        return SableInteractionCoordinates.contains(player.level(), owner(target))
                || SableInteractionCoordinates.contains(player.level(), player.getEyePosition());
    }

    @Override public Vec3 toWorld(BlockControlContext target, Player player, Vec3 storage) {
        return SableInteractionCoordinates.logical(player.level(), owner(target)).toWorld(storage);
    }

    @Override public Vec3 toStorage(BlockControlContext target, Player player, Vec3 world) {
        return SableInteractionCoordinates.logical(player.level(), owner(target)).toStorage(world);
    }

    @Override public Vec3 eye(BlockControlContext target, Player player) {
        return SableInteractionCoordinates.logicalWorld(player.level(), player.getEyePosition());
    }

    @Override public boolean supportsBlock(Level level, BlockPos owner) {
        return SableInteractionCoordinates.contains(level, owner);
    }

    @Override public Vec3 blockToWorld(Level level, BlockPos owner, Vec3 storage) {
        return SableInteractionCoordinates.logical(level, owner).toWorld(storage);
    }
}
