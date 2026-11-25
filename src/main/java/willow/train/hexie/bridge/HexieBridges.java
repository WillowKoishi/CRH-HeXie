package willow.train.hexie.bridge;

import willow.train.hexie.bridge.bootstrap.ModBootstrapBridge;
import willow.train.hexie.bridge.compat.CeeBridge;
import willow.train.hexie.bridge.compat.CompatBridge;
import willow.train.hexie.bridge.compat.CreateCarriageBridge;
import willow.train.hexie.bridge.compat.CreateDoorBridge;
import willow.train.hexie.bridge.compat.CreatePanelBridge;
import willow.train.hexie.bridge.compat.CreatePlayerBridge;
import willow.train.hexie.bridge.compat.CreateTrackBridge;
import willow.train.hexie.bridge.compat.CreateTrainBridgePort;
import willow.train.hexie.bridge.compat.RailxBridge;
import willow.train.hexie.bridge.compat.SableBridge;
import willow.train.hexie.bridge.compat.SimurailBridge;
import willow.train.hexie.bridge.compat.client.IrisBridge;
import willow.train.hexie.bridge.interaction.InteractionBridge;
import willow.train.hexie.bridge.interaction.ItemFactoryBridge;
import willow.train.hexie.bridge.interaction.SurfaceInputBridge;
import willow.train.hexie.bridge.interaction.ToolOpsBridge;
import willow.train.hexie.bridge.railway.RailwayBridge;
import willow.train.hexie.bridge.railway.SignalRegistryBridge;
import willow.train.hexie.bridge.railway.TrackRegistryBridge;
import willow.train.hexie.bridge.render.BlockEntityRenderBridge;
import willow.train.hexie.bridge.render.BlockEntityViewsClientBridge;
import willow.train.hexie.bridge.render.BogeyRegistryClientBridge;
import willow.train.hexie.bridge.render.BogeyRenderBridge;
import willow.train.hexie.bridge.render.CabRenderBridge;
import willow.train.hexie.bridge.render.CarriageRenderBridge;
import willow.train.hexie.bridge.render.CeeClientBridge;
import willow.train.hexie.bridge.render.CompatClientBridge;
import willow.train.hexie.bridge.render.CreateCarriageClientBridge;
import willow.train.hexie.bridge.render.CreateTrainClientBridge;
import willow.train.hexie.bridge.render.DisplayRenderBridge;
import willow.train.hexie.bridge.render.DoorRenderBridge;
import willow.train.hexie.bridge.render.RenderBridge;
import willow.train.hexie.bridge.render.SableClientBridge;
import willow.train.hexie.bridge.render.SimurailClientBridge;
import willow.train.hexie.bridge.render.TrackRenderBridge;
import willow.train.hexie.bridge.render.WorldRegistryClientBridge;
import willow.train.hexie.bridge.sticker.StickerBridge;
import willow.train.hexie.bridge.sticker.StickerClientBridge;
import willow.train.hexie.bridge.sticker.StickerMeshCutterBridge;
import willow.train.hexie.bridge.sync.SyncBridge;
import willow.train.hexie.bridge.ui.MenuBridge;
import willow.train.hexie.bridge.ui.UiBridge;
import willow.train.hexie.bridge.ui.client.ClientCarriageUiBridge;
import willow.train.hexie.bridge.ui.client.ClientSyncBridge;
import willow.train.hexie.bridge.ui.client.SurfaceInputClientBridge;
import willow.train.hexie.bridge.vehicle.BogeyRegistryBridge;
import willow.train.hexie.bridge.vehicle.CabControlBridge;
import willow.train.hexie.bridge.vehicle.CouplingBridge;
import willow.train.hexie.bridge.vehicle.PlayerCarriageBridge;
import willow.train.hexie.bridge.vehicle.VehicleBridge;
import willow.train.hexie.bridge.world.CarriageBlockBridge;
import willow.train.hexie.bridge.world.CouplerBlockBridge;
import willow.train.hexie.bridge.world.PartBlockBridge;
import willow.train.hexie.bridge.world.SignBlockBridge;
import willow.train.hexie.bridge.world.WorldBlockBridge;
import willow.train.hexie.bridge.world.WorldRegistryBridge;
import willow.train.hexie.core.ServiceSlot;
import willow.train.hexie.core.api.bogey.BogeyAnimationRegistry;
import willow.train.hexie.core.api.bogey.BogeyRegistry;
import willow.train.hexie.core.api.cab.CabModuleAccess;
import willow.train.hexie.core.api.railway.RailwayRegistry;

