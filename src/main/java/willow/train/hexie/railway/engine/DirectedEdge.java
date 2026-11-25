package willow.train.hexie.railway.engine;

public record DirectedEdge(long edgeId, Direction direction) {
    public DirectedEdge reverse() { return new DirectedEdge(edgeId, direction.opposite()); }
    public RouteCursor at(double sM) { return new RouteCursor(edgeId, sM, direction); }
}
