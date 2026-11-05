package willow.train.hexie.api.cab;

import net.minecraft.network.chat.Component;

/** A control declared by a console, identified by a stable ID within that console. */
public interface CabControl {
    String id();
    Component nameText();
    Component valueText(CabSurfaceState state);
    boolean accepts(int value);

    interface Handle extends CabControl {
        int minNotch();
        int maxNotch();
        /** Physical positions are local to the cab module. Older desks can retain their existing NBT keys. */
        default String positionKey() { return id(); }
        Component notchText(int notch);

        default int notch(CabSurfaceState state) {
            int value = state.getOrDefault(positionKey(), minNotch());
            return accepts(value) ? value : minNotch();
        }

        default void checkNotch(int notch) {
            if (!accepts(notch)) throw new IllegalArgumentException("Invalid " + id() + " notch: " + notch);
        }

        /** Fraction of the slider measured down from its top, monotonic across the notches. */
        default double sliderPosition(int notch) {
            return maxNotch() == minNotch() ? 0 : ((double) notch - minNotch()) / ((double) maxNotch() - minNotch());
        }

        @Override default boolean accepts(int value) { return value >= minNotch() && value <= maxNotch(); }
        @Override default Component valueText(CabSurfaceState state) { return notchText(notch(state)); }
    }

    interface Button extends CabControl {
        @Override default boolean accepts(int value) { return value == 0; }
    }
}
