package willow.train.hexie.sync.channel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.core.HexieConstants;
import willow.train.hexie.sync.bridge.SyncBridgeBootstrap;
import willow.train.hexie.sync.c2s.BlockControlPacket;
import willow.train.hexie.sync.c2s.CabControlPacket;
import willow.train.hexie.sync.c2s.CabKeyboardPacket;
import willow.train.hexie.sync.c2s.CarriagePlayerMovePacket;
import willow.train.hexie.sync.c2s.CarriageRestoreAckPacket;
import willow.train.hexie.sync.c2s.ControlInputPacket;
import willow.train.hexie.sync.c2s.DrivingConsoleExitPacket;
import willow.train.hexie.sync.c2s.ExtendedBlockInteractPacket;
import willow.train.hexie.sync.c2s.FloorHeightPacket;
import willow.train.hexie.sync.c2s.HexiePacket;
import willow.train.hexie.sync.c2s.ReleaseNativeControlsPacket;
import willow.train.hexie.sync.c2s.signs.CarriageMarkingUpdatePacket;
import willow.train.hexie.sync.c2s.signs.CarriageNoSignUpdatePacket;
import willow.train.hexie.sync.c2s.signs.CarriageNumberUpdatePacket;
import willow.train.hexie.sync.c2s.signs.CarriageTypeSignUpdatePacket;
import willow.train.hexie.sync.c2s.signs.GroundStationSignUpdatePacket;
import willow.train.hexie.sync.c2s.signs.HeadNumberUpdatePacket;
import willow.train.hexie.sync.c2s.signs.LacqueredBoardPacket;
import willow.train.hexie.sync.c2s.signs.PanelTextUpdatePacket;
import willow.train.hexie.sync.c2s.signs.SS7DNumberUpdatePacket;
import willow.train.hexie.sync.c2s.signs.TrainSpeedSignUpdatePacket;
import willow.train.hexie.sync.s2c.CRH1HeadAnimationPacket;
import willow.train.hexie.sync.s2c.CarriageNumberPacket;
import willow.train.hexie.sync.s2c.CarriagePlayerStatePacket;
import willow.train.hexie.sync.s2c.ContraptionSeatPositionPacket;
import willow.train.hexie.sync.s2c.ControlCapturePacket;
import willow.train.hexie.sync.s2c.CouplerDefinitionsPacket;
import willow.train.hexie.sync.s2c.NativeControlsStartedPacket;
import willow.train.hexie.sync.s2c.PanelEditPacket;
import willow.train.hexie.sync.s2c.PassengerDisplayPacket;

public final class HexieNetworkHandler {
    private static final List<Class<? extends HexiePacket>> TYPES = new ArrayList<>();
    private static final List<Function<FriendlyByteBuf, ? extends HexiePacket>> DECODERS = new ArrayList<>();

