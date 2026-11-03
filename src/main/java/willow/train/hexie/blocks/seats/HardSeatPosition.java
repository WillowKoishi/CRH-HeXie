package willow.train.hexie.blocks.seats;

import java.util.Set;
import net.minecraft.world.entity.Entity;

/** Persisted seat metadata shared by native boarding, observer sync and login restoration. */
public final class HardSeatPosition {
    private HardSeatPosition() {}

    public static String tag(int seat, int position) {
        if (seat < 0 || position < 0 || position >= 4) throw new IllegalArgumentException("Invalid hard-seat position");
        return "seats" + seat + "," + position;
    }

    public static int read(Set<String> tags, int seat) {
        if (seat < 0) return -1;
        for (int position = 0; position < 4; position++)
            if (tags.contains(tag(seat, position))) return position;
        return -1;
    }

    public static void clear(Entity passenger) {
        for (var tag : Set.copyOf(passenger.getTags()))
            if (tag.startsWith("seats")) passenger.removeTag(tag);
    }
}
