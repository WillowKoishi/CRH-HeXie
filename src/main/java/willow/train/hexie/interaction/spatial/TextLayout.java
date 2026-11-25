package willow.train.hexie.interaction.spatial;

import java.util.*;
import java.util.function.IntToDoubleFunction;

/** Single-row display layout. Font metrics supply glyph advances in em, coordinates are pixels. */
public final class TextLayout {
    public enum Overflow { FIT, SCROLL }
    public record Column(String text, double weight, double fontSize, double letterSpacing,
                         int offsetXQuarters, int offsetYQuarters, Overflow overflow) {}
    public record Clip(double x, double y, double width, double height) {}
    public record Glyph(int codePoint, double x, double y, double fontSize) {}
    public record Line(Clip clip, double scale, double scrollOffset, List<Glyph> glyphs) {
        public Line { glyphs = List.copyOf(glyphs); }
    }

    public static List<Line> layout(double width, double height, List<Column> columns,
                                    IntToDoubleFunction advanceEm, double seconds) {
        if (!(width > 0 && height > 0)) throw new IllegalArgumentException("Invalid display size");
        double total = columns.stream().mapToDouble(Column::weight).sum();
        double weights = 0;
        var lines = new ArrayList<Line>();
        for (var c : columns) {
            if (!(c.weight() > 0 && c.fontSize() > 0)) throw new IllegalArgumentException("Invalid column");
            double left = width * weights / total;
            weights += c.weight();
            double span = width * weights / total - left;
            int[] points = c.text().codePoints().toArray();
            double[] advances = new double[points.length];
            double natural = 0;
            for (int i = 0; i < points.length; i++) {
                advances[i] = advanceEm.applyAsDouble(points[i]) * c.fontSize();
                natural += advances[i] + (i == 0 ? 0 : c.letterSpacing());
            }
            double scale = Math.min(1, height / c.fontSize());
            if (c.overflow() == Overflow.FIT && natural > 0) scale = Math.min(scale, span / natural);
            double lineHeight = c.fontSize() * scale;
            double excess = natural * scale - span;
            double offset = excess > 0 && c.overflow() == Overflow.SCROLL ? scroll(seconds, excess, lineHeight) : 0;
            double x = left + Math.max(0, -excess/2) - offset + .25*c.offsetXQuarters();
            double y = (height - lineHeight)/2 + .25*c.offsetYQuarters();
            var visible = new ArrayList<Glyph>();
            for (int i = 0; i < points.length; i++) {
                double right = x + advances[i]*scale;
                if (x < left+span && right > left && y < height && y+lineHeight > 0)
                    visible.add(new Glyph(points[i], x, y, lineHeight));
                x = right + c.letterSpacing()*scale;
            }
            lines.add(new Line(new Clip(left, 0, span, height), scale, offset, visible));
        }
        return List.copyOf(lines);
    }

    private static double scroll(double seconds, double excess, double lineHeight) {
        double speed = .9 * lineHeight;
        double travel = excess/speed;
        double halfPeriod = 1.25 + travel;
        double period = 2 * halfPeriod;
        double phase = seconds - Math.floor(seconds/period)*period;
        if (phase <= 1.25) return 0;
        if (phase < halfPeriod) return (phase-1.25)*speed;
        if (phase <= halfPeriod+1.25) return excess;
        return (period-phase)*speed;
    }
    private TextLayout() {}
}
