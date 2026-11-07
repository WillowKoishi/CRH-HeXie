package willow.train.hexie.api.cab;

import willow.train.hexie.api.interaction.ControlState;

import willow.train.hexie.api.interaction.BlockControl;

import java.util.Objects;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;

/** Optional physical detents for the power handle at zero and the reverser at cut-out. */
public record CabNeutralPositions(@Nullable Detent powerZero, @Nullable Detent reverserCutOut) {
    /** An unbound position is read from the desk's corresponding normalized train request. */
    public static final CabNeutralPositions FROM_REQUEST = new CabNeutralPositions(null, null);

    public static final class Detent {
        private final Predicate<ControlState> atPosition;

        /** A button-operated selector supplies its readout in the restored surface state. */
        public Detent(String positionKey, int notch) {
            Objects.requireNonNull(positionKey);
            atPosition = surface -> Integer.valueOf(notch).equals(surface.positions().get(positionKey));
        }

        /** Read the same physical notch as the slider, including its initial position on a fresh desk. */
        public Detent(BlockControl.Handle handle, int notch) {
            handle.checkNotch(notch);
            atPosition = surface -> handle.notch(surface) == notch;
        }

        public boolean matches(ControlState surface) {
            return atPosition.test(surface);
        }
    }
}
