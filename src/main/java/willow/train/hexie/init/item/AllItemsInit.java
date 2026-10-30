package willow.train.hexie.init.item;

import net.minecraft.core.registries.Registries;
import com.google.common.base.Supplier;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import willow.train.hexie.Main;
import willow.train.hexie.item.FloorSwitcherItem;

public class AllItemsInit {

    public static final Logger LOGGER = LoggerFactory.getLogger("HeXie");

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, Main.MOD_ID);

    public static final DeferredHolder<Item, FloorSwitcherItem> FLOOR_SWITCHER = ITEMS.register("floor_switcher",
            () -> new FloorSwitcherItem(new Item.Properties().stacksTo(1)));

    public static void registerAllItems(IEventBus bus) {
        fireAllInit();
        ITEMS.register(bus);
    }

    private static void fireAllInit() {
        new DietInit();
        new EditablePanelItemInit();
        new LogoItemInit();
        new ResourceItemInit();
        new TicketSystemItemInit();
        new SchematicItemInit();
    }

    static <T extends Item> DeferredHolder<Item, T> register(final String name, final Supplier<T> item) {
        return AllItemsInit.ITEMS.register(name, item);
    }
}
