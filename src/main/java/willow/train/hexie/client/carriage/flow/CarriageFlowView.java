package willow.train.hexie.client.carriage.flow;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import willow.train.hexie.Main;

import java.util.*;

import static willow.train.hexie.client.carriage.flow.CarriageFlowPanBounds.BOTTOM;
import static willow.train.hexie.client.carriage.flow.CarriageFlowPanBounds.LEFT;
import static willow.train.hexie.client.carriage.flow.CarriageFlowPanBounds.RIGHT;
import static willow.train.hexie.client.carriage.flow.CarriageFlowPanBounds.TOP;

public final class CarriageFlowView implements AutoCloseable {
    static final ResourceLocation SLOT_TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/creative_inventory/tab_items.png");
    private static final int STRIP_Y = 17;
    private static final int STRIP_HEIGHT = 18;
    private static final int STRIP_CLICK_SLOP = 3;
    private static final int PAN_DRAG_SLOP = 3;
    private static final int CONTROL_X = 174, CONTROL_Y = 19, CONTROL_SIZE = 14, CONTROL_PITCH = 18;
    private static final int SCHEMATIC_X = 173, SCHEMATIC_Y = 112;
    private static final float FRONT_YAW = 142;
    private static final float CAMERA_PITCH = 44;
    final CreativeModeInventoryScreen host;
    private final net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
    private static final int width = 178, height = 146;
    private static final double PAN_INERTIA_DAMPING = 6.5;
    private static final double PAN_MAX_SPEED = 18;
    private static final double PAN_STOP_SPEED = .08;
    private static final double PAN_RESTRICTED_SNAP_DISTANCE = 9;
    private static final double FOCUS_FADE_SECONDS = .35;
    private CarriageFlowAssets.Catalog catalog;
    private final List<CarriageFlowPreview> previews = new ArrayList<>();
    private CarriageFlowLayout layout;
    private int generation = -1;
    private long selected;
    private double position, targetPosition, lastFrame = now(), lastInput = -100;
    private final CarriageFlowMotion motion = new CarriageFlowMotion();
    private float neighborVisibility = .25f;
    private float maxLength, focusDistance, visibleRadius;
    private long phase;
    // Camera-only track offset; never applied to the train motion or the upper strip.
    private double panZ, panTarget, panVelocity, lastPanInputTime;
    private double panDragStartX, panDragStartY, panDragStartPosition, panDirectionY;
    private int panScreenDirection;
    private boolean panDraggedRight, manualPan;
    private CarriageFlowMotion settlingPan;
    private long previousFocus;
    private double focusStarted = Double.NEGATIVE_INFINITY;
    private boolean draggingPan, panSnapPending, panSnapCaptured;
    private CarriagePress carriagePress;
    private StripPress stripPress;
    private boolean draggingStrip;
    private long stripSelection;
    private Matrix4f selectedTransform;
    private double yaw = FRONT_YAW, fromYaw = FRONT_YAW, targetYaw = FRONT_YAW, viewStarted;
    private float shell = 1, fromShell = 1, targetShell = 1;
    private float sceneScale, stripScale, trackOriginY;
    boolean openingPalette;
    private CarriageFlowPreview.Hit hovered;
    private Matrix4f hoveredTransform;
    private Slot originalSchematicSlot, schematicSlot;
    private ItemStack schematicPrototype, schematicStack = ItemStack.EMPTY;
    private final List<StripEntry> stripEntries = new ArrayList<>();
    private static final Map<ResourceLocation, Saved> SAVED = new HashMap<>();
    final ResourceLocation tab;
    private record Saved(ResourceLocation model, long index, long phase, boolean open, double pan, double offset,
                         double velocity, double settlingOffset, double settlingVelocity) {}
    private record StripEntry(long index, double left, double right) {}
    private record CarriagePress(double mouseX, double mouseY, double pressedAt, long index,
                                 CarriageFlowPreview.Hit hit) {}
    private record StripPress(double mouseX, double mouseY, double position, double clickSlopX, StripEntry entry) {
        boolean withinClickSlop(double x, double y) {
            return Math.abs(x - mouseX) <= clickSlopX && Math.abs(y - mouseY) <= STRIP_CLICK_SLOP;
        }
    }

    public CarriageFlowView(CreativeModeInventoryScreen host) { this.host = host; this.tab = CarriageFlowClient.tabId(host); init(); }
    private static double now() { return System.nanoTime() / 1_000_000_000.0; }

