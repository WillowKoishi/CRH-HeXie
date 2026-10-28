package willow.train.hexie.client.sign;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/** Shared profiles and adapter for older sign renderers with downward-positive pixel poses. */
public final class SignLettering {
    public static final ResourceLocation ROUTE_NAME = id("route_name"), ROUTE_PINYIN = id("route_pinyin"),
            ROUTE_SERVICE = id("route_service"), ROUTE_NUMBER = id("route_number"),
            CARRIAGE_NUMBER = id("carriage_number"), VEHICLE_CHINESE = id("vehicle_chinese"),
            VEHICLE_PINYIN = id("vehicle_pinyin"), VEHICLE_CODE = id("vehicle_code"), SPEED = id("speed_mark");
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("hexie", path); }
    public static String text(FormattedCharSequence sequence) {
        StringBuilder text = new StringBuilder(); sequence.accept((index, style, codePoint) -> { text.appendCodePoint(codePoint); return true; });
        return text.toString();
    }
    public static int width(ResourceLocation profile, FormattedCharSequence text) {
        return (int) Math.ceil(SignTextRenderer.width(profile, text(text), 8, 180));
    }
    public static void draw(ResourceLocation profile, FormattedCharSequence text, float x, float y, int color,
                            PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose(); pose.scale(1, -1, 1);
        SignTextRenderer.drawLine(profile, text(text), pose, buffers, x, -y, 8, 180,
                color | 0xFF000000, light, SignTextRenderer.Alignment.LEFT);
        pose.popPose();
    }
    private SignLettering() {}
}