/** Ports installed by the composition root during mod construction. */
public final class HexieBridges {
    private HexieBridges() {}

    private static final ServiceSlot<ModBootstrapBridge> MOD_BOOTSTRAP = new ServiceSlot<>("ModBootstrapBridge");
    private static final ServiceSlot<CeeBridge> CEE = new ServiceSlot<>("CeeBridge");
    private static final ServiceSlot<CompatBridge> COMPAT = new ServiceSlot<>("CompatBridge");
    private static final ServiceSlot<CreateCarriageBridge> CREATE_CARRIAGE = new ServiceSlot<>("CreateCarriageBridge");
    private static final ServiceSlot<CreateDoorBridge> CREATE_DOOR = new ServiceSlot<>("CreateDoorBridge");
    private static final ServiceSlot<CreatePanelBridge> CREATE_PANEL = new ServiceSlot<>("CreatePanelBridge");
    private static final ServiceSlot<CreatePlayerBridge> CREATE_PLAYER = new ServiceSlot<>("CreatePlayerBridge");
    private static final ServiceSlot<CreateTrackBridge> CREATE_TRACK = new ServiceSlot<>("CreateTrackBridge");
    private static final ServiceSlot<CreateTrainBridgePort> CREATE_TRAIN = new ServiceSlot<>("CreateTrainBridgePort");
    private static final ServiceSlot<RailxBridge> RAILX = new ServiceSlot<>("RailxBridge");
    private static final ServiceSlot<SableBridge> SABLE = new ServiceSlot<>("SableBridge");
    private static final ServiceSlot<SimurailBridge> SIMURAIL = new ServiceSlot<>("SimurailBridge");
    private static final ServiceSlot<InteractionBridge> INTERACTION = new ServiceSlot<>("InteractionBridge");
    private static final ServiceSlot<ItemFactoryBridge> ITEM_FACTORY = new ServiceSlot<>("ItemFactoryBridge");
    private static final ServiceSlot<SurfaceInputBridge> SURFACE_INPUT = new ServiceSlot<>("SurfaceInputBridge");
    private static final ServiceSlot<ToolOpsBridge> TOOL_OPS = new ServiceSlot<>("ToolOpsBridge");
    private static final ServiceSlot<RailwayBridge> RAILWAY = new ServiceSlot<>("RailwayBridge");
    private static final ServiceSlot<SignalRegistryBridge> SIGNAL_REGISTRY = new ServiceSlot<>("SignalRegistryBridge");
    private static final ServiceSlot<TrackRegistryBridge> TRACK_REGISTRY = new ServiceSlot<>("TrackRegistryBridge");
    private static final ServiceSlot<SyncBridge> SYNC = new ServiceSlot<>("SyncBridge");
    private static final ServiceSlot<UiBridge> UI = new ServiceSlot<>("UiBridge");
    private static final ServiceSlot<BogeyRegistryBridge> BOGEY_REGISTRY = new ServiceSlot<>("BogeyRegistryBridge");
    private static final ServiceSlot<CabControlBridge> CAB_CONTROL = new ServiceSlot<>("CabControlBridge");
    private static final ServiceSlot<CouplingBridge> COUPLING = new ServiceSlot<>("CouplingBridge");
    private static final ServiceSlot<PlayerCarriageBridge> PLAYER_CARRIAGE = new ServiceSlot<>("PlayerCarriageBridge");
    private static final ServiceSlot<VehicleBridge> VEHICLE = new ServiceSlot<>("VehicleBridge");
    private static final ServiceSlot<WorldBlockBridge> WORLD_BLOCK = new ServiceSlot<>("WorldBlockBridge");
    private static final ServiceSlot<WorldRegistryBridge> WORLD_REGISTRY = new ServiceSlot<>("WorldRegistryBridge");
    private static final ServiceSlot<StickerBridge> STICKER = new ServiceSlot<>("StickerBridge");

