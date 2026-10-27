package willow.train.hexie.client.carriage.browser;

/** Critically damped motion. Retargeting preserves both position and velocity. */
public final class CarriageMotion {
    private double position, velocity, target;
    public double position() { return position; }
    public double velocity() { return velocity; }
    public void target(double value) { target = value; }
    public void reset() { position = velocity = target = 0; }
    public void restore(double position, double velocity, double target) {
        this.position = position; this.velocity = velocity; this.target = target;
    }
    public void advance(double seconds) {
        if (seconds <= 0) return;
        double offset = position - target;
        double c = velocity + 10 * offset;
        double decay = Math.exp(-10 * seconds);
        position = target + (offset + c * seconds) * decay;
        velocity = (velocity - 10 * c * seconds) * decay;
        if (Math.abs(position - target) < .0001 && Math.abs(velocity) < .001) {
            position = target;
            velocity = 0;
        }
    }
}
