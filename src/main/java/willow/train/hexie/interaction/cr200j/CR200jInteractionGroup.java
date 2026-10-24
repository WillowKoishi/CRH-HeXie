package willow.train.hexie.interaction.cr200j;

import net.minecraft.network.chat.Component;
import willow.train.hexie.interaction.base.ICustomInteraction;
import willow.train.hexie.interaction.base.ICustomInteractionGroup;

import java.util.HashMap;

public class CR200jInteractionGroup implements ICustomInteractionGroup {

    private HashMap<String, ICustomInteraction> interactionHashMap = new HashMap<>();

    @Override
    public void addInteractions(ICustomInteraction... interactions) {
        for(ICustomInteraction interaction : interactions) {
            if(!interaction.group().equals(name())) continue;
            interactionHashMap.put(interaction.key(), interaction);
        }
    }

    @Override
    public String name() {
        return "cr200j";
    }

    @Override
    public boolean automaticOnly() {
        return false;
    }

    @Override
    public Component descriptionId() {
        return Component.translatable("tooltip.hexie.abc.cr200j.group");
    }

    @Override
    public HashMap<String, ICustomInteraction> interactions() {
        return interactionHashMap;
    }

    @Override
    public ICustomInteraction getInteraction(String key) {
        return interactionHashMap.getOrDefault(key, null);
    }
}
