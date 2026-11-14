package willow.train.hexie.vehicle.control;

import com.google.common.collect.MapMaker;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.api.interaction.BlockControl;
import willow.train.hexie.api.cab.CabControlHost;
import willow.train.hexie.api.interaction.ControlState;
import willow.train.hexie.api.traincontrol.CabTrainControl;
import willow.train.hexie.api.traincontrol.CabTrainInput;
import willow.train.hexie.api.traincontrol.CabTrainRequest;
import willow.train.hexie.vehicle.runtime.VehicleRuntime;

/** Vehicle-owned converted state. Occupancy authorizes operations, never continued execution. */
public final class CabModule implements CabTrainInput {
    // Legacy list declarations are stable objects; weak identity keys avoid rescanning or hashing their elements.
    private static final Map<List<? extends BlockControl>, Map<String, BlockControl>> DECLARED_CONTROLS =
            new MapMaker().weakKeys().makeMap();
    public record State(CabController.Input input, CabController.Mode mode, CabBrakeConversion.State brakes,
                        boolean approachHeld, boolean deskEnabled, boolean simple, Map<String, Integer> handlePositions) {
        public static final State NEUTRAL = new State(new CabController.Input(0, 0, 0),
                CabController.Mode.MANUAL, CabBrakeConversion.State.NORMAL, false, false);
        public State(CabController.Input input, CabController.Mode mode, CabBrakeConversion.State brakes,
                     boolean approachHeld, boolean deskEnabled) {
            this(input, mode, brakes, approachHeld, deskEnabled, false);
        }
        public State(CabController.Input input, CabController.Mode mode, CabBrakeConversion.State brakes,
                     boolean approachHeld, boolean deskEnabled, boolean simple) {
            this(input, mode, brakes, approachHeld, deskEnabled, simple, Map.of());
        }
        public State {
            Objects.requireNonNull(input); Objects.requireNonNull(mode); Objects.requireNonNull(brakes);
            handlePositions = Map.copyOf(handlePositions);
        }
        public boolean automatic() { return mode == CabController.Mode.AUTOMATIC; }
        /** Shunting supplies its own enable input while the desk retains its physical setting. */
        public boolean enabled() { return deskEnabled || simple; }
        public State withApproach(boolean held) { return new State(input, mode, brakes, held, deskEnabled, simple, handlePositions); }
        public State withEnabled(boolean deskEnabled) {
            return new State(input, mode, brakes, approachHeld, deskEnabled, simple, handlePositions);
        }
        public CabTrainRequest request() {
            return new CabTrainRequest(input.power(), brakes.serviceBrake(), input.reverser(), deskEnabled, automatic(),
                    brakes.parking(), brakes.emergencyHandle(), false, approachHeld);
        }
        public ControlState surface(CabTrainControl desk) {
            return desk.restore(new ControlState(handlePositions), request());
        }
        public State withSimple(boolean value) {
            if (simple == value) return this;
            return new State(input, mode, brakes, approachHeld, deskEnabled, value, handlePositions);
        }
        /** Replace converted train demands without changing this desk's local operating settings. */
        public State withDemands(CabController.Input input, CabBrakeConversion.State brakes) {
            return new State(input, mode, brakes, approachHeld, deskEnabled, simple, handlePositions);
        }
    }
    private State state = State.NEUTRAL;
    private int keyboardMask;
    public State state() { return state; }
    public CabController.Mode mode() { return state.mode(); }
    public void submit(State input) { state = Objects.requireNonNull(input); }
    @Override public void submit(CabTrainRequest request, double speed) {
        state = accept(state, request, state.handlePositions(), speed);
    }
    /** Held keys are transient and never saved as physical handle positions. */
    public void keyboard(int mask) { SimpleCabControls.requireValidMask(mask); keyboardMask = mask; }
    public int keyboardMask() { return keyboardMask; }
    public CabController.Input effectiveInput(double cabSpeed) { return SimpleCabControls.effectiveInput(state, keyboardMask, cabSpeed); }
    public void releaseApproach() { state = state.withApproach(false); }

    /** Copy exact demands between desks, converting the source direction into the target's frame. */
    public static State synchronize(CabTrainControl desk, State previous, State source, int relativeDirection) {
        var next = previous.withDemands(source.input().direction(relativeDirection), source.brakes());
        next = next.withEnabled(desk.synchronizedEnabled(next.request()));
        var surface = desk.synchronize(new ControlState(previous.handlePositions()), synchronizedRequest(next));
        return withSurface(next, surface);
    }

    /** Also update any train-control instruments mounted on the target desk. */
    public static State synchronize(CabControlHost host, BlockState block, State previous, State source, int relativeDirection) {
        var next = synchronize(host.trainControl(block), previous, source, relativeDirection);
        var surface = new ControlState(next.handlePositions());
        var request = synchronizedRequest(next);
        for (var mount : host.surfaceGeometry(block).mounts())
            if (mount.module() instanceof CabTrainControl module)
                surface = module.synchronize(surface, request);
        return withSurface(next, surface);
    }

    private static CabTrainRequest synchronizedRequest(State state) {
        return new CabTrainRequest(state.input().power(), state.input().brake(), state.input().reverser(),
                state.deskEnabled(), state.automatic(), state.brakes().parking(),
                state.brakes().emergencyHandle() || state.brakes().emergencyLatched(), false, state.approachHeld());
    }

