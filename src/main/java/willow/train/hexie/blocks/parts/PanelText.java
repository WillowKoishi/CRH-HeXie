package willow.train.hexie.blocks.parts;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import willow.train.hexie.blockentity.GroundStationSignEntity;

/** Font size is in model pixels; offsets are in quarter model pixels. Tracking is in hundredths of an em. */
public record PanelText(String text, float size, int x, int y, String font, int spacing) {
    public static final PanelText EMPTY = new PanelText("", 2, 0, 0, "hexie:carriage_marking_chinese", 0);
    public PanelText {
        text = GroundStationSignEntity.clean(text);
        if (!Float.isFinite(size) || size <= 0) size = 2;
        if (net.minecraft.resources.ResourceLocation.tryParse(font) == null || font.isEmpty() || font.length() > 128)
            font = "hexie:carriage_marking_chinese";
    }
    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putString("Text", text);
        tag.putFloat("FontSize", size);
        tag.putInt("X", x);
        tag.putInt("Y", y);
        tag.putString("Font", font);
        tag.putInt("Spacing", spacing);
        return tag;
    }
    public static PanelText load(CompoundTag tag) {
        return new PanelText(tag.getString("Text"),
                tag.contains("FontSize") ? tag.getFloat("FontSize") : tag.getInt("Size") / 4f, tag.getInt("X"),
                tag.getInt("Y"), tag.getString("Font"), tag.getInt("Spacing"));
    }
    public PanelText(FriendlyByteBuf buf) {
        this(buf.readUtf(128), buf.readFloat(), buf.readInt(), buf.readInt(), buf.readUtf(128), buf.readInt());
    }
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(text, 128);
        buf.writeFloat(size);
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeUtf(font, 128);
        buf.writeInt(spacing);
    }
}
