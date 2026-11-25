package willow.train.hexie.core.api.bogey;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import willow.train.hexie.core.ServiceSlot;

/** HeXie owns backend blocks, block entities, styles, assembly and compatibility. */
public final class BogeyRegistry {
    public interface Backend {
        BogeyHandle register(IEventBus bus, ResourceLocation id, BogeyDefinition definition);
        List<BogeyHandle> entries();
    }

    private static final ServiceSlot<Backend> BACKEND = new ServiceSlot<>("BogeyRegistry");

    public static void registerBackend(Backend backend) { BACKEND.register(backend); }

    private BogeyRegistry() {}

    /**
     * Call in the contributing mod's constructor with its own mod event bus.
     * The ID must equal definition.model().id(); block and registration IDs share an owner.
     * This also registers operating parameters and allowed railways during common setup;
     * do not register those same IDs again through the individual catalogs.
     */
    public static BogeyHandle register(IEventBus bus, ResourceLocation id, BogeyDefinition definition) {
        return BACKEND.get().register(bus, id, definition);
    }

    public static List<BogeyHandle> entries() { return BACKEND.get().entries(); }
}
