package willow.train.hexie.vehicle.assembly;

import willow.train.hexie.bridge.vehicle.VehicleBridge;

/** A moving structure's selected floor offset, available to rail-height block shapes. */
public interface CarriageFloorView extends VehicleBridge.CarriageFloorViewView {
    double floorOffset();
}