    public static void registerModBootstrap(ModBootstrapBridge service) {
        MOD_BOOTSTRAP.register(service);
    }
    public static ModBootstrapBridge modBootstrap() { return MOD_BOOTSTRAP.get(); }

    public static void registerCee(CeeBridge service) {
        CEE.register(service);
    }
    public static CeeBridge cee() { return CEE.get(); }

    public static void registerCompat(CompatBridge service) {
        COMPAT.register(service);
    }
    public static CompatBridge compat() { return COMPAT.get(); }

    public static void registerCreateCarriage(CreateCarriageBridge service) {
        CREATE_CARRIAGE.register(service);
    }
    public static CreateCarriageBridge createCarriage() { return CREATE_CARRIAGE.get(); }

    public static void registerCreateDoor(CreateDoorBridge service) {
        CREATE_DOOR.register(service);
    }
    public static CreateDoorBridge createDoor() { return CREATE_DOOR.get(); }

    public static void registerCreatePanel(CreatePanelBridge service) {
        CREATE_PANEL.register(service);
    }
    public static CreatePanelBridge createPanel() { return CREATE_PANEL.get(); }

    public static void registerCreatePlayer(CreatePlayerBridge service) {
        CREATE_PLAYER.register(service);
    }
    public static CreatePlayerBridge createPlayer() { return CREATE_PLAYER.get(); }

    public static void registerCreateTrain(CreateTrainBridgePort service) {
        CREATE_TRAIN.register(service);
    }
    public static CreateTrainBridgePort createTrain() { return CREATE_TRAIN.get(); }

    public static void registerRailx(RailxBridge service) {
        RAILX.register(service);
    }
    public static RailxBridge railx() { return RAILX.get(); }

    public static void registerSable(SableBridge service) {
        SABLE.register(service);
    }
    public static SableBridge sable() { return SABLE.get(); }

    public static void registerSimurail(SimurailBridge service) {
        SIMURAIL.register(service);
    }
    public static SimurailBridge simurail() { return SIMURAIL.get(); }

    public static void registerInteraction(InteractionBridge service) {
        INTERACTION.register(service);
    }
    public static InteractionBridge interaction() { return INTERACTION.get(); }

    public static void registerItemFactory(ItemFactoryBridge service) {
        ITEM_FACTORY.register(service);
    }
    public static ItemFactoryBridge itemFactory() { return ITEM_FACTORY.get(); }

    public static void registerSurfaceInput(SurfaceInputBridge service) {
        SURFACE_INPUT.register(service);
    }
    public static SurfaceInputBridge surfaceInput() { return SURFACE_INPUT.get(); }

    public static void registerToolOps(ToolOpsBridge service) {
        TOOL_OPS.register(service);
    }
    public static ToolOpsBridge toolOps() { return TOOL_OPS.get(); }

    public static void registerRailway(RailwayBridge service) {
        RAILWAY.register(service);
    }
    public static RailwayBridge railway() { return RAILWAY.get(); }

    public static void registerSignalRegistry(SignalRegistryBridge service) {
        SIGNAL_REGISTRY.register(service);
    }
    public static SignalRegistryBridge signalRegistry() { return SIGNAL_REGISTRY.get(); }

    public static void registerCreateTrack(CreateTrackBridge service) { CREATE_TRACK.register(service); }
    public static CreateTrackBridge createTrack() { return CREATE_TRACK.get(); }

    public static void registerTrackRegistry(TrackRegistryBridge service) {
        RailwayRegistry.registerBackend(service);
        TRACK_REGISTRY.register(service);
    }
    public static TrackRegistryBridge trackRegistry() { return TRACK_REGISTRY.get(); }

    public static void registerSync(SyncBridge service) {
        SYNC.register(service);
    }
    public static SyncBridge sync() { return SYNC.get(); }

    public static void registerUi(UiBridge service) {
        UI.register(service);
    }
    public static UiBridge ui() { return UI.get(); }

    public static void registerBogeyRegistry(BogeyRegistryBridge service) {
        BogeyRegistry.registerBackend(service);
        BOGEY_REGISTRY.register(service);
    }
    public static BogeyRegistryBridge bogeyRegistry() { return BOGEY_REGISTRY.get(); }

