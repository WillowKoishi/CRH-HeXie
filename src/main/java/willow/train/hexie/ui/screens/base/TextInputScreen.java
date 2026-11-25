package willow.train.hexie.ui.screens.base;

import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.navigation.ScreenDirection;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.lwjgl.glfw.GLFW;
import willow.train.hexie.bridge.ui.client.ClientCarriageUiBridge.TextInputScreenView;

/**
 * Marks text editors whose keyboard belongs to the screen, including when a button has focus.
 * Widget and focus navigation follow Screen.keyPressed before the editor consumes the key.
 */
public abstract class TextInputScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> implements TextInputScreenView<M> {
    protected TextInputScreen(M menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && shouldCloseOnEsc()) {
            onClose();
            return true;
        }
        var focused = getFocused();
        if (focused != null && focused.keyPressed(keyCode, scanCode, modifiers)) return true;

        // Screen's navigation, including Tab wrapping. Editors consume all remaining keys.
        FocusNavigationEvent navigation = switch (keyCode) {
            case GLFW.GLFW_KEY_TAB -> new FocusNavigationEvent.TabNavigation(!hasShiftDown());
            case GLFW.GLFW_KEY_RIGHT -> new FocusNavigationEvent.ArrowNavigation(ScreenDirection.RIGHT);
            case GLFW.GLFW_KEY_LEFT -> new FocusNavigationEvent.ArrowNavigation(ScreenDirection.LEFT);
            case GLFW.GLFW_KEY_DOWN -> new FocusNavigationEvent.ArrowNavigation(ScreenDirection.DOWN);
            case GLFW.GLFW_KEY_UP -> new FocusNavigationEvent.ArrowNavigation(ScreenDirection.UP);
            default -> null;
        };
        if (navigation != null) {
            ComponentPath path = super.nextFocusPath(navigation);
            if (path == null && navigation instanceof FocusNavigationEvent.TabNavigation) {
                clearFocus();
                path = super.nextFocusPath(navigation);
            }
            if (path != null) changeFocus(path);
        }
        return true;
    }
}
