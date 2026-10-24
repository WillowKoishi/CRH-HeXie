package willow.train.hexie.client.util.gui;

import net.minecraft.network.chat.Component;

/** Button constructor shared by the mod's custom controls. */
public class Button extends net.minecraft.client.gui.components.Button {
    public Button(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }
}
