package willow.train.hexie.core.api.traincontrol;

public record TrainCommand(double throttle, double brake) {
    public static final TrainCommand NEUTRAL = new TrainCommand(0, 0);
    public TrainCommand {
        if (!Double.isFinite(throttle) || Math.abs(throttle) > 1
                || !Double.isFinite(brake) || brake < 0 || brake > 1)
            throw new IllegalArgumentException("Control must be throttle [-1,1], brake [0,1]");
    }
}
