package willow.train.hexie.world.registry.items;

import net.minecraft.world.item.Item;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredHolder;
import willow.train.hexie.core.HexieConstants;

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
    public static final DeferredHolder<Item, Item> IncompleteStandardTrack = incompleteTrack("incomplete_standard_track");
    public static final DeferredHolder<Item, Item> IncompleteBallastlessTrack = incompleteTrack("incomplete_ballastless_track");
    public static final DeferredHolder<Item, Item> IncompleteTielessTrack = incompleteTrack("incomplete_tieless_track");

    private static DeferredHolder<Item, Item> incompleteTrack(String legacyPath) {
        if (!ModList.get().isLoaded("create")) return null;
        var item = AllItemsInit.register("create_" + legacyPath, () -> new Item(new Item.Properties()));
        AllItemsInit.ITEMS.addAlias(HexieConstants.asResource(legacyPath), item.getId());
        return item;
    }
}
