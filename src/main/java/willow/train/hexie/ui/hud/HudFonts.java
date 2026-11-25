package willow.train.hexie.ui.hud;

// split from willow/train/hexie/client/hud/HudFonts.java

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import willow.train.hexie.ui.hud.HudFontFaces.Face;

/** Optional HUD faces, backed by Minecraft's glyph atlas and its normal font as a fallback. */
public final class HudFonts extends SimplePreparableReloadListener<HudFonts.FontFiles> {
    static final Logger LOGGER = LoggerFactory.getLogger(HudFonts.class);
    private static final HudFonts INSTANCE = new HudFonts();
    private static final ResourceLocation LATIN_FILE = id("sign_fonts/barlow_condensed_medium.ttf");
    private static final ResourceLocation CJK_FILE = id("sign_fonts/noto_sans_cjk_sc_bold.otf");
    static final float OVERSAMPLE = 2f;

    // The two faces have different cap heights and advances at the same nominal size.
    private static final float LATIN_SIZE = 11f, LATIN_TRACKING = .20f, LATIN_Y_OFFSET = .5f;
    private static final float CJK_SIZE = 9f, CJK_TRACKING = .10f, CJK_Y_OFFSET = .75f;

    private Face latin;
    private Face cjk;

    private HudFonts() {}

    public static void register(IEventBus bus) {
        bus.addListener((RegisterClientReloadListenersEvent event) -> event.registerReloadListener(INSTANCE));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("hexie", path);
    }

    public record FontFiles(byte[] latin, byte[] cjk) {}

    @Override
    protected FontFiles prepare(ResourceManager resources, ProfilerFiller profiler) {
        return new FontFiles(read(resources, LATIN_FILE), read(resources, CJK_FILE));
    }

    private static byte[] read(ResourceManager resources, ResourceLocation path) {
        var resource = resources.getResource(path);
        if (resource.isEmpty()) return null;
        try (var stream = resource.get().open()) {
            return stream.readAllBytes();
        } catch (IOException e) {
            LOGGER.warn("Unable to read HUD font {}", path, e);
            return null;
        }
    }

    @Override
    protected void apply(FontFiles files, ResourceManager resources, ProfilerFiller profiler) {
        if (latin != null) latin.close();
        if (cjk != null) cjk.close();
        latin = Face.load(files.latin(), id("dynamic/hud_font/barlow"), LATIN_SIZE, LATIN_TRACKING, LATIN_Y_OFFSET, 'A');
        cjk = Face.load(files.cjk(), id("dynamic/hud_font/noto"), CJK_SIZE, CJK_TRACKING, CJK_Y_OFFSET, 0x4e2d);
    }

    /** A laid-out line keeps exactly the same advances for measurement and drawing. */
    public static final class Text {
        private final List<Run> runs;
        private final float width;

        private Text(List<Run> runs, float width) {
            this.runs = runs;
            this.width = width;
        }

        public int width() { return Mth.ceil(width); }
        public float exactWidth() { return width; }

        public void draw(GuiGraphics graphics, float x, float y, int color) {
            for (var run : runs) {
                graphics.drawString(run.font(), run.text(), x, y + run.yOffset(), color, false);
                x += run.width();
            }
        }
    }

    private record Run(Font font, String text, float width, float yOffset) {}

    public static Text layout(Font fallback, String text) {
        if (text.isEmpty()) return new Text(List.of(), 0);
        // Keep vanilla's formatting and bidirectional processing intact for those strings.
        if (text.indexOf('\u00a7') >= 0 || fallback.isBidirectional())
            return single(fallback, text);

        List<Run> runs = new ArrayList<>();
        float width = 0;
        Face previous = null;
        int start = 0;
        boolean first = true;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            Face selected = INSTANCE.select(codePoint);
            if (!first && selected != previous) {
                width += addRun(runs, fallback, previous, text.substring(start, offset));
                start = offset;
            }
            previous = selected;
            first = false;
            offset += Character.charCount(codePoint);
        }
        width += addRun(runs, fallback, previous, text.substring(start));
        return new Text(List.copyOf(runs), width);
    }

    private static Text single(Font font, String text) {
        float width = font.getSplitter().stringWidth(text);
        return new Text(List.of(new Run(font, text, width, 0)), width);
    }

    private static float addRun(List<Run> runs, Font fallback, Face face, String text) {
        Font font = face == null ? fallback : face.font;
        float width = font.getSplitter().stringWidth(text);
        runs.add(new Run(font, text, width, face == null ? 0 : face.yOffset));
        return width;
    }

    private Face select(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        boolean chinese = script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.BOPOMOFO
                || codePoint >= 0x3000 && codePoint <= 0x303f || codePoint >= 0xff00 && codePoint <= 0xffef;
        Face selected = chinese ? cjk : codePoint < 128 || script == Character.UnicodeScript.LATIN
                || script == Character.UnicodeScript.COMMON ? latin : null;
        return selected != null && selected.request(codePoint) ? selected : null;
    }

}
