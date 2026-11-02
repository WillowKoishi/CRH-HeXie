package willow.train.hexie.client.sign;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Map;

/** Client-side font outline rasterization; independent of Minecraft's bitmap font. */
public final class SignTextRasterizer {
    private static final FontRenderContext CONTEXT = new FontRenderContext(null, true, true);
    private static final int PAD = 2;
    public record Raster(BufferedImage image, int inkWidth, int inkHeight, float firstDigitCenter, float lastDigitCenter) {}

    public static Raster rasterize(Font source, String text, float tracking, float widthScale) {
        if (text.isBlank()) return null;
        Font font = source.deriveFont(72f).deriveFont(Map.of(TextAttribute.TRACKING, tracking));
        TextLayout layout = new TextLayout(text, font, CONTEXT);
        Rectangle2D bounds = layout.getBounds();
        if (bounds.isEmpty()) return null;
        // Keep exceptionally long labels bounded before allocating CPU/GPU images.
        double scale = Math.min(1, 2044 / Math.max(1, bounds.getWidth() * widthScale));
        int width = Math.max(1, (int) Math.ceil(bounds.getWidth() * widthScale * scale));
        int height = Math.max(1, (int) Math.ceil(bounds.getHeight() * scale));
        BufferedImage image = new BufferedImage(width + PAD * 2, height + PAD * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            graphics.setColor(Color.WHITE);
            graphics.translate(PAD, PAD);
            graphics.scale(widthScale * scale, scale);
            graphics.translate(-bounds.getX(), -bounds.getY());
            layout.draw(graphics, 0, 0);
        } finally { graphics.dispose(); }
        int first=-1,last=-1;
        for(int i=0;i<text.length();) {
            int cp=text.codePointAt(i);
            if(Character.isDigit(cp)){if(first<0)first=i;last=i;}
            i+=Character.charCount(cp);
        }
        if(first<0){first=0;last=text.offsetByCodePoints(text.length(),-1);}
        return new Raster(image,width,height,
                glyphCenter(layout,text,first,bounds,widthScale*scale),
                glyphCenter(layout,text,last,bounds,widthScale*scale));
    }
    private static float glyphCenter(TextLayout layout,String text,int index,Rectangle2D bounds,double scale) {
        var glyph=layout.getBlackBoxBounds(index,index+Character.charCount(text.codePointAt(index))).getBounds2D();
        return (float)((glyph.getCenterX()-bounds.getX())*scale);
    }
    private SignTextRasterizer() {}
}
