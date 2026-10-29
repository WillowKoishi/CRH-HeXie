package willow.train.hexie.vehicle.bogey;

import java.util.List;

/** Immutable model geometry in blocks, independent of any track or physics backend. */
public record BogeyGeometry(double wheelRadius, List<Double> axlePositions) {
    public BogeyGeometry {
        if (!Double.isFinite(wheelRadius) || wheelRadius <= 0)
            throw new IllegalArgumentException("Wheel radius must be finite and positive");
        axlePositions = List.copyOf(axlePositions);
        if (axlePositions.isEmpty())
            throw new IllegalArgumentException("At least one axle is required");
        double previous = Double.NEGATIVE_INFINITY;
        for (double position : axlePositions) {
            if (!Double.isFinite(position) || position <= previous)
                throw new IllegalArgumentException("Axle positions must be finite and strictly increasing");
            previous = position;
        }
    }

    public double axleSpan() {
        return axlePositions.getLast() - axlePositions.getFirst();
    }

    public double[] axlePositionArray() {
        return axlePositions.stream().mapToDouble(Double::doubleValue).toArray();
    }
}
