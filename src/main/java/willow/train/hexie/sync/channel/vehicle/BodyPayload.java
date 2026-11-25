package willow.train.hexie.sync.channel.vehicle;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import willow.train.hexie.sync.engine.SyncMessage.Body;

public record BodyPayload(Body message) implements CustomPacketPayload {
    public static final Type<BodyPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("hexie", "body"));
    public static final StreamCodec<FriendlyByteBuf, BodyPayload> CODEC = StreamCodec.ofMember(
            BodyPayload::write, b -> new BodyPayload(new Body(
                    b.readUUID(), b.readVarLong(), b.readVarLong(), b.readByteArray())));

    private void write(FriendlyByteBuf b) {
        b.writeUUID(message.carriageId());
        b.writeVarLong(message.interestSeq());
        b.writeVarLong(message.contentRevision());
        b.writeByteArray(message.cells());
    }

    @Override public Type<BodyPayload> type() { return TYPE; }
}
