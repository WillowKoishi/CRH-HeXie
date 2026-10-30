package willow.train.hexie.vehicle.runtime;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** One frozen carriage structure. Its UUID is also the corresponding CarriageSnapshot UUID. */
public record CarriageDefinition(UUID id, List<BogeyDefinition> bogeys,
                                 List<CouplingEndpoint> couplings, double rearExtent, double frontExtent,
                                 double floorOffset) {
    public CarriageDefinition(UUID id, List<BogeyDefinition> bogeys, List<CouplingEndpoint> couplings,
                              double rearExtent, double frontExtent) {
        this(id, bogeys, couplings, rearExtent, frontExtent, 0);
    }

    public CarriageDefinition {
        Objects.requireNonNull(id, "id");
        bogeys = List.copyOf(bogeys);
        couplings = List.copyOf(couplings);
        if (bogeys.isEmpty()) throw new IllegalArgumentException("A carriage requires registered bogeys");
        if (!Double.isFinite(rearExtent) || !Double.isFinite(frontExtent)
                || rearExtent < 0 || frontExtent < 0 || rearExtent + frontExtent <= 0)
            throw new IllegalArgumentException("Invalid carriage extents");
        if (!Double.isFinite(floorOffset)) throw new IllegalArgumentException("Non-finite carriage floor offset");
        var ids = new HashSet<UUID>();
        for (BogeyDefinition bogey : bogeys)
            if (!ids.add(bogey.id())) throw new IllegalArgumentException("Duplicate bogey UUID");
        for (CouplingEndpoint endpoint : couplings)
            if (!ids.add(endpoint.id())) throw new IllegalArgumentException("Duplicate endpoint UUID");
        if (!Double.isFinite(bogeys.stream().mapToDouble(b -> b.parameters().loadKg()).sum()))
            throw new IllegalArgumentException("Carriage mass overflow");
    }

    public double massKg() { return bogeys.stream().mapToDouble(b -> b.parameters().loadKg()).sum(); }

    /** Shift only body-local attachments; track pivots and captured block coordinates stay fixed. */
    public CarriageDefinition withFloorOffset(double next) {
        if (!Double.isFinite(next)) throw new IllegalArgumentException("Non-finite carriage floor offset");
        if (next == floorOffset) return this;
        var shift = new CouplingEndpoint.Point(0, floorOffset - next, 0);
        var shiftedBogeys = bogeys.stream().map(b -> new BogeyDefinition(b.id(), b.modelId(), b.offset(),
                b.geometry(), b.parameters(), new BogeyMount(b.mount().bodyPoint().plus(shift), b.mount().trackPivot(),
                b.mount().type(), b.mount().lateralLimit()))).toList();
        var shiftedCouplings = couplings.stream().map(e -> new CouplingEndpoint(e.id(), e.blockCenter(), e.facing(),
                e.pivot().plus(shift), e.start().plus(shift), e.complete().plus(shift), e.kind(), e.maxExtension(),
                e.model(), e.adapter())).toList();
        return new CarriageDefinition(id, shiftedBogeys, shiftedCouplings, rearExtent, frontExtent, next);
    }

    /** Keep one geometric end as the rail anchor, independently of scan/list order or velocity. */
    public BogeyDefinition referenceBogey() {
        return bogeys.stream().min(java.util.Comparator.comparingDouble(BogeyDefinition::offset)).orElseThrow();
    }
}
