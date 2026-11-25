package willow.train.hexie.core.sticker;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Full parametric specification for a custom window sticker:
 * frame color, glass color + alpha, frame thickness, corner radius, width, height, and plane position (offsetU, offsetV).
 */
public record WindowStickerParams(
        int frameColorRgb,
        int glassColorRgb,
        float glassAlpha,
        float frameThickness,
        float cornerRadius,
        float width,
        float height,
        float offsetU,
        float offsetV
) {
    public static final float MIN_SIZE = 1f / 16f;
    public static final float MAX_SIZE = 16f;
    public static final float MAX_OFFSET = 16f;

    public static final WindowStickerParams DEFAULT = new WindowStickerParams(
            0x2B2F36, 0x8EC5E8, 0.38f, 0.0625f, 0.125f, 0.875f, 0.875f, 0f, 0f);

    public WindowStickerParams {
        frameColorRgb &= 0x00FFFFFF;
        glassColorRgb &= 0x00FFFFFF;
        glassAlpha = clamp(glassAlpha, 0f, 1f);
        width = clamp(width, MIN_SIZE, MAX_SIZE);
        height = clamp(height, MIN_SIZE, MAX_SIZE);
        float maxHalf = Math.min(width, height) * 0.5f;
        frameThickness = clamp(frameThickness, 0f, maxHalf);
        cornerRadius = clamp(cornerRadius, 0f, maxHalf);
        offsetU = clamp(offsetU, -MAX_OFFSET, MAX_OFFSET);
        offsetV = clamp(offsetV, -MAX_OFFSET, MAX_OFFSET);
    }

    public WindowStickerParams withOffset(float u, float v) {
        return new WindowStickerParams(frameColorRgb, glassColorRgb, glassAlpha, frameThickness, cornerRadius, width, height, u, v);
    }

    public WindowStickerParams withSize(float w, float h) {
        return new WindowStickerParams(frameColorRgb, glassColorRgb, glassAlpha, frameThickness, cornerRadius, w, h, offsetU, offsetV);
    }

    public int frameArgb() { return 0xFF000000 | frameColorRgb; }

    public int glassArgb() {
        int a = Math.max(0, Math.min(255, Math.round(glassAlpha * 255f)));
        return (a << 24) | glassColorRgb;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("FrameColor", frameColorRgb);
        tag.putInt("GlassColor", glassColorRgb);
        tag.putFloat("GlassAlpha", glassAlpha);
        tag.putFloat("FrameThickness", frameThickness);
        tag.putFloat("CornerRadius", cornerRadius);
        tag.putFloat("Width", width);
        tag.putFloat("Height", height);
        tag.putFloat("OffsetU", offsetU);
        tag.putFloat("OffsetV", offsetV);
        return tag;
    }

    public static WindowStickerParams load(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return DEFAULT;
        return new WindowStickerParams(
                tag.contains("FrameColor") ? tag.getInt("FrameColor") : DEFAULT.frameColorRgb,
                tag.contains("GlassColor") ? tag.getInt("GlassColor") : DEFAULT.glassColorRgb,
                tag.contains("GlassAlpha") ? tag.getFloat("GlassAlpha") : DEFAULT.glassAlpha,
                tag.contains("FrameThickness") ? tag.getFloat("FrameThickness") : DEFAULT.frameThickness,
                tag.contains("CornerRadius") ? tag.getFloat("CornerRadius") : DEFAULT.cornerRadius,
                tag.contains("Width") ? tag.getFloat("Width") : DEFAULT.width,
                tag.contains("Height") ? tag.getFloat("Height") : DEFAULT.height,
                tag.getFloat("OffsetU"),
                tag.getFloat("OffsetV"));
    }

    public void writeBuf(FriendlyByteBuf buf) {
        buf.writeInt(frameColorRgb);
        buf.writeInt(glassColorRgb);
        buf.writeFloat(glassAlpha);
        buf.writeFloat(frameThickness);
        buf.writeFloat(cornerRadius);
        buf.writeFloat(width);
        buf.writeFloat(height);
        buf.writeFloat(offsetU);
        buf.writeFloat(offsetV);
    }

    public static WindowStickerParams readBuf(FriendlyByteBuf buf) {
        return new WindowStickerParams(
                buf.readInt(), buf.readInt(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    private static float clamp(float v, float lo, float hi) {
        if (!Float.isFinite(v)) return lo;
        return Math.max(lo, Math.min(hi, v));
    }
}
