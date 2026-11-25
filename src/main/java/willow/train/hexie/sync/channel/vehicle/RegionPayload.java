package willow.train.hexie.sync.channel.vehicle;

import java.util.HashSet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import willow.train.hexie.bridge.sync.RegionData.*;
import willow.train.hexie.bridge.sync.VehicleSyncData.Id;
import willow.train.hexie.core.HexieConstants;
import willow.train.hexie.core.api.vehicle.Point3;
import willow.train.hexie.sync.cache.ClientVehicleCache;

public sealed interface RegionPayload extends CustomPacketPayload {
    Type<RegionPayload> TYPE = new Type<>(HexieConstants.asResource("region"));
    StreamCodec<FriendlyByteBuf, RegionPayload> CODEC = StreamCodec.ofMember(RegionPayload::write, RegionPayload::read);

    record Snapshot(RegionSnapshot page, boolean subscribe) implements RegionPayload {}
    record Delta(RegionPatch patch) implements RegionPayload {}
    record Leave(RegionKey key) implements RegionPayload {}

    default void handle(ClientVehicleCache cache) {
        switch (this) {
            case Snapshot s -> {
                if (s.subscribe) cache.subscribeRegion(s.page);
                else cache.receive(s.page);
            }
            case Delta d -> cache.receive(d.patch);
            case Leave l -> cache.unsubscribeRegion(l.key);
        }
    }

    private void write(FriendlyByteBuf buf) {
        switch (this) {
            case Snapshot s -> {
                buf.writeByte(0);
                var page = s.page.page();
                buf.writeBoolean(s.subscribe);
                writeKey(buf, page.key());
                buf.writeVarLong(page.regionRevision());
                buf.writeCollection(page.edgeIds(), (b, id) -> b.writeLong(id));
                buf.writeCollection(page.boundary(), RegionPayload::writePort);
                buf.writeMap(s.page.edges(), (b, id) -> b.writeLong(id), RegionPayload::writeEdge);
            }
            case Delta d -> {
                buf.writeByte(1);
                var p = d.patch;
                writeKey(buf, p.key());
                buf.writeVarLong(p.baseRevision());
                buf.writeVarLong(p.newRevision());
                buf.writeMap(p.changed(), (b, id) -> b.writeLong(id), RegionPayload::writeEdge);
                buf.writeCollection(p.removed(), (b, id) -> b.writeLong(id));
                buf.writeOptional(p.boundary(), (b, ports) -> b.writeCollection(ports, RegionPayload::writePort));
            }
            case Leave l -> {
                buf.writeByte(2);
                writeKey(buf, l.key);
            }
        }
    }

    private static RegionPayload read(FriendlyByteBuf buf) {
        return switch (buf.readUnsignedByte()) {
            case 0 -> {
                boolean subscribe = buf.readBoolean();
                var page = new RegionPage(readKey(buf), buf.readVarLong(), buf.readList(b -> b.readLong()),
                        buf.readList(RegionPayload::readPort));
                yield new Snapshot(new RegionSnapshot(page, buf.readMap(b -> b.readLong(), RegionPayload::readEdge)), subscribe);
            }
            case 1 -> new Delta(new RegionPatch(readKey(buf), buf.readVarLong(), buf.readVarLong(),
                    buf.readMap(b -> b.readLong(), RegionPayload::readEdge), new HashSet<>(buf.readList(b -> b.readLong())),
                    buf.readOptional(b -> b.readList(RegionPayload::readPort))));
            case 2 -> new Leave(readKey(buf));
            default -> throw new IllegalArgumentException("Unknown railway region message");
        };
    }

    static void writeKey(FriendlyByteBuf buf, RegionKey key) {
        buf.writeUtf(key.dimensionId().namespace());
        buf.writeUtf(key.dimensionId().path());
        buf.writeLong(key.networkId());
        buf.writeInt(key.regionX());
        buf.writeInt(key.regionZ());
    }

    static RegionKey readKey(FriendlyByteBuf buf) {
        return new RegionKey(new Id(buf.readUtf(), buf.readUtf()), buf.readLong(), buf.readInt(), buf.readInt());
    }

    private static void writePort(FriendlyByteBuf buf, BoundaryPort port) {
        buf.writeLong(port.nodeId());
        writeKey(buf, port.neighbor());
    }

    private static BoundaryPort readPort(FriendlyByteBuf buf) { return new BoundaryPort(buf.readLong(), readKey(buf)); }

    private static void writeEdge(FriendlyByteBuf buf, EdgeGeometry edge) {
        buf.writeLong(edge.edgeId());
        buf.writeLong(edge.fromNode());
        buf.writeLong(edge.toNode());
        buf.writeVarLong(edge.geometryRevision());
        buf.writeCollection(edge.samples(), (b, p) -> {
            b.writeDouble(p.x());
            b.writeDouble(p.y());
            b.writeDouble(p.z());
        });
    }

    private static EdgeGeometry readEdge(FriendlyByteBuf buf) {
        return new EdgeGeometry(buf.readLong(), buf.readLong(), buf.readLong(), buf.readVarLong(),
                buf.readList(b -> new Point3(b.readDouble(), b.readDouble(), b.readDouble())));
    }

    @Override default Type<RegionPayload> type() { return TYPE; }
}
