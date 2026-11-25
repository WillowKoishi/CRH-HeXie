package willow.train.hexie.interaction.surface;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.lwjgl.glfw.GLFW;
import willow.train.hexie.bridge.sync.SyncBridge.ControlInputPacketPhase;
import willow.train.hexie.bridge.sync.SyncBridge.ControlInputPacketTarget;
import willow.train.hexie.core.api.cab.DrivingConsoleContext;
import willow.train.hexie.core.api.interaction.BlockControl;
import willow.train.hexie.core.api.interaction.BlockControlHost;
import willow.train.hexie.core.api.interaction.ControlInput;
import willow.train.hexie.core.api.interaction.ControlInputLayout.Action;
import willow.train.hexie.core.api.interaction.ControlInputLayout.Binding;
import willow.train.hexie.core.api.interaction.ControlInputLayout.Device;
import willow.train.hexie.interaction.common.ControlInputTargets;

// split from willow/train/hexie/client/interaction/SurfaceInputClient.java


/** Executes block-declared input ownership. Train meanings and output conversion belong to the block. */
@EventBusSubscriber(modid = "hexie", value = Dist.CLIENT)
public final class SurfaceInputClient {
    static SurfaceInputTarget active;
    private static Object exitedSeat;
    private static long seatRetryAt;
    private static final Set<InputConstants.Key> captured = new HashSet<>();
    private static final Set<InputConstants.Key> suppressed = new HashSet<>();
    private static Set<String> lastHeld = Set.of();
    private static final ControlStepRepeater stepRepeater = new ControlStepRepeater();
    static boolean pointer;
    private static int inputTicks;
    private static long requestSequence, captureRequest;

    private SurfaceInputClient() {}

    public static DrivingConsoleContext cab() {
        return active != null && !SurfaceInputSources.blocked() && active.valid() ? active.cab : null;
    }
    public static DrivingConsoleContext cameraCab() {
        return capturesInput() && active.layout().view().seatedView() ? cab() : null;
    }
    public static boolean capturesInput() {
        return active != null && gameplay() && !SurfaceInputSources.blocked() && active.valid();
    }
    public static boolean dragging() { return SurfaceInteraction.dragging(); }
    public static Set<String> heldControls() { return capturesInput() ? lastHeld : Set.of(); }
    static Set<String> heldFor(Object identity) {
        return active != null && active.identity().equals(identity) ? heldBindings() : Set.of();
    }

    static boolean gameplay() {
        var mc = Minecraft.getInstance();
        return mc.player != null && mc.level != null && mc.getConnection() != null
                && mc.player.isAlive() && !mc.player.isSpectator() && mc.screen == null
                && mc.getOverlay() == null && mc.isWindowActive();
    }

    @SubscribeEvent
    public static void frame(RenderFrameEvent.Pre event) {
        SurfacePointerQuery.invalidate();
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Pre event) {
        var mc = Minecraft.getInstance();
        if (active != null && (!active.valid() || !active.canCapture() || SurfaceInputSources.blocked())) close();
        var cab = SurfaceInputSources.blocked() ? null : DrivingConsoleContext.find(mc.player);
        if (cab != null && mc.level != null) {
            var context = ControlInputTargets.seated(cab, 1);
            var host = context == null ? null : BlockControlHost.find(context.state());
            if (host != null && host.inputLayout(context).seatedCapture()) {
                var candidate = new SurfaceInputTarget(cab, context, Vec3.atCenterOf(cab.console()));
                if (candidate.identity().equals(exitedSeat) && mc.level.getGameTime() >= seatRetryAt) exitedSeat = null;
                if (gameplay() && candidate.canCapture() && !candidate.identity().equals(exitedSeat)
                        && (active == null || !active.identity().equals(candidate.identity()))) {
                    exitedSeat = null;
                    activate(candidate, -1);
                }
            } else if (active != null && active.cab != null) close();
        } else {
            exitedSeat = null;
            if (active != null && active.cab != null) close();
        }
        if (!capturesInput()) {
            suspend();
            return;
        }
        var holds = heldBindings();
        if (!holds.equals(lastHeld) || ++inputTicks % 10 == 0) send(new ControlInput("", 0, ControlInput.Kind.NONE, holds));
        for (var binding : stepRepeater.tick(active.layout().bindings(active.cab != null), binding -> {
            var key = key(binding);
            return key != null && physicallyDown(key);
        })) send(new ControlInput(binding.controlId(), binding.value(), ControlInput.Kind.STEP, holds));
        var claimed = new HashSet<InputConstants.Key>();
        for (var binding : active.layout().bindings(active.cab != null)) {
            var key = key(binding);
            if (key != null) claimed.add(key);
        }
        for (var old : Set.copyOf(suppressed)) if (!claimed.contains(old)) {
            KeyMapping.set(old, physicallyDown(old));
            captured.remove(old);
        }
        for (var key : claimed) KeyMapping.set(key, false);
        suppressed.clear(); suppressed.addAll(claimed);
        boolean cursor = pointer || SurfaceInteraction.dragging();
        if (cursor && mc.mouseHandler.isMouseGrabbed()) mc.mouseHandler.releaseMouse();
        if (!cursor && !mc.mouseHandler.isMouseGrabbed()) mc.mouseHandler.grabMouse();
    }

