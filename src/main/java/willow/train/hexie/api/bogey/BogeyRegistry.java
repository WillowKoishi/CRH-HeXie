package willow.train.hexie.api.bogey;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import willow.train.hexie.bogey.BogeyCatalog;

/** HeXie owns backend blocks, block entities, styles, assembly and compatibility. */
public final class BogeyRegistry {
    private static final BogeyCatalog CATALOG = new BogeyCatalog();
    private BogeyRegistry() {}

    /**
     * Call in the contributing mod's constructor with its own mod event bus.
     * The ID must equal definition.model().id(); block and registration IDs share an owner.
     * This also registers operating parameters and allowed railways during common setup;
     * do not register those same IDs again through the individual catalogs.
     */
    public static BogeyHandle register(IEventBus bus, ResourceLocation id, BogeyDefinition definition) {
        return CATALOG.register(bus, id, definition);
    }

    public static List<BogeyHandle> entries() { return CATALOG.entries(); }
}
