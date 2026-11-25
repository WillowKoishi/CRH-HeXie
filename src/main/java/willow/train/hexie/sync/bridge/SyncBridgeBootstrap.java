package willow.train.hexie.sync.bridge;

import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.minecraft.server.level.ServerPlayer;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.sync.cache.*;
import willow.train.hexie.sync.channel.vehicle.*;
import willow.train.hexie.sync.sticker.*;

public final class SyncBridgeBootstrap {
    private static final ServerVehicleSync server = new ServerVehicleSync();
    private static ClientVehicleCache client;

    private SyncBridgeBootstrap() {}

    public static void install() {
        HexieBridges.registerSync(new PacketDispatch(server));
        VehicleSyncEvents.install(server);
        StickerSyncEvents.install();
    }

    public static void installClient() {
        client = new ClientVehicleCache(get -> PacketDistributor.sendToServer(new GetBodyPayload(get)),
                key -> PacketDistributor.sendToServer(new GetRegionPayload(key)));
        HexieBridges.Client.registerClientSync(new ClientSeatPositions(client));
        ClientVehicleEvents.install(client);
    }

    public static void registerVehiclePayloads(RegisterPayloadHandlersEvent event) {
        var channel = event.registrar("1").optional();
        channel.playToClient(CompositionPayload.TYPE, CompositionPayload.CODEC, (p, c) -> client.receive(p.message()));
        channel.playToClient(InterestPayload.TYPE, InterestPayload.CODEC, (p, c) -> client.receive(p.message()));
        channel.playToClient(BodyPayload.TYPE, BodyPayload.CODEC, (p, c) -> client.receive(p.message()));
        channel.playToClient(CellPatchPayload.TYPE, CellPatchPayload.CODEC, (p, c) -> client.receive(p.message()));
        channel.playToServer(GetBodyPayload.TYPE, GetBodyPayload.CODEC, (p, c) -> server.request((ServerPlayer) c.player(), p.message()));
        channel.playToClient(RegionPayload.TYPE, RegionPayload.CODEC, (p, c) -> p.handle(client));
        channel.playToServer(GetRegionPayload.TYPE, GetRegionPayload.CODEC, (p, c) -> server.recover((ServerPlayer) c.player(), p.key()));
        channel.playToServer(StickerUpsertPacket.TYPE, StickerUpsertPacket.CODEC, (p, c) -> p.handle(c));
        channel.playToServer(StickerRemovePacket.TYPE, StickerRemovePacket.CODEC, (p, c) -> p.handle(c));
        channel.playToServer(StickerImageUploadPacket.TYPE, StickerImageUploadPacket.CODEC, (p, c) -> p.handle(c));
        channel.playToServer(StickerImageRequestPacket.TYPE, StickerImageRequestPacket.CODEC, (p, c) -> p.handle(c));
        channel.playToClient(StickerImageSyncPacket.TYPE, StickerImageSyncPacket.CODEC, (p, c) -> p.handle(c));
        channel.playToClient(StickerChunkSyncPacket.TYPE, StickerChunkSyncPacket.CODEC, (p, c) -> p.handle(c));
    }
}
