package willow.train.hexie.compat.create;

import com.simibubi.create.content.trains.entity.Train;

/** Reason-specific native navigation changes; never alters a timetable or independent cab input. */
public final class CreateNavigationState {
    public enum Reason { TIMETABLE_REPLACED, SCHEDULE_REMOVED, TRACK_END, MANUAL_APPROACH_CANCELLED, MODE_CHANGED }
    private CreateNavigationState() {}

    public static void cancel(Train train, Reason reason) {
        train.navigation.cancelNavigation();
        if (reason == Reason.TIMETABLE_REPLACED || reason == Reason.MANUAL_APPROACH_CANCELLED
                || reason == Reason.MODE_CHANGED) clearSignalWait(train);
        if (train instanceof CompatibilityTrain own) own.automaticTarget = null;
    }

    public static void clearSignalWait(Train train) {
        train.navigation.waitingForSignal = null;
        train.navigation.ticksWaitingForSignal = 0;
        train.navigation.distanceToSignal = Double.MAX_VALUE;
    }
}
