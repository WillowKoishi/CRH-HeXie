package willow.train.hexie.ui.flow;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import static willow.train.hexie.ui.flow.CarriageFlowPanBounds.BOTTOM;
import static willow.train.hexie.ui.flow.CarriageFlowPanBounds.LEFT;
import static willow.train.hexie.ui.flow.CarriageFlowPanBounds.RIGHT;
import static willow.train.hexie.ui.flow.CarriageFlowPanBounds.TOP;

// split from willow/train/hexie/client/carriage/flow/CarriageFlowView.java
final class CarriageFlowScene {
    private final CarriageFlowView view;

    CarriageFlowScene(CarriageFlowView view) { this.view = view; }

    static final ResourceLocation SLOT_TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/creative_inventory/tab_items.png");
    static final int STRIP_Y = 17;
    static final int STRIP_HEIGHT = 18;

    private static final int CONTROL_X = 174, CONTROL_Y = 19, CONTROL_SIZE = 14, CONTROL_PITCH = 18;

    Matrix4f selectedTransform;

    float sceneScale, stripScale, trackOriginY;

    CarriageFlowPreview.Hit hovered;
    private Matrix4f hoveredTransform;

    final List<StripEntry> stripEntries = new ArrayList<>();

    record StripEntry(long index, double left, double right) {}

    private void scissor(GuiGraphics gui, int left, int top, int right, int bottom) {
        gui.enableScissor(view.host.getGuiLeft() + left, view.host.getGuiTop() + top,
                view.host.getGuiLeft() + right, view.host.getGuiTop() + bottom);
    }

