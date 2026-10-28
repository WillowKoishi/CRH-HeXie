package willow.train.hexie.client.sign;

/** Layout in local sign units, +Y down. Input widths use the same 8-unit ink height as drawing. */
public final class CarriageMarkingLayout {
    public static final float MODULE_GAP = .65f, SERIES_GAP = .12f;
    public record Part(float x, float y, float scaleX, float scaleY) {}
    public record Layout(Part[] parts, float width) {}

    public static Layout arrange(float[] widths, boolean reversed) {
        if (widths.length != 5) throw new IllegalArgumentException("Five marking fields required");
        float[] sx = {.13f, .08f, .32f, .12f, .32f};
        float[] sy = {.18f, .08f, .32f, .12f, .32f};
        float[] w = new float[5];
        for (int i = 0; i < 5; i++) {
            if (!Float.isFinite(widths[i]) || widths[i] < 0) throw new IllegalArgumentException("Invalid width");
            w[i] = widths[i] * sx[i];
        }
        float nameWidth = Math.max(w[0], w[1]);
        float typeGap = w[2] > 0 && w[3] > 0 ? SERIES_GAP : 0;
        float typeWidth = w[2] + typeGap + w[3];
        float numberGap = typeWidth > 0 && w[4] > 0 ? MODULE_GAP : 0;
        float codeWidth = typeWidth + numberGap + w[4];
        float nameGap = nameWidth > 0 && codeWidth > 0 ? MODULE_GAP : 0;
        float total = nameWidth + nameGap + codeWidth;
        float nameX = reversed ? codeWidth + nameGap : 0;
        float codeX = reversed ? 0 : nameWidth + nameGap;
        float height = 8 * sy[2];
        float nameHeight = (w[0] > 0 ? 8 * sy[0] : 0) + (w[1] > 0 ? 8 * sy[1] : 0)
                + (w[0] > 0 && w[1] > 0 ? .25f : 0);
        float nameY = (height - nameHeight) / 2;
        float pinyinY = nameY + (w[0] > 0 ? 8 * sy[0] + (w[1] > 0 ? .25f : 0) : 0);
        Part[] parts = {
            new Part(nameX + (nameWidth - w[0]) / 2, nameY, sx[0], sy[0]),
            new Part(nameX + (nameWidth - w[1]) / 2, pinyinY, sx[1], sy[1]),
            new Part(codeX, 0, sx[2], sy[2]),
            new Part(codeX + w[2] + typeGap, height - 8 * sy[3], sx[3], sy[3]),
            new Part(codeX + typeWidth + numberGap, 0, sx[4], sy[4])
        };
        return new Layout(parts, total);
    }
    private CarriageMarkingLayout() {}
}
