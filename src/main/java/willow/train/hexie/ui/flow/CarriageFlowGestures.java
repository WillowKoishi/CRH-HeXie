package willow.train.hexie.ui.flow;

import java.util.*;
import org.lwjgl.glfw.GLFW;

import static willow.train.hexie.ui.flow.CarriageFlowPanBounds.BOTTOM;
import static willow.train.hexie.ui.flow.CarriageFlowPanBounds.LEFT;
import static willow.train.hexie.ui.flow.CarriageFlowPanBounds.RIGHT;
import static willow.train.hexie.ui.flow.CarriageFlowPanBounds.TOP;

// split from willow/train/hexie/client/carriage/flow/CarriageFlowView.java
final class CarriageFlowGestures {
    private final CarriageFlowView view;

    CarriageFlowGestures(CarriageFlowView view) { this.view = view; }

    private static final int STRIP_CLICK_SLOP = 3;

    CarriagePress carriagePress;
    StripPress stripPress;
    boolean draggingStrip;
    long stripSelection;

    private record CarriagePress(double mouseX, double mouseY, double pressedAt, long index,
                                 CarriageFlowPreview.Hit hit) {}
    private record StripPress(double mouseX, double mouseY, double position, double clickSlopX, CarriageFlowScene.StripEntry entry) {
        boolean withinClickSlop(double x, double y) {
            return Math.abs(x - mouseX) <= clickSlopX && Math.abs(y - mouseY) <= STRIP_CLICK_SLOP;
        }
    }

    private boolean overCarriage(double x, double y) {
        return view.layout != null && view.scene.selectedTransform != null && view.previews.get(view.layout.model(view.navigation.selected)).pick(
                view.scene.selectedTransform, x + view.host.getGuiLeft(), y + view.host.getGuiTop(), view.navigation.shell > .5) != null;
    }

    private static boolean overStrip(double x, double y) {
        return x >= 9 && x < 169 && y >= CarriageFlowScene.STRIP_Y && y <= CarriageFlowScene.STRIP_Y + CarriageFlowScene.STRIP_HEIGHT;
    }

