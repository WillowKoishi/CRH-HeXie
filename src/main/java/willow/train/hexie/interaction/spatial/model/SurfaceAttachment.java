package willow.train.hexie.interaction.spatial.model;

import java.util.*;

/** The anchor is always covered. Span cells are addresses in the same host coordinate space. */
public record SurfaceAttachment(UUID attachmentId, SurfaceHostId anchor, List<Cell> spanCells,
                                Face face, Id kind, long revision, Optional<SurfaceDetails> details) {
    public SurfaceAttachment {
        Objects.requireNonNull(attachmentId);
        Objects.requireNonNull(anchor);
        Objects.requireNonNull(face);
        Objects.requireNonNull(kind);
        Objects.requireNonNull(details);
        spanCells = List.copyOf(spanCells);
    }
    public SurfaceAttachment(UUID id, SurfaceHostId anchor, List<Cell> span, Face face, Id kind, long revision) {
        this(id, anchor, span, face, kind, revision, Optional.empty());
    }
    public SurfaceAttachment withDetails(SurfaceDetails value) {
        return new SurfaceAttachment(attachmentId, anchor, spanCells, face, kind, revision + 1, Optional.of(value));
    }
}
