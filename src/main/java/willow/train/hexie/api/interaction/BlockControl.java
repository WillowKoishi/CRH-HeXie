package willow.train.hexie.api.interaction;

import net.minecraft.network.chat.Component;

/** A control declared by a block surface, identified by a stable ID within that surface. */
public interface BlockControl {
    String id();
    Component nameText();
    Component valueText(ControlState state);
    boolean accepts(int value);

    /** Whether this control displays its name and value while hovered or dragged. */
    default boolean showTooltip() { return false; }

    interface Handle extends BlockControl {
        int minNotch();
        int maxNotch();
        enum DragAxis { HORIZONTAL, VERTICAL }

        /** Screen travel in scaled GUI pixels. sliderPosition defines its direction and detent spacing. */
        record DragSpec(DragAxis axis, int travel) {
            public static final DragSpec DEFAULT = new DragSpec(DragAxis.VERTICAL, 90);
            public DragSpec {
                java.util.Objects.requireNonNull(axis);
                if (travel < 1) throw new IllegalArgumentException("Handle drag travel must be positive");
            }
        }

        default DragSpec dragSpec() { return DragSpec.DEFAULT; }

        /**
         * Legal detents in numeric order, shared by keyboard stepping and mouse dragging.
         * Invalid or excessively large declared ranges have no interactive detents.
         */
        default java.util.List<Integer> validNotches() {
            int minimum = minNotch(), maximum = maxNotch();
            long count = (long) maximum - minimum + 1;
            if (count <= 0 || count > 4096) return java.util.List.of();
            var result = new java.util.ArrayList<Integer>();
            for (int index = 0; index < count; index++) {
                int value = minimum + index;
                if (accepts(value)) result.add(value);
            }
            return java.util.List.copyOf(result);
        }
        /** Physical positions are local to the module. Existing surfaces may retain their NBT keys. */
        default String positionKey() { return id(); }
        Component notchText(int notch);

        default int notch(ControlState state) {
            int value = state.getOrDefault(positionKey(), minNotch());
            if (value >= minNotch() && value <= maxNotch() && accepts(value)) return value;
            var notches = validNotches();
            return notches.isEmpty() ? minNotch() : notches.getFirst();
        }

        default void checkNotch(int notch) {
            if (!accepts(notch)) throw new IllegalArgumentException("Invalid " + id() + " notch: " + notch);
        }

        /** Fraction in [0, 1] measured from the top or left; it may increase or decrease across detents. */
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
