package willow.train.hexie.blocks.seats;

import net.minecraft.world.entity.Entity;

/** Temporary passenger tag used to carry a selected seat through Create's seat API. */
public final class HardSeatPositionRequest {
    private static final String PREFIX = "hexie_yz_seat_target_";

    private HardSeatPositionRequest() {}

    public static String tag(int seatIndex, int position) {
        return PREFIX + seatIndex + "_" + position;
    }

    public static int get(Entity passenger, int seatIndex) {
        String prefix = PREFIX + seatIndex + "_";
        for (String tag : passenger.getTags()) {
            if (!tag.startsWith(prefix))
                continue;
            try {
                return Integer.parseInt(tag.substring(prefix.length()));
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }
}