    private static void activate(SurfaceInputTarget target, int key) {
        close();
        active = target;
        pointer = false; SurfaceInputView.resetZoom(); inputTicks = 0;
        if (++requestSequence == 0) ++requestSequence;
        captureRequest = requestSequence;
        active.send(ControlInputPacketPhase.OPEN, key, ControlInput.RELEASE, captureRequest);
    }

    public static void captureResult(ControlInputPacketTarget target,
            int vehicleId, net.minecraft.core.BlockPos position, long requestId, boolean accepted) {
        if (active == null || requestId != captureRequest || active.vehicleId != vehicleId
                || !active.position.equals(position) || (active.cab != null)
                != (target == ControlInputPacketTarget.SEATED)) return;
        if (!accepted) {
            if (active.cab != null) {
                exitedSeat = active.identity();
                seatRetryAt = active.level.getGameTime() + 20;
            }
            close();
        }
    }

    private static void suspend() {
        if (active != null) {
            close();
            return;
        }
        releaseMappings();
        pointer = false;
    }

    private static void close() {
        close(ControlInputPacketPhase.CLOSE);
    }
    private static void close(ControlInputPacketPhase reason) {
        if (active != null) {
            SurfaceInteraction.cancel(active.identity());
            active.send(reason, -1, ControlInput.RELEASE);
        }
        active = null;
        lastHeld = Set.of();
        stepRepeater.reset();
        pointer = false; SurfaceInputView.resetZoom();
        releaseMappings();
        var mc = Minecraft.getInstance();
        if (gameplay() && !SurfaceInteraction.dragging()) mc.mouseHandler.grabMouse();
    }

    private static void releaseMappings() {
        for (var key : suppressed) KeyMapping.set(key, gameplay() && physicallyDown(key));
        suppressed.clear();
        captured.clear();
    }

