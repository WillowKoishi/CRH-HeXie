package willow.train.hexie.bogey;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import willow.train.hexie.api.bogey.BogeyDefinition;
import willow.train.hexie.api.bogey.BogeyHandle;

/** Internal backend SPI. Addons register against BogeyRegistry, not this interface. */
public interface BogeyBackend {
    BogeyHandle register(IEventBus bus, ResourceLocation id, BogeyDefinition definition);
}
