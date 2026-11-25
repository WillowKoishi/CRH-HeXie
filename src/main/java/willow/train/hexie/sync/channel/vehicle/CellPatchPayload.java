package willow.train.hexie.sync.channel.vehicle;

import static java.nio.charset.StandardCharsets.UTF_8;
import willow.train.hexie.bridge.sync.VehicleSyncData.Cell;
import willow.train.hexie.sync.engine.SyncMessage.CellChange;
import willow.train.hexie.bridge.sync.VehicleSyncData.BlockData;
import willow.train.hexie.bridge.sync.VehicleSyncData.Id;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import willow.train.hexie.sync.engine.SyncMessage.CellPatch;

public record CellPatchPayload(CellPatch message) implements CustomPacketPayload {
    public static final Type<CellPatchPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("hexie", "cell_patch"));
    public static final StreamCodec<FriendlyByteBuf, CellPatchPayload> CODEC = StreamCodec.ofMember(
            CellPatchPayload::write, b -> new CellPatchPayload(new CellPatch(
                    b.readUUID(), b.readVarLong(), b.readVarLong(), b.readVarLong(), b.readList(CellPatchPayload::readChange))));

    private void write(FriendlyByteBuf b) {
        b.writeUUID(message.carriageId());
        b.writeVarLong(message.interestSeq());
        b.writeVarLong(message.baseRevision());
        b.writeVarLong(message.newRevision());
        b.writeCollection(message.changes(), CellPatchPayload::writeChange);
    }

    private static Id readId(FriendlyByteBuf buf) {
        var id = buf.readResourceLocation();
        return new Id(id.getNamespace(), id.getPath());
    }

    private static CellChange readChange(FriendlyByteBuf b) {
        var cell = new Cell(b.readInt(), b.readInt(), b.readInt());
        var block = b.readOptional(buf -> new BlockData(readId(buf),
                buf.readMap(in -> in.readUtf(), in -> in.readUtf()),
                buf.readOptional(in -> new String(in.readByteArray(), UTF_8))));
        return new CellChange(cell, block);
    }

    private static void writeChange(FriendlyByteBuf b, CellChange change) {
        b.writeInt(change.cell().x());
        b.writeInt(change.cell().y());
        b.writeInt(change.cell().z());
        b.writeOptional(change.block(), (buf, block) -> {
            buf.writeResourceLocation(ResourceLocation.fromNamespaceAndPath(block.blockId().namespace(), block.blockId().path()));
            buf.writeMap(block.properties(), (out, k) -> out.writeUtf(k), (out, v) -> out.writeUtf(v));
            buf.writeOptional(block.blockEntityData(), (out, text) -> out.writeByteArray(text.getBytes(UTF_8)));
        });
    }
    @Override public Type<CellPatchPayload> type() { return TYPE; }
}
