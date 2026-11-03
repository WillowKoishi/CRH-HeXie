package willow.train.hexie.compat.create;

import java.util.UUID;
import willow.train.hexie.api.cab.CabDisplayState;
import willow.train.hexie.api.cab.DmiState;
import willow.train.hexie.vehicle.control.TrainDisplayReadings;

/** Binds the current compatibility controller to our display projection. Never operates a train. */
final class CreateCabDisplaySource {
    private CreateCabDisplaySource() {}

    static CabDisplayState read(AssembledVehicleMotion motion, UUID member) {
        if (motion == null) return CabDisplayState.speedOnly(0);
        var train = motion.compatibility() == null ? null : motion.compatibility().train(member);
        // Transitional source selection: use the console's authoritative mode when its controls are bound.
        var cabMode = motion.cabMode(member);
        var mode = cabMode != null ? (cabMode == willow.train.hexie.vehicle.control.CabController.Mode.MANUAL
                ? DmiState.Mode.MANUAL : DmiState.Mode.AUTOMATIC)
                : motion.isManuallyControlled(member) ? DmiState.Mode.MANUAL
                : train != null && train.automaticControlActive ? DmiState.Mode.AUTOMATIC : DmiState.Mode.UNKNOWN;
        var route = train == null ? TrainDisplayReadings.Route.unavailable() : train.displayRoute;
        if (train != null) {
            String station = train.navigation.destination == null || CreateWaypointSupport.currentInstruction(train)
                    ? route.station() : train.navigation.destination.name;
            route = route.withAutomaticStop(train.automaticTarget, station, "Create");
        }
        return TrainDisplayReadings.read(motion.runtime(), member, motion.controlSource(member), mode, route,
                train == null || train.runtime.currentTitle == null ? "" : train.runtime.currentTitle);
    }
}
