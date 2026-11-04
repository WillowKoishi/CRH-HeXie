package willow.train.hexie.api.cab;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import willow.train.hexie.vehicle.control.BrakingCurve;

/** Advisory safety constraints. Automatic running plans never enter this calculation. */
public record DmiGuidance(boolean available, double speedKmh, double brakingDistanceM,
                          double secondsToBrake, boolean brakeNow) {
    public record CurvePoint(double distanceM, double speedKmh) {}

    /** One safety envelope is shared by the speedometer, target indicators and distance display. */
    public static final class Resolved {
        private static final double MAX_DISTANCE = 16000;
        private final DmiState data;
        private final DmiState.Target target;
        private final DmiGuidance guidance;
        private final List<CurvePoint> curve;
        private final double descentStart;

        public Resolved(DmiState data, DmiState.Target target, DmiGuidance guidance) {
            this.data = data;
            this.target = target;
            this.guidance = guidance;
            var distances = new TreeSet<Double>();
            distances.add(0d);
            distances.add(MAX_DISTANCE);
            // Sampling follows the existing linear/logarithmic distance axis. Actual constraint
            // positions are included separately so short restrictions cannot disappear between samples.
            for (int i = 1; i <= 40; i++) distances.add(i * 25d);
            for (int i = 1; i <= 128; i++) distances.add(1000 * Math.pow(2, i / 32d));
            double firstDescent = Double.POSITIVE_INFINITY;
            for (var point : constraints(data)) {
                if (point.distanceM() <= MAX_DISTANCE) distances.add(point.distanceM());
                if (canBrake(data)) {
                    double start = data.lineLimitKmh() < 0 ? 0 : Math.max(0, point.distanceM()
                            - brakingDistance(data, data.lineLimitKmh() / 3.6, point.speedKmh() / 3.6));
                    firstDescent = Math.min(firstDescent, start);
                    if (start <= MAX_DISTANCE) distances.add(start);
                }
            }
            descentStart = firstDescent;
            var points = new ArrayList<CurvePoint>(distances.size());
            for (double distance : distances) points.add(new CurvePoint(distance, envelope(data, distance)));
            curve = List.copyOf(points);
        }

        public DmiState data() { return data; }
        public DmiState.Target target() { return target; }
        public DmiGuidance guidance() { return guidance; }
        public boolean hasTargetCurve() { return target != null && canBrake(data); }
        public double descentStartDistance() { return descentStart; }

        /** Safety speed only; the actual speed and maximum traction do not define this curve. */
        public double speedAtDistance(double aheadM) {
            if (!Double.isFinite(aheadM) || aheadM < 0) throw new IllegalArgumentException("Invalid look-ahead distance");
            if (aheadM > MAX_DISTANCE) return envelope(data, aheadM);
            int low = 0, high = curve.size() - 1;
            while (low + 1 < high) {
                int middle = (low + high) >>> 1;
                if (curve.get(middle).distanceM() <= aheadM) low = middle;
                else high = middle;
            }
            var first = curve.get(low);
            if (aheadM == first.distanceM()) return first.speedKmh();
            var second = curve.get(high);
            if (aheadM == second.distanceM()) return second.speedKmh();
            // Arbitrary queries retain the same safety formula as train control. Rendering uses
            // the precomputed samples below instead of reintegrating each vertex.
            return envelope(data, aheadM);
        }

        /** Cached samples, clipped to the requested scale and the first supplied zero-speed point. */
        public List<CurvePoint> plotPoints(double rangeM) {
            if (!Double.isFinite(rangeM) || rangeM <= 0) throw new IllegalArgumentException("Invalid display range");
            double end = Math.min(rangeM, MAX_DISTANCE);
            for (var point : constraints(data)) if (point.speedKmh() == 0) end = Math.min(end, point.distanceM());
            var points = new ArrayList<CurvePoint>();
            for (var point : curve) {
                if (point.distanceM() >= end) break;
                points.add(point);
            }
            points.add(new CurvePoint(end, speedAtDistance(end)));
            return List.copyOf(points);
        }
    }

    public static Resolved resolve(CabDisplayState state) {
        var data = state.dmi();
        var target = selectTarget(data);
        if (target == null || !canBrake(data)) {
            boolean known = data.lineLimitKmh() >= 0;
            return new Resolved(data, target, new DmiGuidance(known, known ? data.lineLimitKmh() : 0,
                    0, Double.POSITIVE_INFINITY, known && state.speedKmh() > data.lineLimitKmh() + .5));
        }
        double v = state.speedKmh() / 3.6, end = target.speedKmh() / 3.6;
        double distance = brakingDistance(data, v, end);
        double permitted = envelope(data, 0);
        double seconds = v > 0 ? Math.max(0, target.distanceM() - distance) / v : Double.POSITIVE_INFINITY;
        return new Resolved(data, target, new DmiGuidance(true, permitted, distance, seconds,
                state.speedKmh() > permitted + .5 || v > end && distance >= target.distanceM()));
    }

    public static DmiGuidance from(CabDisplayState state) { return resolve(state).guidance(); }
    public static boolean hasTargetCurve(CabDisplayState state) { return resolve(state).hasTargetCurve(); }
    public static DmiState.Target target(CabDisplayState state) { return selectTarget(state.dmi()); }

    /** Compare required stopping distance from one reference speed; station identity wins ties. */
    private static DmiState.Target selectTarget(DmiState data) {
        var best = data.target();
        double reference = Math.max(0, data.lineLimitKmh()) / 3.6;
        for (var target : constraints(data)) reference = Math.max(reference, target.speedKmh() / 3.6);
        double bound = best == null ? Double.POSITIVE_INFINITY
                : best.distanceM() - brakingDistance(data, reference, best.speedKmh() / 3.6);
        for (var point : data.ctcs().targets()) {
            double value = point.distanceM() - brakingDistance(data, reference, point.speedKmh() / 3.6);
            if (value < bound) {
                bound = value;
                best = new DmiState.Target(point.speedKmh() == 0 ? DmiState.TargetKind.SIGNAL : DmiState.TargetKind.SPEED_RESTRICTION,
                        "", point.distanceM(), point.speedKmh());
            }
        }
        return best;
    }

    public static double speedAtDistance(CabDisplayState state, double aheadM) { return envelope(state.dmi(), aheadM); }

    private static double envelope(DmiState data, double aheadM) {
        if (!Double.isFinite(aheadM) || aheadM < 0) throw new IllegalArgumentException("Invalid look-ahead distance");
        double speed = data.limits() == null
                ? data.ctcs().speedAt(aheadM, data.serviceDeceleration(), data.lineLimitKmh())
                : data.ctcs().speedAt(aheadM, data.limits(), data.lineLimitKmh());
        if (data.target() != null && canBrake(data)) {
            var target = data.target();
            double curve = data.limits() == null
                    ? BrakingCurve.speed(target.speedKmh() / 3.6, target.distanceM() - aheadM, data.serviceDeceleration()) * 3.6
                    : data.limits().permittedSpeed(target.speedKmh() / 3.6, target.distanceM() - aheadM,
                            data.lineLimitKmh() >= 0 ? data.lineLimitKmh() / 3.6 : Double.POSITIVE_INFINITY) * 3.6;
            speed = speed >= 0 ? Math.min(speed, curve) : curve;
        }
        return speed;
    }

    private static boolean canBrake(DmiState data) {
        // A supplied zero capability is known, not missing data: its safety envelope
        // still applies and must not turn into an unrestricted speedometer reading.
        return data.limits() != null || data.serviceDeceleration() > 0;
    }

    private static double brakingDistance(DmiState data, double speedMps, double targetMps) {
        return data.limits() == null ? BrakingCurve.distance(speedMps, targetMps, data.serviceDeceleration())
                : data.limits().brakingDistance(speedMps, targetMps);
    }

    private static List<willow.train.hexie.vehicle.control.CtcsControl.SpeedTarget> constraints(DmiState data) {
        var targets = new ArrayList<>(data.ctcs().targets());
        if (data.target() != null) targets.add(new willow.train.hexie.vehicle.control.CtcsControl.SpeedTarget(
                data.target().distanceM(), data.target().speedKmh()));
        return targets;
    }
}
