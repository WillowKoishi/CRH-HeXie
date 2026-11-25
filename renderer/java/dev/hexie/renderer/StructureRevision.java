package dev.hexie.renderer;

import java.util.*;

public record StructureRevision(long groupId, long revision, SurfaceHostId host,
                                List<ModelRef> models, List<OpeningRequest> openings) {
    public StructureRevision { models = List.copyOf(models); openings = List.copyOf(openings); }
    public record Cell(int x,int y,int z) {}
    public sealed interface SurfaceHostId {
        record World(String dimensionId, Cell cell) implements SurfaceHostId {}
        record Carriage(UUID carriageId, Cell cell) implements SurfaceHostId {}
    }
    public record ModelRef(String geometryId, String materialId, Pose localTransform) {}
    public record AnchorHit(long contentRevision, long primitiveId, double u, double v) {}
    public enum CutMode { ANCHORED_LAYER, THROUGH_ALL }
    public enum OpeningFailure { STALE_ANCHOR, AMBIGUOUS_LAYER, INCOMPLETE_COVERAGE, INVALID_GEOMETRY }
    public record OpeningRequest(ModelRef target, Pose placement, Vec inward, AnchorHit anchor,
                                 CutMode mode, Optional<String> cutMask, ModelRef insert, Map<String,Double> parameters) {
        public OpeningRequest {
            mode = Objects.requireNonNullElse(mode, CutMode.ANCHORED_LAYER);
            parameters = Map.copyOf(parameters);
        }
    }
}
