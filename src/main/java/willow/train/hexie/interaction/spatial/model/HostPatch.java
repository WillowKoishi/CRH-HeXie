package willow.train.hexie.interaction.spatial.model;

import java.util.*;

/** Mask coordinates are in the projection frame of the inward unit direction, relative to the cell origin. */
public record HostPatch(SurfaceHostId host, AnchorHit anchorHit, Vec direction, CutMode mode, CutMask footprint,
        Optional<CutMask> cutMask) {
    public enum CutMode { AnchoredLayer, ThroughAll }
    public record AnchorHit(long geometryRevision, int faceId, double bWeight, double cWeight) {}
    public record UV(double u, double v) {}
    public record CutMask(List<UV> outer, List<List<UV>> holes) {
        public CutMask {
            outer = List.copyOf(outer);
            holes = holes.stream().map(List::copyOf).toList();
        }
        public static CutMask rectangle(double left, double bottom, double right, double top) {
            return new CutMask(
                    List.of(new UV(left, bottom), new UV(right, bottom), new UV(right, top), new UV(left, top)),
                    List.of());
        }
    }

    public HostPatch(SurfaceHostId host, AnchorHit anchor, Vec direction, CutMask footprint) {
        this(host, anchor, direction, CutMode.AnchoredLayer, footprint, Optional.empty());
    }
    public CutMask mask() {
        return cutMask.orElse(footprint);
    }
}
