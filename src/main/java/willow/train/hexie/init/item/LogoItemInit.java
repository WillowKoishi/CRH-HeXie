package willow.train.hexie.init.item;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;

public class LogoItemInit {

    public static final DeferredHolder<Item, Item> CA_25T =
            AllItemsInit.register("ca_25t", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> SERIES_25_LOGOS =
            AllItemsInit.register("series25_logos", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> LOCO_LOGOS =
            AllItemsInit.register("loco_logos", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> LOGO_A25T =
            AllItemsInit.register("logo_a25t", () -> new Item(new Item.Properties()));
}