    private void init() {
        if (catalog == null || generation != CarriageFlowClient.resourceGeneration || previews.isEmpty()) load();
        maxLength = catalog.models().stream().map(m -> (float) m.length()).max(Float::compare).orElse(19f);
        float maxWidth = catalog.models().stream().map(m -> (float) m.width()).max(Float::compare).orElse(5f);
        float maxHeight = catalog.models().stream().map(m -> (float) m.height()).max(Float::compare).orElse(6f);
        // Fixed close-up: six blocks occupy 72 GUI pixels, regardless of carriage length.
        sceneScale = 12;
        focusDistance = Math.max(0, maxLength / 2 - 3);
        visibleRadius = width / sceneScale + maxWidth + maxHeight;
        stripScale = STRIP_HEIGHT / 9f;
        // Leave room for the near end below the track focus with the steeper roof view.
        trackOriginY = TOP + (BOTTOM - TOP) * .65f;
    }

    private void load() {
        closeMeshes();
        generation = CarriageFlowClient.resourceGeneration;
        CarriageFlowClient.warmPreviews();
        catalog = CarriageFlowClient.catalog(CarriageFlowClient.page(host));
        List<CarriageFlowAssets.Model> usable = catalog.models();
        for (var model : usable) previews.add(CarriageFlowClient.preview(model.id()));
        if (!usable.isEmpty()) {
            layout = new CarriageFlowLayout(usable.stream().mapToDouble(CarriageFlowAssets.Model::length).toArray(),
                    usable.stream().mapToDouble(CarriageFlowAssets.Model::frontGap).toArray(),
                    usable.stream().mapToDouble(CarriageFlowAssets.Model::rearGap).toArray());
        } else layout = null;
        selected = 0;
        position = targetPosition = 0;
        motion.reset();
        panZ = panTarget = panVelocity = lastPanInputTime = 0;
        settlingPan = null;
        focusStarted = Double.NEGATIVE_INFINITY;
        manualPan = false;
        panSnapPending = false;
        panSnapCaptured = false;
        carriagePress = null;
        Saved saved = SAVED.get(tab);
        if (layout != null && saved != null) {
            int match = -1;
            for (int i = 0; i < usable.size(); i++) if (usable.get(i).id().equals(saved.model())) { match = i; break; }
            if (match >= 0) {
                selected = layout.model(saved.index()) == match ? saved.index() : match;
                targetPosition = layout.center(selected);
                double offset = saved.offset();
                double velocity = saved.velocity();
                // Re-enter a short approach even when the previous train was fully stopped.
                if (Math.abs(offset) < .02 && Math.abs(velocity) < .1) { offset = -1.2; velocity = 6; }
                position = targetPosition + offset;
                motion.restore(position, velocity, targetPosition);
                phase = saved.phase();
                yaw = fromYaw = targetYaw = FRONT_YAW + phase * 90;
                shell = fromShell = targetShell = saved.open() ? 0 : 1;
                panTarget = saved.pan();
                panZ = panTarget + saved.settlingOffset();
                if (saved.settlingOffset() != 0 || saved.settlingVelocity() != 0) {
                    settlingPan = new CarriageFlowMotion();
                    settlingPan.restore(panZ, saved.settlingVelocity(), panTarget);
                }
                neighborVisibility = 1;
                lastInput = now();
            }
        }
        lastFrame = now();
        draggingPan = false;
        panSnapPending = false;
        carriagePress = null;
        stripPress = null;
        draggingStrip = false;
        hovered = null;
    }

    private void animate() {
        double time = now();
        double elapsed = Math.min(.1, Math.max(0, time - lastFrame));
        lastFrame = time;
        if (stripPress == null) {
            double previousPosition = position;
            motion.advance(elapsed);
            position = motion.position();
            if (manualPan) {
                // Keep a manually positioned preview steady while the strip finishes moving.
                double shift = previousPosition - position;
                panZ += shift;
                panTarget += shift;
            }
        }
        double t = CarriageFlowLayout.ease((time - viewStarted) / .8);
        yaw = fromYaw + (targetYaw - fromYaw) * t;
        shell = (float) (fromShell + (targetShell - fromShell) * t);
        if (settlingPan != null) {
            settlingPan.target(panTarget);
            settlingPan.advance(elapsed);
            panZ = settlingPan.position();
            if (panZ == panTarget && settlingPan.velocity() == 0) settlingPan = null;
        } else if (panSnapPending && Math.abs(panVelocity) >= PAN_STOP_SPEED) {
            double decay = Math.exp(-PAN_INERTIA_DAMPING * elapsed);
            double requested = panZ + panVelocity * (1 - decay) / PAN_INERTIA_DAMPING;
            panVelocity *= decay;
            if (Math.abs(panVelocity) < PAN_STOP_SPEED) panVelocity = 0;
            movePan(requested);
        } else if (!draggingPan) {
            panVelocity = 0;
            panZ += (panTarget - panZ) * (1 - Math.exp(-elapsed * 12));
            if (Math.abs(panTarget - panZ) < .0001) panZ = panTarget;
        }
        if (panSnapPending && !draggingPan && panVelocity == 0) panSnapPending = false;
        boolean scrolling = draggingStrip || time - lastInput < .16;
        float opacity = scrolling ? 1 : .25f + .75f * (float) Math.min(1, Math.abs(motion.velocity()) / 12);
        neighborVisibility += (opacity - neighborVisibility) * (float) (1 - Math.exp(-elapsed * 12));
    }

