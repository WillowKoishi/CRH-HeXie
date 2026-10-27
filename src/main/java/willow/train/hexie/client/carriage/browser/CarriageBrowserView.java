package willow.train.hexie.client.carriage.browser;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import willow.train.hexie.Main;

import java.util.*;

public final class CarriageBrowserView implements AutoCloseable {
    static final ResourceLocation SLOT_TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/creative_inventory/tab_items.png");
    private static final int STRIP_Y = 17;
    private static final int STRIP_HEIGHT = 18;
    private static final int PREVIEW_TOP = 37;
    private static final float FRONT_YAW = 142;
    private static final float CAMERA_PITCH = 44;
    final CreativeModeInventoryScreen host;
    private final net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
    private static final int width = 178, height = 146;
    private CarriageAssets.Catalog catalog;
    private final List<CarriagePreview> previews = new ArrayList<>();
    private CarriageLayout layout;
    private int generation = -1;
    private long selected;
    private double position, targetPosition, lastFrame = now(), lastInput = -100;
    private final CarriageMotion motion = new CarriageMotion();
    private float neighborVisibility = .25f;
    private float maxLength, focusDistance, visibleRadius;
    private long phase;
    private double yaw = FRONT_YAW, fromYaw = FRONT_YAW, targetYaw = FRONT_YAW, viewStarted;
    private float shell = 1, fromShell = 1, targetShell = 1;
    private float sceneScale, stripScale, trackOriginY;
    boolean openingPalette;
    private CarriagePreview.Hit hovered;
    private Matrix4f hoveredTransform;
    private final List<StripEntry> stripEntries = new ArrayList<>();
    private static final Map<ResourceLocation, Saved> SAVED = new HashMap<>();
    final ResourceLocation tab;
    private record Saved(ResourceLocation model, long index, long phase, double offset, double velocity) {}
    private record StripEntry(long index, double left, double right) {}

    public CarriageBrowserView(CreativeModeInventoryScreen host) { this.host = host; this.tab = CarriageBrowserClient.tabId(host); init(); }
    private static double now() { return System.nanoTime() / 1_000_000_000.0; }

    private void init() {
        if (catalog == null || generation != CarriageBrowserClient.resourceGeneration || previews.isEmpty()) load();
        maxLength = catalog.models().stream().map(m -> (float) m.length()).max(Float::compare).orElse(19f);
        float maxWidth = catalog.models().stream().map(m -> (float) m.width()).max(Float::compare).orElse(5f);
        float maxHeight = catalog.models().stream().map(m -> (float) m.height()).max(Float::compare).orElse(6f);
        // Fixed close-up: six blocks occupy 72 GUI pixels, regardless of carriage length.
        sceneScale = 12;
        focusDistance = Math.max(0, maxLength / 2 - 3);
        visibleRadius = width / sceneScale + maxWidth + maxHeight;
        stripScale = STRIP_HEIGHT / 9f;
        // Leave room for the near end below the track focus with the steeper roof view.
        trackOriginY = PREVIEW_TOP + (106 - PREVIEW_TOP) * .65f;
    }

