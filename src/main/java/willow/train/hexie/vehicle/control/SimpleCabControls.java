package willow.train.hexie.vehicle.control;

import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import willow.train.hexie.api.cab.CabControlHost;
import willow.train.hexie.api.cab.CabNeutralPositions;
import willow.train.hexie.api.cab.CabSurfaceState;
import willow.train.hexie.api.traincontrol.CabTrainRequest;

/** Converts a console's declared directional actions, independently of its physical key bindings. */
public final class SimpleCabControls {
    public static final int FORWARD = 1, BACKWARD = 2, LEFT = 4, RIGHT = 8, APPROACH = 16;
    public static final int ALL = FORWARD | BACKWARD | LEFT | RIGHT | APPROACH;
    private SimpleCabControls() {}

    public record ShuntingReadiness(boolean powerAtZero, boolean reverserCutOut) {
        private static ShuntingReadiness read(CabNeutralPositions positions, CabSurfaceState surface, CabTrainRequest request) {
            var power = positions.powerZero();
            var reverser = positions.reverserCutOut();
            return new ShuntingReadiness(power == null ? request.power() == 0 : power.matches(surface),
                    reverser == null ? request.reverser() == 0 : reverser.matches(surface));
        }

        public boolean ready() { return powerAtZero && reverserCutOut; }
    }

    public static boolean validMask(int mask) { return (mask & ~ALL) == 0; }
    public static void requireValidMask(int mask) {
        if (!validMask(mask)) throw new IllegalArgumentException("Invalid cab keyboard actions");
    }

    /** Compare against the authoritative state at commit time, including on a stationary console. */
    public static void submitAndNotify(@Nullable Player driver, CabModule module, CabModule.State next) {
        boolean wasSimple = module.state().simple();
        module.submit(next);
        if (driver != null) notifyModeChange(driver, wasSimple, next.simple());
    }

    public static void notifyModeChange(Player driver, boolean wasSimple, boolean simple) {
        if (wasSimple != simple)
            driver.displayClientMessage(Component.translatable(simple
                    ? "gui.hexie.cab.switched_to_shunting" : "gui.hexie.cab.switched_to_driving"), true);
    }

    public static void notifyShuntingBlocked(Player driver, ShuntingReadiness readiness) {
        String key;
        if (!readiness.powerAtZero() && !readiness.reverserCutOut()) key = "gui.hexie.cab.shunting_reset_both";
        else if (!readiness.powerAtZero()) key = "gui.hexie.cab.shunting_reset_power";
        else key = "gui.hexie.cab.shunting_cut_out_reverser";
        driver.displayClientMessage(Component.translatable(key), true);
    }

    /** Returns null when the occupied surface does not accept this gesture in its current mode. */
    public static CabModule.State applyBindings(CabControlHost host, BlockState block, CabModule.State previous,
                                                boolean toggleSimple, int mask, double speed) {
        return applyBindings(host, block, previous, toggleSimple, mask, speed, ignored -> {});
    }

    public static CabModule.State applyBindings(CabControlHost host, BlockState block, CabModule.State previous,
                                                boolean toggleSimple, int mask, double speed,
                                                Consumer<ShuntingReadiness> onBlocked) {
        var actions = host.keyboardActions(block, previous.simple());
        if (toggleSimple && !actions.contains(CabControlHost.KeyboardAction.TOGGLE_SIMPLE)) return null;
        int permitted = 0;
        for (var action : actions) permitted |= action.inputMask();
        if ((mask & ~permitted) != 0) return null;
        return apply(previous, toggleSimple, mask, speed, actions.contains(CabControlHost.KeyboardAction.APPROACH),
                () -> ShuntingReadiness.read(host.neutralPositions(block), host.surfaceState(block, previous), previous.request()), onBlocked);
    }

    /** Only explicit toggles change input style; heartbeat packets never change stored handles. */
    public static CabModule.State apply(CabModule.State previous, boolean toggleSimple, int mask, double speed) {
        return apply(previous, toggleSimple, mask, speed, true);
    }

    public static CabModule.State apply(CabModule.State previous, boolean toggleSimple, int mask, double speed,
                                        boolean updateApproach) {
        return apply(previous, toggleSimple, mask, speed, updateApproach,
                () -> ShuntingReadiness.read(CabNeutralPositions.FROM_REQUEST, CabSurfaceState.EMPTY, previous.request()), ignored -> {});
    }

    private static CabModule.State apply(CabModule.State previous, boolean toggleSimple, int mask, double speed,
                                         boolean updateApproach, Supplier<ShuntingReadiness> entryConditions,
                                         Consumer<ShuntingReadiness> onBlocked) {
        requireValidMask(mask);
        if (!Double.isFinite(speed)) throw new IllegalArgumentException("Non-finite cab speed");
        if (toggleSimple && !previous.simple()) {
            var readiness = entryConditions.get();
            if (!readiness.ready()) {
                onBlocked.accept(readiness);
                return null;
            }
        }
        var state = toggleSimple ? previous.withSimple(!previous.simple()) : previous;
        return updateApproach ? state.withApproach((mask & APPROACH) != 0) : state;
    }

    /** Same directional semantics as Create controls, with the own-cab actuator limits. */
    public static CabController.Input effectiveInput(CabModule.State state, int mask, double cabSpeed) {
        requireValidMask(mask);
        if (!Double.isFinite(cabSpeed)) throw new IllegalArgumentException("Non-finite cab speed");
        if (!state.simple()) return state.input();
        int direction = direction(mask);
        boolean opposingMotion = direction * cabSpeed < -1e-5;
        double keyBrake = opposingMotion ? TrainControl.Source.CAB_MANUAL.serviceBrakeLimit() : 0;
        double keyPower = opposingMotion ? 0 : Math.abs(direction) * TrainControl.Source.CAB_MANUAL.powerLimit();
        return new CabController.Input(keyPower, Math.max(keyBrake, state.input().brake()), direction);
    }

    public static int direction(int mask) { return ((mask & FORWARD) != 0 ? 1 : 0) - ((mask & BACKWARD) != 0 ? 1 : 0); }
    public static int steering(int mask) { return ((mask & RIGHT) != 0 ? 1 : 0) - ((mask & LEFT) != 0 ? 1 : 0); }
}
