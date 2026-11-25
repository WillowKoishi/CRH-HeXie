package willow.train.hexie.vehicle.control;

import com.google.common.collect.MapMaker;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.core.api.cab.CabControlHost;
import willow.train.hexie.core.api.cab.CabState;
import willow.train.hexie.core.api.interaction.BlockControl;
import willow.train.hexie.core.api.interaction.ControlState;
import willow.train.hexie.core.api.traincontrol.CabTrainControl;
import willow.train.hexie.core.api.traincontrol.CabTrainRequest;

// split from willow/train/hexie/vehicle/control/CabModule.java
final class CabDeskControls {
    // Legacy list declarations are stable objects; weak identity keys avoid rescanning or hashing their elements.
    private static final Map<List<? extends BlockControl>, Map<String, BlockControl>> DECLARED_CONTROLS =
            new MapMaker().weakKeys().makeMap();

    /** Copy exact demands between desks, converting the source direction into the target's frame. */
    static CabState synchronize(CabTrainControl desk, CabState previous, CabState source, int relativeDirection) {
        var next = normalize(desk, previous.withDemands(source.input().direction(relativeDirection), source.brakes()));
        next = next.withEnabled(desk.synchronizedEnabled(next.request()));
        var surface = desk.synchronize(new ControlState(previous.handlePositions()), synchronizedRequest(next));
        return withSurface(next, surface);
    }

    /** Also update any train-control instruments mounted on the target desk. */
    static CabState synchronize(CabControlHost host, BlockState block, CabState previous, CabState source, int relativeDirection) {
        var next = synchronize(host.trainControl(block), previous, source, relativeDirection);
        var surface = new ControlState(next.handlePositions());
        var request = synchronizedRequest(next);
        for (var mount : host.surfaceGeometry(block).mounts())
            if (mount.module() instanceof CabTrainControl module)
                surface = module.synchronize(surface, request);
        return withSurface(next, surface);
    }

    private static CabTrainRequest synchronizedRequest(CabState state) {
        return new CabTrainRequest(state.input().power(), state.input().brake(), state.input().reverser(),
                state.deskEnabled(), state.automatic(), state.brakes().parking(),
                state.brakes().emergencyHandle() || state.brakes().emergencyLatched(), false, state.approachHeld());
    }

    private static CabState withSurface(CabState state, ControlState surface) {
        return new CabState(state.input(), state.mode(), state.brakes(), state.approachHeld(),
                state.deskEnabled(), state.simple(), surface.positions());
    }

    /** Bring older or synchronized requests into the range represented by this physical desk. */
    static CabState normalize(CabTrainControl desk, CabState state) {
        int direction = desk.normalizeDirection(state.request());
        if (direction == state.input().reverser()) return state;
        var input = new CabState.Input(state.input().power(), state.input().brake(), direction);
        var next = state.withDemands(input, state.brakes());
        next = next.withEnabled(desk.synchronizedEnabled(next.request()));
        return withSurface(next, desk.restore(new ControlState(next.handlePositions()), next.request()));
    }

    /** A rejected request returns null before touching the saved positions or train commands. */
    static CabState operateControls(List<? extends BlockControl> declared, CabTrainControl desk,
                                        String id, int value, CabState previous, double speed) {
        previous = normalize(desk, previous);
        var index = DECLARED_CONTROLS.computeIfAbsent(declared, controls -> {
            var byId = new LinkedHashMap<String, BlockControl>();
            for (var control : controls) byId.putIfAbsent(control.id(), control);
            return Map.copyOf(byId);
        });
        return operateControls(index.get(id), desk, value, previous.surface(desk), previous, speed);
    }

    static CabState operateControls(CabControlHost host, BlockState block, String id, int value, CabState previous, double speed) {
        previous = normalize(host.trainControl(block), previous);
        var surface = host.surfaceGeometry(block);
        var module = surface.owner(id);
        var owner = module == null ? host.trainControl(block)
                : module instanceof CabTrainControl mounted ? mounted : CabTrainControl.NONE;
        return operateControls(surface.control(id), owner, value, host.surfaceState(block, previous), previous, speed);
    }

    /** Resolve the same declared owner used by operateControls, without inferring meaning from control IDs. */
    static Set<CabTrainControl.Demand> operatedDemands(CabControlHost host, BlockState block, String id) {
        var surface = host.surfaceGeometry(block);
        var control = surface.control(id);
        if (control == null) return Set.of();
        var module = surface.owner(id);
        var owner = module == null ? host.trainControl(block)
                : module instanceof CabTrainControl mounted ? mounted : CabTrainControl.NONE;
        return Set.copyOf(owner.operatedDemands(control));
    }

    private static CabState operateControls(BlockControl target, CabTrainControl owner,
                                         int value, ControlState before, CabState previous, double speed) {
        if (target == null || !target.accepts(value)) return null;
        var after = before.after(target, value);
        var result = owner.operate(target, value, before, after, previous.request(), speed);
        return accept(previous, result.request(), result.surface().positions(), speed);
    }

    static CabState accept(CabState previous, CabTrainRequest request, Map<String, Integer> positions, double speed) {
        var brakes = CabBrakeConversion.operate(previous.brakes(), request, speed);
        var mode = request.automatic() ? CabState.Mode.AUTOMATIC : CabState.Mode.MANUAL;
        return new CabState(new CabState.Input(request.power(), brakes.brake(), request.reverser()), mode,
                brakes.state(), request.approach(), request.enabled(), previous.simple(), positions);
    }
}
