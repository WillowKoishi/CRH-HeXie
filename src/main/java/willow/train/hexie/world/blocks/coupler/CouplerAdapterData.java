package willow.train.hexie.world.blocks.coupler;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.CouplingBridge;
import willow.train.hexie.core.api.vehicle.Point3;

/** Shared block-entity and item custom-data format; absent or malformed adapters are ignored. */
public final class CouplerAdapterData {
    public static final String KEY = "HexieCouplerAdapter";

    private CouplerAdapterData() {}

    public static CouplingBridge.CouplerAdapterView read(CompoundTag root) {
        if (root == null || !root.contains(KEY, Tag.TAG_COMPOUND)) return null;
        CompoundTag tag = root.getCompound(KEY);
        try {
            return decode(tag);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static CouplingBridge.CouplerAdapterView decode(CompoundTag tag) {
        var layers = new ArrayList<CouplingBridge.CouplerAdapterView>();
        for (Tag layer : tag.getList("Layers", Tag.TAG_COMPOUND)) layers.add(decode((CompoundTag) layer));
        double thickness = tag.getDouble("Thickness");
        if (!tag.contains("Offset")) return HexieBridges.coupling().couplerAdapter().create(tag.getString("Item"), tag.getString("FirstModel"),
                tag.getString("SecondModel"), thickness, layers);
        if (!tag.contains("Offset", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Invalid adapter offset");
        var offset = tag.getCompound("Offset");
        for (var key : List.of("X", "Y", "Z"))
            if (!offset.contains(key, Tag.TAG_DOUBLE)) throw new IllegalArgumentException("Missing adapter offset " + key);
        return HexieBridges.coupling().couplerAdapter().create(tag.getString("Item"), tag.getString("FirstModel"), tag.getString("SecondModel"),
                thickness, layers, new Point3(
                        offset.getDouble("X"), offset.getDouble("Y"), offset.getDouble("Z")));
    }

    public static CompoundTag encode(CouplingBridge.CouplerAdapterView adapter) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Item", adapter.itemId());
        tag.putString("FirstModel", adapter.firstModel());
        tag.putString("SecondModel", adapter.secondModel());
        tag.putDouble("Thickness", adapter.thickness());
        if (adapter.offset().x() != 0 || adapter.offset().y() != 0) {
            var offset = new CompoundTag();
            offset.putDouble("X", adapter.offset().x()); offset.putDouble("Y", adapter.offset().y());
            offset.putDouble("Z", adapter.offset().z()); tag.put("Offset", offset);
        }
        if (!adapter.layers().isEmpty()) {
            var layers = new ListTag();
            adapter.layers().forEach(layer -> layers.add(encode(layer)));
            tag.put("Layers", layers);
        }
        return tag;
    }

    public static void write(CompoundTag root, CouplingBridge.CouplerAdapterView adapter) {
        if (adapter == null) { root.remove(KEY); return; }
        root.put(KEY, encode(adapter));
    }
}
