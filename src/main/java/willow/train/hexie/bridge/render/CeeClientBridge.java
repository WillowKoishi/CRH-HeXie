package willow.train.hexie.bridge.render;

import net.neoforged.neoforge.client.event.ModelEvent;

public interface CeeClientBridge {
    PantographBaseModelsOperations pantographBaseModels();

    interface PantographBaseModelsOperations {
        void onBaking(ModelEvent.ModifyBakingResult event);
    }
}
