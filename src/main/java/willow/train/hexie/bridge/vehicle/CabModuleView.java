package willow.train.hexie.bridge.vehicle;

import net.minecraft.nbt.CompoundTag;
import willow.train.hexie.core.api.cab.CabModuleAccess;
import willow.train.hexie.core.api.cab.CabState;
import willow.train.hexie.core.api.traincontrol.CabTrainControl;
import willow.train.hexie.core.api.traincontrol.TrainCommand;

public interface CabModuleView extends CabModuleAccess {
    CabState state();
    CabState.Mode mode();
    void submit(CabState input);
    void keyboard(int mask);
    int keyboardMask();
    CabState.Input effectiveInput(double cabSpeed);
    void releaseApproach();
    TrainCommand update(CabState.Mode mode, int direction, double speed,
            CabControlBridge.AutomaticTrainControlTargetView target, double serviceDeceleration);
    TrainCommand update(CabState.Mode mode, int direction, double speed,
            CabControlBridge.AutomaticTrainControlTargetView target, double serviceDeceleration,
            CabControlBridge.StationStopControlStateView stationStop);
    TrainCommand updateSimple(boolean approachingStation, int direction, double speed,
            CabControlBridge.AutomaticTrainControlTargetView target, double serviceDeceleration,
            CabControlBridge.StationStopControlStateView stationStop);
    TrainCommand convertOutput(CabTrainControl desk, TrainCommand controlled, int direction, double speed);
    TrainCommand convertOutput(CabTrainControl desk, TrainCommand controlled, int direction, double speed,
            boolean selected, boolean routeControl);
    CompoundTag write();
}
