package willow.train.hexie.client.interaction;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.lwjgl.glfw.GLFW;
import willow.train.hexie.api.cab.DrivingConsoleContext;
import willow.train.hexie.api.interaction.*;
import willow.train.hexie.api.interaction.ControlInputLayout.*;
import willow.train.hexie.interaction.ControlInputTargets;
import willow.train.hexie.network.c2s.ControlInputPacket.Phase;

/** Executes block-declared input ownership. Train meanings and output conversion belong to the block. */
@EventBusSubscriber(modid = "hexie", value = Dist.CLIENT)
public final class SurfaceInputClient {
    private static SurfaceInputTarget active;
    private static Object exitedSeat;
    private static long seatRetryAt;
    private static final Set<InputConstants.Key> captured = new HashSet<>();
    private static final Set<InputConstants.Key> suppressed = new HashSet<>();
    private static Set<String> lastHeld = Set.of();
    private static final ControlStepRepeater stepRepeater = new ControlStepRepeater();
    private static boolean pointer;
    private static double zoom = 1, renderedFov = 70;
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

    private static boolean gameplay() {
        var mc = Minecraft.getInstance();
        return mc.player != null && mc.level != null && mc.getConnection() != null
                && mc.player.isAlive() && !mc.player.isSpectator() && mc.screen == null
                && mc.getOverlay() == null && mc.isWindowActive();
    }

    @SubscribeEvent
    public static void frame(RenderFrameEvent.Pre event) {
        SurfaceInputTarget.beginFrame();
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
        pointer = false; zoom = 1; inputTicks = 0;
        if (++requestSequence == 0) ++requestSequence;
        captureRequest = requestSequence;
        active.send(Phase.OPEN, key, ControlInput.RELEASE, captureRequest);
    }

