package willow.train.hexie.bridge.vehicle;

import java.util.function.Consumer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.core.api.cab.CabControlHost;
import willow.train.hexie.core.api.cab.CabState;

public interface SimpleCabControlsView {
    int FORWARD = 1;
    int BACKWARD = 2;
    int LEFT = 4;
    int RIGHT = 8;
    int APPROACH = 16;
    boolean validMask(int mask);
    void submitAndNotify(Player driver, CabModuleView module, CabState next);
    void notifyShuntingBlocked(Player driver, CabControlBridge.SimpleCabControlsShuntingReadinessView readiness);
    CabState applyBindings(CabControlHost host, BlockState block, CabState previous, boolean toggleSimple,
            int mask, double speed, Consumer<CabControlBridge.SimpleCabControlsShuntingReadinessView> onBlocked);
    int steering(int mask);
}
