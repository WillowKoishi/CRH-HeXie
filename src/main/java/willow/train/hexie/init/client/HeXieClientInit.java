package willow.train.hexie.init.client;

import com.simibubi.create.content.trains.bogey.BogeyBlockEntityRenderer;
import willow.train.hexie.client.render.SnapshotCarriageRenderer;
import willow.train.hexie.client.render.SnapshotCarriageVisual;
import dev.engine_room.flywheel.lib.visualization.SimpleEntityVisualizer;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import willow.train.hexie.Main;
import willow.train.hexie.client.screens.*;
import willow.train.hexie.event.client.HXInventoryEvents;
import willow.train.hexie.init.BlockEntitiesInit;
import willow.train.hexie.init.EntityInit;
import willow.train.hexie.init.track.TrackBlockInit;
import willow.train.hexie.init.bogeys.BogeyEntityInit;
import willow.train.hexie.init.semaphore.SemaphoreEntityInit;
import willow.train.hexie.client.render.blockentity.ThroughSignalRenderer;
import willow.train.hexie.client.render.blockentity.*;
import willow.train.hexie.client.render.track.StandardTrackRenderProperties;

import java.util.*;
import java.util.function.Supplier;

public class HeXieClientInit {

    public static void init() {
        willow.train.hexie.client.render.display.PassengerPanelModels.register();
        willow.train.hexie.client.render.CRH2StaticSections.register();
        Main.bus.addListener(willow.train.hexie.client.render.bogey.BogeyTopModel::register);
        willow.train.hexie.client.render.track.BallastCurveBuffers.register();
        willow.train.hexie.compat.HeXieSimulatedClientCompat.register(Main.bus);
        willow.train.hexie.client.render.carriage.CarriageTransparencyCompat.register(Main.bus);
        willow.train.hexie.client.sign.SignTextClient.register(Main.bus);
        willow.train.hexie.client.carriage.browser.CarriageBrowserClient.register(Main.bus);
        Main.bus.addListener(HeXieClientInit::registerBlockExtensions);
        Main.bus.addListener(HeXieClientInit::registerMenus);
        Main.bus.addListener(willow.train.hexie.client.interaction.SurfaceControlCaption::register);
        Main.bus.addListener(HeXieClientInit::registerRenderers);
        Main.bus.addListener(HeXieClientInit::registerEntityVisuals);
        PartialModelInit.init();
        ClientModEventSubscriber.registerModelLayers();
        Main.bus.addListener(HeXieClientInit::onModelLayerRegistration);
        NeoForge.EVENT_BUS.register(new HXInventoryEvents());
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
        event.registerBlockEntityRenderer(BlockEntitiesInit.PASSENGER_PANEL.get(),
                willow.train.hexie.client.render.blockentity.PassengerPanelRenderer::new);
        event.registerBlockEntityRenderer(BlockEntitiesInit.CRH2A_PART.get(), CarriageMarkingRenderer::new);
        event.registerBlockEntityRenderer(BlockEntitiesInit.CRH2A_HEAD.get(), HeadNumberRenderer::new);
        event.registerBlockEntityRenderer(BlockEntitiesInit.DRIVING_CONSOLE.get(),
                willow.train.hexie.client.render.blockentity.DrivingConsoleRenderer::new);
        // Entity builders run before Registrate is attached to our bus. Bind directly so the
        // renderer is present when the client dispatcher is created, including without Flywheel.
        event.registerEntityRenderer(EntityInit.ASSEMBLED_CARRIAGE.get(), SnapshotCarriageRenderer::new);
        event.registerBlockEntityRenderer(BlockEntitiesInit.GROUND_STATION_SIGN.get(), GroundStationSignRenderer::new);
        event.registerBlockEntityRenderer(BlockEntitiesInit.COUPLER.get(), CouplerBlockEntityRenderer::new);
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

    public static void registerEntityVisuals(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            willow.train.hexie.client.render.SnapshotCouplerRenderer.register(
                    willow.train.hexie.init.block.BlockInit.GENERAL_JANNEY_COUPLER.get(),
                    new willow.train.hexie.client.render.JanneyCouplerRenderer(
                            PartialModelInit.JANNEY_BASE, PartialModelInit.JANNEY_BODY, PartialModelInit.JANNEY_KNUCKLE,
                            PartialModelInit.JANNEY_BODY_PIN, PartialModelInit.JANNEY_KNUCKLE_PIN,
                            willow.train.hexie.init.block.BlockInit.GENERAL_JANNEY_COUPLER.get().geometry()));
            willow.train.hexie.client.render.SnapshotCouplerRenderer.register(
                    willow.train.hexie.init.block.BlockInit.CRH2A_AUTO_COUPLER.get(),
                    new willow.train.hexie.client.render.CoveredCouplerRenderer(
                            PartialModelInit.CRH2_AUTO_COUPLER_BASE, PartialModelInit.CRH2_AUTO_COUPLER_ARM,
                            PartialModelInit.CRH2_AUTO_COUPLER_COVER_LEFT, PartialModelInit.CRH2_AUTO_COUPLER_COVER_RIGHT,
                            willow.train.hexie.init.block.BlockInit.CRH2A_AUTO_COUPLER.get().geometry()));
            var endCoupler = willow.train.hexie.init.block.BlockInit.CRH2A_END_FACE.get();
            var pivot = endCoupler.axis().atHeight(endCoupler.complete().y());
            double back = willow.train.hexie.blocks.emu.crh2a.CRH2EndFaceBlock.BELLOWS_BASE_Z;
            double front = willow.train.hexie.blocks.emu.crh2a.CRH2EndFaceBlock.BELLOWS_FRONT_Z;
            double maximum = willow.train.hexie.blocks.emu.crh2a.CRH2EndFaceBlock.MAX_BELLOWS_THICKNESS;
            var bellows = java.util.stream.Stream.of(PartialModelInit.CRH2_END_BELLOWS_LEFT,
                    PartialModelInit.CRH2_END_BELLOWS_RIGHT, PartialModelInit.CRH2_END_BELLOWS_TOP,
                    PartialModelInit.CRH2_END_BELLOWS_BOTTOM)
                    .map(model -> new willow.train.hexie.client.render.PartialCouplerRenderer.BellowsPart(
                            model, back, front, maximum)).toList();
            willow.train.hexie.client.render.SnapshotCouplerRenderer.register(
                    willow.train.hexie.init.block.BlockInit.CRH2A_END_FACE.get(),
                    new willow.train.hexie.client.render.CRH2EndFaceRenderer(new willow.train.hexie.client.render.PartialCouplerRenderer(PartialModelInit.CRH2_END_COUPLER,
                            new net.minecraft.world.phys.Vec3(pivot.x() + .5, pivot.y() + .5, pivot.z() + .5),
                            null, bellows, .5, state -> willow.train.hexie.blocks.emu.crh2a.CRH2EndFaceBlock.bounds(
                                    state.getValue(willow.train.hexie.blocks.emu.crh2a.CRH2EndFaceBlock.FACING)),
                            new willow.train.hexie.client.render.PartialCouplerRenderer.ContactSpan(14 / 16d, 51 / 16d))));
        });
        event.enqueueWork(() -> SimpleEntityVisualizer.builder(EntityInit.ASSEMBLED_CARRIAGE.get())
                .factory(SnapshotCarriageVisual::new)
                // The base renderer still draws the non-instanced parts of a contraption.
                .skipVanillaRender(entity -> false)
                .apply());
    }

    public static void registerMenus(final RegisterMenuScreensEvent event) {
        event.register(MenuInit.CARRIAGE_NUMBER_MENU.get(), CarriageNumberScreen::new);
        event.register(MenuInit.PANEL_TEXT_MENU.get(), PanelTextScreen::new);
        event.register(MenuInit.CARRIAGE_MARKING_MENU.get(), CarriageMarkingScreen::new);
        event.register(MenuInit.HEAD_NUMBER_MENU.get(), HeadNumberScreen::new);
        event.register(MenuInit.GROUND_STATION_SIGN_MENU.get(), GroundStationSignScreen::new);
        event.register(
                MenuInit.CARRIAGE_TYPE_SIGN_EDIT_MENU.get(), CarriageTypeSignEditScreen::new);
        event.register(
                MenuInit.CARRIAGE_NO_SIGN_EDIT_MENU.get(), CarriageNoSignEditScreen::new);
        event.register(MenuInit.LAQUERED_BOARD_EDIT_MENU.get(), LacqueredBoardEditScreen::new);
        event.register(
                MenuInit.TRAIN_SPEED_SIGN_EDIT_MENU.get(), TrainSpeedSignEditScreen::new);
        event.register(MenuInit.TICKET_VENDOR_MENU.get(), TicketVendorScreen::new);
    }
}
