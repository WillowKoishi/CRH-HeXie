package willow.train.hexie.vehicle.control;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import willow.train.hexie.core.api.cab.CabState;

// split from willow/train/hexie/vehicle/control/CabModule.java
final class CabModuleNbt {
    private final CabModule module;

    CabModuleNbt(CabModule module) {
        this.module = module;
    }

    CompoundTag write() {
        var tag = new CompoundTag();
        tag.putInt("CabVersion", 5);
        tag.putBoolean("Enabled", module.state.deskEnabled());
        tag.putBoolean("Simple", module.state.simple());
        tag.putDouble("Power", module.state.input().power()); tag.putDouble("Brake", module.state.input().brake());
        tag.putInt("Reverser", module.state.input().reverser()); tag.putBoolean("Automatic", module.state.mode() == CabState.Mode.AUTOMATIC);
        tag.putDouble("ServiceBrake", module.state.brakes().serviceBrake());
        tag.putBoolean("Parking", module.state.brakes().parking()); tag.putBoolean("EmergencyHandle", module.state.brakes().emergencyHandle());
        tag.putBoolean("Emergency", module.state.brakes().emergencyLatched()); tag.putBoolean("Released", module.state.brakes().released());
        if (!module.state.handlePositions().isEmpty()) {
            var positions = new CompoundTag();
            module.state.handlePositions().forEach(positions::putInt);
            tag.put("HandlePositions", positions);
        }
        return tag;
    }
    static CabModule read(CompoundTag tag) {
        var module = new CabModule();
        if (tag.isEmpty()) return module;
        if (tag.contains("CabVersion") && !tag.contains("CabVersion", Tag.TAG_INT))
            throw new IllegalArgumentException("Invalid cab version type");
        int version = tag.contains("CabVersion") ? tag.getInt("CabVersion") : 0;
        if (version < 0 || version > 5) throw new IllegalArgumentException("Unsupported cab version " + version);
        required(tag, "Power", version == 0 ? Tag.TAG_INT : Tag.TAG_DOUBLE);
        required(tag, "Reverser", Tag.TAG_INT); required(tag, "Automatic", Tag.TAG_BYTE);
        required(tag, "Parking", Tag.TAG_BYTE); required(tag, "Emergency", Tag.TAG_BYTE);
        if (version == 0) required(tag, "Brake", Tag.TAG_INT);
        else { required(tag, "ServiceBrake", Tag.TAG_DOUBLE); required(tag, "EmergencyHandle", Tag.TAG_BYTE); }
        if (version >= 2) { required(tag, "Brake", Tag.TAG_DOUBLE); required(tag, "Released", Tag.TAG_BYTE); }
        if (version >= 3) required(tag, "Enabled", Tag.TAG_BYTE);
        if (version >= 4) required(tag, "Simple", Tag.TAG_BYTE);
        if (version == 4) required(tag, "DetailedEnabled", Tag.TAG_BYTE);
        int legacyBrake = version == 0 ? tag.getInt("Brake") : 0;
        if (version == 0 && (legacyBrake < 0 || legacyBrake > 11)) throw new IllegalArgumentException("Invalid legacy brake notch");
        double power = version == 0 ? tag.getInt("Power") / 10d : tag.getDouble("Power");
        double service = version == 0 ? Math.min(10, legacyBrake) / 10d : tag.getDouble("ServiceBrake");
        if (version < 2) {
            if (!Double.isFinite(service) || service < 0 || service > 1) throw new IllegalArgumentException("Invalid legacy service brake");
            service *= TrainControl.Source.CAB_MANUAL.serviceBrakeLimit();
        }
        boolean handle = version == 0 ? legacyBrake == 11 : tag.getBoolean("EmergencyHandle");
        boolean emergency = tag.getBoolean("Emergency") || version < 2 && handle;
        boolean parking = tag.getBoolean("Parking");
        double brake = version >= 2 ? tag.getDouble("Brake") : emergency || parking ? 1 : service;
        var brakes = new CabState.BrakeState(service, handle, emergency, parking, version >= 2 && tag.getBoolean("Released"));
        int reverser = tag.getInt("Reverser");
        // Versions 0-2 were the existing reverser-operated console. Migrate that old surface
        // at the format boundary; current modules accept an independent enable input verbatim.
        boolean enabled = version >= 3 ? tag.getBoolean("Enabled") : reverser != 0;
        boolean simple = version >= 4 && tag.getBoolean("Simple");
        // Version 4 stored both the promoted enable and the physical setting on entry to shunting.
        // Recover the desk request, including a switch cleared after entry.
        if (version == 4 && simple) enabled &= tag.getBoolean("DetailedEnabled");
        var positions = new LinkedHashMap<String, Integer>();
        if (tag.contains("HandlePositions")) {
            required(tag, "HandlePositions", Tag.TAG_COMPOUND);
            var savedPositions = tag.getCompound("HandlePositions");
            for (String key : savedPositions.getAllKeys()) {
                required(savedPositions, key, Tag.TAG_INT);
                positions.put(key, savedPositions.getInt(key));
            }
        }
        module.submit(new CabState(new CabState.Input(power, brake, reverser),
                tag.getBoolean("Automatic") ? CabState.Mode.AUTOMATIC : CabState.Mode.MANUAL, brakes, false, enabled,
                simple, positions));
        return module;
    }
    private static void required(CompoundTag tag, String key, int type) {
        if (!tag.contains(key, type)) throw new IllegalArgumentException("Missing or invalid cab field " + key);
    }
    /** Decode once on frame receipt, including the previous single-console frame format. */
    static Map<BlockPos, CabState> readFrames(CompoundTag frame) {
        var result = new LinkedHashMap<BlockPos, CabState>();
        if (frame.contains("Modules", Tag.TAG_LIST)) {
            for (var value : frame.getList("Modules", Tag.TAG_COMPOUND)) {
                var saved = (CompoundTag) value;
                required(saved, "Position", Tag.TAG_LONG); required(saved, "Controls", Tag.TAG_COMPOUND);
                if (result.putIfAbsent(BlockPos.of(saved.getLong("Position")), read(saved.getCompound("Controls")).state()) != null)
                    throw new IllegalArgumentException("Duplicate cab position in frame");
            }
        } else if (frame.contains("Position", Tag.TAG_LONG))
            result.put(BlockPos.of(frame.getLong("Position")), read(frame.getCompound("Controls")).state());
        return Map.copyOf(result);
    }
}
