package willow.train.hexie.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import willow.train.hexie.Main;
import willow.train.hexie.network.c2s.*;
import willow.train.hexie.network.c2s.signs.*;
import willow.train.hexie.network.s2c.ContraptionSeatPositionPacket;


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
        register(InventoryUpdatePacket.class, InventoryUpdatePacket::new);
        register(GroundStationSignUpdatePacket.class, GroundStationSignUpdatePacket::new);
        var registrar = event.registrar("2");
        registrar.playToServer(SignalPlacementFacingPacket.TYPE, SignalPlacementFacingPacket.CODEC,
                (packet, context) -> packet.handle(context));
        registrar.playToServer(Payload.TYPE, Payload.CODEC,
                (payload, context) -> payload.packet().handle(context));
        registrar.playToServer(
                willow.train.hexie.network.c2s.SeatYZContraptionInteractionPacket.TYPE,
                willow.train.hexie.network.c2s.SeatYZContraptionInteractionPacket.CODEC,
                (packet, context) -> packet.handle(context));
        registrar.playToClient(
                ContraptionSeatPositionPacket.TYPE,
                ContraptionSeatPositionPacket.CODEC,
                (packet, context) -> packet.handle(context));
    }

    private static <T extends HexiePacket> void register(Class<T> type, Function<FriendlyByteBuf, T> decoder) {
        TYPES.add(type);
        DECODERS.add(decoder);
    }

    public static void sendToServer(HexiePacket packet) {
        PacketDistributor.sendToServer(new Payload(packet));
    }

    private record Payload(HexiePacket packet) implements CustomPacketPayload {
        private static final Type<Payload> TYPE = new Type<>(Main.asResource("main"));
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
