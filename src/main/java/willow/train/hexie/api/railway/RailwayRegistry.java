package willow.train.hexie.api.railway;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import willow.train.hexie.railway.RailwayCatalog;
import willow.train.hexie.railway.RailwayBackendLoader;

/**
 * HeXie-owned railway registration. Call during the contributing mod's constructor,
 * passing its mod event bus. The railway ID and block/item ID belong to that mod's namespace.
 * Registration creates the active backend's blocks, items and railway binding internally.
 */
public final class RailwayRegistry {
    private static final RailwayCatalog CATALOG = new RailwayCatalog(RailwayBackendLoader::get);
    private RailwayRegistry() {}

    public static RailwayHandle register(IEventBus modBus, ResourceLocation id, RailwayDefinition definition) {
        return CATALOG.register(modBus, id, definition);
    }

    public static List<RailwayHandle> entries() { return CATALOG.entries(); }
}
