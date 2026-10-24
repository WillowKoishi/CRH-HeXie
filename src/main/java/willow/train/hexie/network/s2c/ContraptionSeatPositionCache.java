package willow.train.hexie.network.s2c;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public final class ContraptionSeatPositionCache {
    private static final Map<Key, Position> POSITIONS = new ConcurrentHashMap<>();

    private ContraptionSeatPositionCache() {}

    public static void update(UUID contraptionId, UUID passengerId, int seatIndex, int position) {
        Key key = new Key(contraptionId, passengerId);
        if (position < 0) {
            POSITIONS.remove(key);
            return;
        }
        POSITIONS.put(key, new Position(seatIndex, position));
    }

    public static int get(UUID contraptionId, UUID passengerId, int seatIndex) {
        Position position = POSITIONS.get(new Key(contraptionId, passengerId));
        return position != null && position.seatIndex() == seatIndex
                ? position.position()
                : -1;
    }

    public static void clear() {
        POSITIONS.clear();
    }

    private record Key(UUID contraptionId, UUID passengerId) {}

    private record Position(int seatIndex, int position) {}
}
