package willow.train.hexie.client.util;

import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

import willow.train.hexie.Main;

public class HexieFontStyle {

    public static final ResourceLocation SOURCE_HANS_SANS_CN =
            ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "source_han_sans_cn-medium");
    public static final Style HEXIE_STANDARD_STYLE = Style.EMPTY.withFont(SOURCE_HANS_SANS_CN);

    public static void apply() {}
    ;
}
