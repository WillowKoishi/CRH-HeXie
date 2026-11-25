package willow.train.hexie.render.init;

// split from willow/train/hexie/init/client/HeXieClientInit.java

import java.util.function.Consumer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.render.bogey.BogeyTopModel;
import willow.train.hexie.render.bogey.QJBogeyStationPreview;
import willow.train.hexie.render.carriage.CRH1StaticSections;
import willow.train.hexie.render.carriage.CRH2StaticSections;
import willow.train.hexie.render.carriage.CarriageTransparencyCompat;
import willow.train.hexie.render.display.PassengerPanelModels;
import willow.train.hexie.render.door.DoorPartialModelCache;
import willow.train.hexie.render.sign.SignTextClient;
import willow.train.hexie.render.track.BallastCurveBuffers;
import willow.train.hexie.render.track.HexieTrackGraphOverlay;

public class HeXieClientInit {

    public static void init(IEventBus bus, Consumer<RegisterMenuScreensEvent> registerScreens) {
        bus.addListener(HexieBridges.Client.ceeClient().pantographBaseModels()::onBaking);
        PassengerPanelModels.register();
        CRH2StaticSections.register();
        CRH1StaticSections.register();
        if (ModList.get().isLoaded("create")) CreateOnlyListeners.register(bus);
        HexieBridges.Client.compatClient().heXieSimulatedClientCompat().register(bus);
        SignTextClient.register(bus);
        HexieBridges.Client.clientCarriageUi().hudFonts().register(bus);
        HexieBridges.Client.clientCarriageUi().carriageFlowClient().register(bus);
        bus.addListener(registerScreens);
        bus.addListener(HexieBridges.Client.surfaceInputClient().surfaceControlCaption()::register);
        bus.addListener(ClientRendererRegistration::registerRenderers);
        // Bind the self-registered seat after the legacy Create seat listener.
        bus.addListener(EventPriority.LOW, ClientRendererRegistration::registerSeatRenderer);
        bus.addListener(ClientVisualRegistration::registerEntityVisuals);
        bus.addListener(DoorPartialModelCache::registerModels);
        PartialModelInit.init();
        ClientModEventSubscriber.registerModelLayers();
        bus.addListener(ClientModelLayers::onModelLayerRegistration);
        HexieBridges.Client.clientCarriageUi().registerInventoryEvents(NeoForge.EVENT_BUS);
    }

    private static final class CreateOnlyListeners {
        private static void register(IEventBus bus) {
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, HexieTrackGraphOverlay::afterTick);
            NeoForge.EVENT_BUS.addListener(QJBogeyStationPreview::onBlockEntityBehaviours);
            bus.addListener(BogeyTopModel::register);
            bus.addListener(HexieBridges.Client.createTrainClient().createAddonBogeyClient()::registerAdditional);
            BallastCurveBuffers.register();
            CarriageTransparencyCompat.register(bus);
            bus.addListener(TrackClientExtensions::registerBlockExtensions);
        }
    }
}
