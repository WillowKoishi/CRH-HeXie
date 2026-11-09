package willow.train.hexie.bogey;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** Keeps saved locomotive and QJ entries readable after renaming and splitting their categories. */
public final class BogeyStyleMigration {
    private static final ResourceLocation LEGACY_STYLE = ResourceLocation.parse("hexie:df11gbogey");
    private static final ResourceLocation LOCO_STYLE = ResourceLocation.parse("hexie:loco");
    private static final ResourceLocation STEAM_STYLE = ResourceLocation.parse("hexie:steambogey");
    private static final ResourceLocation DF11G = ResourceLocation.parse("create:small");
    private static final ResourceLocation DF11G_BACKWARD = ResourceLocation.parse("hexie:backward");
    private static final ResourceLocation QJ = ResourceLocation.parse("hexie:qj");
    private static final ResourceLocation QJ_BACKWARD = ResourceLocation.parse("hexie:qj_backward");

    private BogeyStyleMigration() {}

    public static ResourceLocation normalizeStyle(ResourceLocation style, ResourceLocation size) {
        if (!LEGACY_STYLE.equals(style)) return style;
        if (QJ.equals(size) || QJ_BACKWARD.equals(size)) return STEAM_STYLE;
        if (DF11G.equals(size) || DF11G_BACKWARD.equals(size)) return LOCO_STYLE;
        return style;
    }

    public static void normalizeData(CompoundTag data, String styleKey, ResourceLocation size) {
        var saved = ResourceLocation.tryParse(data.getString(styleKey));
        var normalized = normalizeStyle(saved, size);
        if (normalized != saved) data.putString(styleKey, normalized.toString());
    }
}