    /** Called at the keyboard entry point, before Minecraft updates its own mappings. */
    public static boolean handleKey(int key, int scanCode, int action) {
        var physical = InputConstants.getKey(key, scanCode);
        if (action == GLFW.GLFW_RELEASE && captured.remove(physical)) {
            if (capturesInput()) {
                var released = binding(Device.KEY, key);
                boolean anotherHeld = released != null && active.layout().bindings(active.cab != null).stream()
                        .anyMatch(other -> other.action() == Action.STEP_HOLD && other.code() != key
                                && other.controlId().equals(released.controlId()) && key(other) != null && physicallyDown(key(other)));
                if (!anotherHeld && released != null && released.action() == Action.STEP_HOLD
                        && active.control(released.controlId()) instanceof BlockControl.Handle handle && handle.hasSpringReturn())
                    send(new ControlInput(handle.id(), 0, ControlInput.Kind.RELEASE_HANDLE, heldBindings()));
                else send(new ControlInput("", 0, ControlInput.Kind.NONE, heldBindings()));
            }
            return true;
        }
        if (action == GLFW.GLFW_REPEAT && captured.contains(physical)) return true;
        if (!gameplay() || SurfaceInputSources.blocked()) return false;
        if (!capturesInput()) {
            if (action != GLFW.GLFW_PRESS || SurfaceInteraction.dragging()) return false;
            var selected = SurfacePointerQuery.pointed(key);
            if (selected == null || !selected.target().canCapture()) return false;
            activate(selected.target(), key);
            captured.add(physical);
            KeyMapping.set(physical, false);
            return true;
        }
        var binding = binding(Device.KEY, key);
        if (binding == null) return false;
        if (action != GLFW.GLFW_RELEASE) captured.add(physical);
        KeyMapping.set(physical, false);
        if (action != GLFW.GLFW_REPEAT) dispatch(binding, action == GLFW.GLFW_PRESS, 1);
        return true;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void mouse(InputEvent.MouseButton.Pre event) {
        if (SurfaceInteraction.handleMouseButton(event) || !capturesInput()) return;
        var binding = binding(Device.MOUSE, event.getButton());
        if (binding == null) return;
        event.setCanceled(true);
        KeyMapping.set(InputConstants.Type.MOUSE.getOrCreate(event.getButton()), false);
        dispatch(binding, event.getAction() == GLFW.GLFW_PRESS, 1);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void scroll(InputEvent.MouseScrollingEvent event) {
        if (!capturesInput() || event.getScrollDeltaY() == 0) return;
        var binding = binding(Device.SCROLL, event.getScrollDeltaY() > 0 ? 1 : -1);
        if (binding == null) return;
        event.setCanceled(true);
        dispatch(binding, true, Math.abs(event.getScrollDeltaY()));
    }

    private static Binding binding(Device device, int code) {
        if (active == null) return null;
        return active.layout().binding(device, code, active.cab != null);
    }

    private static void dispatch(Binding binding, boolean pressed, double amount) {
        if (binding.action() == Action.HOLD) {
            send(new ControlInput("", 0, ControlInput.Kind.NONE, heldBindings()));
            return;
        }
        if (!pressed) return;
        switch (binding.action()) {
            case PRESS, SET, STEP -> send(new ControlInput(binding.controlId(), binding.value(), switch (binding.action()) {
                case PRESS -> ControlInput.Kind.PRESS; case SET -> ControlInput.Kind.SET; default -> ControlInput.Kind.STEP;
            }, heldBindings()));
            case POINTER -> {
                if (SurfaceInteraction.dragging()) return;
                pointer = !pointer;
                var mouse = Minecraft.getInstance().mouseHandler;
                if (pointer) mouse.releaseMouse(); else mouse.grabMouse();
            }
            case INTERACT -> {
                if (SurfaceInteraction.dragging()) return;
                var ray = SurfaceInputView.ray(pointer);
                var hit = active.pick(ray.from(), ray.to());
                if (hit != null) SurfaceInteraction.click(hit.target(), hit.control(),
                        binding.device() == Device.MOUSE ? binding.code() : -1, pointer);
            }
            case EXIT -> {
                var cab = active.cab;
                if (cab != null) {
                    exitedSeat = active.identity();
                    seatRetryAt = Long.MAX_VALUE;
                }
                close(ControlInputPacketPhase.EXIT);
            }
            case ZOOM -> {
                if (active.layout().view().zoom() && !SurfaceInteraction.dragging())
                    SurfaceInputView.adjustZoom(binding.value() * amount);
            }
            default -> { }
        }
    }

    private static Set<String> heldBindings() {
        if (active == null || !gameplay()) return Set.of();
        Set<String> result = new HashSet<>();
        for (var binding : active.layout().holdBindings(active.cab != null)) {
            var key = key(binding);
            if (key != null && physicallyDown(key)) result.add(binding.controlId());
        }
        return Set.copyOf(result);
    }
    static InputConstants.Key key(Binding binding) {
        return switch (binding.device()) {
            case KEY -> InputConstants.Type.KEYSYM.getOrCreate(binding.code());
            case MOUSE -> InputConstants.Type.MOUSE.getOrCreate(binding.code());
            case SCROLL -> null;
        };
    }
    static boolean physicallyDown(InputConstants.Key key) {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return key.getType() == InputConstants.Type.MOUSE
                ? GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS
                : key.getValue() >= 0 && InputConstants.isKeyDown(window, key.getValue());
    }
    private static void send(ControlInput input) {
        if (active == null) return;
        active.send(ControlInputPacketPhase.OPERATE, -1, input);
        lastHeld = input.held();
    }

    /** Ordinary mouse use only captures a drag gesture; it never activates a whole operating session. */
    public static void directClick(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || !gameplay() || SurfaceInteraction.dragging() || SurfaceInputSources.blocked()) return;
        SurfacePointerQuery.invalidate();
        var use = Minecraft.getInstance().options.keyUse.getKey();
        if (capturesInput() && binding(use.getType() == InputConstants.Type.MOUSE ? Device.MOUSE : Device.KEY,
                use.getValue()) != null) return;
        var picked = SurfacePointerQuery.pointed(null);
        if (picked == null) return;
        // Native uses keep the selected face and hand in the block/carrier interaction path.
        if (picked.target().host.nativeInteraction()) return;
        int button = use.getType() == InputConstants.Type.MOUSE ? use.getValue() : -1;
        if (SurfaceInteraction.click(picked.target().withHand(event.getHand()), picked.control(), button, false)) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

}
