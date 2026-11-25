package willow.train.hexie.bridge.compat.client;

import net.minecraft.client.renderer.RenderType;
import net.neoforged.bus.api.IEventBus;

public interface IrisBridge {
    CurveBedPbrOperations curveBedPbr();

    interface CurveBedPbrOperations {
        void register();
    }

    PassengerPanelTextLayerOperations passengerPanelTextLayer();

    interface PassengerPanelTextLayerOperations {
        RenderType of(RenderType nativeType);
        void register(IEventBus bus);
    }
}
