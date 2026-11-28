package willow.train.hexie.core.creative;

import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Metadata for a full-row (162x18) scrollable section banner inside a {@link SectionedCreativeTab}.
 * Common-side safe: holds only text components, texture ids, and ARGB colors.
 */
public record BannerSection(
        Component title,
        ResourceLocation texture,
        int overlayColor,
        int textColor
) {
    public static final int DEFAULT_OVERLAY_COLOR = 0x80000000;
    public static final int DEFAULT_TEXT_COLOR = 0xFFFFFFFF;
    public static final int BANNER_WIDTH = 162;
    public static final int BANNER_HEIGHT = 18;

    public BannerSection {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(texture, "texture");
    }

    public BannerSection(Component title, ResourceLocation texture) {
        this(title, texture, DEFAULT_OVERLAY_COLOR, DEFAULT_TEXT_COLOR);
    }

    public static BannerSection of(Component title, ResourceLocation texture) {
        return new BannerSection(title, texture);
    }
}
