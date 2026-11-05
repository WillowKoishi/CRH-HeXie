package willow.train.hexie.api.cab;

import net.minecraft.world.entity.player.Player;

/** A placed block entity can implement this to operate its surface from an existing seat. */
public interface CabSurfaceInstance {
    /** Backed by this instance's synchronized physical positions and readouts. */
    CabSurfaceState surfaceState();

    /** Runs on the server after the seat, control ID and requested detent have been checked. */
    void operateControl(Player player, CabControl control, int value);
}
