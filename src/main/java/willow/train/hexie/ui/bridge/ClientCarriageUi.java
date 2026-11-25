package willow.train.hexie.ui.bridge;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.SnapshotCarriageView;
import willow.train.hexie.bridge.ui.client.ClientCarriageUiBridge;
import willow.train.hexie.ui.events.camera.CarriageCamera;
import willow.train.hexie.ui.events.input.DrivingConsoleClient;
import willow.train.hexie.ui.events.inventory.HXInventoryEvents;
import willow.train.hexie.ui.events.sync.CarriageStandingClient;
import willow.train.hexie.ui.flow.CarriageFlowClient;
import willow.train.hexie.ui.hud.HudFonts;
import willow.train.hexie.ui.menu.MenuInit;
import willow.train.hexie.ui.screens.base.TextInputScreen;
import willow.train.hexie.ui.screens.carriage.CarriageMarkingScreen;
import willow.train.hexie.ui.screens.carriage.CarriageNumberScreen;
import willow.train.hexie.ui.screens.carriage.HeadNumberScreen;
import willow.train.hexie.ui.screens.carriage.SS7DNumberScreen;
import willow.train.hexie.ui.screens.panel.LacqueredBoardEditScreen;
import willow.train.hexie.ui.screens.panel.PanelTextScreen;
import willow.train.hexie.ui.screens.sign.CarriageNoSignEditScreen;
import willow.train.hexie.ui.screens.sign.CarriageTypeSignEditScreen;
import willow.train.hexie.ui.screens.sign.GroundStationSignScreen;
import willow.train.hexie.ui.screens.sign.TrainSpeedSignEditScreen;
import willow.train.hexie.ui.screens.ticket.TicketVendorScreen;
import willow.train.hexie.ui.screens.track.StandardTrackPavingScreen;

final class ClientCarriageUi implements ClientCarriageUiBridge {
    @Override public void registerStandingEvents(IEventBus gameBus) {
        gameBus.register(CarriageStandingClient.class);
    }

    private CabHud cabHud = new CabHud() {};

    @Override
    public CabHud cabHud() { return cabHud; }

    @Override
    public void registerCabHud(CabHud hud) { cabHud = hud; }

    @Override
    public int cabKeyboardMask() { return DrivingConsoleClient.cabKeyboardMask(); }

    private static final CarriageCameraOperations carriageCamera = new CarriageCameraOperations() {
        @Override
        public boolean followsSeat(Player player) {
            return CarriageCamera.followsSeat(player);
        }

        @Override
        public void beforePlayerTick() {
            CarriageCamera.beforePlayerTick();
        }

        @Override
        public boolean turn(Entity entity, double horizontal, double vertical) {
            return CarriageCamera.turn(entity, horizontal, vertical);
        }

        @Override
        public Vec3 position(Entity entity, boolean detached, float partial, Vec3 original) {
            return CarriageCamera.position(entity, detached, partial, original);
        }
    };

    @Override
    public CarriageCameraOperations carriageCamera() { return carriageCamera; }

    private static final CarriageFlowClientOperations carriageFlowClient = new CarriageFlowClientOperations() {
        @Override
        public void captureOriginal(CreativeModeInventoryScreen screen) {
            CarriageFlowClient.captureOriginal(screen);
        }

        @Override
        public void register(IEventBus bus) {
            CarriageFlowClient.register(bus);
        }

        @Override
        public boolean active(Screen screen) {
            return CarriageFlowClient.active(screen);
        }

        @Override
        public boolean hiddenSlot(Screen screen, Slot slot) {
            return CarriageFlowClient.hiddenSlot(screen, slot);
        }

        @Override
        public void restoreSlots(CreativeModeInventoryScreen screen) {
            CarriageFlowClient.restoreSlots(screen);
        }

        @Override
        public void render(CreativeModeInventoryScreen screen, GuiGraphics gui, int x, int y) {
            CarriageFlowClient.render(screen, gui, x, y);
        }

        @Override
        public void removed(CreativeModeInventoryScreen screen) {
            CarriageFlowClient.removed(screen);
        }
    };

    @Override
    public CarriageFlowClientOperations carriageFlowClient() { return carriageFlowClient; }

    private static final CarriageStandingClientOperations carriageStandingClient = new CarriageStandingClientOperations() {
        @Override
        public void afterCollisionMovement(Runnable movement) {
            CarriageStandingClient.afterCollisionMovement(movement);
        }

        @Override
        public long movementEpoch() {
            return CarriageStandingClient.movementEpoch();
        }

        @Override
        public void dismount(Player player, SnapshotCarriageView car, Vec3 localFeet) {
            CarriageStandingClient.dismount(player, car, localFeet);
        }

        @Override
        public void resetMovement(Player player) {
            CarriageStandingClient.resetMovement(player);
        }
    };

    @Override
    public CarriageStandingClientOperations carriageStandingClient() { return carriageStandingClient; }

    private static final DrivingConsoleClientOperations drivingConsoleClient = DrivingConsoleClient::handleKey;

    @Override
    public DrivingConsoleClientOperations drivingConsoleClient() { return drivingConsoleClient; }

    private static final HXInventoryEventsOperations hxInventoryEvents = HXInventoryEvents::captureLegacyScroll;

    @Override
    public HXInventoryEventsOperations hxInventoryEvents() { return hxInventoryEvents; }

    private static final HudFontsOperations hudFonts = HudFonts::register;

    @Override
    public HudFontsOperations hudFonts() { return hudFonts; }

    @Override
    public void registerInventoryEvents(IEventBus gameBus) {
        gameBus.register(new HXInventoryEvents());
    }

    @Override
    public boolean isTextInputScreen(Screen screen) {
        return screen instanceof TextInputScreen<?>;
    }

    @Override
    public void registerMenuScreens(RegisterMenuScreensEvent event) {
        registerScreens(event);
    }

    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(MenuInit.CARRIAGE_NUMBER_MENU.get(), CarriageNumberScreen::new);
        event.register(MenuInit.PANEL_TEXT_MENU.get(), PanelTextScreen::new);
        event.register(MenuInit.CARRIAGE_MARKING_MENU.get(), CarriageMarkingScreen::new);
        event.register(MenuInit.HEAD_NUMBER_MENU.get(), HeadNumberScreen::new);
        event.register(MenuInit.SS7D_NUMBER_MENU.get(), SS7DNumberScreen::new);
        event.register(MenuInit.GROUND_STATION_SIGN_MENU.get(), GroundStationSignScreen::new);
        event.register(MenuInit.CARRIAGE_TYPE_SIGN_EDIT_MENU.get(), CarriageTypeSignEditScreen::new);
        event.register(MenuInit.CARRIAGE_NO_SIGN_EDIT_MENU.get(), CarriageNoSignEditScreen::new);
        event.register(MenuInit.LAQUERED_BOARD_EDIT_MENU.get(), LacqueredBoardEditScreen::new);
        event.register(MenuInit.TRAIN_SPEED_SIGN_EDIT_MENU.get(), TrainSpeedSignEditScreen::new);
        event.register(MenuInit.TICKET_VENDOR_MENU.get(), TicketVendorScreen::new);
        event.register(MenuInit.STANDARD_TRACK_PAVING_MENU.get(), StandardTrackPavingScreen::new);
    }
}
