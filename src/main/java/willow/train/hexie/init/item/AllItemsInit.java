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

    public static final DeferredHolder<Item, FloorSwitcherItem> HARMONY_WRENCH = ITEMS.register("harmony_wrench",
            () -> new FloorSwitcherItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<Item, willow.train.hexie.item.RescueCouplerItem> CRH2A_RESCUE_COUPLER =
            ITEMS.register("crh2a_rescue_coupler",
                    () -> new willow.train.hexie.item.RescueCouplerItem(new Item.Properties()));

    public static void registerAllItems(IEventBus bus) {
        fireAllInit();
        ITEMS.addAlias(Main.asResource("floor_switcher"), HARMONY_WRENCH.getId());
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
