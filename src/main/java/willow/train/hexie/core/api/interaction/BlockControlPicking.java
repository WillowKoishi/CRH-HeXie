package willow.train.hexie.core.api.interaction;

import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Picks a host's declared surface in its storage coordinates. */
public final class BlockControlPicking {
    private BlockControlPicking() {}

    @FunctionalInterface
    public interface Picker {
        BlockControlHit pick(BlockControlHost host, BlockControlContext target, Vec3 from, Vec3 to);
    }
    private static Picker clientPicker;

    /** Installs the shared implementation once during client setup, independently of block registration. */
    public static void registerClientPicker(Picker picker) {
        clientPicker = java.util.Objects.requireNonNull(picker);
    }

    /** Default host selection from its declared surface. Existing host overrides keep their own native routing. */
    public static BlockControlHit pickDeclared(BlockControlHost host, BlockControlContext target, Vec3 from, Vec3 to) {
        boolean client = target.carrier() != null ? target.carrier().level().isClientSide
                : target.blocks() instanceof Level level && level.isClientSide;
        if (client && clientPicker != null) return clientPicker.pick(host, target, from, to);
        Vec3 offset = Vec3.atLowerCornerOf(target.position()), ray = to.subtract(from);
        var hit = host.surfaceGeometry(target.state()).pick(host.surfaceFacing(target.state()), from.subtract(offset), ray,
                ray.length(), host.surfaceState(target), host.animation(target));
        if (hit == null) return null;
        var face = Direction.getNearest(-ray.x, -ray.y, -ray.z);
        return new BlockControlHit(hit.control(), new BlockHitResult(hit.point().add(offset), face, target.position(), false));
    }
}
