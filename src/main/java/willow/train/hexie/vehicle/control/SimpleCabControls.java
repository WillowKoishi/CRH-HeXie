package willow.train.hexie.vehicle.control;

import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.api.cab.CabControlHost;

/** Converts a console's declared directional actions, independently of its physical key bindings. */
public final class SimpleCabControls {
    public static final int FORWARD = 1, BACKWARD = 2, LEFT = 4, RIGHT = 8, APPROACH = 16;
    public static final int ALL = FORWARD | BACKWARD | LEFT | RIGHT | APPROACH;
    private SimpleCabControls() {}

    public static boolean validMask(int mask) { return (mask & ~ALL) == 0; }
    public static void requireValidMask(int mask) {
        if (!validMask(mask)) throw new IllegalArgumentException("Invalid cab keyboard actions");
    }

    /** Returns null when the occupied surface does not accept this gesture in its current mode. */
    public static CabModule.State applyBindings(CabControlHost host, BlockState block, CabModule.State previous,
                                                boolean toggleSimple, int mask, double speed) {
        var actions = host.keyboardActions(block, previous.simple());
        if (toggleSimple && !actions.contains(CabControlHost.KeyboardAction.TOGGLE_SIMPLE)) return null;
        int permitted = 0;
        for (var action : actions) permitted |= action.inputMask();
        if ((mask & ~permitted) != 0) return null;
        return apply(previous, toggleSimple, mask, speed, actions.contains(CabControlHost.KeyboardAction.APPROACH));
    }

    /** Only explicit toggles change input style; heartbeat packets never change stored handles. */
    public static CabModule.State apply(CabModule.State previous, boolean toggleSimple, int mask, double speed) {
        return apply(previous, toggleSimple, mask, speed, true);
    }

    public static CabModule.State apply(CabModule.State previous, boolean toggleSimple, int mask, double speed,
                                        boolean updateApproach) {
        requireValidMask(mask);
        if (!Double.isFinite(speed)) throw new IllegalArgumentException("Non-finite cab speed");
        var state = toggleSimple ? previous.withSimple(!previous.simple()) : previous;
        return updateApproach ? state.withApproach((mask & APPROACH) != 0) : state;
    }

    /** Same directional semantics as Create controls, with the own-cab actuator limits. */
    public static CabController.Input effectiveInput(CabModule.State state, int mask, double cabSpeed) {
        requireValidMask(mask);
        if (!Double.isFinite(cabSpeed)) throw new IllegalArgumentException("Non-finite cab speed");
        if (!state.simple()) return state.input();
        int direction = direction(mask);
        double brake = direction * cabSpeed < -1e-5 ? TrainControl.Source.CAB_MANUAL.serviceBrakeLimit() : 0;
        if (state.brakes().emergencyLatched() || state.brakes().parking()) brake = 1;
        return new CabController.Input(brake > 0 ? 0 : Math.abs(direction) * TrainControl.Source.CAB_MANUAL.powerLimit(), brake, direction);
    }

    public static int direction(int mask) { return ((mask & FORWARD) != 0 ? 1 : 0) - ((mask & BACKWARD) != 0 ? 1 : 0); }
    public static int steering(int mask) { return ((mask & RIGHT) != 0 ? 1 : 0) - ((mask & LEFT) != 0 ? 1 : 0); }
}
