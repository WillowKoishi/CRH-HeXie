package willow.train.hexie.sync.channel.vehicle;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import willow.train.hexie.sync.engine.SyncMessage.Interest;

public record InterestPayload(Interest message) implements CustomPacketPayload {
    public static final Type<InterestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("hexie", "interest"));
    public static final StreamCodec<FriendlyByteBuf, InterestPayload> CODEC = StreamCodec.ofMember(
            InterestPayload::write, b -> new InterestPayload(new Interest(
                    b.readUUID(), b.readVarLong(), b.readBoolean())));

    private void write(FriendlyByteBuf b) {
        b.writeUUID(message.carriageId());
        b.writeVarLong(message.interestSeq());
        b.writeBoolean(message.tracking());
    }

    @Override public Type<InterestPayload> type() { return TYPE; }
}
