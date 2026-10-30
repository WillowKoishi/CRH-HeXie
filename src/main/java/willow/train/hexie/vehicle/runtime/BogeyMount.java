package willow.train.hexie.vehicle.runtime;

import java.util.Objects;

/**
 * A registered attachment between a carriage and a bogey. Both frames face north (-Z).
 * The body point stays fixed in the carriage; the track pivot is relative to the bogey's
 * track-following base. For a bolsterless end support, lateralLimit bounds the suspension
 * offset used while solving the rigid body pose: the track/chord midpoint difference is
 * projected into that support's own lateral axis. This symmetric geometric approximation
 * introduces no spring constants or steering-angle limit. Intermediate supports follow
 * the solved body, using their local suspension travel before any remaining off-rail offset.
 */
public record BogeyMount(CouplingEndpoint.Point bodyPoint, CouplingEndpoint.Point trackPivot,
                         Type type, double lateralLimit) {
    public enum Type { BOLSTERED, BOLSTERLESS }

    /** Legacy direct attachment behaviour, not a claim about the real bogey's construction. */
    public BogeyMount(CouplingEndpoint.Point bodyPoint, CouplingEndpoint.Point trackPivot) {
        this(bodyPoint, trackPivot, Type.BOLSTERED, 0);
    }

    public BogeyMount {
        Objects.requireNonNull(bodyPoint, "bodyPoint");
        Objects.requireNonNull(trackPivot, "trackPivot");
        Objects.requireNonNull(type, "type");
        if (!Double.isFinite(lateralLimit) || lateralLimit < 0)
            throw new IllegalArgumentException("Lateral limit must be finite and non-negative");
    }
}
