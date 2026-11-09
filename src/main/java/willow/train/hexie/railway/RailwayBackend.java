package willow.train.hexie.railway;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import willow.train.hexie.api.railway.RailwayDefinition;
import willow.train.hexie.api.railway.RailwayHandle;

/** Internal backend SPI. Add-ons use RailwayRegistry; only HeXie supplies this service. */
public interface RailwayBackend {
    RailwayHandle register(IEventBus bus, ResourceLocation id, RailwayDefinition definition);
}
