package willow.train.hexie.bridge.render;

import net.neoforged.bus.api.IEventBus;

public interface CompatClientBridge {
    HeXieSimulatedClientCompatOperations heXieSimulatedClientCompat();

    interface HeXieSimulatedClientCompatOperations {
        void register(IEventBus modBus);
    }
}
