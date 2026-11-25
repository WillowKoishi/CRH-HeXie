package willow.train.hexie.ui.hud;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.providers.FreeTypeUtil;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;

// split from willow/train/hexie/client/hud/HudFonts.java
final class HudFontFaces {
    static final class Face {
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
                    source = new TrueTypeGlyphProvider(memory, nativeFace, size, HudFonts.OVERSAMPLE, 0, 0, "");
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
                HudFonts.LOGGER.warn("Unable to load HUD font {}; using Minecraft's font", name, failure);
                return null;
            }
        }

        boolean request(int codePoint) { return provider.request(codePoint); }

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