    private static State withSurface(State state, ControlState surface) {
        return new State(state.input(), state.mode(), state.brakes(), state.approachHeld(),
                state.deskEnabled(), state.simple(), surface.positions());
    }

    /** A rejected request returns null before touching the saved positions or train commands. */
    public static State operateControls(List<? extends BlockControl> declared, CabTrainControl desk,
                                        String id, int value, State previous, double speed) {
        var index = DECLARED_CONTROLS.computeIfAbsent(declared, controls -> {
            var byId = new LinkedHashMap<String, BlockControl>();
            for (var control : controls) byId.putIfAbsent(control.id(), control);
            return Map.copyOf(byId);
        });
        return operateControls(index.get(id), desk, value, previous.surface(desk), previous, speed);
    }

    public static State operateControls(CabControlHost host, BlockState block, String id, int value, State previous, double speed) {
        var surface = host.surfaceGeometry(block);
        var module = surface.owner(id);
        var owner = module == null ? host.trainControl(block)
                : module instanceof CabTrainControl mounted ? mounted : CabTrainControl.NONE;
        return operateControls(surface.control(id), owner, value, host.surfaceState(block, previous), previous, speed);
    }

    /** Resolve the same declared owner used by operateControls, without inferring meaning from control IDs. */
    public static Set<CabTrainControl.Demand> operatedDemands(CabControlHost host, BlockState block, String id) {
        var surface = host.surfaceGeometry(block);
        var control = surface.control(id);
        if (control == null) return Set.of();
        var module = surface.owner(id);
        var owner = module == null ? host.trainControl(block)
                : module instanceof CabTrainControl mounted ? mounted : CabTrainControl.NONE;
        return Set.copyOf(owner.operatedDemands(control));
    }

    private static State operateControls(BlockControl target, CabTrainControl owner,
                                         int value, ControlState before, State previous, double speed) {
        if (target == null || !target.accepts(value)) return null;
        var after = before.after(target, value);
        var result = owner.operate(target, value, before, after, previous.request(), speed);
        return accept(previous, result.request(), result.surface().positions(), speed);
    }

    private static State accept(State previous, CabTrainRequest request, Map<String, Integer> positions, double speed) {
        var brakes = CabBrakeConversion.operate(previous.brakes(), request, speed);
        var mode = request.automatic() ? CabController.Mode.AUTOMATIC : CabController.Mode.MANUAL;
        return new State(new CabController.Input(request.power(), brakes.brake(), request.reverser()), mode,
                brakes.state(), request.approach(), request.enabled(), previous.simple(), positions);
    }
    public VehicleRuntime.Control update(CabController.Mode mode, int direction,
            double speed, AutomaticTrainControl.Target target, double serviceDeceleration) {
        return update(mode, direction, speed, target, serviceDeceleration, StationStopControl.State.RUNNING);
    }
    public VehicleRuntime.Control update(CabController.Mode mode, int direction,
            double speed, AutomaticTrainControl.Target target, double serviceDeceleration, StationStopControl.State stationStop) {
        return CabController.update(mode, state.input().direction(direction), speed, target, serviceDeceleration, stationStop);
    }
    /** Select keyboard output using the existing manual controller or explicit station-approach controller. */
    public VehicleRuntime.Control updateSimple(boolean approachingStation, int direction,
            double speed, AutomaticTrainControl.Target target, double serviceDeceleration, StationStopControl.State stationStop) {
        var mode = approachingStation ? CabController.Mode.AUTOMATIC : CabController.Mode.MANUAL;
        return CabController.update(mode, effectiveInput(speed * direction).direction(direction), speed, target, serviceDeceleration, stationStop);
    }

    /** Apply the desk's output policy before the common aggregator receives independent demands. */
    public VehicleRuntime.Control convertOutput(CabTrainControl desk, VehicleRuntime.Control controlled,
                                               int direction, double speed) {
        var handles = CabController.manual(effectiveInput(speed * direction).direction(direction));
        return desk.output(state.enabled(), handles, controlled);
    }
    public CompoundTag write() {
        var tag = new CompoundTag();
        tag.putInt("CabVersion", 5);
        tag.putBoolean("Enabled", state.deskEnabled());
        tag.putBoolean("Simple", state.simple());
        tag.putDouble("Power", state.input().power()); tag.putDouble("Brake", state.input().brake());
        tag.putInt("Reverser", state.input().reverser()); tag.putBoolean("Automatic", state.mode() == CabController.Mode.AUTOMATIC);
        tag.putDouble("ServiceBrake", state.brakes().serviceBrake());
        tag.putBoolean("Parking", state.brakes().parking()); tag.putBoolean("EmergencyHandle", state.brakes().emergencyHandle());
        tag.putBoolean("Emergency", state.brakes().emergencyLatched()); tag.putBoolean("Released", state.brakes().released());
        if (!state.handlePositions().isEmpty()) {
            var positions = new CompoundTag();
            state.handlePositions().forEach(positions::putInt);
            tag.put("HandlePositions", positions);
        }
        return tag;
    }
    public static CabModule read(CompoundTag tag) {
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
        var brakes = new CabBrakeConversion.State(service, handle, emergency, parking, version >= 2 && tag.getBoolean("Released"));
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
        module.submit(new State(new CabController.Input(power, brake, reverser),
                tag.getBoolean("Automatic") ? CabController.Mode.AUTOMATIC : CabController.Mode.MANUAL, brakes, false, enabled,
                simple, positions));
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
