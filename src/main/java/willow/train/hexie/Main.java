package willow.train.hexie;

import java.util.Random;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.bootstrap.ModBootstrapBridge;
import willow.train.hexie.compat.bridge.CompatBridgeBootstrap;
import willow.train.hexie.compat.cee.CeePantographCompat;
import willow.train.hexie.compat.common.HeXieSimurailCompat;
import willow.train.hexie.compat.create.CreateBootstrap;
import willow.train.hexie.compat.create.block.CreateClipboardCompat;
import willow.train.hexie.compat.create.block.CreateHeadTransformCompat;
import willow.train.hexie.compat.create.block.CreatePanelPlacementHelper;
import willow.train.hexie.compat.create.block.CreateSchematicDataCompat;
import willow.train.hexie.compat.create.block.CreateSchematicItemInit;
import willow.train.hexie.compat.create.block.CreateSchematicRequirements;
import willow.train.hexie.compat.create.block.CreateWrenchCompat;
import willow.train.hexie.compat.create.contraption.CreateInteractionBehaviourInit;
import willow.train.hexie.compat.create.interaction.CreateControlInputTargets;
import willow.train.hexie.compat.create.player.CreateDrivingConsoleSeats;
import willow.train.hexie.compat.create.railx.RailxTracks;
import willow.train.hexie.compat.create.signal.SemaphoreBlockInit;
import willow.train.hexie.compat.create.signal.SemaphoreEntityInit;
import willow.train.hexie.compat.create.track.CreateRailwayResources;
import willow.train.hexie.compat.create.track.CreateTrackBlockInit;
import willow.train.hexie.compat.create.track.StandardTrackPavingDataInit;
import willow.train.hexie.compat.create.track.TrackItems;
import willow.train.hexie.compat.create.track.TrackMaterialInit;
import willow.train.hexie.compat.sable.SableControlInputCoordinates;
import willow.train.hexie.compat.simurail.SimurailHeadRegistration;
import willow.train.hexie.core.config.VehicleConfig;
import willow.train.hexie.core.util.general.CreativeTabGroup;
import willow.train.hexie.interaction.bridge.InteractionBridgeBootstrap;
import willow.train.hexie.railway.bridge.RailwayBridgeBootstrap;
import willow.train.hexie.railway.graph.RailwayResourcePack;
import willow.train.hexie.railway.track.registry.TrackBlockInit;
import willow.train.hexie.render.bridge.RenderBridgeBootstrap;
import willow.train.hexie.render.init.HeXieClientInit;
import willow.train.hexie.render.rendererbridge.HexieRendererBridge;
import willow.train.hexie.render.sticker.StickerWorldRenderer;
import willow.train.hexie.sync.bridge.SyncBridgeBootstrap;
import willow.train.hexie.sync.channel.HexieNetworkHandler;
import willow.train.hexie.ui.bridge.UiBridgeBootstrap;
import willow.train.hexie.ui.menu.MenuInit;
import willow.train.hexie.ui.screens.StartupWarningScreen;
import willow.train.hexie.vehicle.bogey.registry.BogeyEntityInit;
import willow.train.hexie.vehicle.bridge.VehicleBridgeBootstrap;
import willow.train.hexie.world.blocks.carriage.PanelTypesUtil;
import willow.train.hexie.world.bridge.WorldBridgeBootstrap;
import willow.train.hexie.world.placement.TrainPanelPlacement;
import willow.train.hexie.world.registry.blocks.BlockInit;
import willow.train.hexie.world.registry.core.BlockEntitiesInit;
import willow.train.hexie.world.registry.core.CreativeTabsInit;
import willow.train.hexie.world.registry.core.EffectInit;
import willow.train.hexie.world.registry.core.EntityInit;
import willow.train.hexie.world.registry.core.SoundsInit;
import willow.train.hexie.world.registry.creative.HexieCreativeTabs;
import willow.train.hexie.world.registry.items.AllItemsInit;
import willow.train.hexie.world.registry.recipes.RecipeInit;
import willow.train.hexie.world.registry.recipes.RecipeSerializerInit;
import willow.train.hexie.world.sticker.StickerAttachments;
import willow.train.hexie.compat.create.bogey.BogeySizesInit;
import willow.train.hexie.compat.create.bogey.BogeyStylesInit;
import willow.train.hexie.core.resources.BogeyResources;

@Mod("hexie")
public class Main {
    public static final Logger LOGGER = LoggerFactory.getLogger("HeXie");

    public static final String MOD_ID = "hexie";
    public static final boolean INTERNAL_TEST = false;
    public static final Random HEXIE_RANDOM = new Random();
    public static IEventBus bus;
    private static ModContainer modContainer;

    public static PanelTypesUtil PANEL_TYPE_UTIL;

    public Main(IEventBus modBus, ModContainer container) {
        bus = modBus;
        modContainer = container;
        boolean createLoaded = CreateBootstrap.isCreateLoaded();
        HexieBridges.registerModBootstrap(new ModBootstrapBridge() {
            @Override public IEventBus modBus() { return modBus; }
            @Override public boolean isCreateLoaded() { return createLoaded; }
            @Override public CreativeTabGroup hexieMain() { return HexieCreativeTabs.HEXIE_MAIN; }
            @Override public CreativeTabGroup hexieEmu() { return HexieCreativeTabs.HEXIE_EMU; }
            @Override public CreativeTabGroup hexieNormalSpeedPassageCarriageTab() {
                return HexieCreativeTabs.HEXIE_NORMAL_SPEED_PASSAGE_CARRIAGE_TAB;
            }
            @Override public CreativeTabGroup hexieLocos() { return HexieCreativeTabs.HEXIE_LOCOS; }
            @Override public CreativeTabGroup hexieDiet() { return HexieCreativeTabs.HEXIE_DIET; }
            @Override public PanelTypesUtil panelTypes() { return PANEL_TYPE_UTIL; }
        });
        RailwayBridgeBootstrap.install();
        VehicleBridgeBootstrap.install();
        WorldBridgeBootstrap.install();
        InteractionBridgeBootstrap.install();
        SyncBridgeBootstrap.install();
        UiBridgeBootstrap.install();
        CompatBridgeBootstrap.install();
        if (FMLEnvironment.dist == Dist.CLIENT) ClientSetup.install();

        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER,
                VehicleConfig.SPEC, "hexie-server.toml");
        bus.addListener(HexieNetworkHandler::registerPackets);
        CreativeTabsInit.register(bus);
        if (createLoaded) {
            CreateSetup.registerItemData(bus);
        }

