package willow.train.hexie.vehicle.control;

// split from willow/train/hexie/vehicle/control/CabModule.java

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.bridge.vehicle.CabControlBridge;
import willow.train.hexie.bridge.vehicle.CabModuleView;
import willow.train.hexie.core.api.cab.CabControlHost;
import willow.train.hexie.core.api.cab.CabState;
import willow.train.hexie.core.api.interaction.BlockControl;
import willow.train.hexie.core.api.traincontrol.CabTrainControl;
import willow.train.hexie.core.api.traincontrol.CabTrainRequest;
import willow.train.hexie.core.api.traincontrol.TrainCommand;

/** Vehicle-owned converted state. Occupancy authorizes operations, never continued execution. */
public final class CabModule implements CabModuleView {
    private final CabModuleNbt nbt = new CabModuleNbt(this);
    // Legacy list declarations are stable objects; weak identity keys avoid rescanning or hashing their elements.

    CabState state = CabState.NEUTRAL;
    private int keyboardMask;
    public CabState state() { return state; }
    public CabState.Mode mode() { return state.mode(); }
    public void submit(CabState input) { state = Objects.requireNonNull(input); }
    @Override public void submit(CabTrainRequest request, double speed) {
        state = accept(state, request, state.handlePositions(), speed);
    }
    /** Held keys are transient and never saved as physical handle positions. */
    public void keyboard(int mask) { SimpleCabControls.requireValidMask(mask); keyboardMask = mask; }
    public int keyboardMask() { return keyboardMask; }
    public CabState.Input effectiveInput(double cabSpeed) { return SimpleCabControls.effectiveInput(state, keyboardMask, cabSpeed); }
    public void releaseApproach() { state = state.withApproach(false); }

    /** Copy exact demands between desks, converting the source direction into the target's frame. */
    public static CabState synchronize(CabTrainControl desk, CabState previous, CabState source, int relativeDirection) {
        return CabDeskControls.synchronize(desk, previous, source, relativeDirection);
    }

    /** Also update any train-control instruments mounted on the target desk. */
    public static CabState synchronize(CabControlHost host, BlockState block, CabState previous, CabState source, int relativeDirection) {
        return CabDeskControls.synchronize(host, block, previous, source, relativeDirection);
    }

    /** Bring older or synchronized requests into the range represented by this physical desk. */
    public static CabState normalize(CabTrainControl desk, CabState state) {
        return CabDeskControls.normalize(desk, state);
    }

    /** A rejected request returns null before touching the saved positions or train commands. */
    public static CabState operateControls(List<? extends BlockControl> declared, CabTrainControl desk,
                                        String id, int value, CabState previous, double speed) {
        return CabDeskControls.operateControls(declared, desk, id, value, previous, speed);
    }

    public static CabState operateControls(CabControlHost host, BlockState block, String id, int value, CabState previous, double speed) {
        return CabDeskControls.operateControls(host, block, id, value, previous, speed);
    }

    /** Resolve the same declared owner used by operateControls, without inferring meaning from control IDs. */
    public static Set<CabTrainControl.Demand> operatedDemands(CabControlHost host, BlockState block, String id) {
        return CabDeskControls.operatedDemands(host, block, id);
    }

    static CabState accept(CabState previous, CabTrainRequest request, Map<String, Integer> positions, double speed) {
        return CabDeskControls.accept(previous, request, positions, speed);
    }
    public TrainCommand update(CabState.Mode mode, int direction,
            double speed, CabControlBridge.AutomaticTrainControlTargetView target, double serviceDeceleration) {
        return update(mode, direction, speed, target, serviceDeceleration, StationStopControl.State.RUNNING);
    }
    public TrainCommand update(CabState.Mode mode, int direction,
            double speed, CabControlBridge.AutomaticTrainControlTargetView target, double serviceDeceleration, CabControlBridge.StationStopControlStateView stationStop) {
        return CabController.update(mode, state.input().direction(direction), speed, (AutomaticTrainControl.Target) target, serviceDeceleration, (StationStopControl.State) stationStop);
    }
    /** Select keyboard output using the existing manual controller or explicit station-approach controller. */
    public TrainCommand updateSimple(boolean approachingStation, int direction,
            double speed, CabControlBridge.AutomaticTrainControlTargetView target, double serviceDeceleration, CabControlBridge.StationStopControlStateView stationStop) {
        var mode = approachingStation ? CabState.Mode.AUTOMATIC : CabState.Mode.MANUAL;
        return CabController.update(mode, effectiveInput(speed * direction).direction(direction), speed, (AutomaticTrainControl.Target) target, serviceDeceleration, (StationStopControl.State) stationStop);
    }

    /** Apply the desk's output policy before the common aggregator receives independent demands. */
    public TrainCommand convertOutput(CabTrainControl desk, TrainCommand controlled,
                                               int direction, double speed) {
        return convertOutput(desk, controlled, direction, speed, true, state.automatic() || state.approachHeld());
    }
    public TrainCommand convertOutput(CabTrainControl desk, TrainCommand controlled,
                                               int direction, double speed, boolean selected, boolean routeControl) {
        var handles = CabController.manual(effectiveInput(speed * direction).direction(direction));
        var context = new CabTrainControl.OutputContext(state.enabled(), selected, state.simple(), routeControl,
                state.surface(desk), state.request(), direction, speed);
        return desk.output(context, handles, controlled);
    }
    public CompoundTag write() {
        return nbt.write();
    }
    public static CabModule read(CompoundTag tag) {
        return CabModuleNbt.read(tag);
    }

    /** Decode once on frame receipt, including the previous single-console frame format. */
    public static Map<BlockPos, CabState> readFrames(CompoundTag frame) {
        return CabModuleNbt.readFrames(frame);
    }
}
