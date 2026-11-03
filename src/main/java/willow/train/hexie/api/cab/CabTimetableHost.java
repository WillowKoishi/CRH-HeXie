package willow.train.hexie.api.cab;

import net.minecraft.nbt.CompoundTag;

/** Stores opaque backend timetable data; the console never interprets its contents. */
public interface CabTimetableHost {
    String PENDING_TIMETABLE = "PendingTimetable";
    /** Returns a copy; an empty tag means no timetable is stored. */
    CompoundTag timetable();
    /** Replaces the stored timetable; an empty tag removes it. */
    void readTimetable(CompoundTag data);
}