    void renderScene(GuiGraphics gui, int mouseX, int mouseY) {
        view.navigation.animate();
        // Stretch only the flat slot interior; preserve the original window, tabs and hotbar.
        gui.blit(SLOT_TEXTURE, 8, 17, 162, 90, 9, 18, 16, 16, 256, 256);
        gui.blit(SLOT_TEXTURE, 173, 17, 16, 114, 4, 4, 1, 1, 256, 256);
        // Outer slot bevel and one horizontal divider, never a tiled grid.
        gui.blit(SLOT_TEXTURE, 8, 17, 162, 1, 8, 17, 18, 1, 256, 256);
        gui.blit(SLOT_TEXTURE, 8, 17, 1, 90, 8, 17, 1, 18, 256, 256);
        gui.blit(SLOT_TEXTURE, 169, 17, 1, 90, 25, 17, 1, 18, 256, 256);
        gui.blit(SLOT_TEXTURE, 8, BOTTOM, 162, 1, 8, 34, 18, 1, 256, 256);
        gui.blit(SLOT_TEXTURE, 8, 34, 162, 2, 8, 34, 18, 2, 256, 256);
        gui.blit(SLOT_TEXTURE, CarriageFlowSchematicSlot.SCHEMATIC_X - 1, CarriageFlowSchematicSlot.SCHEMATIC_Y - 1, 8, 17, 18, 18);
        hovered = null;
        selectedTransform = null;
        stripEntries.clear();
        if (view.layout == null) { renderControls(gui, mouseX, mouseY); return; }
        long previewIndex = view.gestures.draggingStrip ? view.gestures.stripSelection : view.navigation.selected;
        gui.flush();
        scissor(gui, 9, STRIP_Y + 1, 169, 34);
        Matrix4f sideTrack = new Matrix4f(gui.pose().last().pose())
                .translate(CarriageFlowView.width / 2f, STRIP_Y + STRIP_HEIGHT, 200)
                .scale(stripScale, -stripScale, stripScale).rotateY((float) (Math.PI / 2));
        view.previews.get(view.layout.model(previewIndex)).renderRails(sideTrack, (int) Math.ceil(CarriageFlowView.width / (256f * stripScale)));
        // Cover the visible strip on both sides, including repeated instances of a single model.
        double stripRadius = 81 / stripScale + view.maxLength / 2;
        long stripFirst = view.layout.floorIndex(view.navigation.position - stripRadius);
        long stripLast = view.layout.floorIndex(view.navigation.position + stripRadius) + 1;
        for (long index = stripFirst; index <= stripLast; index++) {
            var model = view.catalog.models().get(view.layout.model(index));
            double x = CarriageFlowView.width / 2.0 + (view.layout.center(index) - view.navigation.position) * stripScale;
            double half = model.length() * stripScale / 2.0;
            if (x + half < 8 || x - half > CarriageFlowView.width - 8) continue;
            Matrix4f pose = new Matrix4f(gui.pose().last().pose()).translate((float) x, STRIP_Y + STRIP_HEIGHT, 200)
                    .scale(stripScale, -stripScale, stripScale).rotateY((float) (Math.PI / 2))
                    .translate(-model.width() / 2f, 0, -model.length() / 2f);
            view.previews.get(view.layout.model(index)).render(pose, 1, 1);
            stripEntries.add(new StripEntry(index, x - half, x + half));
        }
        int markerX = CarriageFlowView.width / 2;
        if (view.gestures.draggingStrip) markerX += (int) Math.round((view.layout.center(previewIndex) - view.navigation.position) * stripScale);
        gui.fill(markerX - 1, 18, markerX + 1, 20, 0xff875312);
        gui.flush();
        gui.disableScissor();
        int bottom = BOTTOM;
        if (bottom > TOP) {
            gui.flush();
            scissor(gui, LEFT, TOP, RIGHT, bottom);
            double focusZ = view.navigation.focusZ() + view.navigation.panZ;
            Matrix4f camera = new Matrix4f(gui.pose().last().pose()).mul(view.navigation.localCamera(view.navigation.panZ));
            // Panning changes the camera for both rails and carriages; train motion remains independent.
            view.previews.get(view.layout.model(previewIndex)).renderRails(camera, 1);
            // Include camera panning when choosing which carriages are visible.
            double sceneCenter = view.navigation.position + focusZ + view.navigation.panZ;
            long first = view.layout.floorIndex(sceneCenter - view.visibleRadius - view.maxLength / 2);
            long last = view.layout.floorIndex(sceneCenter + view.visibleRadius + view.maxLength / 2) + 1;
            float focusBlend = view.gestures.draggingStrip ? 1 : (float) CarriageFlowLayout.ease((view.navigation.lastFrame - view.navigation.focusStarted) / CarriageFlowNavigation.FOCUS_FADE_SECONDS);
            for (long index = first; index <= last; index++) {
                int modelIndex = view.layout.model(index);
                var model = view.catalog.models().get(modelIndex);
                Matrix4f transform = new Matrix4f(camera).translate(-model.width() / 2f, 0,
                        (float) (view.layout.center(index) - view.navigation.position - model.length() / 2.0));
                boolean focused = index == previewIndex;
                if (focused) selectedTransform = new Matrix4f(transform);
                float emphasis = focused ? focusBlend : index == view.navigation.previousFocus ? 1 - focusBlend : 0;
                view.previews.get(modelIndex).render(transform, Mth.lerp(emphasis, view.navigation.neighborVisibility, 1), Mth.lerp(emphasis, 1, view.navigation.shell));
                if (focused && !view.navigation.draggingPan && view.gestures.stripPress == null
                        && mouseX >= LEFT && mouseX < RIGHT && mouseY >= TOP && mouseY < bottom
                        && Math.abs(view.navigation.position - view.navigation.targetPosition) < .02 && (view.navigation.shell > .995 || view.navigation.shell < .005)) {
                    hovered = view.previews.get(modelIndex).pick(transform, mouseX + view.host.getGuiLeft(), mouseY + view.host.getGuiTop(), view.navigation.shell > .5);
                    hoveredTransform = transform;
                }
            }
            if (hovered != null) drawOutline(hoveredTransform, hovered.outline());
            gui.flush();
            gui.disableScissor();
        }
        renderControls(gui, mouseX, mouseY);
    }

    int controlAt(double x, double y) {
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
            boolean hover = view.layout != null && hoveredControl == i;
            // Bedrock touch D-pad: square bevel, broad solid arrow and a one-pixel recess.
            gui.fill(x, y, x + CONTROL_SIZE, y + CONTROL_SIZE, 0xFF666666);
            gui.fill(x + 1, y + 1, x + 13, y + 13, 0xFFAAAAAA);
            gui.fill(x + 1, y + 1, x + 13, y + 2, 0xFFFFFFFF);
            gui.fill(x + 1, y + 2, x + 2, y + 12, 0xFFF7F7F7);
            gui.fill(x + 2, y + 2, x + 12, y + 11, hover ? 0xFFF8F8F8 : 0xFFE2E2E2);
            gui.fill(x + 2, y + 11, x + 12, y + 12, 0xFFC4C4C4);
            String[] icon = switch (i) {
                case 0 -> view.navigation.targetShell > .5f ? CONTROL_UP : CONTROL_DOWN;
                case 1 -> CONTROL_CLOCKWISE;
                case 2 -> CONTROL_COUNTERCLOCKWISE;
                case 3 -> CONTROL_LEFT;
                default -> CONTROL_RIGHT;
            };
            drawControlPixels(gui, icon, x + 3, y + 4, view.layout == null ? 0xFFC0C0C0 : 0xFFA4A4A4);
            drawControlPixels(gui, icon, x + 3, y + 3, view.layout == null ? 0xFFA8A8A8 : 0xFF555555);
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
}
