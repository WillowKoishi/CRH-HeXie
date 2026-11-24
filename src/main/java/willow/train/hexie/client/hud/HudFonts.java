package willow.train.hexie.client.hud;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.providers.FreeTypeUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Optional HUD faces, backed by Minecraft's glyph atlas and its normal font as a fallback. */
public final class HudFonts extends SimplePreparableReloadListener<HudFonts.FontFiles> {
    private static final Logger LOGGER = LoggerFactory.getLogger(HudFonts.class);
    private static final HudFonts INSTANCE = new HudFonts();
    private static final ResourceLocation LATIN_FILE = id("sign_fonts/barlow_condensed_medium.ttf");
    private static final ResourceLocation CJK_FILE = id("sign_fonts/noto_sans_cjk_sc_bold.otf");
    private static final float OVERSAMPLE = 2f;

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
        return selected != null && selected.provider.request(codePoint) ? selected : null;
    }

    private static final class Face {
        final FontSet set;
        final TrueTypeGlyphProvider source;
        final LazyGlyphProvider provider;
        final Font font;
        final float yOffset;

        private Face(FontSet set, TrueTypeGlyphProvider source, LazyGlyphProvider provider, float yOffset) {
            this.set = set;
            this.source = source;
            this.provider = provider;
            this.font = new Font(ignored -> set, false);
            this.yOffset = yOffset;
        }

        static Face load(byte[] bytes, ResourceLocation name, float size, float tracking, float yOffset, int probe) {
            if (bytes == null) return null;
            ByteBuffer memory = null;
            FT_Face nativeFace = null;
            TrueTypeGlyphProvider source = null;
            FontSet set = null;
            try {
                memory = MemoryUtil.memAlloc(bytes.length);
                memory.put(bytes).flip();
                synchronized (FreeTypeUtil.LIBRARY_LOCK) {
                    try (var stack = MemoryStack.stackPush()) {
                        var pointer = stack.mallocPointer(1);
                        FreeTypeUtil.assertError(FreeType.FT_New_Memory_Face(
                                FreeTypeUtil.getLibrary(), memory, 0, pointer), "Initializing HUD font");
                        nativeFace = FT_Face.create(pointer.get(0));
                    }
                    FreeTypeUtil.assertError(FreeType.FT_Select_Charmap(nativeFace, FreeType.FT_ENCODING_UNICODE),
                            "Selecting HUD font character map");
                    // FreeType supports both the CFF OpenType face and the TrueType face in the font pack.
                    source = new TrueTypeGlyphProvider(memory, nativeFace, size, OVERSAMPLE, 0, 0, "");
                }
                var provider = new LazyGlyphProvider(source, tracking);
                if (!provider.request(probe)) throw new IllegalStateException("HUD font has no expected glyph");
                set = new FontSet(Minecraft.getInstance().getTextureManager(), name);
                set.reload(List.of(new GlyphProvider.Conditional(provider, FontOption.Filter.ALWAYS_PASS)), Set.of());
                return new Face(set, source, provider, yOffset);
            } catch (RuntimeException | LinkageError failure) {
                if (set != null) set.close();
                if (source != null) source.close();
                else {
                    if (nativeFace != null) synchronized (FreeTypeUtil.LIBRARY_LOCK) {
                        FreeType.FT_Done_Face(nativeFace);
                    }
                    if (memory != null) MemoryUtil.memFree(memory);
                }
                LOGGER.warn("Unable to load HUD font {}; using Minecraft's font", name, failure);
                return null;
            }
        }

        void close() {
            set.close();
            source.close();
        }
    }

    /**
     * FontSet selects providers using their advertised glyphs. Advertise only requested glyphs
     * so loading a large CJK face does not rasterize its entire character set on resource reload.
     */
    private static final class LazyGlyphProvider implements GlyphProvider {
        private final TrueTypeGlyphProvider source;
        private final float tracking;
        private final IntSet requested = new IntOpenHashSet();
        private final Map<Integer, GlyphInfo> glyphs = new HashMap<>();

        private LazyGlyphProvider(TrueTypeGlyphProvider source, float tracking) {
            this.source = source;
            this.tracking = tracking;
        }

        boolean request(int codePoint) {
            if (!glyphs.containsKey(codePoint)) {
                GlyphInfo base;
                try {
                    base = source.getGlyph(codePoint);
                } catch (RuntimeException failure) {
                    base = null;
                }
                glyphs.put(codePoint, base == null ? null : new TrackedGlyph(base, tracking));
            }
            if (glyphs.get(codePoint) == null) return false;
            requested.add(codePoint);
            return true;
        }

        @Override public GlyphInfo getGlyph(int codePoint) {
            return requested.contains(codePoint) ? glyphs.get(codePoint) : null;
        }

        @Override public IntSet getSupportedGlyphs() { return requested; }
    }

    private record TrackedGlyph(GlyphInfo base, float tracking) implements GlyphInfo {
        @Override public float getAdvance() { return base.getAdvance() + tracking; }
        @Override public float getBoldOffset() { return base.getBoldOffset(); }
        @Override public float getShadowOffset() { return base.getShadowOffset(); }
        @Override public BakedGlyph bake(Function<SheetGlyphInfo, BakedGlyph> stitch) { return base.bake(stitch); }
    }
}
