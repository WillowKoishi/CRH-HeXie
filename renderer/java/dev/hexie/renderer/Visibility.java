package dev.hexie.renderer;

public enum Visibility {
    VISIBLE, OCCLUDED, UNDETERMINED;

    public Visibility and(Visibility other) {
        if (this == OCCLUDED || other == OCCLUDED) return OCCLUDED;
        if (this == VISIBLE && other == VISIBLE) return VISIBLE;
        return UNDETERMINED;
    }
}
