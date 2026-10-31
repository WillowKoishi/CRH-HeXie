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
        var mode = motion.isManuallyControlled(member) ? DmiState.Mode.MANUAL
                : motion.hasTestCab(member) || train != null && train.automaticControlActive
                ? DmiState.Mode.AUTOMATIC : DmiState.Mode.UNKNOWN;
        var route = train == null ? TrainDisplayReadings.Route.unavailable() : CreateDmiRouteSource.read(motion, train);
        if (train != null && train.automaticTarget != null) {
            var target = train.automaticTarget;
            String station = train.navigation.destination == null ? "" : train.navigation.destination.name;
            route = new TrainDisplayReadings.Route(new DmiState.Target(target.station()
                    ? DmiState.TargetKind.STATION : DmiState.TargetKind.SIGNAL,
                    target.station() ? station : "前方停车点", Math.max(0, target.distanceM()), 0), station,
                    Double.isFinite(target.speedLimitMps()) ? target.speedLimitMps() * 3.6 : -1,
                    target.station() ? "Create" : "Create / 前方停车");
        }
        return TrainDisplayReadings.read(motion.runtime(), member, motion.controlSource(member), mode, route);
    }
}
