package willow.train.hexie.api.cab;

import net.minecraft.nbt.CompoundTag;

/** Owns the timetable stored in this console; backend execution never consumes this storage. */
public interface CabTimetableHost {
    // Retain the existing save key, including after assembly.
    String PENDING_TIMETABLE = "PendingTimetable";
    /** Returns a copy; an empty tag means no timetable is stored. */
    CompoundTag timetable();
    /** Replaces the stored timetable; an empty tag removes it. */
    void readTimetable(CompoundTag data);
}