    public static void registerPackets(RegisterPayloadHandlersEvent event) {
        TYPES.clear();
        DECODERS.clear();
        register(CarriageTypeSignUpdatePacket.class, CarriageTypeSignUpdatePacket::new);
        register(CarriageNoSignUpdatePacket.class, CarriageNoSignUpdatePacket::new);
        register(LacqueredBoardPacket.class, LacqueredBoardPacket::new);
        register(TrainSpeedSignUpdatePacket.class, TrainSpeedSignUpdatePacket::new);
        register(GroundStationSignUpdatePacket.class, GroundStationSignUpdatePacket::new);
        register(DrivingConsoleExitPacket.class, DrivingConsoleExitPacket::new);
        register(CabControlPacket.class, CabControlPacket::new);
        register(BlockControlPacket.class, BlockControlPacket::new);
        register(HeadNumberUpdatePacket.class, HeadNumberUpdatePacket::new);
        register(PanelTextUpdatePacket.class, PanelTextUpdatePacket::new);
        register(CarriageMarkingUpdatePacket.class, CarriageMarkingUpdatePacket::new);
        register(CabKeyboardPacket.class, CabKeyboardPacket::new);
        register(ReleaseNativeControlsPacket.class, ReleaseNativeControlsPacket::new);
        register(CarriageNumberUpdatePacket.class, CarriageNumberUpdatePacket::new);
        register(ControlInputPacket.class, ControlInputPacket::new);
        register(SS7DNumberUpdatePacket.class, SS7DNumberUpdatePacket::new);
        var registrar = event.registrar("16");
        registrar.playToClient(ControlCapturePacket.TYPE, ControlCapturePacket.CODEC,
                (packet, context) -> packet.handle(context));
        registrar.playToClient(PassengerDisplayPacket.TYPE, PassengerDisplayPacket.CODEC,
                (packet, context) -> packet.handle(context));
        registrar.playToClient(CarriageNumberPacket.TYPE, CarriageNumberPacket.CODEC,
                (packet, context) -> packet.handle(context));
        registrar.playToClient(CRH1HeadAnimationPacket.TYPE, CRH1HeadAnimationPacket.CODEC,
                (packet, context) -> packet.handle(context));
        registrar.playToClient(PanelEditPacket.TYPE, PanelEditPacket.CODEC,
                (packet, context) -> packet.handle(context));
        if (HexieBridges.modBootstrap().isCreateLoaded()) {
            HexieBridges.createPanel().registerPanelPayloads(registrar);
            registrar.playToServer(CarriagePlayerMovePacket.TYPE, CarriagePlayerMovePacket.CODEC,
                    (packet, context) -> packet.handle(context));
            registrar.playToServer(CarriageRestoreAckPacket.TYPE, CarriageRestoreAckPacket.CODEC,
                    (packet, context) -> packet.handle(context));
            registrar.playToClient(CarriagePlayerStatePacket.TYPE, CarriagePlayerStatePacket.CODEC,
                    (packet, context) -> packet.handle(context));
        }
        registrar.playToClient(NativeControlsStartedPacket.TYPE, NativeControlsStartedPacket.CODEC,
                (packet, context) -> packet.handle(context));
        registrar.playToClient(CouplerDefinitionsPacket.TYPE, CouplerDefinitionsPacket.CODEC,
                (packet, context) -> packet.handle(context));
        registrar.playToServer(FloorHeightPacket.TYPE, FloorHeightPacket.CODEC,
                (packet, context) -> packet.handle(context));
        registrar.playToServer(Payload.TYPE, Payload.CODEC,
                (payload, context) -> payload.packet().handle(context));
        registrar.playToClient(ContraptionSeatPositionPacket.TYPE, ContraptionSeatPositionPacket.CODEC,
                (packet, context) -> packet.handle(context));
        SyncBridgeBootstrap.registerVehiclePayloads(event);
        registrar.playToServer(ExtendedBlockInteractPacket.TYPE, ExtendedBlockInteractPacket.CODEC,
                ExtendedBlockInteractPacket::handle);
    }

    private static <T extends HexiePacket> void register(Class<T> type, Function<FriendlyByteBuf, T> decoder) {
        TYPES.add(type);
        DECODERS.add(decoder);
    }

    public static void sendToServer(HexiePacket packet) {
        PacketDistributor.sendToServer(new Payload(packet));
    }

    private record Payload(HexiePacket packet) implements CustomPacketPayload {
        private static final Type<Payload> TYPE = new Type<>(HexieConstants.asResource("main"));
        private static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    int id = TYPES.indexOf(payload.packet().getClass());
                    if (id < 0) throw new IllegalArgumentException("Unregistered HeXie packet: " + payload.packet().getClass());
                    buffer.writeVarInt(id);
                    payload.packet().encode(buffer);
                }, buffer -> {
                    int id = buffer.readVarInt();
                    if (id < 0 || id >= DECODERS.size()) throw new IllegalArgumentException("Unknown HeXie packet ID: " + id);
                    return new Payload(DECODERS.get(id).apply(buffer));
                });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
