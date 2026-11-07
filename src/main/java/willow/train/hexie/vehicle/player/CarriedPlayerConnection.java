package willow.train.hexie.vehicle.player;

import java.util.UUID;
import net.minecraft.world.phys.Vec3;

/** Connection bookkeeping for client-resolved carriage movement. */
public interface CarriedPlayerConnection {
    boolean hexie$canCarry();
    void hexie$grounded();
    boolean hexie$acceptCarriageMovement(Vec3 world, Vec3 localMovement, Vec3 worldMovement,
            float yaw, float pitch, boolean onGround);
    void hexie$beginCarriageDismount(int id, UUID uuid);
    long hexie$beginCarriageRestore(int id, UUID uuid);
    void hexie$acknowledgeCarriageRestore(long token);
    void hexie$rememberCarriage(int id, UUID uuid);
    boolean hexie$wasCarriedBy(int id, UUID uuid);
    void hexie$forgetCarriage();
}
