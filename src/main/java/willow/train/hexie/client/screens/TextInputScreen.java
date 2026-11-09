package willow.train.hexie.client.screens;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Marks text editors whose keyboard belongs to the screen, including when a button has focus.
 * The client keyboard hooks retain vanilla screen dispatch and skip game/container shortcuts.
 */
public abstract class TextInputScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {
    protected TextInputScreen(M menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
