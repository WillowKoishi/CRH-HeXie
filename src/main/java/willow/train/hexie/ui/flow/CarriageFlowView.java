package willow.train.hexie.ui.flow;

// split from willow/train/hexie/client/carriage/flow/CarriageFlowView.java

import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;

import static willow.train.hexie.ui.flow.CarriageFlowPanBounds.BOTTOM;
import static willow.train.hexie.ui.flow.CarriageFlowPanBounds.TOP;

public final class CarriageFlowView implements AutoCloseable {
    final CarriageFlowNavigation navigation = new CarriageFlowNavigation(this);
    final CarriageFlowGestures gestures = new CarriageFlowGestures(this);
    final CarriageFlowScene scene = new CarriageFlowScene(this);
    final CarriageFlowSchematicSlot schematic = new CarriageFlowSchematicSlot(this);

    final CreativeModeInventoryScreen host;
    final net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
    static final int width = 178, height = 146;
    CarriageFlowAssets.Catalog catalog;
    final List<CarriageFlowPreview> previews = new ArrayList<>();
    CarriageFlowLayout layout;
    private int generation = -1;
    float maxLength, focusDistance, visibleRadius;
    boolean openingPalette;
    private static final Map<ResourceLocation, Saved> SAVED = new HashMap<>();
    final ResourceLocation tab;
    private record Saved(ResourceLocation model, long index, long phase, boolean open, double pan, double offset,
                         double velocity, double settlingOffset, double settlingVelocity) {}

    public CarriageFlowView(CreativeModeInventoryScreen host) { this.host = host; this.tab = CarriageFlowClient.tabId(host); init(); }

    private void init() {
        if (catalog == null || generation != CarriageFlowClient.resourceGeneration || previews.isEmpty()) load();
        maxLength = catalog.models().stream().map(m -> (float) m.length()).max(Float::compare).orElse(19f);
        float maxWidth = catalog.models().stream().map(m -> (float) m.width()).max(Float::compare).orElse(5f);
        float maxHeight = catalog.models().stream().map(m -> (float) m.height()).max(Float::compare).orElse(6f);
        // Fixed close-up: six blocks occupy 72 GUI pixels, regardless of carriage length.
        scene.sceneScale = 12;
        focusDistance = Math.max(0, maxLength / 2 - 3);
        visibleRadius = width / scene.sceneScale + maxWidth + maxHeight;
        scene.stripScale = CarriageFlowScene.STRIP_HEIGHT / 9f;
        // Leave room for the near end below the track focus with the steeper roof view.
        scene.trackOriginY = TOP + (BOTTOM - TOP) * .65f;
    }

