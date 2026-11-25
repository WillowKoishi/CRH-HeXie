package willow.train.hexie.sync.channel.vehicle;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import willow.train.hexie.sync.engine.SyncMessage.GetBody;

public record GetBodyPayload(GetBody message) implements CustomPacketPayload {
    public static final Type<GetBodyPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("hexie", "get_body"));
    public static final StreamCodec<FriendlyByteBuf, GetBodyPayload> CODEC = StreamCodec.ofMember(
            GetBodyPayload::write, b -> new GetBodyPayload(new GetBody(
                    b.readUUID(), b.readVarLong(), b.readVarLong())));

    private void write(FriendlyByteBuf b) {
        b.writeUUID(message.carriageId());
        b.writeVarLong(message.interestSeq());
        b.writeVarLong(message.haveContentRevision());
    }

    @Override public Type<GetBodyPayload> type() { return TYPE; }
}