    public static void captureResult(willow.train.hexie.network.c2s.ControlInputPacket.Target target,
            int vehicleId, net.minecraft.core.BlockPos position, long requestId, boolean accepted) {
        if (active == null || requestId != captureRequest || active.vehicleId != vehicleId
                || !active.position.equals(position) || (active.cab != null)
                != (target == willow.train.hexie.network.c2s.ControlInputPacket.Target.SEATED)) return;
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
        close(Phase.CLOSE);
    }
    private static void close(Phase reason) {
        if (active != null) {
            SurfaceInteraction.cancel(active.identity());
            active.send(reason, -1, ControlInput.RELEASE);
        }
        active = null;
        lastHeld = Set.of();
        stepRepeater.reset();
        pointer = false; zoom = 1;
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
            if (capturesInput()) send(new ControlInput("", 0, ControlInput.Kind.NONE, heldBindings()));
            return true;
        }
        if (action == GLFW.GLFW_REPEAT && captured.contains(physical)) return true;
        if (!gameplay() || SurfaceInputSources.blocked()) return false;
        if (!capturesInput()) {
            if (action != GLFW.GLFW_PRESS || SurfaceInteraction.dragging()) return false;
            var selected = SurfaceInputTarget.pointed(key);
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
                var ray = ray(pointer);
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
                close(Phase.EXIT);
            }
            case ZOOM -> {
                if (active.layout().view().zoom() && !SurfaceInteraction.dragging())
                    zoom = Math.clamp(zoom * Math.pow(1.12, binding.value() * amount), .5, 4);
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
    private static InputConstants.Key key(Binding binding) {
        return switch (binding.device()) {
            case KEY -> InputConstants.Type.KEYSYM.getOrCreate(binding.code());
            case MOUSE -> InputConstants.Type.MOUSE.getOrCreate(binding.code());
            case SCROLL -> null;
        };
    }
    private static boolean physicallyDown(InputConstants.Key key) {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return key.getType() == InputConstants.Type.MOUSE
                ? GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS
                : key.getValue() >= 0 && InputConstants.isKeyDown(window, key.getValue());
    }
    private static void send(ControlInput input) {
        if (active == null) return;
        active.send(Phase.OPERATE, -1, input);
        lastHeld = input.held();
    }

    /** Ordinary mouse use only captures a drag gesture; it never activates a whole operating session. */
    public static void directClick(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || !gameplay() || SurfaceInteraction.dragging()) return;
        var use = Minecraft.getInstance().options.keyUse.getKey();
        if (capturesInput() && binding(use.getType() == InputConstants.Type.MOUSE ? Device.MOUSE : Device.KEY,
                use.getValue()) != null) return;
        var picked = SurfaceInputTarget.pointed(null);
        if (picked == null) return;
        // The same owner/part picking includes doors and extended couplers. Their host keeps
        // the complete native item/block/Create use chain, so do not also send a control packet.
        if (picked.target().host.nativeInteraction()) return;
        int button = use.getType() == InputConstants.Type.MOUSE ? use.getValue() : -1;
        if (SurfaceInteraction.click(picked.target().withHand(event.getHand()), picked.control(), button, false)) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void arm(RenderArmEvent event) {
        if (capturesInput() && active.layout().view().hideHands()
                && event.getPlayer() == Minecraft.getInstance().player) event.setCanceled(true);
    }
    @SubscribeEvent
    public static void hud(RenderGuiLayerEvent.Pre event) {
        if (!capturesInput()) return;
        var layer = event.getName();
        if (active.layout().view().hideHotbar() && (layer.equals(VanillaGuiLayers.HOTBAR)
                || layer.equals(VanillaGuiLayers.SELECTED_ITEM_NAME))
                || pointer && layer.equals(VanillaGuiLayers.CROSSHAIR)) event.setCanceled(true);
    }
    @SubscribeEvent
    public static void hover(RenderGuiEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (!gameplay() || mc.options.hideGui || SurfaceInteraction.dragging() || SurfaceInputSources.blocked()) return;
        if (!capturesInput()) {
            var hit = SurfaceInputTarget.pointed(null);
            if (hit != null) {
                if (hit.control().control().showTooltip())
                    SurfaceInteraction.hover(hit.control(), hit.target().state(), event.getGuiGraphics(), false);
                return;
            }
            var entry = SurfaceInputTarget.pointedEntry();
            if (entry != null) SurfaceControlCaption.entry(mc, event.getGuiGraphics(), entry.entry().hint());
            return;
        }
        var pushed = new LinkedHashMap<String, BlockControl>();
        for (var binding : active.layout().bindings(active.cab != null)) {
            if (binding.action() != Action.STEP_HOLD) continue;
            var key = key(binding);
            if (key == null || !physicallyDown(key)) continue;
            var control = active.control(binding.controlId());
            if (control != null && control.showTooltip()) pushed.putIfAbsent(control.id(), control);
        }
        if (!pushed.isEmpty()) {
            SurfaceControlCaption.held(mc, event.getGuiGraphics(), pushed.values().stream().toList(), active.state());
            return;
        }
        var ray = ray(pointer);
        var hit = active.pick(ray.from(), ray.to());
        if (hit != null && hit.control().control().showTooltip())
            SurfaceInteraction.hover(hit.control(), active.state(), event.getGuiGraphics(), pointer);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void fov(ViewportEvent.ComputeFov event) {
        if (!event.usedConfiguredFov()) return;
        double factor = capturesInput() && active.layout().view().zoom() ? zoom : 1;
        renderedFov = Math.toDegrees(2 * Math.atan(Math.tan(Math.toRadians(event.getFOV()) / 2) / factor));
        if (factor != 1) event.setFOV(renderedFov);
    }

    record Ray(Vec3 from, Vec3 to) {}
    static Ray ray(boolean pointer) {
        var mc = Minecraft.getInstance();
        var camera = mc.gameRenderer.getMainCamera();
        var window = mc.getWindow();
        double x = pointer ? 2 * mc.mouseHandler.xpos() / window.getScreenWidth() - 1 : 0;
        double y = pointer ? 1 - 2 * mc.mouseHandler.ypos() / window.getScreenHeight() : 0;
        double scale = Math.tan(Math.toRadians(renderedFov) / 2);
        var direction = new org.joml.Vector3f((float) (x * scale * window.getWidth() / window.getHeight()),
                (float) (y * scale), -1).rotate(camera.rotation()).normalize();
        Vec3 eye = camera.getPosition();
        return new Ray(eye, eye.add(direction.x * mc.player.blockInteractionRange(),
                direction.y * mc.player.blockInteractionRange(), direction.z * mc.player.blockInteractionRange()));
    }
}
