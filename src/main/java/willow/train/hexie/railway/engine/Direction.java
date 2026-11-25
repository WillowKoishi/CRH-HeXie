package willow.train.hexie.railway.engine;

public enum Direction {
    FORWARD, BACKWARD;

    public Direction opposite() {
        return this == FORWARD ? BACKWARD : FORWARD;
    }
}
