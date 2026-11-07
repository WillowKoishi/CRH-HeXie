package willow.train.hexie.compat.create;

import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
import com.simibubi.create.content.trains.schedule.destination.ScheduleInstruction;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;
import willow.train.hexie.api.display.PassengerDisplayState;

/** Read-only current title and schedule endpoints from the existing uniquely enabled cab selection. */
public final class CreatePassengerDisplaySource {
    private static final ResourceLocation WAYPOINT =
            ResourceLocation.fromNamespaceAndPath("railways", "waypoint_destination");

    private CreatePassengerDisplaySource() {}

    /** The server publishes this projection; clients do not reconstruct timetable execution. */
    public static PassengerDisplayState read(CarriageContraptionEntity entity) {
        if (entity == null || entity.level().isClientSide) return empty();
        var carriage = entity.getCarriage();
        if (carriage == null || carriage.train == null) return empty();
        var train = carriage.train;
        AssembledVehicleMotion motion = null;
        if (train instanceof CompatibilityTrain compatibility) {
            motion = entity instanceof SnapshotCarriageEntity snapshot ? snapshot.motion()
                    : compatibility.bridge == null ? null : compatibility.bridge.motion();
        }
        return read(train, motion, entity.getUUID());
    }

    static PassengerDisplayState read(Train train, AssembledVehicleMotion motion, UUID member) {
        String name = train == null || train.name == null ? "" : train.name.getString();
        var selected = train instanceof CompatibilityTrain && motion != null
                ? motion.selectedCabTimetable(member) : null;
        // No enabled cab, conflicting cabs, and an enabled cab without a schedule all show
        // only the train's current name. Never borrow an inactive cab or background runtime.
        return project(selected == null ? null : selected.runtime, name);
    }

    /** Current title follows the selected cab; endpoints retain their authored schedule order. */
    static PassengerDisplayState project(ScheduleRuntime runtime, String trainName) {
        var schedule = runtime == null ? null : runtime.getSchedule();
        if (schedule == null) return new PassengerDisplayState(trainName, "", "", "");
        String origin = "", destination = "";
        boolean stopFound = false;
        for (var entry : schedule.entries) {
            var instruction = entry.instruction;
            if (!isStop(instruction)) continue;
            String station = ((DestinationInstruction) instruction).getFilter();
            if (!stopFound) { origin = station; stopFound = true; }
            destination = station;
        }
        var result = new PassengerDisplayState(runtime.currentTitle, origin, destination, "");
        return result.trainNumber().isEmpty()
                ? new PassengerDisplayState(trainName, result.origin(), result.destination(), "") : result;
    }

    private static boolean isStop(ScheduleInstruction instruction) {
        return instruction instanceof DestinationInstruction && !WAYPOINT.equals(instruction.getId());
    }

    private static PassengerDisplayState empty() {
        return new PassengerDisplayState("", "", "", "");
    }
}
