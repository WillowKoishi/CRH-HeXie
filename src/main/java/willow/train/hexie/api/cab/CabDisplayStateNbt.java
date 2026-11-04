package willow.train.hexie.api.cab;

import java.util.HashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Transport for immutable display readings, not saved controller state. */
public final class CabDisplayStateNbt {
    private CabDisplayStateNbt() {}

    public static CompoundTag write(CabDisplayState state) {
        return write(state, true);
    }

    /** A cab's route projection; physical readings are carried once by the containing frame. */
    public static CompoundTag writeProjection(CabDisplayState state) {
        return write(state, false);
    }

    private static CompoundTag write(CabDisplayState state, boolean includePhysicalReadings) {
        var tag = new CompoundTag();
        var d = state.dmi();
        if (includePhysicalReadings) {
            tag.putDouble("Speed", state.speedKmh());
            tag.putDouble("Deceleration", d.serviceDeceleration());
            tag.putDouble("Traction", d.traction());
            tag.putDouble("Brake", d.brake());
        }
        tag.putString("Mode", d.mode().name());
        tag.putDouble("Limit", d.lineLimitKmh());
        tag.putString("Compatibility", d.compatibilityStatus());
        tag.putString("Station", d.stationName());
        tag.putString("TrainNumber", d.trainNumber());
        tag.putString("Time", d.time());
        tag.putString("Kilometer", d.kilometerMarker());
        tag.putString("CtcsCode", d.ctcs().code().name());
        if (includePhysicalReadings && d.limits() != null) {
            var limits = d.limits();
            var value = new CompoundTag();
            value.putDouble("Mass", limits.massKg());
            value.putDouble("ServiceBrakeForce", limits.serviceBrakeForceN());
            value.putDouble("ResistanceA", limits.resistanceA());
            value.putDouble("ResistanceB", limits.resistanceB());
            value.putDouble("ResistanceC", limits.resistanceC());
            value.putDouble("BrakingMultiplier", limits.brakingMultiplier());
            tag.put("Limits", value);
        }
        if (d.speedReadings() != null) {
            var speed = new CompoundTag();
            speed.putString("Monitor", d.speedReadings().monitor().name());
            speed.putDouble("Permitted", d.speedReadings().permittedKmh());
            speed.putDouble("Target", d.speedReadings().targetKmh());
            speed.putDouble("ServiceIntervention", d.speedReadings().serviceInterventionKmh());
            speed.putDouble("EmergencyIntervention", d.speedReadings().emergencyInterventionKmh());
            tag.put("SpeedReadings", speed);
        }
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
        if (includePhysicalReadings) {
            var lamps = new CompoundTag();
            state.lamps().forEach((key, lamp) -> {
                var value = new CompoundTag();
                value.putInt("Rgb", lamp.rgb()); value.putBoolean("Lit", lamp.lit());
                lamps.put(key, value);
            });
            tag.put("Lamps", lamps);
        }
        return tag;
    }

    public static CabDisplayState read(CompoundTag tag) {
        return read(tag, null);
    }

    public static CabDisplayState readProjection(CabDisplayState base, CompoundTag tag) {
        return read(tag, java.util.Objects.requireNonNull(base));
    }

    private static CabDisplayState read(CompoundTag tag, CabDisplayState base) {
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
        return new CabDisplayState(base == null ? tag.getDouble("Speed") : base.speedKmh(),
                base == null ? lamps : base.lamps(), new DmiState(mode, target,
                tag.contains("Limit", Tag.TAG_ANY_NUMERIC) ? tag.getDouble("Limit") : -1,
                base == null ? tag.getDouble("Deceleration") : base.dmi().serviceDeceleration(),
                base == null ? tag.getDouble("Traction") : base.dmi().traction(),
                base == null ? tag.getDouble("Brake") : base.dmi().brake(),
                tag.getString("Compatibility"), tag.getString("Station"), tag.getString("TrainNumber"),
                tag.getString("Time"), tag.getString("Kilometer"),
                new willow.train.hexie.vehicle.control.CtcsControl.Profile(code, constraints), readSpeedReadings(tag),
                base == null ? readLimits(tag) : base.dmi().limits()));
    }

    private static willow.train.hexie.vehicle.control.TrainControlLimits readLimits(CompoundTag tag) {
        if (!tag.contains("Limits", Tag.TAG_COMPOUND)) return null;
        var value = tag.getCompound("Limits");
        for (String key : new String[]{"Mass", "ServiceBrakeForce", "ResistanceA", "ResistanceB", "ResistanceC",
                "BrakingMultiplier"})
            if (!value.contains(key, Tag.TAG_ANY_NUMERIC)) return null;
        try {
            return new willow.train.hexie.vehicle.control.TrainControlLimits(value.getDouble("Mass"),
                    value.getDouble("ServiceBrakeForce"), value.getDouble("ResistanceA"), value.getDouble("ResistanceB"),
                    value.getDouble("ResistanceC"), value.getDouble("BrakingMultiplier"));
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static DmiSpeedReadings readSpeedReadings(CompoundTag tag) {
        if (!tag.contains("SpeedReadings", Tag.TAG_COMPOUND)) return null;
        var speed = tag.getCompound("SpeedReadings");
        try {
            return new DmiSpeedReadings(DmiSpeedReadings.Monitor.valueOf(speed.getString("Monitor")),
                    knownSpeed(speed, "Permitted"), knownSpeed(speed, "Target"),
                    knownSpeed(speed, "ServiceIntervention"), knownSpeed(speed, "EmergencyIntervention"));
        } catch (IllegalArgumentException invalid) {
            return DmiSpeedReadings.NONE;
        }
    }

    private static double knownSpeed(CompoundTag tag, String key) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getDouble(key) : -1;
    }
}
