package willow.train.hexie.compat.create;

import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.foundation.data.CreateRegistrate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import willow.train.hexie.compat.create.interaction.CreateControlInputClient;
import willow.train.hexie.compat.create.interaction.CreatePassengerDisplaySync;
import willow.train.hexie.compat.create.motion.CompatibilityTrain;
import willow.train.hexie.compat.create.panel.CreatePanelClipboard;
import willow.train.hexie.compat.create.panel.CreatePanelClipboardClient;
import willow.train.hexie.compat.create.panel.CreatePanelClipboardCopyPacket;
import willow.train.hexie.compat.create.panel.CreatePanelEditorClient;
import willow.train.hexie.compat.create.player.CarriagePlayerPersistence;
import willow.train.hexie.compat.create.player.CarriagePlayerSupport;
import willow.train.hexie.compat.create.player.CarriagePlayerSync;
import willow.train.hexie.compat.create.player.CreateHardSeatSync;
import willow.train.hexie.compat.create.railx.RailxBallastModels;
import willow.train.hexie.compat.create.seat.SeatYZContraptionInteractionPacket;
import willow.train.hexie.compat.create.signal.CreateConsoleTimetable;
import willow.train.hexie.compat.create.signal.SignalPlacementFacingPacket;
import willow.train.hexie.compat.create.track.TrackBallastPacket;
import willow.train.hexie.compat.create.ui.CreateUiCompat;
import willow.train.hexie.core.HexieConstants;

public final class CreateBootstrap {
    private CreateBootstrap() {}

    public static boolean isCreateLoaded() {
        return ModList.get().isLoaded("create");
    }

    /** For callers inside the Create-loaded branch of mod bootstrap. */
    public static CreateRegistrate registrate() {
        return RegistrateHolder.INSTANCE;
    }

    public static void registerEventListeners(IEventBus bus) {
        if (isCreateLoaded()) RegistrateHolder.registerEventListeners(bus);
    }

    private static final class RegistrateHolder {
        private static final CreateRegistrate INSTANCE = CreateRegistrate.create(HexieConstants.MOD_ID);
        private static boolean registered;

        static {
            INSTANCE.defaultCreativeTab((ResourceKey<CreativeModeTab>) null);
        }

        private static synchronized void registerEventListeners(IEventBus modBus) {
            if (registered) return;
            INSTANCE.registerEventListeners(modBus);
            modBus.addListener(RegistrateHolder::registerFrontStress);
            modBus.addListener(SeatYZContraptionInteractionPacket::register);
            CreateUiCompat.register(modBus);
            modBus.addListener(RegistrateHolder::registerCarriageAlias);
            modBus.addListener(RegistrateHolder::registerRailwayPayloads);
            modBus.register(CreatePanelClipboardCopyPacket.Registration.class);
            NeoForge.EVENT_BUS.register(CreatePassengerDisplaySync.class);
            NeoForge.EVENT_BUS.register(CompatibilityTrain.class);
            NeoForge.EVENT_BUS.register(CreatePanelClipboard.class);
            NeoForge.EVENT_BUS.register(CarriagePlayerPersistence.class);
            NeoForge.EVENT_BUS.register(CarriagePlayerSupport.class);
            NeoForge.EVENT_BUS.register(CarriagePlayerSync.class);
            NeoForge.EVENT_BUS.register(CreateHardSeatSync.class);
            NeoForge.EVENT_BUS.register(CreateConsoleTimetable.class);
            if (FMLEnvironment.dist == Dist.CLIENT) ClientEventListeners.register(modBus);
            registered = true;
        }

        private static void registerFrontStress(FMLCommonSetupEvent event) {
            event.enqueueWork(() -> {
                BlockStressValues.IMPACTS.register(BuiltInRegistries.BLOCK.get(HexieConstants.asResource("df11g_front2")), () -> 2.0);
                BlockStressValues.IMPACTS.register(BuiltInRegistries.BLOCK.get(HexieConstants.asResource("hxd3d_front")), () -> 2.0);
            });
        }

        private static void registerRailwayPayloads(RegisterPayloadHandlersEvent event) {
            var registrar = event.registrar("16");
            registrar.playToServer(TrackBallastPacket.TYPE, TrackBallastPacket.CODEC, TrackBallastPacket::handle);
            registrar.playToServer(SignalPlacementFacingPacket.TYPE, SignalPlacementFacingPacket.CODEC, SignalPlacementFacingPacket::handle);
        }

        private static void registerCarriageAlias(RegisterEvent event) {
            if (event.getRegistryKey().equals(Registries.ENTITY_TYPE)) {
                BuiltInRegistries.ENTITY_TYPE.addAlias(HexieConstants.asResource("assembled_carriage"),
                        HexieConstants.asResource("create_assembled_carriage"));
            }
        }
    }

    private static final class ClientEventListeners {
        private static void register(IEventBus modBus) {
            modBus.register(CreateControlInputClient.class);
            NeoForge.EVENT_BUS.register(CreatePanelClipboardClient.class);
            NeoForge.EVENT_BUS.register(CreatePanelEditorClient.class);
            if (ModList.get().isLoaded("railx")) modBus.register(RailxBallastModels.class);
        }
    }
}
