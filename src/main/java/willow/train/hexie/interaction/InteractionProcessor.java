package willow.train.hexie.interaction;

import net.minecraft.nbt.CompoundTag;
import willow.train.hexie.interaction.base.ICustomInteraction;
import willow.train.hexie.interaction.base.ICustomInteractionGroup;
import willow.train.hexie.interaction.cr200j.CR200jFairingSwitchInteraction;
import willow.train.hexie.interaction.cr200j.CR200jInteractionGroup;
import willow.train.hexie.interaction.cr200j.CR200jWindShieldSwitchInteraction;
import willow.train.hexie.interaction.door.*;

import java.util.HashMap;

public class InteractionProcessor {

    public static final HashMap<String, ICustomInteractionGroup> INTERACTIONS = new HashMap<>();

    public static final ICustomInteractionGroup CR200J_INTERACTIONS = register(
            new CR200jInteractionGroup(),
            new CR200jFairingSwitchInteraction(),
            new CR200jWindShieldSwitchInteraction()
    );

    public static final ICustomInteractionGroup DOOR_INTERACTIONS = register(
            new DoorInteractionGroup(),
            new DoorCloseInteraction(),
            new DoorOpenInteraction(),
            new DoorSwitchInteraction()
    );


    public static ICustomInteractionGroup register(ICustomInteractionGroup group, ICustomInteraction... interactions){
        group.addInteractions(interactions);
        INTERACTIONS.put(group.name(), group);
        return group;
    }

    public static CompoundTag toFullTag(CompoundTag tag) {
        for(String groupName : INTERACTIONS.keySet()) {
            for(String name : INTERACTIONS.get(groupName).interactions().keySet()) {
                tag.putString(groupName, tag.contains(groupName) ? tag.getString(groupName) + ", " + name : name);
            }
        }
        return tag;
    }

    public static ICustomInteraction getInteraction(String groupKey, String interactionKey) {
        if(INTERACTIONS.get(groupKey) != null) {
            return INTERACTIONS.get(groupKey).getInteraction(interactionKey);
        }
        return null;
    }

    public static ICustomInteractionGroup getGroup(String groupKey) {
        return INTERACTIONS.getOrDefault(groupKey, null);
    }

    public static void invoke(){}
}