        //InternalTestUtil.InternalTest();

        AllItemsInit.registerAllItems(bus);

        BlockInit.registerAllBlocks(bus);

        BlockEntitiesInit.BLOCK_ENTITIES.register(bus);

        StickerAttachments.register(bus);

        EntityInit.ENTITY_TYPES.register(bus);

        // Self-registered bogeys, locomotive fronts, and bogey block entities always register via NeoForge.
        TrackBlockInit.register(bus);
        BogeyEntityInit.register(bus);

        if (createLoaded) {
            CreateSetup.register(bus);
        } else {
            RailwayResourcePack.register(bus, BogeyResources::resources);
        }

        if (createLoaded && ModList.get().isLoaded("simurail")) {
            SimurailHeadRegistration.register(bus);
        }
        if (createLoaded && ModList.get().isLoaded("electroenergetics")) {
            CeePantographCompat.register(bus);
        }

        SoundsInit.register(bus);

        EffectInit.register(bus);

        RecipeInit.register(bus);

        RecipeSerializerInit.register(bus);

        bus.addListener(this::setup);

        if (FMLEnvironment.dist == Dist.CLIENT) ClientSetup.registerListeners(modBus);

        MenuInit.register(bus);

        PANEL_TYPE_UTIL = new PanelTypesUtil();
    }

    protected void setup(final FMLCommonSetupEvent event) {
        if (CreateBootstrap.isCreateLoaded()) {
            CreateSetup.registerCommonSetup(event);
        }
        if (ModList.get().isLoaded("sable")) {
            event.enqueueWork(SableControlInputCoordinates::register);
        }
        if (CreateBootstrap.isCreateLoaded() && ModList.get().isLoaded("simurail")) {
            event.enqueueWork(HeXieSimurailCompat::register);
        }
    }

    public static ResourceLocation asResource(String name) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, name);
    }

    public static void modSetup() {
        if (CreateBootstrap.isCreateLoaded()) {
            CreateSetup.registerObjects();
        }
    }

    private static final class CreateSetup {
        static void registerItemData(IEventBus modBus) {
            StandardTrackPavingDataInit.register(modBus);
            CreateSchematicItemInit.register();
        }

        static void register(IEventBus modBus) {
            EntityInit.registerCreateEntities();
            registerObjects();
            RailwayResourcePack.register(modBus, CreateRailwayResources::resources);
            if (ModList.get().isLoaded("railx")) {
                RailxTracks.register(modBus);
            }
            CreateBootstrap.registerEventListeners(modBus);
        }

        static void registerCommonSetup(FMLCommonSetupEvent event) {
            event.enqueueWork(CreateInteractionBehaviourInit::register);
            event.enqueueWork(() -> TrainPanelPlacement.register(CreatePanelPlacementHelper.register()));
            event.enqueueWork(CreateDrivingConsoleSeats::register);
            event.enqueueWork(CreateControlInputTargets::register);
            event.enqueueWork(CreateWrenchCompat::register);
            event.enqueueWork(CreateClipboardCompat::register);
            event.enqueueWork(CreateHeadTransformCompat::register);
            event.enqueueWork(CreateSchematicRequirements::register);
            event.enqueueWork(CreateSchematicDataCompat::register);
        }

        static void registerObjects() {
            TrackMaterialInit.register();
            BogeyStylesInit.register();
            TrackItems.register();
            BogeySizesInit.register();
            CreateTrackBlockInit.register();
            SemaphoreBlockInit.register();
            SemaphoreEntityInit.register();
            CreateSchematicItemInit.register();
        }
    }

    private static final class ClientSetup {
        static void install() {
            VehicleBridgeBootstrap.installClient();
            WorldBridgeBootstrap.installClient();
            InteractionBridgeBootstrap.installClient();
            SyncBridgeBootstrap.installClient();
            UiBridgeBootstrap.installClient();
            CompatBridgeBootstrap.installClient();
            if (CreateBootstrap.isCreateLoaded()) RenderBridgeBootstrap.installClient();
        }

        static void registerListeners(IEventBus modBus) {
            StartupWarningScreen.register();
            HexieRendererBridge.install(modBus);
            StickerWorldRenderer.install();
            if (CreateBootstrap.isCreateLoaded()) {
                HeXieClientInit.init(modBus, UiBridgeBootstrap::registerScreens);
            } else {
                var ui = HexieBridges.Client.clientCarriageUi();
                ui.hudFonts().register(modBus);
                ui.carriageFlowClient().register(modBus);
                ui.registerInventoryEvents(NeoForge.EVENT_BUS);
                modBus.addListener(UiBridgeBootstrap::registerScreens);
                modBus.addListener(HexieBridges.Client.surfaceInputClient().surfaceControlCaption()::register);
            }
        }
    }

}
