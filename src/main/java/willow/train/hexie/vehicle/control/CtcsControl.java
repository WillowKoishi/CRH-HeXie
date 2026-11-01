package willow.train.hexie.vehicle.control;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Backend-neutral continuous block information and target-distance supervision (advisory in manual mode). */
public final class CtcsControl {
    private CtcsControl() {}
    public enum Code { UNKNOWN, HU, U, LU, L, L2, L3, L4, L5 }
    public enum Occupancy { UNKNOWN, CLEAR, BLOCKED }
    /** Entry distance along the selected route; linked entries form one interlocking route. */
    public record Section(String id, double distanceM, Occupancy occupancy, boolean linked) {
        public Section {
            Objects.requireNonNull(id); Objects.requireNonNull(occupancy);
            if (!Double.isFinite(distanceM) || distanceM < 0) throw new IllegalArgumentException("Invalid block distance");
        }
    }
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
                double end = target.speedKmh() / 3.6;
                double allowed = Math.sqrt(end * end + 2 * deceleration * Math.max(0, target.distanceM() - aheadM)) * 3.6;
                speed = speed < 0 ? allowed : Math.min(speed, allowed);
            }
            return speed;
        }
    }

    public static Profile decode(List<Section> sections, List<SpeedTarget> restrictions) {
        var targets = new ArrayList<>(restrictions);
        var seen = new HashSet<String>();
        int clear = 0, chainClear = 0;
        double chainStart = -1, previous = -1;
        boolean unknown = false, stopped = false;
        for (var section : sections) {
            if (section.distanceM() < previous) throw new IllegalArgumentException("Unordered blocks");
            previous = section.distanceM();
            if (section.linked() && chainStart < 0) { chainStart = section.distanceM(); chainClear = clear; }
            if (section.occupancy() == Occupancy.BLOCKED) {
                targets.add(new SpeedTarget(chainStart < 0 ? section.distanceM() : chainStart, 0));
                if (chainStart >= 0) clear = chainClear;
                stopped = true;
                break;
            }
            if (section.occupancy() == Occupancy.UNKNOWN) unknown = true;
            if (!unknown && seen.add(section.id())) clear++;
            if (!section.linked()) chainStart = -1;
        }
        // A short scan of explicitly clear blocks is also permissive. No observations
        // or unknown occupancy must not be mistaken for an observed clear route.
        Code code = clear >= 7 ? Code.L5 : !stopped && !unknown && clear > 0 ? Code.L : stopped && !unknown ? switch (clear) {
            case 0 -> Code.HU; case 1 -> Code.U; case 2 -> Code.LU; case 3 -> Code.L;
            case 4 -> Code.L2; case 5 -> Code.L3; case 6 -> Code.L4; default -> Code.UNKNOWN;
        } : Code.UNKNOWN;
        return new Profile(code, targets);
    }

    /** Each physical signal reads the route beginning at its own entry, not at the train. */
    public static List<Code> codesAtSignals(List<Section> sections) {
        var codes = new ArrayList<Code>(sections.size());
        for (int i = 0; i < sections.size(); i++)
            codes.add(decode(sections.subList(i, sections.size()), List.of()).code());
        return List.copyOf(codes);
    }
}
