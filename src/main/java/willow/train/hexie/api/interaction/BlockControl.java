package willow.train.hexie.api.interaction;

import net.minecraft.network.chat.Component;

/** A control declared by a block surface, identified by a stable ID within that surface. */
public interface BlockControl {
    String id();
    Component nameText();
    Component valueText(ControlState state);
    boolean accepts(int value);

    interface Handle extends BlockControl {
        int minNotch();
        int maxNotch();
        /** Physical positions are local to the module. Existing surfaces may retain their NBT keys. */
        default String positionKey() { return id(); }
        Component notchText(int notch);

        default int notch(ControlState state) {
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
        @Override default Component valueText(ControlState state) { return notchText(notch(state)); }
    }

    interface Button extends BlockControl {
        @Override default boolean accepts(int value) { return value == 0; }
    }
}
