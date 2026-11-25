package willow.train.hexie.core.sticker;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Parameters for a locally uploaded image decal projected along the aimed face's plane.
 */
public record DecalStickerParams(
        String imageHash,
        float width,
        float height,
        float rotationDegrees,
        float offsetU,
        float offsetV
) {
    public static final float MIN_SIZE = 1f / 16f;
    public static final float MAX_SIZE = 16f;
    public static final float MAX_OFFSET = 16f;

    public static final DecalStickerParams DEFAULT = new DecalStickerParams("", 1.0f, 1.0f, 0f, 0f, 0f);

    public DecalStickerParams {
        imageHash = imageHash == null ? "" : imageHash.trim();
        width = clamp(width, MIN_SIZE, MAX_SIZE);
        height = clamp(height, MIN_SIZE, MAX_SIZE);
        rotationDegrees = normalizeAngle(rotationDegrees);
        offsetU = clamp(offsetU, -MAX_OFFSET, MAX_OFFSET);
        offsetV = clamp(offsetV, -MAX_OFFSET, MAX_OFFSET);
    }

    public boolean hasImage() { return !imageHash.isEmpty(); }

    public DecalStickerParams withScale(float factor) {
        return new DecalStickerParams(imageHash, width * factor, height * factor, rotationDegrees, offsetU, offsetV);
    }

    public DecalStickerParams withRotation(float deg) {
        return new DecalStickerParams(imageHash, width, height, deg, offsetU, offsetV);
    }

    public DecalStickerParams withOffset(float u, float v) {
        return new DecalStickerParams(imageHash, width, height, rotationDegrees, u, v);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("ImageHash", imageHash);
        tag.putFloat("Width", width);
        tag.putFloat("Height", height);
        tag.putFloat("Rotation", rotationDegrees);
        tag.putFloat("OffsetU", offsetU);
        tag.putFloat("OffsetV", offsetV);
        return tag;
    }

    public static DecalStickerParams load(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return DEFAULT;
        return new DecalStickerParams(
                tag.getString("ImageHash"),
                tag.contains("Width") ? tag.getFloat("Width") : 1.0f,
                tag.contains("Height") ? tag.getFloat("Height") : 1.0f,
                tag.getFloat("Rotation"),
                tag.getFloat("OffsetU"),
                tag.getFloat("OffsetV"));
    }

    public void writeBuf(FriendlyByteBuf buf) {
        buf.writeUtf(imageHash, 128);
        buf.writeFloat(width);
        buf.writeFloat(height);
        buf.writeFloat(rotationDegrees);
        buf.writeFloat(offsetU);
        buf.writeFloat(offsetV);
    }

    public static DecalStickerParams readBuf(FriendlyByteBuf buf) {
        return new DecalStickerParams(
                buf.readUtf(128), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    private static float clamp(float v, float lo, float hi) {
        if (!Float.isFinite(v)) return lo;
        return Math.max(lo, Math.min(hi, v));
    }

    private static float normalizeAngle(float deg) {
        if (!Float.isFinite(deg)) return 0f;
        float r = deg % 360f;
        return r < 0f ? r + 360f : r;
    }
}
