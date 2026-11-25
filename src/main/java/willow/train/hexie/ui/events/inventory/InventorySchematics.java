package willow.train.hexie.ui.events.inventory;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.ItemStack;
import willow.train.hexie.core.HexieConstants;

// split from willow/train/hexie/event/client/HXInventoryEvents.java
final class InventorySchematics {
    List<ItemStack> schematicItemList = new ArrayList<>();

    public void addSchematicToTab(CreativeModeInventoryScreen.ItemPickerMenu menu) {
        if (!schematicItemList.isEmpty()) {
            for (ItemStack stack : schematicItemList) {
                if (!menu.items.contains(stack)) {
                    menu.items.add(stack);
                }
            }
            //menu.scrollTo(1);
        }
    }

    public void generateSchematicTab() {
        try {
            // TODO: for next version (0.4)
        } catch (Exception e) {
            HexieConstants.LOGGER.error("FATAL ERROR IN LOADING HEXIE SCHEMATICS ", e);
        }
    }
}
