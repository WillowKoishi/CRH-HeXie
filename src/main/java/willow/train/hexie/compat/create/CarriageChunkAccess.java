package willow.train.hexie.compat.create;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Terrain reads made while following a carriage may inspect loaded chunks, but never wait for one. */
public final class CarriageChunkAccess {
    private static final ThreadLocal<Level> READING = new ThreadLocal<>();

    private CarriageChunkAccess() {}

    public static boolean independent(Entity entity) {
        return entity instanceof SnapshotCarriageEntity
                || entity instanceof Player player && CarriagePlayerSupport.attachedCarriage(player) != null;
    }

    public static Level enter(Entity entity) {
        Level previous = READING.get();
        if (!entity.level().isClientSide && independent(entity)) READING.set(entity.level());
        return previous;
    }

    public static void exit(Level previous) {
        if (previous == null) READING.remove();
        else READING.set(previous);
    }

    public static boolean reading(Level level) { return READING.get() == level; }
}
