package willow.train.hexie.interaction.spatial;

import java.util.*;
import java.util.function.IntToDoubleFunction;
import willow.train.hexie.interaction.spatial.model.SurfaceDetails;
import willow.train.hexie.interaction.spatial.model.SurfaceDetails.GlassOrder;

/** Both text fields are strings, including empty text and leading zeroes. */
public final class DisplayService {
    public record Panel(GlassOrder glassOrder, List<TextLayout.Line> columns) {}

    public static Panel layout(SurfaceDetails details, boolean dualColumn, double width, double height,
            IntToDoubleFunction advanceEm, double seconds) {
        var e = details.editable();
        var fields = dualColumn ? List.of(e.first(), e.second()) : List.of(e.first());
        var columns = fields.stream()
                              .map(text
                                      -> new TextLayout.Column(text, 1, height, 0, 0, 0,
                                              dualColumn ? TextLayout.Overflow.SCROLL : TextLayout.Overflow.FIT))
                              .toList();
        return layout(details, width, height, columns, advanceEm, seconds);
    }
    /** Callers resolving a named style supply column metrics, offsets and overflow for each field. */
    public static Panel layout(SurfaceDetails details, double width, double height, List<TextLayout.Column> style,
            IntToDoubleFunction advanceEm, double seconds) {
        var value = details.editable();
        if (style.isEmpty() || style.size() > 2)
            throw new IllegalArgumentException("Display requires one or two columns");
        var columns = new ArrayList<TextLayout.Column>();
        for (var c : style)
            columns.add(new TextLayout.Column(columns.isEmpty() ? value.first() : value.second(), c.weight(),
                    c.fontSize(), c.letterSpacing(), c.offsetXQuarters(), c.offsetYQuarters(), c.overflow()));
        return new Panel(value.glassOrder(),
                TextLayout.layout(width, height, columns, advanceEm, seconds * value.scrollSpeed()));
    }
    private DisplayService() {}
}
