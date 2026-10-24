package willow.train.hexie.interaction.base;

import net.minecraft.network.chat.Component;

import java.util.HashMap;

public interface ICustomInteractionGroup {

    void addInteractions(ICustomInteraction... interactions);
    String name();
    boolean automaticOnly();
    Component descriptionId();
    HashMap<String, ICustomInteraction> interactions();
    ICustomInteraction getInteraction(String key);
}
