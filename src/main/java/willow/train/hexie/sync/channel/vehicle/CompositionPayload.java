package willow.train.hexie.sync.channel.vehicle;

import java.util.*;
import willow.train.hexie.sync.engine.TrainComposition;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import willow.train.hexie.sync.engine.SyncMessage.Composition;

public record CompositionPayload(Composition message) implements CustomPacketPayload {
    public static final Type<CompositionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("hexie", "composition"));
    public static final StreamCodec<FriendlyByteBuf, CompositionPayload> CODEC = StreamCodec.ofMember(
            CompositionPayload::write, b -> new CompositionPayload(new Composition(
                    b.readVarLong(), b.readList(CompositionPayload::readTrain), b.readList(buf -> buf.readUUID()))));

    private void write(FriendlyByteBuf b) {
        b.writeVarLong(message.compositionRevision());
        b.writeCollection(message.changed(), CompositionPayload::writeTrain);
        b.writeCollection(message.removed(), (buf, id) -> buf.writeUUID(id));
    }

    private static TrainComposition readTrain(FriendlyByteBuf b) {
        var train = b.readUUID();
        var ids = new ArrayList<UUID>();
        var reversed = new ArrayList<Boolean>();
        var revisions = new ArrayList<Long>();
        int count = b.readVarInt();
        for (int i=0; i<count; i++) {
            ids.add(b.readUUID());
            reversed.add(b.readBoolean());
            revisions.add(b.readVarLong());
        }
        return new TrainComposition(train, ids, reversed, revisions);
    }

    private static void writeTrain(FriendlyByteBuf b, TrainComposition train) {
        b.writeUUID(train.trainId());
        b.writeVarInt(train.orderedCarriages().size());
        for (int i=0; i<train.orderedCarriages().size(); i++) {
            b.writeUUID(train.orderedCarriages().get(i));
            b.writeBoolean(train.reversed().get(i));
            b.writeVarLong(train.contentRevisions().get(i));
        }
    }
    @Override public Type<CompositionPayload> type() { return TYPE; }
}
