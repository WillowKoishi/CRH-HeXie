package willow.train.hexie.bridge.vehicle;

import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.HexieBridges;

public interface PlayerCarriageBridge {
    Entity createHexieSeatEntity(EntityType<?> type, Level level);

    default EntityType.EntityFactory<Entity> seatEntityFactory() { return this::createHexieSeatEntity; }

    Entity createHexieSeatEntity(Level world);

    /** Seat implementations can recognize subclasses registered under additional entity types. */
    default boolean isHexieSeat(Entity seat) {
        return seat.getType() == HexieBridges.worldRegistry().entityInit().yzSeatEntity();
    }

    RemotePlayerInterpolationView createRemotePlayerInterpolation(Vec3 position, long sampleTick);

    SupportFrameView createSupportFrame(Vec3 origin, Vec3 x, Vec3 y, Vec3 z);

    interface CarriedPlayerConnectionView {
        boolean hexie$canCarry();
        boolean hexie$acceptCarriageMovement(Vec3 world, Vec3 localMovement, Vec3 worldMovement, float yaw,
                float pitch, boolean onGround);
        void hexie$beginCarriageDismount(int id, UUID uuid);
        long hexie$beginCarriageRestore(int id, UUID uuid);
        void hexie$acknowledgeCarriageRestore(long token);
        void hexie$rememberCarriage(int id, UUID uuid);
        boolean hexie$wasCarriedBy(int id, UUID uuid);
        void hexie$forgetCarriage();
        void hexie$grounded();
    }

    interface RemotePlayerInterpolationView {
        void accept(Vec3 nextPosition, long sampleTick);
        Vec3 tick();
    }

    interface SupportFrameView {
        Vec3 world(Vec3 local);
        Vec3 local(Vec3 world);
        Vec3 rotateHorizontalFrom(SupportFrameView previous, Vec3 velocity);
        Vec3 x();
        Vec3 y();
        Vec3 z();
        Vec3 origin();
    }
}
