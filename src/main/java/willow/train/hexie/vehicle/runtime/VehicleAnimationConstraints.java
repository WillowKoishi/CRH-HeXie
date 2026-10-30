package willow.train.hexie.vehicle.runtime;

import java.util.List;
import java.util.UUID;

/** Optional animation contracts. No radial steering, articulation or tilt is enabled by default. */
public final class VehicleAnimationConstraints {
    private VehicleAnimationConstraints() { }

    public record RadialInput(UUID bogeyId, double frameYaw, List<Double> axleTrackYaw) {
        public RadialInput { axleTrackYaw = List.copyOf(axleTrackYaw); }
    }
    public record RadialPose(double frameYaw, List<Double> axleYaw) {
        public RadialPose { axleYaw = List.copyOf(axleYaw); }
    }
    @FunctionalInterface public interface Radial {
        RadialPose solve(RadialInput input);
    }

    public record ArticulatedInput(UUID firstCarriage, UUID secondCarriage, UUID sharedBogey,
                                   double firstBodyYaw, double secondBodyYaw, double trackYaw) { }
    public record ArticulatedPose(double bogeyYaw, double firstBodyYaw, double secondBodyYaw) { }
    @FunctionalInterface public interface Articulated {
        ArticulatedPose solve(ArticulatedInput input);
    }

    /** Couplers receive engagement progress, shared head gap and each hook's local angles; discs use Articulated. */
    @FunctionalInterface public interface Coupler {
        void update(VehicleRuntime.CouplingState state);
    }
}
