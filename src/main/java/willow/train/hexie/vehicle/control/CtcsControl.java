package willow.train.hexie.vehicle.control;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import willow.train.hexie.bridge.vehicle.CabControlBridge;
import willow.train.hexie.core.api.cab.CtcsProfile;

/** Backend-neutral continuous block information and target-distance supervision (advisory in manual mode). */
public final class CtcsControl {
    private CtcsControl() {}
    /** Entry distance along the selected route; linked entries form one interlocking route. */
    public record Section(String id, double distanceM, CabControlBridge.CtcsControlOccupancy occupancy, boolean linked) implements CabControlBridge.CtcsControlSectionView {
        public Section {
            Objects.requireNonNull(id); Objects.requireNonNull(occupancy);
            if (!Double.isFinite(distanceM) || distanceM < 0) throw new IllegalArgumentException("Invalid block distance");
        }
    }

    public static CtcsProfile.Profile decode(List<Section> sections, List<CtcsProfile.SpeedTarget> restrictions) {
        var targets = new ArrayList<>(restrictions);
        var seen = new HashSet<String>();
        int clear = 0, chainClear = 0;
        double chainStart = -1, previous = -1;
        boolean unknown = false, stopped = false;
        for (var section : sections) {
            if (section.distanceM() < previous) throw new IllegalArgumentException("Unordered blocks");
            previous = section.distanceM();
            if (section.linked() && chainStart < 0) { chainStart = section.distanceM(); chainClear = clear; }
            if (section.occupancy() == CabControlBridge.CtcsControlOccupancy.BLOCKED) {
                targets.add(new CtcsProfile.SpeedTarget(chainStart < 0 ? section.distanceM() : chainStart, 0));
                if (chainStart >= 0) clear = chainClear;
                stopped = true;
                break;
            }
            if (section.occupancy() == CabControlBridge.CtcsControlOccupancy.UNKNOWN) unknown = true;
            if (!unknown && seen.add(section.id())) clear++;
            if (!section.linked()) chainStart = -1;
        }
        // A short scan of explicitly clear blocks is also permissive. No observations
        // or unknown occupancy must not be mistaken for an observed clear route.
        CtcsProfile.Code code = clear >= 7 ? CtcsProfile.Code.L5 : !stopped && !unknown && clear > 0 ? CtcsProfile.Code.L : stopped && !unknown ? switch (clear) {
            case 0 -> CtcsProfile.Code.HU; case 1 -> CtcsProfile.Code.U; case 2 -> CtcsProfile.Code.LU; case 3 -> CtcsProfile.Code.L;
            case 4 -> CtcsProfile.Code.L2; case 5 -> CtcsProfile.Code.L3; case 6 -> CtcsProfile.Code.L4; default -> CtcsProfile.Code.UNKNOWN;
        } : CtcsProfile.Code.UNKNOWN;
        return new CtcsProfile.Profile(code, targets);
    }

    /** Each physical signal reads the route beginning at its own entry, not at the train. */
    public static List<CtcsProfile.Code> codesAtSignals(List<Section> sections) {
        var codes = new ArrayList<CtcsProfile.Code>(sections.size());
        for (int i = 0; i < sections.size(); i++)
            codes.add(decode(sections.subList(i, sections.size()), List.of()).code());
        return List.copyOf(codes);
    }
}