    private void select(long index) {
        if (layout == null) return;
        panSnapPending = false;
        carriagePress = null;
        animate();
        draggingPan = false;
        focusCarriage(index);
    }

    private void focusCarriage(long index) {
        if (index != selected) {
            manualPan = false;
            previousFocus = selected;
            focusStarted = draggingStrip ? Double.NEGATIVE_INFINITY : lastFrame;
            // Both the strip and the camera offset start their approach at the displayed pose and velocity.
            if (settlingPan == null && (panZ != 0 || panVelocity != 0)) {
                settlingPan = new CarriageFlowMotion();
                settlingPan.restore(panZ, panVelocity, 0);
            }
            panTarget = 0;
            selected = index;
        }
        panVelocity = 0;
        targetPosition = layout.center(index);
        motion.target(targetPosition);
        lastInput = now();
        selectedTransform = null;
        hovered = null;
        syncSchematicSlot();
    }

    private void cycle(int delta) {
        panSnapPending = false;
        animate();
        fromYaw = yaw;
        fromShell = shell;
        panVelocity = 0;
        if (delta < 0) {
            if (targetShell > .5f) targetShell = 0;
            else phase--;
        } else {
            if (targetShell < .5f) targetShell = 1;
            else phase++;
        }
        targetYaw = FRONT_YAW + phase * 90;
        viewStarted = now();
    }

    private double focusZ() {
        // Orbit the current observation point instead of sliding the pivot with yaw.
        return -focusDistance;
    }

    private Matrix4f localCamera(double pan) {
        return new Matrix4f().translate(width / 2f, trackOriginY, 200)
                .scale(sceneScale, -sceneScale, sceneScale)
                .rotateX((float) Math.toRadians(CAMERA_PITCH)).rotateY((float) Math.toRadians(yaw))
                .translate(0, 0, (float) -(focusZ() + pan));
    }

