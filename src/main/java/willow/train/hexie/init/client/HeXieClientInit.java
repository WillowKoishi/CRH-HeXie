package willow.train.hexie.init.client;

import com.simibubi.create.content.trains.bogey.BogeyBlockEntityRenderer;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;
import willow.train.hexie.Main;
import willow.train.hexie.client.screens.*;
import willow.train.hexie.client.screens.controls.AnimatableBlockControllerScreen;
import willow.train.hexie.client.screens.controls.AutomaticControlCouplingScreen;
import willow.train.hexie.client.overlay.OverlayManager;
import willow.train.hexie.event.client.HXInventoryEvents;
import willow.train.hexie.init.BlockEntitiesInit;
import willow.train.hexie.init.track.TrackBlockInit;
import willow.train.hexie.init.bogeys.BogeyEntityInit;
import willow.train.hexie.init.client.PartialModelInit;
import willow.train.hexie.init.semaphore.SemaphoreEntityInit;
import willow.train.hexie.client.render.blockentity.ThroughSignalRenderer;
import willow.train.hexie.client.render.blockentity.*;
import willow.train.hexie.client.render.track.StandardTrackRenderProperties;

import java.util.*;
import java.util.function.Supplier;

public class HeXieClientInit {

    public static void init() {
        willow.train.hexie.client.render.track.BallastCurveBuffers.register();
        willow.train.hexie.compat.HeXieSimulatedClientCompat.register(Main.bus);
        willow.train.hexie.client.render.carriage.CarriageTransparencyCompat.register(Main.bus);
        willow.train.hexie.client.sign.SignTextClient.register(Main.bus);
        willow.train.hexie.client.carriage.browser.CarriageBrowserClient.register(Main.bus);
        Main.bus.addListener(HeXieClientInit::registerBlockExtensions);
        Main.bus.addListener(HeXieClientInit::registerMenus);
        Main.bus.addListener(HeXieClientInit::registerRenderers);
        PartialModelInit.init();
        ClientModEventSubscriber.registerModelLayers();
        Main.bus.addListener(HeXieClientInit::onModelLayerRegistration);
        NeoForge.EVENT_BUS.register(new HXInventoryEvents());
        NeoForge.EVENT_BUS.register(new OverlayManager.OverlayRenderEvent());
    }

    public static void registerBlockExtensions(RegisterClientExtensionsEvent event) {
        // Register directly: the track builders are declared before Registrate is attached to the mod bus.
        event.registerBlock(new StandardTrackRenderProperties(),
                TrackBlockInit.STANDARD_TRACK.get(),
                TrackBlockInit.BALLASTLESS_TRACK.get(),
                TrackBlockInit.TIELESS_TRACK.get());
    }

    private static final Map<ModelLayerLocation, Supplier<LayerDefinition>> modelLayers =
            new HashMap<>();

    public static void registerModelLayer(
            ModelLayerLocation layer, Supplier<LayerDefinition> definition) {
        modelLayers.put(layer, definition);
    }

    public static void onModelLayerRegistration(
            EntityRenderersEvent.RegisterLayerDefinitions event) {
        modelLayers.forEach(event::registerLayerDefinition);
        modelLayers.clear();
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BlockEntitiesInit.GROUND_STATION_SIGN.get(), GroundStationSignRenderer::new);
        // Register here so NeoForge has the renderer before it builds the dispatcher.
        event.registerBlockEntityRenderer(
                BogeyEntityInit.HX_STANDARD_BOGEY.get(), BogeyBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(
                BogeyEntityInit.DF11G_BOGEY.get(), BogeyBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(
                BogeyEntityInit.CRH_BOGEY.get(), BogeyBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(
                SemaphoreEntityInit.HX_SEMAPHORE.get(), ThroughSignalRenderer::new);
        event.registerBlockEntityRenderer(
                SemaphoreEntityInit.ADJUSTABLE_SEMAPHORE.get(), AdjustableSignalRenderer::new);
        event.registerBlockEntityRenderer(
                BlockEntitiesInit.CARRIAGE_TYPE_SIGN.get(), CarriageTypeSignRenderer::new);
        event.registerBlockEntityRenderer(
                BlockEntitiesInit.CARRIAGE_NO_SIGN.get(), CarriageNoSignRenderer::new);
        event.registerBlockEntityRenderer(
                BlockEntitiesInit.TRAIN_SPEED_SIGN.get(), TrainSpeedSignEntityRenderer::new);
        event.registerBlockEntityRenderer(
                BlockEntitiesInit.LAQUERED_BOARD.get(), LacqueredBoardEntityRenderer::new);
        event.registerBlockEntityRenderer(
                BlockEntitiesInit.ELECTRIC_FENCE_DOOR.get(), ElectricFenceDoorRenderer::new);
        event.registerBlockEntityRenderer(
                BlockEntitiesInit.OBJ_RENDERABLE_END_FACE_BLOCK_ENTITY.get(),
                OBJRenderableEndFaceRenderer::new);
        event.registerBlockEntityRenderer(
                BlockEntitiesInit.CR200J_FRONT_BLOCK_ENTITY.get(),
                CR200jFrontRenderer::new);
        event.registerBlockEntityRenderer(
                BlockEntitiesInit.CUSTOM_RENDERED_DOOR_ENTITY.get(),
                CustomRenderedDoorRenderer::new);
    }

    public static void registerMenus(final RegisterMenuScreensEvent event) {
        event.register(MenuInit.GROUND_STATION_SIGN_MENU.get(), GroundStationSignScreen::new);
        event.register(
                MenuInit.CARRIAGE_TYPE_SIGN_EDIT_MENU.get(), CarriageTypeSignEditScreen::new);
        event.register(
                MenuInit.CARRIAGE_NO_SIGN_EDIT_MENU.get(), CarriageNoSignEditScreen::new);
        event.register(MenuInit.LAQUERED_BOARD_EDIT_MENU.get(), LacqueredBoardEditScreen::new);
        event.register(
                MenuInit.TRAIN_SPEED_SIGN_EDIT_MENU.get(), TrainSpeedSignEditScreen::new);
        event.register(MenuInit.TICKET_VENDOR_MENU.get(), TicketVendorScreen::new);
        event.register(MenuInit.ANIMATABLE_BLOCK_CONTROLLER_MENU.get(), AnimatableBlockControllerScreen::new);
        event.register(MenuInit.AUTOMATIC_CONTROL_COUPLING_MENU.get(), AutomaticControlCouplingScreen::new);
    }
}