    private void load() {
        closeMeshes();
        generation = CarriageFlowClient.resourceGeneration;
        CarriageFlowPreviewCache.warmPreviews();
        catalog = CarriageFlowPreviewCache.catalog(CarriageFlowClient.page(host));
        List<CarriageFlowAssets.Model> usable = catalog.models();
        for (var model : usable) previews.add(CarriageFlowPreviewCache.preview(model.id()));
        if (!usable.isEmpty()) {
            layout = new CarriageFlowLayout(usable.stream().mapToDouble(CarriageFlowAssets.Model::length).toArray(),
                    usable.stream().mapToDouble(CarriageFlowAssets.Model::frontGap).toArray(),
                    usable.stream().mapToDouble(CarriageFlowAssets.Model::rearGap).toArray());
        } else layout = null;
        navigation.selected = 0;
        navigation.position = navigation.targetPosition = 0;
        navigation.motion.reset();
        navigation.panZ = navigation.panTarget = navigation.panVelocity = navigation.lastPanInputTime = 0;
        navigation.settlingPan = null;
        navigation.focusStarted = Double.NEGATIVE_INFINITY;
        navigation.manualPan = false;
        navigation.panSnapPending = false;
        navigation.panSnapCaptured = false;
        gestures.carriagePress = null;
        Saved saved = SAVED.get(tab);
        if (layout != null && saved != null) {
            int match = -1;
            for (int i = 0; i < usable.size(); i++) if (usable.get(i).id().equals(saved.model())) { match = i; break; }
            if (match >= 0) {
                navigation.selected = layout.model(saved.index()) == match ? saved.index() : match;
                navigation.targetPosition = layout.center(navigation.selected);
                double offset = saved.offset();
                double velocity = saved.velocity();
                // Re-enter a short approach even when the previous train was fully stopped.
                if (Math.abs(offset) < .02 && Math.abs(velocity) < .1) { offset = -1.2; velocity = 6; }
                navigation.position = navigation.targetPosition + offset;
                navigation.motion.restore(navigation.position, velocity, navigation.targetPosition);
                navigation.phase = saved.phase();
                navigation.yaw = navigation.fromYaw = navigation.targetYaw = CarriageFlowNavigation.FRONT_YAW + navigation.phase * 90;
                navigation.shell = navigation.fromShell = navigation.targetShell = saved.open() ? 0 : 1;
                navigation.panTarget = saved.pan();
                navigation.panZ = navigation.panTarget + saved.settlingOffset();
                if (saved.settlingOffset() != 0 || saved.settlingVelocity() != 0) {
                    navigation.settlingPan = new CarriageFlowMotion();
                    navigation.settlingPan.restore(navigation.panZ, saved.settlingVelocity(), navigation.panTarget);
                }
                navigation.neighborVisibility = 1;
                navigation.lastInput = CarriageFlowNavigation.now();
            }
        }
        navigation.lastFrame = CarriageFlowNavigation.now();
        navigation.draggingPan = false;
        navigation.panSnapPending = false;
        gestures.carriagePress = null;
        gestures.stripPress = null;
        gestures.draggingStrip = false;
        scene.hovered = null;
    }

    public void render(GuiGraphics gui, int mouseX, int mouseY) {
        if (generation != CarriageFlowClient.resourceGeneration) init();
        schematic.syncSchematicSlot();
        gui.flush();
        gui.pose().pushPose();
        gui.pose().translate(host.getGuiLeft(), host.getGuiTop(), 0);
        try { scene.renderScene(gui, mouseX - host.getGuiLeft(), mouseY - host.getGuiTop()); }
        finally { gui.pose().popPose(); }
    }

    private void closeMeshes() { previews.clear(); }
    @Override public void close() {
        navigation.draggingPan = false;
        navigation.panSnapPending = false;
        navigation.panSnapCaptured = false;
        gestures.carriagePress = null;
        gestures.stripPress = null;
        gestures.draggingStrip = false;
        schematic.restoreSchematicSlot();
        if (layout != null) {
            SAVED.put(tab, new Saved(catalog.models().get(layout.model(navigation.selected)).id(), navigation.selected, navigation.phase, navigation.targetShell < .5f,
                    navigation.panTarget, navigation.position - navigation.targetPosition, navigation.motion.velocity(), navigation.settlingPan == null ? 0 : navigation.panZ - navigation.panTarget,
                    navigation.settlingPan == null ? 0 : navigation.settlingPan.velocity()));
        }
        closeMeshes();
    }

    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return gestures.mouseScrolled(x, y, horizontal, vertical);
    }

    public boolean mouseClicked(double x, double y, int button) {
        return gestures.mouseClicked(x, y, button);
    }

    public boolean mouseDragged(double x, double y, int button, double dragX, double dragY) {
        return gestures.mouseDragged(x, y, button, dragX, dragY);
    }

    public boolean mouseReleased(double x, double y, int button) {
        return gestures.mouseReleased(x, y, button);
    }

    public boolean keyPressed(int key, int scan, int modifiers) {
        return gestures.keyPressed(key, scan, modifiers);
    }

    public boolean contains(double x, double y) {
        return gestures.contains(x, y);
    }

    boolean isSchematicSlot(Slot slot) {
        return schematic.isSchematicSlot(slot);
    }

    void restoreSchematicSlot() {
        schematic.restoreSchematicSlot();
    }
}
