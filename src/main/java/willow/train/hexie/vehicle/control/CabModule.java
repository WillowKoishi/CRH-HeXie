package willow.train.hexie.vehicle.control;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Vehicle-owned converted state. Occupancy authorizes operations, never continued execution. */
public final class CabModule {
    public record State(CabController.Input input, CabController.Mode mode, CabBrakeConversion.State brakes,
                        boolean approachHeld) {
        public static final State NEUTRAL = new State(new CabController.Input(0, 0, 0),
                CabController.Mode.MANUAL, CabBrakeConversion.State.NORMAL, false);
        public State { Objects.requireNonNull(input); Objects.requireNonNull(mode); Objects.requireNonNull(brakes); }
        public boolean automatic() { return mode == CabController.Mode.AUTOMATIC; }
        public State withApproach(boolean held) { return new State(input, mode, brakes, held); }
    }
    private State state = State.NEUTRAL;
    public State state() { return state; }
    public CabController.Mode mode() { return state.mode(); }
    public void submit(State input) { state = Objects.requireNonNull(input); }
    public void releaseApproach() { state = state.withApproach(false); }
    public willow.train.hexie.vehicle.runtime.VehicleRuntime.Control update(CabController.Mode mode, int direction,
            double speed, AutomaticTrainControl.Target target, double serviceDeceleration) {
        return CabController.update(mode, state.input().direction(direction), speed, target, serviceDeceleration);
    }
    public CompoundTag write() {
        var tag = new CompoundTag();
        tag.putInt("CabVersion", 2);
        tag.putDouble("Power", state.input().power()); tag.putDouble("Brake", state.input().brake());
        tag.putInt("Reverser", state.input().reverser()); tag.putBoolean("Automatic", state.automatic());
        tag.putDouble("ServiceBrake", state.brakes().serviceBrake());
        tag.putBoolean("Parking", state.brakes().parking()); tag.putBoolean("EmergencyHandle", state.brakes().emergencyHandle());
        tag.putBoolean("Emergency", state.brakes().emergencyLatched()); tag.putBoolean("Released", state.brakes().released());
        return tag;
    }
    public static CabModule read(CompoundTag tag) {
        var module = new CabModule();
        if (tag.isEmpty()) return module;
        if (tag.contains("CabVersion") && !tag.contains("CabVersion", Tag.TAG_INT))
            throw new IllegalArgumentException("Invalid cab version type");
        int version = tag.contains("CabVersion") ? tag.getInt("CabVersion") : 0;
        if (version < 0 || version > 2) throw new IllegalArgumentException("Unsupported cab version " + version);
        required(tag, "Power", version == 0 ? Tag.TAG_INT : Tag.TAG_DOUBLE);
        required(tag, "Reverser", Tag.TAG_INT); required(tag, "Automatic", Tag.TAG_BYTE);
        required(tag, "Parking", Tag.TAG_BYTE); required(tag, "Emergency", Tag.TAG_BYTE);
        if (version == 0) required(tag, "Brake", Tag.TAG_INT);
        else { required(tag, "ServiceBrake", Tag.TAG_DOUBLE); required(tag, "EmergencyHandle", Tag.TAG_BYTE); }
        if (version == 2) { required(tag, "Brake", Tag.TAG_DOUBLE); required(tag, "Released", Tag.TAG_BYTE); }
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
        double brake = version == 2 ? tag.getDouble("Brake") : emergency || parking ? 1 : service;
        var brakes = new CabBrakeConversion.State(service, handle, emergency, parking, version == 2 && tag.getBoolean("Released"));
        module.submit(new State(new CabController.Input(power, brake, tag.getInt("Reverser")),
                tag.getBoolean("Automatic") ? CabController.Mode.AUTOMATIC : CabController.Mode.MANUAL, brakes, false));
        return module;
    }
    private static void required(CompoundTag tag, String key, int type) {
        if (!tag.contains(key, type)) throw new IllegalArgumentException("Missing or invalid cab field " + key);
    }
    /** Decode once on frame receipt, including the previous single-console frame format. */
    public static Map<BlockPos, State> readFrames(CompoundTag frame) {
        var result = new LinkedHashMap<BlockPos, State>();
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