    private void load() {
        closeMeshes();
        generation = CarriageBrowserClient.resourceGeneration;
        catalog = CarriageAssets.load(minecraft.getResourceManager(), CarriageBrowserClient.page(host).carriages());
        List<CarriageAssets.Model> usable = new ArrayList<>();
        List<String> errors = new ArrayList<>(catalog.errors());
        for (var model : catalog.models()) {
            try { previews.add(new CarriagePreview(model)); usable.add(model); }
            catch (RuntimeException e) {
                errors.add(model.id() + ": " + e.getMessage());
                Main.LOGGER.warn("Could not bake carriage preview {}", model.id(), e);
            }
        }
        catalog = new CarriageAssets.Catalog(List.copyOf(usable), List.copyOf(errors));
        if (!usable.isEmpty()) {
            layout = new CarriageLayout(usable.stream().mapToDouble(CarriageAssets.Model::length).toArray(),
                    usable.stream().mapToDouble(CarriageAssets.Model::frontGap).toArray(),
                    usable.stream().mapToDouble(CarriageAssets.Model::rearGap).toArray());
        } else layout = null;
        selected = 0;
        position = targetPosition = 0;
        motion.reset();
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
                yaw = fromYaw = targetYaw = FRONT_YAW + Math.floorDiv(phase + 1, 2) * 180;
                shell = fromShell = targetShell = Math.floorMod(phase, 4) < 2 ? 1 : 0;
                neighborVisibility = 1;
                lastInput = now();
            }
        }
        lastFrame = now();
        hovered = null;
    }

    private void animate() {
        double time = now();
        double elapsed = Math.min(.1, Math.max(0, time - lastFrame));
        lastFrame = time;
        motion.advance(elapsed);
        position = motion.position();
        boolean scrolling = time - lastInput < .16;
        float opacity = scrolling ? 1 : .25f + .75f * (float) Math.min(1, Math.abs(motion.velocity()) / 12);
        neighborVisibility += (opacity - neighborVisibility) * (float) (1 - Math.exp(-elapsed * 12));
        double t = CarriageLayout.ease((now() - viewStarted) / .8);
        yaw = fromYaw + (targetYaw - fromYaw) * t;
        shell = (float) (fromShell + (targetShell - fromShell) * t);
    }

    private void select(long index) {
        if (layout == null) return;
        animate();
        selected = index;
        targetPosition = layout.center(index);
        motion.target(targetPosition);
        lastInput = now();
        hovered = null;
    }

    private void cycle(int delta) {
        animate();
        phase += delta;
        fromYaw = yaw;
        fromShell = shell;
        targetYaw = FRONT_YAW + Math.floorDiv(phase + 1, 2) * 180;
        targetShell = Math.floorMod(phase, 4) < 2 ? 1 : 0;
        viewStarted = now();
    }

    public void render(GuiGraphics gui, int mouseX, int mouseY) {
        if (generation != CarriageBrowserClient.resourceGeneration) init();
        if (!host.getMenu().items.isEmpty()) {
            host.getMenu().items.clear();
            host.getMenu().scrollTo(0);
        }
        gui.flush();
        gui.pose().pushPose();
        gui.pose().translate(host.getGuiLeft(), host.getGuiTop(), 0);
        try { renderScene(gui, mouseX - host.getGuiLeft(), mouseY - host.getGuiTop()); }
        finally { gui.pose().popPose(); }
    }

    private void scissor(GuiGraphics gui, int left, int top, int right, int bottom) {
        gui.enableScissor(host.getGuiLeft() + left, host.getGuiTop() + top,
                host.getGuiLeft() + right, host.getGuiTop() + bottom);
    }

    private void renderScene(GuiGraphics gui, int mouseX, int mouseY) {
        animate();
        // Stretch only the flat slot interior; preserve the original window, tabs and hotbar.
        gui.blit(SLOT_TEXTURE, 8, 17, 162, 90, 9, 18, 16, 16, 256, 256);
        gui.blit(SLOT_TEXTURE, 173, 17, 16, 96, 4, 4, 1, 1, 256, 256);
        // Outer slot bevel and one horizontal divider, never a tiled grid.
        gui.blit(SLOT_TEXTURE, 8, 17, 162, 1, 8, 17, 18, 1, 256, 256);
        gui.blit(SLOT_TEXTURE, 8, 17, 1, 90, 8, 17, 1, 18, 256, 256);
        gui.blit(SLOT_TEXTURE, 169, 17, 1, 90, 25, 17, 1, 18, 256, 256);
        gui.blit(SLOT_TEXTURE, 8, 106, 162, 1, 8, 34, 18, 1, 256, 256);
        gui.blit(SLOT_TEXTURE, 8, 34, 162, 2, 8, 34, 18, 2, 256, 256);
        hovered = null;
        stripEntries.clear();
        if (layout == null) return;
        gui.flush();
        scissor(gui, 9, STRIP_Y + 1, 169, 34);
        Matrix4f sideTrack = new Matrix4f(gui.pose().last().pose())
                .translate(width / 2f, STRIP_Y + STRIP_HEIGHT, 200)
                .scale(stripScale, -stripScale, stripScale).rotateY((float) (Math.PI / 2));
        previews.get(layout.model(selected)).renderRails(sideTrack, (int) Math.ceil(width / (256f * stripScale)));
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
        gui.fill(width / 2 - 1, 18, width / 2 + 1, 20, 0xff875312);
        gui.flush();
        gui.disableScissor();
        int bottom = 106;
        if (bottom > PREVIEW_TOP) {
            gui.flush();
            scissor(gui, 9, PREVIEW_TOP, 169, bottom);
            double focusZ = -Math.cos(Math.toRadians(yaw - FRONT_YAW)) * focusDistance;
            Matrix4f camera = new Matrix4f(gui.pose().last().pose())
                    .translate(width / 2f, trackOriginY, 200)
                    .scale(sceneScale, -sceneScale, sceneScale)
                    .rotateX((float) Math.toRadians(CAMERA_PITCH)).rotateY((float) Math.toRadians(yaw))
                    .translate(0, 0, (float) -focusZ);
            // Camera and rail geometry stay fixed. Only carriage positions translate along Z.
            previews.get(layout.model(selected)).renderRails(camera, 1);
            // Visibility follows the actual train position, not the potentially distant target.
            double sceneCenter = position + focusZ;
            long first = layout.floorIndex(sceneCenter - visibleRadius - maxLength / 2);
            long last = layout.floorIndex(sceneCenter + visibleRadius + maxLength / 2) + 1;
            for (long index = first; index <= last; index++) {
                int modelIndex = layout.model(index);
                var model = catalog.models().get(modelIndex);
                Matrix4f transform = new Matrix4f(camera).translate(-model.width() / 2f, 0,
                        (float) (layout.center(index) - position - model.length() / 2.0));
                previews.get(modelIndex).render(transform, index == selected ? 1 : neighborVisibility, index == selected ? shell : 1);
                if (index == selected && mouseX >= 9 && mouseX < 169 && mouseY >= PREVIEW_TOP && mouseY < bottom
                        && Math.abs(position - targetPosition) < .02 && (shell > .995 || shell < .005)) {
                    hovered = previews.get(modelIndex).pick(transform, mouseX + host.getGuiLeft(), mouseY + host.getGuiTop(), shell > .5);
                    hoveredTransform = transform;
                }
            }
            if (hovered != null) drawOutline(hoveredTransform, hovered.outline());
            gui.flush();
            gui.disableScissor();
        }
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

    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        double delta = vertical != 0 ? vertical : horizontal;
        if (delta == 0) return false;
        int step = delta > 0 ? -1 : 1;
        if (y >= STRIP_Y && y <= STRIP_Y + STRIP_HEIGHT) select(selected + step);
        else if (y >= PREVIEW_TOP && y < height - 39) cycle(step);
        else return false;
        return true;
    }

    public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && y >= STRIP_Y && y <= STRIP_Y + STRIP_HEIGHT) {
            for (StripEntry entry : stripEntries) if (x >= entry.left && x <= entry.right) { select(entry.index); return true; }
        }
        if (button == 0 && hovered != null && layout != null && y >= PREVIEW_TOP && y < height - 39) {
            var model = catalog.models().get(layout.model(selected));
            var items = catalog.variants(model, hovered.cell());
            if (!items.isEmpty()) {
                openingPalette = true;
                minecraft.setScreen(new CarriagePaletteScreen(this, items, (int) x, (int) y));
            }
            return true;
        }
        return false;
    }

    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) { select(selected + (key == GLFW.GLFW_KEY_LEFT ? -1 : 1)); return true; }
        if (key == GLFW.GLFW_KEY_SPACE) { cycle(1); return true; }
        return false;
    }

    public boolean contains(double x, double y) { return x >= 8 && x < 188 && y >= 17 && y < 107; }
    private void closeMeshes() { previews.forEach(CarriagePreview::close); previews.clear(); }
    @Override public void close() {
        if (layout != null) {
            SAVED.put(tab, new Saved(catalog.models().get(layout.model(selected)).id(), selected, phase,
                    position - targetPosition, motion.velocity()));
        }
        closeMeshes();
    }
}
