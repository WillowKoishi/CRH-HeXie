package willow.train.hexie.init.item;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ResourceItemInit {

    public static final DeferredHolder<Item, Item> WeatheringResistantSteelIngot =
            AllItemsInit.register(
                    "weathering_resistant_steel_ingot",
                    () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> WeatheringResistantSteelNugget =
            AllItemsInit.register(
                    "weathering_resistant_steel_nugget",
                    () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> WeatheringResistantSteelBoard =
            AllItemsInit.register(
                    "weathering_resistant_steel_board",
                    () -> new Item(new Item.Properties())); // ));
    public static final DeferredHolder<Item, Item> WeatheringResistantSteelStick =
            AllItemsInit.register(
                    "weathering_resistant_steel_stick",
                    () -> new Item(new Item.Properties())); // ));
    public static final DeferredHolder<Item, Item> WeatheringResistantSteelString =
            AllItemsInit.register(
                    "steel_string",
                    () -> new Item(new Item.Properties())); // ));

    // 合成表材料
    public static final DeferredHolder<Item, Item> PantographStick =
            AllItemsInit.register(
                    "pantograph_stick",
                    () -> new Item(new Item.Properties())); // ));
    public static final DeferredHolder<Item, Item> IncompleteStandardTrack =
            AllItemsInit.register(
                    "incomplete_standard_track",
                    () -> new Item(new Item.Properties())); // ));
    public static final DeferredHolder<Item, Item> IncompleteBallastlessTrack =
            AllItemsInit.register(
                    "incomplete_ballastless_track",
                    () -> new Item(new Item.Properties()));

    public static final DeferredHolder<Item, Item> IncompleteTielessTrack =
            AllItemsInit.register(
                    "incomplete_tieless_track",
                    () -> new Item(new Item.Properties()));

}