    public static void registerCabControl(CabControlBridge service) {
        CabModuleAccess.Factory.register(service);
        CAB_CONTROL.register(service);
    }
    public static CabControlBridge cabControl() { return CAB_CONTROL.get(); }

    public static void registerCoupling(CouplingBridge service) {
        COUPLING.register(service);
    }
    public static CouplingBridge coupling() { return COUPLING.get(); }

    public static void registerPlayerCarriage(PlayerCarriageBridge service) {
        PLAYER_CARRIAGE.register(service);
    }
    public static PlayerCarriageBridge playerCarriage() { return PLAYER_CARRIAGE.get(); }

    public static void registerVehicle(VehicleBridge service) {
        VEHICLE.register(service);
    }
    public static VehicleBridge vehicle() { return VEHICLE.get(); }

    public static void registerWorldBlock(WorldBlockBridge service) {
        WORLD_BLOCK.register(service);
    }
    public static WorldBlockBridge worldBlock() { return WORLD_BLOCK.get(); }

    public static void registerWorldRegistry(WorldRegistryBridge service) {
        WORLD_REGISTRY.register(service);
    }
    public static WorldRegistryBridge worldRegistry() { return WORLD_REGISTRY.get(); }

    public static void registerSticker(StickerBridge service) {
        STICKER.register(service);
    }
    public static StickerBridge sticker() { return STICKER.get(); }

    public static MenuBridge menu() { return ui().menus(); }

    public static CarriageBlockBridge carriageBlock() { return worldBlock().carriage(); }

    public static CouplerBlockBridge couplerBlock() { return worldBlock().coupler(); }

    public static PartBlockBridge partBlock() { return worldBlock().part(); }

    public static SignBlockBridge signBlock() { return worldBlock().sign(); }

    public static final class Client {
        private Client() {}

        private static final ServiceSlot<IrisBridge> IRIS = new ServiceSlot<>("IrisBridge");
        private static final ServiceSlot<BlockEntityViewsClientBridge> BLOCK_ENTITY_VIEWS_CLIENT = new ServiceSlot<>("BlockEntityViewsClientBridge");
        private static final ServiceSlot<BogeyRegistryClientBridge> BOGEY_REGISTRY_CLIENT = new ServiceSlot<>("BogeyRegistryClientBridge");
        private static final ServiceSlot<CeeClientBridge> CEE_CLIENT = new ServiceSlot<>("CeeClientBridge");
        private static final ServiceSlot<CompatClientBridge> COMPAT_CLIENT = new ServiceSlot<>("CompatClientBridge");
        private static final ServiceSlot<CreateCarriageClientBridge> CREATE_CARRIAGE_CLIENT = new ServiceSlot<>("CreateCarriageClientBridge");
        private static final ServiceSlot<CreateTrainClientBridge> CREATE_TRAIN_CLIENT = new ServiceSlot<>("CreateTrainClientBridge");
        private static final ServiceSlot<RenderBridge> RENDER = new ServiceSlot<>("RenderBridge");
        private static final ServiceSlot<SableClientBridge> SABLE_CLIENT = new ServiceSlot<>("SableClientBridge");
        private static final ServiceSlot<SimurailClientBridge> SIMURAIL_CLIENT = new ServiceSlot<>("SimurailClientBridge");
        private static final ServiceSlot<WorldRegistryClientBridge> WORLD_REGISTRY_CLIENT = new ServiceSlot<>("WorldRegistryClientBridge");
        private static final ServiceSlot<ClientCarriageUiBridge> CLIENT_CARRIAGE_UI = new ServiceSlot<>("ClientCarriageUiBridge");
        private static final ServiceSlot<ClientSyncBridge> CLIENT_SYNC = new ServiceSlot<>("ClientSyncBridge");
        private static final ServiceSlot<SurfaceInputClientBridge> SURFACE_INPUT_CLIENT = new ServiceSlot<>("SurfaceInputClientBridge");
        private static final ServiceSlot<StickerClientBridge> STICKER_CLIENT = new ServiceSlot<>("StickerClientBridge");
        private static final ServiceSlot<StickerMeshCutterBridge> STICKER_MESH_CUTTER = new ServiceSlot<>("StickerMeshCutterBridge");

        public static void registerIris(IrisBridge service) {
            IRIS.register(service);
        }
        public static IrisBridge iris() { return IRIS.get(); }

