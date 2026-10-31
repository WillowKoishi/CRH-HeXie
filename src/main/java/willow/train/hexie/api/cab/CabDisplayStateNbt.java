package willow.train.hexie.api.cab;

import java.util.HashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Transport for immutable display readings, not saved controller state. */
public final class CabDisplayStateNbt {
    private CabDisplayStateNbt() {}

    public static CompoundTag write(CabDisplayState state) {
        var tag = new CompoundTag();
        tag.putDouble("Speed", state.speedKmh());
        var d = state.dmi();
        tag.putString("Mode", d.mode().name());
        tag.putDouble("Limit", d.lineLimitKmh());
        tag.putDouble("Deceleration", d.serviceDeceleration());
        tag.putDouble("Traction", d.traction());
        tag.putDouble("Brake", d.brake());
        tag.putString("Compatibility", d.compatibilityStatus());
        tag.putString("Station", d.stationName());
        tag.putString("TrainNumber", d.trainNumber());
        tag.putString("Time", d.time());
        tag.putString("Kilometer", d.kilometerMarker());
        tag.putString("CtcsCode", d.ctcs().code().name());
        var constraints = new net.minecraft.nbt.ListTag();
        for (var point : d.ctcs().targets()) {
            var value = new CompoundTag();
            value.putDouble("Distance", point.distanceM()); value.putDouble("Speed", point.speedKmh());
            constraints.add(value);
        }
        tag.put("CtcsTargets", constraints);
        if (d.target() != null) {
            var target = new CompoundTag();
            target.putString("Kind", d.target().kind().name());
            target.putString("Name", d.target().name());
            target.putDouble("Distance", d.target().distanceM());
            target.putDouble("Speed", d.target().speedKmh());
            tag.put("Target", target);
        }
        var lamps = new CompoundTag();
        state.lamps().forEach((key, lamp) -> {
            var value = new CompoundTag();
            value.putInt("Rgb", lamp.rgb()); value.putBoolean("Lit", lamp.lit());
            lamps.put(key, value);
        });
        tag.put("Lamps", lamps);
        return tag;
    }

    public static CabDisplayState read(CompoundTag tag) {
        var mode = DmiState.Mode.UNKNOWN;
        try { mode = DmiState.Mode.valueOf(tag.getString("Mode")); }
        catch (IllegalArgumentException ignored) { }
        DmiState.Target target = null;
        if (tag.contains("Target", Tag.TAG_COMPOUND)) {
            var t = tag.getCompound("Target");
            try {
                if (t.contains("Distance", Tag.TAG_ANY_NUMERIC) && t.contains("Speed", Tag.TAG_ANY_NUMERIC))
                    target = new DmiState.Target(DmiState.TargetKind.valueOf(t.getString("Kind")),
                            t.getString("Name"), t.getDouble("Distance"), t.getDouble("Speed"));
            } catch (IllegalArgumentException ignored) { }
        }
        var lamps = new HashMap<String, CabDisplayState.Lamp>();
        var values = tag.getCompound("Lamps");
        for (var key : values.getAllKeys()) {
            var lamp = values.getCompound(key);
            lamps.put(key, new CabDisplayState.Lamp(lamp.getInt("Rgb"), lamp.getBoolean("Lit")));
        }
        var code = willow.train.hexie.vehicle.control.CtcsControl.Code.UNKNOWN;
        try { code = willow.train.hexie.vehicle.control.CtcsControl.Code.valueOf(tag.getString("CtcsCode")); }
        catch (IllegalArgumentException ignored) { }
        var constraints = new java.util.ArrayList<willow.train.hexie.vehicle.control.CtcsControl.SpeedTarget>();
        var points = tag.getList("CtcsTargets", Tag.TAG_COMPOUND);
        for (int i = 0; i < points.size(); i++) {
            var point = points.getCompound(i);
            if (!point.contains("Distance", Tag.TAG_ANY_NUMERIC) || !point.contains("Speed", Tag.TAG_ANY_NUMERIC)) continue;
            try { constraints.add(new willow.train.hexie.vehicle.control.CtcsControl.SpeedTarget(
                    point.getDouble("Distance"), point.getDouble("Speed"))); }
            catch (IllegalArgumentException ignored) { }
        }
        return new CabDisplayState(tag.getDouble("Speed"), lamps, new DmiState(mode, target,
                tag.contains("Limit", Tag.TAG_ANY_NUMERIC) ? tag.getDouble("Limit") : -1,
                tag.getDouble("Deceleration"), tag.getDouble("Traction"), tag.getDouble("Brake"),
                tag.getString("Compatibility"), tag.getString("Station"), tag.getString("TrainNumber"),
                tag.getString("Time"), tag.getString("Kilometer"),
                new willow.train.hexie.vehicle.control.CtcsControl.Profile(code, constraints)));
    }
}
