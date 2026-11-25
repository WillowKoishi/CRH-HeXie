package willow.train.hexie.ui.events.inventory;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.ItemStack;
import willow.train.hexie.ui.flow.Carriage25B;
import willow.train.hexie.ui.flow.Carriage25BGZK;
import willow.train.hexie.ui.flow.Carriage25G;
import willow.train.hexie.ui.flow.Carriage25K;
import willow.train.hexie.ui.flow.Carriage25T;
import willow.train.hexie.ui.flow.Carriage25Z;
import willow.train.hexie.ui.flow.CarriageCR200j;
import willow.train.hexie.ui.flow.Marshalled25Series;

// split from willow/train/hexie/event/client/HXInventoryEvents.java
final class CarriageInventoryFilter {
    static int carriageType = 1;

    ArrayList<List<ItemStack>> itemList = new ArrayList<>();
    private List<ItemStack> appliedItems = List.of();
    private boolean mappingInitialized;

    public void updateMenuItem(CreativeModeInventoryScreen.ItemPickerMenu menu) {
        ensureMapping();
        switch (carriageType) {
            case 1: // B
                menu.items.clear();
                menu.items.addAll(itemList.get(0));
                menu.items.addAll(itemList.get(7));
                menu.items.addAll(itemList.get(8));
                break;
            case 2: // G
                menu.items.clear();
                menu.items.addAll(itemList.get(1));
                menu.items.addAll(itemList.get(7));
                menu.items.addAll(itemList.get(8));
                break;
            case 3: // K
                menu.items.clear();
                menu.items.addAll(itemList.get(2));
                menu.items.addAll(itemList.get(7));
                menu.items.addAll(itemList.get(8));
                break;
            case 4: // Z
                menu.items.clear();
                menu.items.addAll(itemList.get(3));
                menu.items.addAll(itemList.get(7));
                menu.items.addAll(itemList.get(8));
                break;
            case 5: // T
                menu.items.clear();
                menu.items.addAll(itemList.get(4));
                menu.items.addAll(itemList.get(7));
                break;
            case 6: // M
                menu.items.clear();
                menu.items.addAll(itemList.get(5));
                menu.items.addAll(itemList.get(7));
                break;
            case 7: // M
                menu.items.clear();
                menu.items.addAll(itemList.get(6));
                menu.items.addAll(itemList.get(7));
                break;
            default:
                menu.items.clear();
                menu.items.addAll(itemList.get(0)); // B
                menu.items.addAll(itemList.get(1)); // G
                menu.items.addAll(itemList.get(2)); // K
                menu.items.addAll(itemList.get(3)); // Z
                menu.items.addAll(itemList.get(4)); // T
                menu.items.addAll(itemList.get(5)); // M
                menu.items.addAll(itemList.get(6));  // cr200j
                menu.items.addAll(itemList.get(7)); // general
                menu.items.addAll(itemList.get(8)); // bgzk
        }
        rememberAppliedItems(menu);
    }

    boolean hasAppliedFilter(CreativeModeInventoryScreen.ItemPickerMenu menu) {
        if (appliedItems.size() != menu.items.size()) return false;
        for (int index = 0; index < appliedItems.size(); index++) {
            if (appliedItems.get(index) != menu.items.get(index)) return false;
        }
        return true;
    }

    private void rememberAppliedItems(CreativeModeInventoryScreen.ItemPickerMenu menu) {
        appliedItems = new ArrayList<>(menu.items);
    }

    public void initMapping() {
        if (mappingInitialized) return;
        itemList =
                new ArrayList<>() {
                    {
                        add(Carriage25B.getList());  // 25B 0
                        add(Carriage25G.getList());  // 25G 1
                        add(Carriage25K.getList());  // 25K 2
                        add(Carriage25Z.getList());  // 25Z 3
                        add(Carriage25T.getList());  // 25T 4
                        add(Marshalled25Series.getList());  // 25 Marshalled Series 5
                        add(CarriageCR200j.getList()); // cr200j 6
                        add(
                                List.of(
                                        // BlockInit.ORIGINAL_COLOR_WINDOW_25.get().asItem().getDefaultInstance(),
                                        // BlockInit.ORIGINAL_COLOR_WINDOW_25_SEALED.get().asItem().getDefaultInstance()
                                        )); // 通用 7

                        add(Carriage25BGZK.getList()); // BGZK 8
                    }
                };
        mappingInitialized = true;
    }

    void ensureMapping() {
        if (!mappingInitialized) initMapping();
    }
}
