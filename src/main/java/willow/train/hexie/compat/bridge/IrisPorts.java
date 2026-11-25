package willow.train.hexie.compat.bridge;

import net.minecraft.client.renderer.RenderType;
import net.neoforged.bus.api.IEventBus;
import willow.train.hexie.bridge.compat.client.IrisBridge;
import willow.train.hexie.compat.iris.client.CurveBedPbr;
import willow.train.hexie.compat.iris.client.PassengerPanelTextLayer;

final class IrisPorts implements IrisBridge {
    private final PassengerPanelTextLayerOperations passengerPanelTextLayer = new PassengerPanelTextLayerOperations() {
        @Override public RenderType of(RenderType nativeType) {
            return PassengerPanelTextLayer.of(nativeType);
        }

        @Override public void register(IEventBus bus) {
            PassengerPanelTextLayer.register(bus);
        }
    };

    @Override public CurveBedPbrOperations curveBedPbr() {
        return CurveBedPbr::register;
    }

    @Override public PassengerPanelTextLayerOperations passengerPanelTextLayer() {
        return passengerPanelTextLayer;
    }
}
