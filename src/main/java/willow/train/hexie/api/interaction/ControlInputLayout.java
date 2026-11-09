package willow.train.hexie.api.interaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;

/** Input ownership belongs to the block. Key codes use GLFW/ASCII; scroll codes are -1 or +1. */
public final class ControlInputLayout {
    public enum Device { KEY, MOUSE, SCROLL }
    public enum Action { PRESS, SET, STEP, HOLD, INTERACT, POINTER, EXIT, ZOOM }
    /**
     * Bounds are block-local, already oriented for the current state, and contained by the host's controlBounds.
     * Hint lines are entirely block-defined, including key names; an empty list displays no hint.
     */
    public record Entry(int key, AABB bounds, List<Component> hint) {
        public Entry(int key, AABB bounds) { this(key, bounds, List.of()); }
        public Entry {
            Objects.requireNonNull(bounds);
            hint = List.copyOf(hint);
            if (!keyCode(key) || !Double.isFinite(bounds.minX) || !Double.isFinite(bounds.minY) || !Double.isFinite(bounds.minZ)
                    || !Double.isFinite(bounds.maxX) || !Double.isFinite(bounds.maxY) || !Double.isFinite(bounds.maxZ)
                    || bounds.getXsize() <= 0 || bounds.getYsize() <= 0 || bounds.getZsize() <= 0)
                throw new IllegalArgumentException("Invalid input entry region");
        }
    }
    public record Binding(Device device, int code, Action action, String controlId, int value, boolean seatedOnly) {
        public Binding(Device device, int code, Action action, String controlId, int value) {
            this(device, code, action, controlId, value, false);
        }
        public Binding {
            Objects.requireNonNull(device); Objects.requireNonNull(action); Objects.requireNonNull(controlId);
            boolean command = action == Action.PRESS || action == Action.SET || action == Action.STEP || action == Action.HOLD;
            if (command == controlId.isEmpty() || controlId.length() > 256)
                throw new IllegalArgumentException("Invalid input binding");
            if (device == Device.KEY && !keyCode(code) || device == Device.MOUSE && (code < 0 || code > 7)
                    || device == Device.SCROLL && (Math.abs((long) code) != 1 || action == Action.HOLD)
                    || action == Action.STEP && Math.abs((long) value) != 1)
                throw new IllegalArgumentException("Unsupported input binding");
        }
        public Binding seated() { return new Binding(device, code, action, controlId, value, true); }
    }
    public record View(boolean hideHands, boolean hideHotbar, boolean seatedView, boolean zoom) {
        public static final View NONE = new View(false, false, false, false);
    }
    private record Physical(Device device, int code) {}
    private record Command(Action action, String controlId, int value) {}

    private final boolean seatedCapture;
    private final List<Entry> pointedEntries;
    private final List<Binding> bindings;
    private final View view;
    private final List<Binding> worldBindings;
    private final List<Binding> holdBindings, worldHoldBindings;
    private final Map<Physical, Binding> bindingsByInput;
    private final Set<String> heldControls, worldHeldControls;
    private final Set<Command> commands, worldCommands;

    public static final ControlInputLayout EMPTY = new ControlInputLayout(false, List.of(), List.of(), View.NONE);
    public ControlInputLayout(boolean seatedCapture, List<Entry> pointedEntries, List<Binding> bindings, View view) {
        this.seatedCapture = seatedCapture;
        this.pointedEntries = List.copyOf(pointedEntries);
        this.bindings = List.copyOf(bindings);
        this.view = Objects.requireNonNull(view);
        var world = new ArrayList<Binding>();
        var holds = new ArrayList<Binding>();
        var worldHolds = new ArrayList<Binding>();
        var byInput = new HashMap<Physical, Binding>();
        var held = new HashSet<String>();
        var worldHeld = new HashSet<String>();
        var commands = new HashSet<Command>();
        var worldCommands = new HashSet<Command>();
        for (var binding : this.bindings) {
            if (byInput.putIfAbsent(new Physical(binding.device(), binding.code()), binding) != null)
                throw new IllegalArgumentException("Conflicting input binding: " + binding.device() + "/" + binding.code());
            if (!binding.seatedOnly()) world.add(binding);
            if (binding.action() == Action.HOLD) {
                holds.add(binding);
                held.add(binding.controlId());
                if (!binding.seatedOnly()) {
                    worldHolds.add(binding);
                    worldHeld.add(binding.controlId());
                }
            } else if (binding.action() == Action.PRESS || binding.action() == Action.SET || binding.action() == Action.STEP) {
                var command = new Command(binding.action(), binding.controlId(), binding.value());
                commands.add(command);
                if (!binding.seatedOnly()) worldCommands.add(command);
            }
        }
        worldBindings = List.copyOf(world);
        holdBindings = List.copyOf(holds);
        worldHoldBindings = List.copyOf(worldHolds);
        bindingsByInput = Map.copyOf(byInput);
        heldControls = Set.copyOf(held);
        worldHeldControls = Set.copyOf(worldHeld);
        this.commands = Set.copyOf(commands);
        this.worldCommands = Set.copyOf(worldCommands);
    }

    public boolean seatedCapture() { return seatedCapture; }
    public List<Entry> pointedEntries() { return pointedEntries; }
    public List<Binding> bindings() { return bindings; }
    public View view() { return view; }

    private static boolean keyCode(int code) { return code >= 32 && code <= 348; }
    public List<Binding> bindings(boolean seated) {
        return seated ? bindings : worldBindings;
    }
    public List<Binding> holdBindings(boolean seated) {
        return seated ? holdBindings : worldHoldBindings;
    }
    public Binding binding(Device device, int code, boolean seated) {
        var binding = bindingsByInput.get(new Physical(device, code));
        return binding != null && (seated || !binding.seatedOnly()) ? binding : null;
    }
    /** Capture-bound operations must be explicitly declared. Direct mouse picking is validated separately. */
    public boolean accepts(ControlInput input, boolean seated) {
        if (!(seated ? heldControls : worldHeldControls).containsAll(input.held())) return false;
        if (!input.hasCommand()) return true;
        var action = switch (input.kind()) {
            case PRESS -> Action.PRESS; case SET -> Action.SET; case STEP -> Action.STEP;
            case NONE -> throw new AssertionError();
        };
        return (seated ? commands : worldCommands).contains(new Command(action, input.controlId(), input.value()));
    }

    @Override public boolean equals(Object other) {
        return this == other || other instanceof ControlInputLayout layout && seatedCapture == layout.seatedCapture
                && pointedEntries.equals(layout.pointedEntries) && bindings.equals(layout.bindings) && view.equals(layout.view);
    }
    @Override public int hashCode() {
        int result = Boolean.hashCode(seatedCapture);
        result = 31 * result + pointedEntries.hashCode();
        result = 31 * result + bindings.hashCode();
        return 31 * result + view.hashCode();
    }
    @Override public String toString() {
        return "ControlInputLayout[seatedCapture=" + seatedCapture + ", pointedEntries=" + pointedEntries
                + ", bindings=" + bindings + ", view=" + view + "]";
    }
}
