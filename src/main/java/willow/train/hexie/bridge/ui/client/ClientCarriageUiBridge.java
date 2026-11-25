package willow.train.hexie.bridge.ui.client;

import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import willow.train.hexie.bridge.compat.SnapshotCarriageView;
import willow.train.hexie.core.api.cab.CabDisplaySource;
import willow.train.hexie.core.api.cab.CabDisplayState;
import willow.train.hexie.core.api.cab.DrivingConsoleContext;

/** Client hooks for camera, movement, focused editors and carriage inventory screens. */
public interface ClientCarriageUiBridge {
    void registerMenuScreens(RegisterMenuScreensEvent event);

    boolean isTextInputScreen(Screen screen);

    void registerInventoryEvents(IEventBus gameBus);
    void registerStandingEvents(IEventBus gameBus);

    CarriageCameraOperations carriageCamera();

    interface CarriageCameraOperations {
        boolean followsSeat(Player player);
        void beforePlayerTick();
        boolean turn(Entity entity, double horizontal, double vertical);
        Vec3 position(Entity entity, boolean detached, float partial, Vec3 original);
    }

    CarriageFlowClientOperations carriageFlowClient();

    interface CarriageFlowClientOperations {
        void captureOriginal(CreativeModeInventoryScreen screen);
        void register(IEventBus bus);
        boolean active(Screen screen);
        boolean hiddenSlot(Screen screen, Slot slot);
        void restoreSlots(CreativeModeInventoryScreen screen);
        void render(CreativeModeInventoryScreen screen, GuiGraphics gui, int x, int y);
        void removed(CreativeModeInventoryScreen screen);
    }

    CarriageStandingClientOperations carriageStandingClient();

    interface CarriageStandingClientOperations {
        void afterCollisionMovement(Runnable movement);
        long movementEpoch();
        void dismount(Player player, SnapshotCarriageView car, Vec3 localFeet);
        void resetMovement(Player player);
    }

    CabHud cabHud();
    void registerCabHud(CabHud hud);
    int cabKeyboardMask();

    interface CabHud {
        default void tick(@Nullable DrivingConsoleContext cab) {}
        @Nullable default NativeTrain train(Entity vehicle) { return null; }

        @Nullable default CabDisplayState displayState(DrivingConsoleContext cab) {
            var level = cab.seatAccess().vehicle().level();
            return level.hasChunkAt(cab.console()) && level.getBlockEntity(cab.console()) instanceof CabDisplaySource source
                    ? source.displayState() : null;
        }
    }

    interface NativeTrain {
        boolean snapshot();
        double speed();
        double throttle();
        float manualLimit();
    }

    DrivingConsoleClientOperations drivingConsoleClient();

    interface DrivingConsoleClientOperations {
        boolean handleKey(int key, int scanCode, int action);
    }

    HXInventoryEventsOperations hxInventoryEvents();

    interface HXInventoryEventsOperations {
        void captureLegacyScroll(CreativeModeInventoryScreen screen);
    }

    HudFontsOperations hudFonts();

    interface HudFontsOperations {
        void register(IEventBus bus);
    }

    interface TextInputScreenView<M extends AbstractContainerMenu> {}
}
