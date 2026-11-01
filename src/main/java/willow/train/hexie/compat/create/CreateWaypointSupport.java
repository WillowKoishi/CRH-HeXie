package willow.train.hexie.compat.create;

import com.simibubi.create.content.trains.entity.Train;
import net.minecraft.resources.ResourceLocation;

/** Optional Steam 'n' Rails instruction, identified by its public registry ID. */
final class CreateWaypointSupport {
    private static final ResourceLocation WAYPOINT = ResourceLocation.fromNamespaceAndPath("railways", "waypoint_destination");
    private CreateWaypointSupport() {}

    static boolean currentInstruction(Train train) {
        var schedule = train.runtime.getSchedule();
        int index = train.runtime.currentEntry;
        return schedule != null && index >= 0 && index < schedule.entries.size()
                && WAYPOINT.equals(schedule.entries.get(index).instruction.getId());
    }

    static boolean active(Train train) {
        return !train.manualTick && !train.runtime.paused && !train.runtime.completed && currentInstruction(train);
    }
}
