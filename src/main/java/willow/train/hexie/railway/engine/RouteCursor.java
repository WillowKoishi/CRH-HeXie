package willow.train.hexie.railway.engine;

/** sM is arc length in metres from the edge's start, in either direction. */
public record RouteCursor(long edgeId, double sM, Direction direction) {}
