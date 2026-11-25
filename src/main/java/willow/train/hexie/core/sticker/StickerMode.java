package willow.train.hexie.core.sticker;

/** Active operating mode of the unified custom-sticker tool. */
public enum StickerMode {
    WINDOW,
    DECAL;

    public StickerMode next() {
        return this == WINDOW ? DECAL : WINDOW;
    }

    public static StickerMode fromName(String name) {
        if (name == null) return WINDOW;
        for (var m : values()) {
            if (m.name().equalsIgnoreCase(name)) return m;
        }
        return WINDOW;
    }
}
