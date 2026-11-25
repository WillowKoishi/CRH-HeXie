package willow.train.hexie.interaction.spatial.model;

/** Result of one atomic attachment transaction, including the number of boundary rebuilds it ran. */
public record SurfaceEditResult(Status status, int cutJobs) {
    public enum Status { Applied, StaleAnchor, AmbiguousLayer, IncompleteCoverage, InvalidGeometry }
    public boolean applied() {
        return status == Status.Applied;
    }
}
