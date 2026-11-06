package willow.train.hexie.vehicle.player;

import java.util.UUID;
import net.minecraft.world.phys.Vec3;

/** Adjust only the platform contribution to vanilla movement validation. */
public interface CarriedPlayerConnection {
    boolean hexie$canCarry();
    boolean hexie$isCorrectingOn(UUID carriage);
    void hexie$carry(Vec3 displacement);
    void hexie$grounded();
}
