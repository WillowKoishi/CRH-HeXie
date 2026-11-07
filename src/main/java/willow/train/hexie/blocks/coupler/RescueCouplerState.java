package willow.train.hexie.blocks.coupler;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import willow.train.hexie.item.RescueCouplerItem;
import willow.train.hexie.vehicle.runtime.CouplerAdapter;

/** The one exposed rescue tongue has its own target; internally mated tongues always stay closed. */
public final class RescueCouplerState {
    private static final String OPEN = "HexieRescueKnuckleOpen", FROM = "HexieRescueKnuckleFrom",
            START = "HexieRescueKnuckleChangedAt", EXPOSED = "HexieRescueKnuckleExposed",
            COUPLED = "HexieRescueKnuckleCoupled";
    private static final List<String> KEYS = List.of(OPEN, FROM, START, EXPOSED, COUPLED);
    private RescueCouplerState() {}

    public static boolean installed(CouplerAdapter adapter) {
        return adapter != null && adapter.items().stream().anyMatch(RescueCouplerState::isRescue);
    }
    public static boolean isRescue(CouplerAdapter layer) { return layer.itemId().equals(RescueCouplerItem.ID); }
    public static boolean exposed(CouplerAdapter adapter, String baseModel, int layerIndex) {
        if (adapter == null || layerIndex != adapter.items().size() - 1) return false;
        String model = baseModel;
        var items = adapter.items();
        for (int i = 0; i < items.size(); i++) {
            var layer = items.get(i);
            if (i == layerIndex) return isRescue(layer) && model.equals(layer.firstModel())
                    && layer.secondModel().equals(RescueCouplerItem.KNUCKLE_MODEL);
            model = layer.otherModel(model);
        }
        return false;
    }
    public static boolean exposed(CouplerAdapter adapter, String baseModel) {
        return adapter != null && exposed(adapter, baseModel, adapter.items().size() - 1);
    }
    public static boolean open(CompoundTag data) { return data == null || !data.contains(OPEN) || data.getBoolean(OPEN); }
    public static float progress(CompoundTag data, long now, float partialTick) {
        float target = open(data) ? 1 : 0;
        if (data == null || !data.contains(FROM) || !data.contains(START)) return target;
        return Mth.approach(Mth.clamp(data.getFloat(FROM), 0, 1), target,
                Math.max(0, now - data.getLong(START) + partialTick) * .1f);
    }
    public static float layerProgress(CouplerAdapter adapter, String baseModel, int layerIndex,
            CompoundTag data, long now, float partialTick) {
        return exposed(adapter, baseModel, layerIndex) ? progress(data, now, partialTick) : 0;
    }
    public static CompoundTag withOpen(CompoundTag data, boolean open, long now) {
        var next = data == null ? new CompoundTag() : data.copy();
        if (!next.contains(OPEN) || open(next) != open) {
            next.putFloat(FROM, progress(data, now, 0));
            next.putLong(START, now);
            next.putBoolean(OPEN, open);
        }
        return next;
    }
    /** State changes reset the target; a manual free-end setting survives ordinary pose updates. */
    public static CompoundTag sync(CompoundTag data, CouplerAdapter adapter, String baseModel, boolean coupled, long now) {
        var next = data == null ? new CompoundTag() : data.copy();
        if (!installed(adapter)) { KEYS.forEach(next::remove); return next; }
        boolean exposed = exposed(adapter, baseModel);
        if (!next.contains(EXPOSED) || next.getBoolean(EXPOSED) != exposed
                || !next.contains(COUPLED) || next.getBoolean(COUPLED) != coupled) {
            next = withOpen(next, exposed && !coupled, now);
            next.putBoolean(EXPOSED, exposed); next.putBoolean(COUPLED, coupled);
        }
        return next;
    }
    public static CompoundTag copyState(CompoundTag data) {
        var result = new CompoundTag();
        copyState(data, result);
        return result;
    }
    public static void copyState(CompoundTag from, CompoundTag to) {
        for (var key : KEYS) {
            if (from != null && from.contains(key)) to.put(key, from.get(key).copy());
            else to.remove(key);
        }
    }
}