    private double clampPan(double requested) {
        if (layout == null) return 0;
        var model = catalog.models().get(layout.model(selected));
        Matrix4f camera = localCamera(0);
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) for (int z = 0; z < 2; z++) {
            var corner = camera.transformPosition(new org.joml.Vector3f(
                    (x - .5f) * model.width(), y * model.height(), (z - .5f) * model.length()));
            minX = Math.min(minX, corner.x); maxX = Math.max(maxX, corner.x);
            minY = Math.min(minY, corner.y); maxY = Math.max(maxY, corner.y);
        }
        var direction = camera.transformDirection(new org.joml.Vector3f(0, 0, -1));
        return CarriageFlowPanBounds.clamp(requested, minX, maxX, minY, maxY, direction.x, direction.y);
    }

    private double panSnapPosition(int direction) {
        if (panScreenDirection < 0) {
            if (direction < 0 && panDraggedRight && Math.abs(Math.IEEEremainder(yaw - FRONT_YAW, 360)) < 1e-4) {
                // Pull the carriage at the lower left into the front view.
                return halfVisiblePanPosition(direction);
            }
            double length = catalog.models().get(layout.model(selected)).length();
            return panDragStartPosition + direction * length;
        }

        if (panScreenDirection == 0) {
            double center = layout.center(selected);
            double midpoint = center + (layout.center(selected + direction) - center) / 2;
            double bound = position + clampPan(direction < 0 ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);
            if (direction * (bound - midpoint) >= 0) return midpoint;
        }
        return halfVisiblePanPosition(direction);
    }

    private double halfVisiblePanPosition(int direction) {
        double center = layout.center(selected);
        double neighborCenter = layout.center(selected + direction);
        var model = catalog.models().get(layout.model(selected + direction));
        double pan = CarriageFlowPanBounds.panForHalfVisible(localCamera(0),
                neighborCenter - position, model.width(), model.height(), model.length(), direction);
        if (Double.isFinite(pan)) {
            double visiblePosition = position + pan;
            return direction < 0 ? Math.min(center, visiblePosition) : Math.max(center, visiblePosition);
        }
        return center + direction * Math.min(PAN_RESTRICTED_SNAP_DISTANCE, Math.abs(neighborCenter - center) / 2);
    }

    private void movePan(double requested) {
        if (layout == null || requested == panZ) return;
        int direction = requested < panZ ? -1 : 1;
        double threshold = panSnapPosition(direction) - position;
        if (direction * (requested - threshold) < 0) {
            panZ = panTarget = requested;
            return;
        }

        // Stop this input at the crossing even if a single mouse event spans several carriages.
        panZ = Math.clamp(threshold, Math.min(panZ, requested), Math.max(panZ, requested));
        if (draggingPan) {
            draggingPan = false;
            panSnapCaptured = true;
            carriagePress = null;
        }
        panSnapPending = false;
        focusCarriage(selected + direction);
    }

    private void holdPan() {
        settlingPan = null;
        panTarget = panZ;
        panVelocity = 0;
    }

    private void startPan(double x, double y, double startedAt) {
        panSnapPending = false;
        animate();
        holdPan();
        panDragStartX = x;
        panDragStartY = panDirectionY = y;
        panScreenDirection = 0;
        panDraggedRight = false;
        panDragStartPosition = position + panZ;
        manualPan = true;
        lastPanInputTime = startedAt;
        draggingPan = true;
    }

    private void updatePanDrag(double x, double y) {
        animate();
        // Remember the mouse's vertical direction in GUI pixels, including for the subsequent fling.
        double dy = y - panDirectionY;
        panDraggedRight = x - panDragStartX > PAN_DRAG_SLOP;
        if (Math.abs(dy) >= 1 || (panScreenDirection == 0 && dy != 0)) {
            panScreenDirection = dy < 0 ? -1 : 1;
            panDirectionY = y;
        }
        var direction = localCamera(0).transformDirection(new org.joml.Vector3f(0, 0, -1));
        double lengthSquared = (double) direction.x * direction.x + (double) direction.y * direction.y;
        double delta = lengthSquared > 1e-8
                ? ((x - panDragStartX) * direction.x + (y - panDragStartY) * direction.y) / lengthSquared : 0;
        double requested = panDragStartPosition + delta - position;
        double time = now();
        double elapsed = time - lastPanInputTime;
        if (elapsed > 1e-4) {
            double sampledVelocity = Math.clamp((requested - panZ) / elapsed, -PAN_MAX_SPEED, PAN_MAX_SPEED);
            panVelocity += (sampledVelocity - panVelocity) * (1 - Math.exp(-elapsed * 18));
        }
        lastPanInputTime = time;
        hovered = null;
        movePan(requested);
    }

    public void render(GuiGraphics gui, int mouseX, int mouseY) {
        if (generation != CarriageFlowClient.resourceGeneration) init();
        syncSchematicSlot();
        gui.flush();
        gui.pose().pushPose();
        gui.pose().translate(host.getGuiLeft(), host.getGuiTop(), 0);
        try { renderScene(gui, mouseX - host.getGuiLeft(), mouseY - host.getGuiTop()); }
        finally { gui.pose().popPose(); }
    }

    private void syncSchematicSlot() {
        var menu = host.getMenu();
        if (schematicSlot == null || menu.slots.getFirst() != schematicSlot) {
            restoreSchematicSlot();
            Slot original = menu.slots.getFirst();
            originalSchematicSlot = original;
            // Reuse a native creative slot and its container. Adding a slot would
            // change vanilla's hotbar-to-server slot arithmetic.
            schematicSlot = new Slot(original.container, original.getContainerSlot(), SCHEMATIC_X, SCHEMATIC_Y) {
                @Override public boolean mayPlace(ItemStack stack) { return original.mayPlace(stack); }
                @Override public boolean mayPickup(net.minecraft.world.entity.player.Player player) {
                    return original.mayPickup(player);
                }
            };
            schematicSlot.index = original.index;
            menu.slots.set(0, schematicSlot);
        }
        ItemStack schematic = layout == null ? ItemStack.EMPTY
                : catalog.models().get(layout.model(selected)).schematic();
        if (schematicPrototype != schematic) {
            schematicPrototype = schematic;
            schematicStack = schematic.copy();
        }
        if (menu.items.size() != 1 || menu.items.getFirst() != schematicStack) {
            menu.items.clear();
            menu.items.add(schematicStack);
            menu.scrollTo(0);
        } else if (schematicSlot.getItem() != schematicStack) {
            // Palette screens share vanilla's creative container with this parent.
            // Refresh only this cell while rendering the parent behind a palette.
            schematicSlot.set(schematicStack);
        }
    }

    boolean isSchematicSlot(Slot slot) { return slot == schematicSlot; }

    void restoreSchematicSlot() {
        if (originalSchematicSlot != null && !host.getMenu().slots.isEmpty()
                && host.getMenu().slots.getFirst() == schematicSlot)
            host.getMenu().slots.set(0, originalSchematicSlot);
        originalSchematicSlot = schematicSlot = null;
    }

    private void scissor(GuiGraphics gui, int left, int top, int right, int bottom) {
        gui.enableScissor(host.getGuiLeft() + left, host.getGuiTop() + top,
                host.getGuiLeft() + right, host.getGuiTop() + bottom);
    }

    private void renderScene(GuiGraphics gui, int mouseX, int mouseY) {
        animate();
        // Stretch only the flat slot interior; preserve the original window, tabs and hotbar.
        gui.blit(SLOT_TEXTURE, 8, 17, 162, 90, 9, 18, 16, 16, 256, 256);
        gui.blit(SLOT_TEXTURE, 173, 17, 16, 114, 4, 4, 1, 1, 256, 256);
        // Outer slot bevel and one horizontal divider, never a tiled grid.
        gui.blit(SLOT_TEXTURE, 8, 17, 162, 1, 8, 17, 18, 1, 256, 256);
        gui.blit(SLOT_TEXTURE, 8, 17, 1, 90, 8, 17, 1, 18, 256, 256);
        gui.blit(SLOT_TEXTURE, 169, 17, 1, 90, 25, 17, 1, 18, 256, 256);
        gui.blit(SLOT_TEXTURE, 8, BOTTOM, 162, 1, 8, 34, 18, 1, 256, 256);
        gui.blit(SLOT_TEXTURE, 8, 34, 162, 2, 8, 34, 18, 2, 256, 256);
        gui.blit(SLOT_TEXTURE, SCHEMATIC_X - 1, SCHEMATIC_Y - 1, 8, 17, 18, 18);
        hovered = null;
        selectedTransform = null;
        stripEntries.clear();
        if (layout == null) { renderControls(gui, mouseX, mouseY); return; }
        long previewIndex = draggingStrip ? stripSelection : selected;
        gui.flush();
        scissor(gui, 9, STRIP_Y + 1, 169, 34);
        Matrix4f sideTrack = new Matrix4f(gui.pose().last().pose())
                .translate(width / 2f, STRIP_Y + STRIP_HEIGHT, 200)
                .scale(stripScale, -stripScale, stripScale).rotateY((float) (Math.PI / 2));
        previews.get(layout.model(previewIndex)).renderRails(sideTrack, (int) Math.ceil(width / (256f * stripScale)));
        // Cover the visible strip on both sides, including repeated instances of a single model.
        double stripRadius = 81 / stripScale + maxLength / 2;
        long stripFirst = layout.floorIndex(position - stripRadius);
        long stripLast = layout.floorIndex(position + stripRadius) + 1;
        for (long index = stripFirst; index <= stripLast; index++) {
            var model = catalog.models().get(layout.model(index));
            double x = width / 2.0 + (layout.center(index) - position) * stripScale;
            double half = model.length() * stripScale / 2.0;
            if (x + half < 8 || x - half > width - 8) continue;
            Matrix4f pose = new Matrix4f(gui.pose().last().pose()).translate((float) x, STRIP_Y + STRIP_HEIGHT, 200)
                    .scale(stripScale, -stripScale, stripScale).rotateY((float) (Math.PI / 2))
                    .translate(-model.width() / 2f, 0, -model.length() / 2f);
            previews.get(layout.model(index)).render(pose, 1, 1);
            stripEntries.add(new StripEntry(index, x - half, x + half));
        }
        int markerX = width / 2;
        if (draggingStrip) markerX += (int) Math.round((layout.center(previewIndex) - position) * stripScale);
        gui.fill(markerX - 1, 18, markerX + 1, 20, 0xff875312);
        gui.flush();
        gui.disableScissor();
        int bottom = BOTTOM;
        if (bottom > TOP) {
            gui.flush();
            scissor(gui, LEFT, TOP, RIGHT, bottom);
            double focusZ = focusZ() + panZ;
            Matrix4f camera = new Matrix4f(gui.pose().last().pose()).mul(localCamera(panZ));
            // Panning changes the camera for both rails and carriages; train motion remains independent.
            previews.get(layout.model(previewIndex)).renderRails(camera, 1);
            // Include camera panning when choosing which carriages are visible.
            double sceneCenter = position + focusZ + panZ;
            long first = layout.floorIndex(sceneCenter - visibleRadius - maxLength / 2);
            long last = layout.floorIndex(sceneCenter + visibleRadius + maxLength / 2) + 1;
            float focusBlend = draggingStrip ? 1 : (float) CarriageFlowLayout.ease((lastFrame - focusStarted) / FOCUS_FADE_SECONDS);
            for (long index = first; index <= last; index++) {
                int modelIndex = layout.model(index);
                var model = catalog.models().get(modelIndex);
                Matrix4f transform = new Matrix4f(camera).translate(-model.width() / 2f, 0,
                        (float) (layout.center(index) - position - model.length() / 2.0));
                boolean focused = index == previewIndex;
                if (focused) selectedTransform = new Matrix4f(transform);
                float emphasis = focused ? focusBlend : index == previousFocus ? 1 - focusBlend : 0;
                previews.get(modelIndex).render(transform, Mth.lerp(emphasis, neighborVisibility, 1), Mth.lerp(emphasis, 1, shell));
                if (focused && !draggingPan && stripPress == null
                        && mouseX >= LEFT && mouseX < RIGHT && mouseY >= TOP && mouseY < bottom
                        && Math.abs(position - targetPosition) < .02 && (shell > .995 || shell < .005)) {
                    hovered = previews.get(modelIndex).pick(transform, mouseX + host.getGuiLeft(), mouseY + host.getGuiTop(), shell > .5);
                    hoveredTransform = transform;
                }
            }
            if (hovered != null) drawOutline(hoveredTransform, hovered.outline());
            gui.flush();
            gui.disableScissor();
        }
        renderControls(gui, mouseX, mouseY);
    }

    private int controlAt(double x, double y) {
        if (x < CONTROL_X || x >= CONTROL_X + CONTROL_SIZE) return -1;
        int index = (int) Math.floor((y - CONTROL_Y) / CONTROL_PITCH);
        return index >= 0 && index < 5 && y < CONTROL_Y + index * CONTROL_PITCH + CONTROL_SIZE ? index : -1;
    }

    // Pixel masks face in screen coordinates; no rotation of an asymmetric outline.
    private static final String[] CONTROL_LEFT = {
            "....#..", "...##..", "..###..", ".####..", "..###..", "...##..", "....#.."};
    private static final String[] CONTROL_RIGHT = {
            "..#....", "..##...", "..###..", "..####.", "..###..", "..##...", "..#...."};
    private static final String[] CONTROL_UP = {
            "...#...", "..###..", ".#####.", "#######", ".......", ".......", ".#####."};
    private static final String[] CONTROL_DOWN = {
            ".......", "#######", ".#####.", "..###..", "...#...", ".......", ".#####."};
    private static final String[] CONTROL_CLOCKWISE = {
            "..###..", ".#...#.", "#....#.", "#...###", "#....#.", ".#.....", "..###.."};
    private static final String[] CONTROL_COUNTERCLOCKWISE = {
            "..###..", ".#...#.", ".#....#", "###...#", ".#....#", ".....#.", "..###.."};

    private void renderControls(GuiGraphics gui, int mouseX, int mouseY) {
        gui.pose().pushPose();
        gui.pose().translate(0, 0, 400);
        int hoveredControl = controlAt(mouseX, mouseY);
        for (int i = 0; i < 5; i++) {
            int x = CONTROL_X, y = CONTROL_Y + i * CONTROL_PITCH;
            boolean hover = layout != null && hoveredControl == i;
            // Bedrock touch D-pad: square bevel, broad solid arrow and a one-pixel recess.
            gui.fill(x, y, x + CONTROL_SIZE, y + CONTROL_SIZE, 0xFF666666);
            gui.fill(x + 1, y + 1, x + 13, y + 13, 0xFFAAAAAA);
            gui.fill(x + 1, y + 1, x + 13, y + 2, 0xFFFFFFFF);
            gui.fill(x + 1, y + 2, x + 2, y + 12, 0xFFF7F7F7);
            gui.fill(x + 2, y + 2, x + 12, y + 11, hover ? 0xFFF8F8F8 : 0xFFE2E2E2);
            gui.fill(x + 2, y + 11, x + 12, y + 12, 0xFFC4C4C4);
            String[] icon = switch (i) {
                case 0 -> targetShell > .5f ? CONTROL_UP : CONTROL_DOWN;
                case 1 -> CONTROL_CLOCKWISE;
                case 2 -> CONTROL_COUNTERCLOCKWISE;
                case 3 -> CONTROL_LEFT;
                default -> CONTROL_RIGHT;
            };
            drawControlPixels(gui, icon, x + 3, y + 4, layout == null ? 0xFFC0C0C0 : 0xFFA4A4A4);
            drawControlPixels(gui, icon, x + 3, y + 3, layout == null ? 0xFFA8A8A8 : 0xFF555555);
        }
        gui.pose().popPose();
    }

    private static void drawControlPixels(GuiGraphics gui, String[] icon, int x, int y, int color) {
        for (int row = 0; row < icon.length; row++) {
            for (int col = 0; col < icon[row].length(); col++) {
                if (icon[row].charAt(col) == '#') gui.fill(x + col, y + row, x + col + 1, y + row + 1, color);
            }
        }
    }

    private void activateControl(int control) {
        if (layout == null) return;
        animate();
        if (control >= 3) {
            // Project the camera pan into screen space: arrow direction describes the visible train,
            // not the camera or the model's north/south axis.
            double screenX = localCamera(0).transformDirection(new org.joml.Vector3f(0, 0, -1)).x;
            if (Math.abs(screenX) < .0001) screenX = -Math.sin(Math.toRadians(targetYaw));
            double step = (control == 3 ? -1 : 1) * (screenX >= 0 ? 1 : -1);
            // A direction reversal should not first finish a queued movement the other way.
            if ((panTarget - panZ) * step < 0) panTarget = panZ;
            panTarget = clampPan(panTarget + step);
        } else {
            fromYaw = yaw;
            fromShell = shell;
            if (control == 0) targetShell = targetShell > .5f ? 0 : 1;
            else phase += control == 1 ? -1 : 1;
            targetYaw = FRONT_YAW + phase * 90;
            viewStarted = now();
        }
        hovered = null;
        minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1));
    }

    private void drawOutline(Matrix4f matrix, net.minecraft.world.phys.shapes.VoxelShape shape) {
        try (ByteBufferBuilder memory = new ByteBufferBuilder(2048)) {
            BufferBuilder lines = new BufferBuilder(memory, VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
            shape.forAllEdges((x1, y1, z1, x2, y2, z2) ->
                    line(lines, matrix, (float) x1, (float) y1, (float) z1,
                            (float) x2, (float) y2, (float) z2, 0xffffd36b));
            drawLines(lines);
        }
    }

    private static void line(BufferBuilder builder, Matrix4f pose, float x, float y, float z, float x2, float y2, float z2, int color) {
        builder.addVertex(pose, x, y, z).setColor(color);
        builder.addVertex(pose, x2, y2, z2).setColor(color);
    }

    private static void drawLines(BufferBuilder builder) {
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    private boolean overCarriage(double x, double y) {
        return layout != null && selectedTransform != null && previews.get(layout.model(selected)).pick(
                selectedTransform, x + host.getGuiLeft(), y + host.getGuiTop(), shell > .5) != null;
    }

    private static boolean overStrip(double x, double y) {
        return x >= 9 && x < 169 && y >= STRIP_Y && y <= STRIP_Y + STRIP_HEIGHT;
    }

    private void dragStrip(double x, double y) {
        if (!draggingStrip && stripPress.withinClickSlop(x, y)) return;
        draggingStrip = true;
        double nextPosition = stripPress.position() - (x - stripPress.mouseX()) / stripScale;
        if (nextPosition != position) {
            position = nextPosition;
            stripSelection = layout.selectDuringDrag(stripSelection, position);
            motion.restore(position, 0, targetPosition);
        }
        hovered = null;
    }

    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (stripPress != null) return true;
        double delta = vertical != 0 ? vertical : horizontal;
        if (delta == 0) return false;
        int step = delta > 0 ? -1 : 1;
        if (overStrip(x, y)) select(selected + step);
        else if (x >= LEFT && x < RIGHT && y >= TOP && y < BOTTOM && layout != null) {
            // Use the same block shapes as hover picking; empty gaps belong to camera panning.
            if (overCarriage(x, y)) cycle(step);
            else {
                panSnapPending = false;
                panVelocity = 0;
                panTarget = clampPan(panTarget + step * .5);
            }
        }
        else return false;
        return true;
    }

    public boolean mouseClicked(double x, double y, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) panSnapCaptured = false;
        int control = controlAt(x, y);
        if (control >= 0) {
            if (button == 0) activateControl(control);
            return true;
        }
        if (x >= 9 && x < 169 && y >= 18 && y < BOTTOM && !host.getMenu().getCarried().isEmpty()) {
            host.getMenu().setCarried(net.minecraft.world.item.ItemStack.EMPTY);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && layout != null && overStrip(x, y)) {
            StripEntry clicked = null;
            for (StripEntry entry : stripEntries) {
                if (x >= entry.left() && x <= entry.right()) {
                    clicked = entry;
                    break;
                }
            }
            double center = layout.center(selected);
            double spacing = Math.min(center - layout.center(selected - 1), layout.center(selected + 1) - center);
            double clickSlopX = Math.min(STRIP_CLICK_SLOP, spacing * stripScale / 2);
            panSnapPending = false;
            carriagePress = null;
            animate();
            holdPan();
            manualPan = false;
            stripPress = new StripPress(x, y, position, clickSlopX, clicked);
            stripSelection = selected;
            draggingStrip = draggingPan = false;
            hovered = null;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && layout != null
                && x >= LEFT && x < RIGHT && y >= TOP && y < BOTTOM) {
            panSnapPending = false;
            double pressedAt = now();
            if (hovered != null) {
                animate();
                holdPan();
                carriagePress = new CarriagePress(x, y, pressedAt, selected, hovered);
            } else {
                carriagePress = null;
                startPan(x, y, pressedAt);
            }
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double x, double y, int button, double dragX, double dragY) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (panSnapCaptured) return true;
        if (layout == null) return false;
        if (stripPress != null) {
            dragStrip(x, y);
            return true;
        }
        if (carriagePress != null) {
            double pressDeltaX = x - carriagePress.mouseX();
            double pressDeltaY = y - carriagePress.mouseY();
            if (!draggingPan) {
                if (Math.abs(pressDeltaX) <= PAN_DRAG_SLOP && Math.abs(pressDeltaY) <= PAN_DRAG_SLOP)
                    return true;
                startPan(carriagePress.mouseX(), carriagePress.mouseY(), carriagePress.pressedAt());
            }
            updatePanDrag(x, y);
            return true;
        }
        if (!draggingPan) return false;
        updatePanDrag(x, y);
        return true;
    }

    public boolean mouseReleased(double x, double y, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (panSnapCaptured) {
            panSnapCaptured = false;
            return true;
        }
        if (stripPress != null) {
            if (draggingStrip) {
                select(stripSelection);
            } else if (stripPress.entry() != null && overStrip(x, y) && stripPress.withinClickSlop(x, y)) {
                select(stripPress.entry().index());
            }
            stripPress = null;
            draggingStrip = false;
            return true;
        }
        if (carriagePress != null) {
            CarriagePress press = carriagePress;
            carriagePress = null;
            if (draggingPan) {
                draggingPan = false;
                panSnapPending = true;
            } else if (Math.abs(x - press.mouseX()) <= PAN_DRAG_SLOP
                    && Math.abs(y - press.mouseY()) <= PAN_DRAG_SLOP) {
                var model = catalog.models().get(layout.model(press.index()));
                var items = catalog.variants(model, press.hit().cell());
                if (!items.isEmpty()) {
                    openingPalette = true;
                    minecraft.setScreen(new CarriageFlowPaletteScreen(this, items,
                            (int) press.mouseX(), (int) press.mouseY()));
                }
            }
            return true;
        }
        if (!draggingPan) return false;
        draggingPan = false;
        panSnapPending = true;
        return true;
    }

    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) {
            if (stripPress == null) select(selected + (key == GLFW.GLFW_KEY_LEFT ? -1 : 1));
            return true;
        }
        if (key == GLFW.GLFW_KEY_SPACE) { cycle(1); return true; }
        return false;
    }

    public boolean contains(double x, double y) { return x >= 8 && x < 188 && y >= 17 && y < 107; }
    private void closeMeshes() { previews.clear(); }
    @Override public void close() {
        draggingPan = false;
        panSnapPending = false;
        panSnapCaptured = false;
        carriagePress = null;
        stripPress = null;
        draggingStrip = false;
        restoreSchematicSlot();
        if (layout != null) {
            SAVED.put(tab, new Saved(catalog.models().get(layout.model(selected)).id(), selected, phase, targetShell < .5f,
                    panTarget, position - targetPosition, motion.velocity(), settlingPan == null ? 0 : panZ - panTarget,
                    settlingPan == null ? 0 : settlingPan.velocity()));
        }
        closeMeshes();
    }
}
