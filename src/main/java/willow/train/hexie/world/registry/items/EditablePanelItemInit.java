package willow.train.hexie.world.registry.items;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import willow.train.hexie.bridge.HexieBridges;

public class EditablePanelItemInit {
    public static final DeferredHolder<Item, Item> Brush =
            AllItemsInit.register(
                    "brush", () -> HexieBridges.itemFactory().createBrush(new Item.Properties()));

    public static final DeferredHolder<Item, Item> ColoredBrush =
            AllItemsInit.register(
                    "colored_brush",
                    () -> HexieBridges.itemFactory().createColoredBrush(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Item, Item> GreenBrush =
            AllItemsInit.register(
                    "green_brush",
                    () -> HexieBridges.itemFactory().createGreenBrush(new Item.Properties()));

    public static final DeferredHolder<Item, Item> LACQUERED_BOARD =
            AllItemsInit.register(
                    "laquered_board_item",
                    () -> HexieBridges.itemFactory().createLacqueredBoardItem(new Item.Properties()));

    public static final DeferredHolder<Item, Item> Sticker =
            AllItemsInit.register(
                    "sticker_item",
                    () -> HexieBridges.itemFactory().createStickerItem(new Item.Properties()));

    public static final DeferredHolder<Item, Item> CUSTOM_STICKER =
            AllItemsInit.register(
                    "custom_sticker",
                    () -> HexieBridges.itemFactory().createCustomStickerItem(new Item.Properties().stacksTo(1)));
}
