package willow.train.hexie.init.item;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;

import willow.train.hexie.item.editable_panels.*;

public class EditablePanelItemInit {
    public static final DeferredHolder<Item, Item> Brush =
            AllItemsInit.register(
                    "brush", () -> new Brush(new Item.Properties()));

    public static final DeferredHolder<Item, Item> ColoredBrush =
            AllItemsInit.register(
                    "colored_brush",
                    () -> new ColoredBrush(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Item, Item> GreenBrush =
            AllItemsInit.register(
                    "green_brush",
                    () -> new GreenBrush(new Item.Properties()));

    public static final DeferredHolder<Item, Item> LACQUERED_BOARD =
            AllItemsInit.register(
                    "laquered_board_item",
                    () -> new LacqueredBoardItem(new Item.Properties()));

    public static final DeferredHolder<Item, Item> Sticker =
            AllItemsInit.register(
                    "sticker_item",
                    () -> new StickerItem(new Item.Properties()));
}
