package willow.train.hexie.vehicle.runtime;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** One frozen carriage structure. Its UUID is also the corresponding CarriageSnapshot UUID. */
public record CarriageDefinition(UUID id, List<BogeyDefinition> bogeys,
                                 List<CouplingEndpoint> couplings, double rearExtent, double frontExtent) {
    public CarriageDefinition {
        Objects.requireNonNull(id, "id");
        bogeys = List.copyOf(bogeys);
        couplings = List.copyOf(couplings);
        if (bogeys.isEmpty()) throw new IllegalArgumentException("A carriage requires registered bogeys");
        if (!Double.isFinite(rearExtent) || !Double.isFinite(frontExtent)
                || rearExtent < 0 || frontExtent < 0 || rearExtent + frontExtent <= 0)
            throw new IllegalArgumentException("Invalid carriage extents");
        var ids = new HashSet<UUID>();
        for (BogeyDefinition bogey : bogeys)
            if (!ids.add(bogey.id())) throw new IllegalArgumentException("Duplicate bogey UUID");
        for (CouplingEndpoint endpoint : couplings)
            if (!ids.add(endpoint.id())) throw new IllegalArgumentException("Duplicate endpoint UUID");
        if (!Double.isFinite(bogeys.stream().mapToDouble(b -> b.parameters().loadKg()).sum()))
            throw new IllegalArgumentException("Carriage mass overflow");
    }

    public double massKg() { return bogeys.stream().mapToDouble(b -> b.parameters().loadKg()).sum(); }

    /** Keep one geometric end as the rail anchor, independently of scan/list order or velocity. */
    public BogeyDefinition referenceBogey() {
        return bogeys.stream().min(java.util.Comparator.comparingDouble(BogeyDefinition::offset)).orElseThrow();
    }
}
