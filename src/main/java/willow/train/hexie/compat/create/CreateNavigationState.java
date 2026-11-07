package willow.train.hexie.compat.create;

import com.simibubi.create.content.trains.entity.Train;

/** Reason-specific native navigation changes; never alters a timetable or independent cab input. */
public final class CreateNavigationState {
    public enum Reason { TIMETABLE_REPLACED, SCHEDULE_REMOVED, TRACK_END, MANUAL_APPROACH_CANCELLED, MODE_CHANGED }
    private CreateNavigationState() {}

    public static void cancel(Train train, Reason reason) {
        var station = train.getCurrentStation();
        train.navigation.cancelNavigation();
        // Space may be released after range arrival but before docking finishes. Native
        // cancellation must not remove the already-present train from that station.
        if (station != null && train.getCurrentStation() == station) station.reserveFor(train);
        if (reason == Reason.TIMETABLE_REPLACED || reason == Reason.MANUAL_APPROACH_CANCELLED
                || reason == Reason.MODE_CHANGED) clearSignalWait(train);
        if (train instanceof CompatibilityTrain own) {
            own.automaticRouteAvailable = false;
            if (reason == Reason.TIMETABLE_REPLACED || reason == Reason.SCHEDULE_REMOVED) own.clearStationStop();
        }
    }

    public static void clearSignalWait(Train train) {
        train.navigation.waitingForSignal = null;
        train.navigation.ticksWaitingForSignal = 0;
        train.navigation.distanceToSignal = Double.MAX_VALUE;
    }
}
