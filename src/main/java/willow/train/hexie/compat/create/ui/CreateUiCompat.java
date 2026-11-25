package willow.train.hexie.compat.create.ui;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.core.api.carriage.CarriageFlowRegistry;

public final class CreateUiCompat {
    private CreateUiCompat() {}

    public static void register(IEventBus modBus) {
        CreateMenuInit.register(modBus);
        HexieBridges.menu().registerBogeySelection(BogeySelectionMenu::open, CreateMenuInit.BOGEY_SELECTION_MENU::get);
        CarriageFlowRegistry.registerTrackPredicate(CreateTrackPredicate::isTrack);
        if (FMLEnvironment.dist == Dist.CLIENT) Client.register(modBus);
    }

    private static final class Client {
        static void register(IEventBus modBus) {
            var ui = HexieBridges.Client.clientCarriageUi();
            ui.registerCabHud(new SimpleCabTrainHud());
            modBus.addListener(BogeySelectionScreen::register);
            modBus.addListener(EventPriority.LOWEST, SimpleCabTrainHud::wrapTrainLayer);
            var gameBus = NeoForge.EVENT_BUS;
            gameBus.register(CurvedBallastInteraction.class);
            gameBus.register(FloorSwitcherClient.class);
            gameBus.register(LocalizedItemTooltips.class);
            gameBus.register(SemaphoreScreenEvents.class);
            ui.registerStandingEvents(gameBus);
            gameBus.register(TrainBogeyClientSync.class);
            gameBus.register(SimpleCabTrainHud.class);
        }
    }
}
