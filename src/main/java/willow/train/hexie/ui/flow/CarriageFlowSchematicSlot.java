package willow.train.hexie.ui.flow;

import java.util.*;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// split from willow/train/hexie/client/carriage/flow/CarriageFlowView.java
final class CarriageFlowSchematicSlot {
    private final CarriageFlowView view;

    CarriageFlowSchematicSlot(CarriageFlowView view) { this.view = view; }

    static final int SCHEMATIC_X = 173, SCHEMATIC_Y = 112;

    private Slot originalSchematicSlot, schematicSlot;
    private ItemStack schematicPrototype, schematicStack = ItemStack.EMPTY;

    void syncSchematicSlot() {
        var menu = view.host.getMenu();
        if (schematicSlot == null || menu.slots.getFirst() != schematicSlot) {
            restoreSchematicSlot();
            Slot original = menu.slots.getFirst();
            originalSchematicSlot = original;
            // Reuse a native creative slot and its container. Adding a slot would
            // change vanilla's hotbar-to-server slot arithmetic.
            schematicSlot = new Slot(original.container, original.getContainerSlot(), SCHEMATIC_X, SCHEMATIC_Y) {
                @Override public boolean mayPlace(ItemStack stack) { return original.mayPlace(stack); }
                @Override public boolean mayPickup(net.minecraft.world.entity.player.Player player) {
                    return original.mayPickup(player);
                }
            };
            schematicSlot.index = original.index;
            menu.slots.set(0, schematicSlot);
        }
        ItemStack schematic = view.layout == null ? ItemStack.EMPTY
                : view.catalog.models().get(view.layout.model(view.navigation.selected)).schematic();
        if (schematicPrototype != schematic) {
            schematicPrototype = schematic;
            schematicStack = schematic.copy();
        }
        if (menu.items.size() != 1 || menu.items.getFirst() != schematicStack) {
            menu.items.clear();
            menu.items.add(schematicStack);
            menu.scrollTo(0);
        } else if (schematicSlot.getItem() != schematicStack) {
            // Palette screens share vanilla's creative container with this parent.
            // Refresh only this cell while rendering the parent behind a palette.
            schematicSlot.set(schematicStack);
        }
    }

    boolean isSchematicSlot(Slot slot) { return slot == schematicSlot; }

    void restoreSchematicSlot() {
        if (originalSchematicSlot != null && !view.host.getMenu().slots.isEmpty()
                && view.host.getMenu().slots.getFirst() == schematicSlot)
            view.host.getMenu().slots.set(0, originalSchematicSlot);
        originalSchematicSlot = schematicSlot = null;
    }
}
