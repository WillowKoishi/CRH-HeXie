package willow.train.hexie.init.item;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;

import willow.train.hexie.item.tickets.CardItem;
import willow.train.hexie.item.tickets.TicketItem;

public class TicketSystemItemInit {

    public static final DeferredHolder<Item, TicketItem> SINGLE_TRIP_TICKET =
            AllItemsInit.register(
                    "ticket",
                    () -> new TicketItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<Item, TicketItem> RED_SINGLE_TRIP_TICKET =
            AllItemsInit.register(
                    "red_ticket",
                    () -> new TicketItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<Item, CardItem> CARD =
            AllItemsInit.register(
                    "card",
                    () -> new CardItem(new Item.Properties().stacksTo(1)));
}
