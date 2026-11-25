package willow.train.hexie.core.api.cab;

import java.util.List;
import java.util.Objects;

/** Backend-neutral continuous block information and target-distance supervision (advisory in manual mode). */
public final class CtcsProfile {
    private static final double LINE_SPEED_LIMIT_MPS = 300 / 3.6;
    private CtcsProfile() {}
    public enum Code { UNKNOWN, HU, U, LU, L, L2, L3, L4, L5 }
    /** An explicit speed at a track position. Unknown data never creates a zero-speed constraint. */
    public record SpeedTarget(double distanceM, double speedKmh) {
        public SpeedTarget {
            if (!Double.isFinite(distanceM) || distanceM < 0 || !Double.isFinite(speedKmh) || speedKmh < 0)
                throw new IllegalArgumentException("Invalid speed target");
        }
    }
    /** Native CTCS providers may supply decoded codes and nonzero speed targets directly. */
    public record Profile(Code code, List<SpeedTarget> targets) {
        public static final Profile EMPTY = new Profile(Code.UNKNOWN, List.of());
        public Profile { Objects.requireNonNull(code); targets = List.copyOf(targets); }
        public double speedAt(double aheadM, double deceleration, double lineLimitKmh) {
            double speed = lineLimitKmh;
            if (deceleration <= 0) return speed;
            for (var target : targets) {
                double allowed = BrakingCurve.speed(target.speedKmh() / 3.6, target.distanceM() - aheadM, deceleration) * 3.6;
                speed = speed < 0 ? allowed : Math.min(speed, allowed);
            }
            return speed;
        }
        /** Shared safety envelope. Automatic running-plan parameters never enter this calculation. */
        public double speedAt(double aheadM, TrainControlLimits limits, double lineLimitKmh) {
            Objects.requireNonNull(limits);
            double speed = lineLimitKmh;
            for (var target : targets) {
                double ceiling = speed < 0 ? LINE_SPEED_LIMIT_MPS : speed / 3.6;
                double allowed = limits.permittedSpeed(target.speedKmh() / 3.6,
                        target.distanceM() - aheadM, ceiling) * 3.6;
                speed = speed < 0 ? allowed : Math.min(speed, allowed);
            }
            return speed;
        }
    }

}