    private void dragStrip(double x, double y) {
        if (!draggingStrip && stripPress.withinClickSlop(x, y)) return;
        draggingStrip = true;
        double nextPosition = stripPress.position() - (x - stripPress.mouseX()) / view.scene.stripScale;
        if (nextPosition != view.navigation.position) {
            view.navigation.position = nextPosition;
            stripSelection = view.layout.selectDuringDrag(stripSelection, view.navigation.position);
            view.navigation.motion.restore(view.navigation.position, 0, view.navigation.targetPosition);
        }
        view.scene.hovered = null;
    }

    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (stripPress != null) return true;
        double delta = vertical != 0 ? vertical : horizontal;
        if (delta == 0) return false;
        int step = delta > 0 ? -1 : 1;
        if (overStrip(x, y)) view.navigation.select(view.navigation.selected + step);
        else if (x >= LEFT && x < RIGHT && y >= TOP && y < BOTTOM && view.layout != null) {
            // Use the same block shapes as hover picking; empty gaps belong to camera panning.
            if (overCarriage(x, y)) view.navigation.cycle(step);
            else {
                view.navigation.panSnapPending = false;
                view.navigation.panVelocity = 0;
                view.navigation.panTarget = view.navigation.clampPan(view.navigation.panTarget + step * .5);
            }
        }
        else return false;
        return true;
    }

    public boolean mouseClicked(double x, double y, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) view.navigation.panSnapCaptured = false;
        int control = view.scene.controlAt(x, y);
        if (control >= 0) {
            if (button == 0) view.navigation.activateControl(control);
            return true;
        }
        if (x >= 9 && x < 169 && y >= 18 && y < BOTTOM && !view.host.getMenu().getCarried().isEmpty()) {
            view.host.getMenu().setCarried(net.minecraft.world.item.ItemStack.EMPTY);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && view.layout != null && overStrip(x, y)) {
            CarriageFlowScene.StripEntry clicked = null;
            for (CarriageFlowScene.StripEntry entry : view.scene.stripEntries) {
                if (x >= entry.left() && x <= entry.right()) {
                    clicked = entry;
                    break;
                }
            }
            double center = view.layout.center(view.navigation.selected);
            double spacing = Math.min(center - view.layout.center(view.navigation.selected - 1), view.layout.center(view.navigation.selected + 1) - center);
            double clickSlopX = Math.min(STRIP_CLICK_SLOP, spacing * view.scene.stripScale / 2);
            view.navigation.panSnapPending = false;
            carriagePress = null;
            view.navigation.animate();
            view.navigation.holdPan();
            view.navigation.manualPan = false;
            stripPress = new StripPress(x, y, view.navigation.position, clickSlopX, clicked);
            stripSelection = view.navigation.selected;
            draggingStrip = view.navigation.draggingPan = false;
            view.scene.hovered = null;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && view.layout != null
                && x >= LEFT && x < RIGHT && y >= TOP && y < BOTTOM) {
            view.navigation.panSnapPending = false;
            double pressedAt = CarriageFlowNavigation.now();
            if (view.scene.hovered != null) {
                view.navigation.animate();
                view.navigation.holdPan();
                carriagePress = new CarriagePress(x, y, pressedAt, view.navigation.selected, view.scene.hovered);
            } else {
                carriagePress = null;
                view.navigation.startPan(x, y, pressedAt);
            }
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double x, double y, int button, double dragX, double dragY) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (view.navigation.panSnapCaptured) return true;
        if (view.layout == null) return false;
        if (stripPress != null) {
            dragStrip(x, y);
            return true;
        }
        if (carriagePress != null) {
            double pressDeltaX = x - carriagePress.mouseX();
            double pressDeltaY = y - carriagePress.mouseY();
            if (!view.navigation.draggingPan) {
                if (Math.abs(pressDeltaX) <= CarriageFlowNavigation.PAN_DRAG_SLOP && Math.abs(pressDeltaY) <= CarriageFlowNavigation.PAN_DRAG_SLOP)
                    return true;
                view.navigation.startPan(carriagePress.mouseX(), carriagePress.mouseY(), carriagePress.pressedAt());
            }
            view.navigation.updatePanDrag(x, y);
            return true;
        }
        if (!view.navigation.draggingPan) return false;
        view.navigation.updatePanDrag(x, y);
        return true;
    }

    public boolean mouseReleased(double x, double y, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (view.navigation.panSnapCaptured) {
            view.navigation.panSnapCaptured = false;
            return true;
        }
        if (stripPress != null) {
            if (draggingStrip) {
                view.navigation.select(stripSelection);
            } else if (stripPress.entry() != null && overStrip(x, y) && stripPress.withinClickSlop(x, y)) {
                view.navigation.select(stripPress.entry().index());
            }
            stripPress = null;
            draggingStrip = false;
            return true;
        }
        if (carriagePress != null) {
            CarriagePress press = carriagePress;
            carriagePress = null;
            if (view.navigation.draggingPan) {
                view.navigation.draggingPan = false;
                view.navigation.panSnapPending = true;
            } else if (Math.abs(x - press.mouseX()) <= CarriageFlowNavigation.PAN_DRAG_SLOP
                    && Math.abs(y - press.mouseY()) <= CarriageFlowNavigation.PAN_DRAG_SLOP) {
                var model = view.catalog.models().get(view.layout.model(press.index()));
                var items = view.catalog.variants(model, press.hit().cell());
                if (!items.isEmpty()) {
                    view.openingPalette = true;
                    view.minecraft.setScreen(new CarriageFlowPaletteScreen(view, items,
                            (int) press.mouseX(), (int) press.mouseY()));
                }
            }
            return true;
        }
        if (!view.navigation.draggingPan) return false;
        view.navigation.draggingPan = false;
        view.navigation.panSnapPending = true;
        return true;
    }

    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) {
            if (stripPress == null) view.navigation.select(view.navigation.selected + (key == GLFW.GLFW_KEY_LEFT ? -1 : 1));
            return true;
        }
        if (key == GLFW.GLFW_KEY_SPACE) { view.navigation.cycle(1); return true; }
        return false;
    }

    public boolean contains(double x, double y) { return x >= 8 && x < 188 && y >= 17 && y < 107; }
}
