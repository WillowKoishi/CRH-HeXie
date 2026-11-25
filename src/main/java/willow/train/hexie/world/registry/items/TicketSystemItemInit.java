package willow.train.hexie.world.registry.items;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import willow.train.hexie.bridge.HexieBridges;

public class TicketSystemItemInit {
    public static final DeferredHolder<Item, Item> SINGLE_TRIP_TICKET =
            AllItemsInit.register(
                    "ticket",
                    () -> HexieBridges.itemFactory().createTicketItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<Item, Item> RED_SINGLE_TRIP_TICKET =
            AllItemsInit.register(
                    "red_ticket",
                    () -> HexieBridges.itemFactory().createTicketItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<Item, Item> CARD =
            AllItemsInit.register(
                    "card",
                    () -> HexieBridges.itemFactory().createCardItem(new Item.Properties().stacksTo(1)));
}
