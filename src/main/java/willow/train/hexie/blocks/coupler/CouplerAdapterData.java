package willow.train.hexie.blocks.coupler;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import willow.train.hexie.vehicle.runtime.CouplerAdapter;

/** Shared block-entity and item custom-data format; absent or malformed adapters are ignored. */
public final class CouplerAdapterData {
    public static final String KEY = "HexieCouplerAdapter";

    private CouplerAdapterData() {}

    public static CouplerAdapter read(CompoundTag root) {
        if (root == null || !root.contains(KEY, Tag.TAG_COMPOUND)) return null;
        CompoundTag tag = root.getCompound(KEY);
        try {
            return decode(tag);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static CouplerAdapter decode(CompoundTag tag) {
        var layers = new java.util.ArrayList<CouplerAdapter>();
        for (Tag layer : tag.getList("Layers", Tag.TAG_COMPOUND)) layers.add(decode((CompoundTag) layer));
        return new CouplerAdapter(tag.getString("Item"), tag.getString("FirstModel"),
                tag.getString("SecondModel"), tag.getDouble("Thickness"), layers);
    }

    public static CompoundTag encode(CouplerAdapter adapter) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Item", adapter.itemId());
        tag.putString("FirstModel", adapter.firstModel());
        tag.putString("SecondModel", adapter.secondModel());
        tag.putDouble("Thickness", adapter.thickness());
        if (!adapter.layers().isEmpty()) {
            var layers = new net.minecraft.nbt.ListTag();
            adapter.layers().forEach(layer -> layers.add(encode(layer)));
            tag.put("Layers", layers);
        }
        return tag;
    }

    public static void write(CompoundTag root, CouplerAdapter adapter) {
        if (adapter == null) { root.remove(KEY); return; }
        root.put(KEY, encode(adapter));
    }
}