        public static void registerBlockEntityViewsClient(BlockEntityViewsClientBridge service) {
            BLOCK_ENTITY_VIEWS_CLIENT.register(service);
        }
        public static BlockEntityViewsClientBridge blockEntityViewsClient() { return BLOCK_ENTITY_VIEWS_CLIENT.get(); }

        public static void registerBogeyRegistryClient(BogeyRegistryClientBridge service) {
            BogeyAnimationRegistry.registerBackend(service);
            BOGEY_REGISTRY_CLIENT.register(service);
        }
        public static BogeyRegistryClientBridge bogeyRegistryClient() { return BOGEY_REGISTRY_CLIENT.get(); }

        public static void registerCeeClient(CeeClientBridge service) {
            CEE_CLIENT.register(service);
        }
        public static CeeClientBridge ceeClient() { return CEE_CLIENT.get(); }

        public static void registerCompatClient(CompatClientBridge service) {
            COMPAT_CLIENT.register(service);
        }
        public static CompatClientBridge compatClient() { return COMPAT_CLIENT.get(); }

        public static void registerCreateCarriageClient(CreateCarriageClientBridge service) {
            CREATE_CARRIAGE_CLIENT.register(service);
        }
        public static CreateCarriageClientBridge createCarriageClient() { return CREATE_CARRIAGE_CLIENT.get(); }

        public static void registerCreateTrainClient(CreateTrainClientBridge service) {
            CREATE_TRAIN_CLIENT.register(service);
        }
        public static CreateTrainClientBridge createTrainClient() { return CREATE_TRAIN_CLIENT.get(); }

        public static void registerRender(RenderBridge service) {
            RENDER.register(service);
        }
        public static RenderBridge render() { return RENDER.get(); }

        public static void registerSableClient(SableClientBridge service) {
            SABLE_CLIENT.register(service);
        }
        public static SableClientBridge sableClient() { return SABLE_CLIENT.get(); }

        public static void registerSimurailClient(SimurailClientBridge service) {
            SIMURAIL_CLIENT.register(service);
        }
        public static SimurailClientBridge simurailClient() { return SIMURAIL_CLIENT.get(); }

        public static void registerWorldRegistryClient(WorldRegistryClientBridge service) {
            WORLD_REGISTRY_CLIENT.register(service);
        }
        public static WorldRegistryClientBridge worldRegistryClient() { return WORLD_REGISTRY_CLIENT.get(); }

        public static void registerClientCarriageUi(ClientCarriageUiBridge service) {
            CLIENT_CARRIAGE_UI.register(service);
        }
        public static ClientCarriageUiBridge clientCarriageUi() { return CLIENT_CARRIAGE_UI.get(); }

        public static void registerClientSync(ClientSyncBridge service) {
            CLIENT_SYNC.register(service);
        }
        public static ClientSyncBridge clientSync() { return CLIENT_SYNC.get(); }

        public static void registerSurfaceInputClient(SurfaceInputClientBridge service) {
            SURFACE_INPUT_CLIENT.register(service);
        }
        public static SurfaceInputClientBridge surfaceInputClient() { return SURFACE_INPUT_CLIENT.get(); }

        public static void registerStickerClient(StickerClientBridge service) {
            STICKER_CLIENT.register(service);
        }
        public static StickerClientBridge stickerClient() { return STICKER_CLIENT.get(); }

        public static void registerStickerMeshCutter(StickerMeshCutterBridge service) {
            STICKER_MESH_CUTTER.register(service);
        }
        public static StickerMeshCutterBridge stickerMeshCutter() {
            return STICKER_MESH_CUTTER.isPresent() ? STICKER_MESH_CUTTER.get() : null;
        }

        public static BlockEntityRenderBridge blockEntityRender() { return render().blockEntityRender(); }

        public static BogeyRenderBridge bogeyRender() { return render().bogeyRender(); }

        public static CabRenderBridge cabRender() { return render().cabRender(); }

        public static CarriageRenderBridge carriageRender() { return render().carriageRender(); }

        public static DisplayRenderBridge displayRender() { return render().displayRender(); }

        public static DoorRenderBridge doorRender() { return render().doorRender(); }

        public static TrackRenderBridge trackRender() { return render().trackRender(); }
    }
}
