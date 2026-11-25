package willow.train.hexie.railway.engine;

public record SignalAnchor(long signalId, Id systemId, long edgeId, double sM, Direction facing) {}
