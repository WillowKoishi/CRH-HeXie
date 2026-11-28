package willow.train.hexie.core.creative;

import java.util.List;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Metadata for a full-row (162x18) scrollable section banner inside a {@link SectionedCreativeTab}.
 * Supports static 162x18 textures, multi-layer seamless horizontal parallax scrolling inside a fixed
 * 1px frame (160x16 inner viewport), and a foreground parallax stream showcasing real items from the
 * section (sequential or randomly shuffled). Common-side safe.
 */
public record BannerSection(
        Component title,
        ResourceLocation texture,
        int overlayColor,
        int textColor,
        int borderColor,
        List<ParallaxLayer> layers,
        ItemShowcase itemShowcase
) {
    public static final int DEFAULT_OVERLAY_COLOR = 0x80000000;
    public static final int DEFAULT_TEXT_COLOR = 0xFFFFFFFF;
    public static final int DEFAULT_BORDER_COLOR = 0xFFE2E8F0;
    public static final int BANNER_WIDTH = 162;
    public static final int BANNER_HEIGHT = 18;
    public static final int INNER_WIDTH = BANNER_WIDTH - 2;
    public static final int INNER_HEIGHT = BANNER_HEIGHT - 2;

    public BannerSection {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(texture, "texture");
        layers = layers == null ? List.of() : List.copyOf(layers);
        itemShowcase = itemShowcase == null ? ItemShowcase.DISABLED : itemShowcase;
    }

    public BannerSection(Component title, ResourceLocation texture) {
        this(title, texture, DEFAULT_OVERLAY_COLOR, DEFAULT_TEXT_COLOR, DEFAULT_BORDER_COLOR, List.of(), ItemShowcase.DISABLED);
    }

    public BannerSection(Component title, ResourceLocation texture, int overlayColor, int textColor) {
        this(title, texture, overlayColor, textColor, DEFAULT_BORDER_COLOR, List.of(), ItemShowcase.DISABLED);
    }

    public static BannerSection of(Component title, ResourceLocation texture) {
        return new BannerSection(title, texture);
    }

    public static BannerSection parallax(
            Component title,
            ResourceLocation fallbackTexture,
            int borderColor,
            List<ParallaxLayer> layers
    ) {
        return new BannerSection(title, fallbackTexture, DEFAULT_OVERLAY_COLOR, DEFAULT_TEXT_COLOR, borderColor, layers, ItemShowcase.DISABLED);
    }

    public static BannerSection parallaxWithItems(
            Component title,
            ResourceLocation fallbackTexture,
            int borderColor,
            List<ParallaxLayer> layers,
            ItemShowcase itemShowcase
    ) {
        return new BannerSection(title, fallbackTexture, DEFAULT_OVERLAY_COLOR, DEFAULT_TEXT_COLOR, borderColor, layers, itemShowcase);
    }

    public boolean hasParallaxLayers() {
        return !this.layers.isEmpty();
    }

    public boolean hasItemShowcase() {
        return this.itemShowcase.enabled();
    }

    /**
     * One seamlessly horizontally tileable layer rendered inside a banner's 160x16 viewport.
     */
    public record ParallaxLayer(
            ResourceLocation texture,
            int textureWidth,
            int textureHeight,
            float speedPxPerSec,
            float scrollParallax
    ) {
        public ParallaxLayer {
            Objects.requireNonNull(texture, "texture");
            if (textureWidth <= 0 || textureHeight <= 0) {
                throw new IllegalArgumentException("ParallaxLayer dimensions must be positive");
            }
        }

        public ParallaxLayer(ResourceLocation texture, float speedPxPerSec, float scrollParallax) {
            this(texture, INNER_WIDTH, INNER_HEIGHT, speedPxPerSec, scrollParallax);
        }

        public ParallaxLayer(ResourceLocation texture, float speedPxPerSec) {
            this(texture, INNER_WIDTH, INNER_HEIGHT, speedPxPerSec, 0.0F);
        }
    }

    /**
     * Configures a foreground parallax stream of real {@code ItemStack}s belonging to the banner's section.
     *
     * @param enabled        whether to draw scrolling item icons from this section
     * @param randomize      whether to shuffle items per cycle (without consecutive repeats when >= 2 items)
     * @param speedPxPerSec  horizontal scroll speed in GUI pixels/sec (positive scrolls left)
     * @param scrollParallax extra horizontal parallax shift across the full creative scrollbar range
     * @param itemPitch      horizontal distance between consecutive item slots in GUI pixels
     * @param itemScale      render scale applied to 16x16 Minecraft item icons (e.g. 0.875 = 14x14 px)
     */
    public record ItemShowcase(
            boolean enabled,
            boolean randomize,
            float speedPxPerSec,
            float scrollParallax,
            int itemPitch,
            float itemScale
    ) {
        public static final ItemShowcase DISABLED = new ItemShowcase(false, false, 0.0F, 0.0F, 24, 0.875F);

        public ItemShowcase {
            if (enabled) {
                if (itemPitch <= 0) {
                    throw new IllegalArgumentException("itemPitch must be positive");
                }
                if (itemScale <= 0.0F || itemScale > 1.0F) {
                    throw new IllegalArgumentException("itemScale must be in (0, 1]");
                }
            }
        }

        public static ItemShowcase randomParallax(float speedPxPerSec, float scrollParallax) {
            return new ItemShowcase(true, true, speedPxPerSec, scrollParallax, 24, 0.875F);
        }

        public static ItemShowcase sequentialParallax(float speedPxPerSec, float scrollParallax) {
            return new ItemShowcase(true, false, speedPxPerSec, scrollParallax, 24, 0.875F);
        }
    }
}
