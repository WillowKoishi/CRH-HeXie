package willow.train.hexie.core.api.railway;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import willow.train.hexie.core.ServiceSlot;

/**
 * HeXie-owned railway registration. Call during the contributing mod's constructor,
 * passing its mod event bus. The railway ID and block/item ID belong to that mod's namespace.
 * Registration creates the active backend's blocks, items and railway binding internally.
 */
public final class RailwayRegistry {
    public interface Backend {
        RailwayHandle register(IEventBus modBus, ResourceLocation id, RailwayDefinition definition);
        List<RailwayHandle> entries();
    }

    private static final ServiceSlot<Backend> BACKEND = new ServiceSlot<>("RailwayRegistry");

    public static void registerBackend(Backend backend) { BACKEND.register(backend); }

    private RailwayRegistry() {}

    public static RailwayHandle register(IEventBus modBus, ResourceLocation id, RailwayDefinition definition) {
        return BACKEND.get().register(modBus, id, definition);
    }

    public static List<RailwayHandle> entries() { return BACKEND.get().entries(); }
}
