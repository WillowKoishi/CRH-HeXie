package willow.train.hexie.interaction.spatial.model;

import java.util.*;
import willow.train.hexie.interaction.spatial.model.HostPatch.UV;

/** Cell-relative placement and owner data stay with the attachment when its editable fields are pasted. */
public record SurfaceDetails(
        AABB bounds, Editable editable, Optional<Id> timetableSource, List<HostPatch> openings, List<Facet> facets) {
    public SurfaceDetails {
        openings = List.copyOf(openings);
        facets = List.copyOf(facets);
    }
    public enum GlassOrder { BehindGlass, InFrontOfGlass }
    public record Editable(String first, String second, String image, String style, boolean reverseU,
            double scrollSpeed, GlassOrder glassOrder) {
        public Editable {
            if (!Double.isFinite(scrollSpeed) || scrollSpeed < 0) throw new IllegalArgumentException("Scroll speed");
        }
    }
    /** Explicit per-corner texture coordinates in original triangle order. */
    public record Facet(int faceId, UV a, UV b, UV c) {}
    public SurfaceDetails edit(Editable value) {
        return new SurfaceDetails(bounds, value, timetableSource, openings, facets);
    }
    public SurfaceDetails place(AABB box, List<HostPatch> patches, List<Facet> faces) {
        return new SurfaceDetails(box, editable, timetableSource, patches, faces);
    }
}
