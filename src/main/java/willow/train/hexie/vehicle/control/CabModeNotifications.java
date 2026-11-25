package willow.train.hexie.vehicle.control;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import willow.train.hexie.bridge.vehicle.CabControlBridge;
import willow.train.hexie.bridge.vehicle.CabModuleView;
import willow.train.hexie.core.api.cab.CabState;

// split from willow/train/hexie/vehicle/control/SimpleCabControls.java
final class CabModeNotifications {
    /** Compare against the authoritative state at commit time, including on a stationary console. */
    static void submitAndNotify(@Nullable Player driver, CabModuleView module, CabState next) {
        boolean wasSimple = module.state().simple();
        module.submit(next);
        if (driver != null) notifyModeChange(driver, wasSimple, next.simple());
    }

    static void notifyModeChange(Player driver, boolean wasSimple, boolean simple) {
        if (wasSimple != simple)
            driver.displayClientMessage(Component.translatable(simple
                    ? "gui.hexie.cab.switched_to_shunting" : "gui.hexie.cab.switched_to_driving"), true);
    }

    static void notifyShuntingBlocked(Player driver, CabControlBridge.SimpleCabControlsShuntingReadinessView readiness) {
        String key;
        if (!readiness.powerAtZero() && !readiness.reverserCutOut()) key = "gui.hexie.cab.shunting_reset_both";
        else if (!readiness.powerAtZero()) key = "gui.hexie.cab.shunting_reset_power";
        else key = "gui.hexie.cab.shunting_cut_out_reverser";
        driver.displayClientMessage(Component.translatable(key), true);
    }
}
