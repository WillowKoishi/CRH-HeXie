package willow.train.hexie.api.cab;

import willow.train.hexie.api.interaction.ControlState;

import willow.train.hexie.api.interaction.BlockControl;

import net.minecraft.world.entity.player.Player;

/** A placed block entity can implement this to operate its surface from an existing seat. */
public interface CabSurfaceInstance extends willow.train.hexie.api.interaction.BlockControlInstance {
    /** Backed by this instance's synchronized physical positions and readouts. */
    ControlState surfaceState();

    /** Runs on the server after the seat, control ID and requested detent have been checked. */
    void operateControl(Player player, BlockControl control, int value);
}
