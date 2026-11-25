package willow.train.hexie.sync.channel.vehicle;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import willow.train.hexie.bridge.sync.RegionData.RegionKey;
import willow.train.hexie.core.HexieConstants;

public record GetRegionPayload(RegionKey key) implements CustomPacketPayload {
    public static final Type<GetRegionPayload> TYPE = new Type<>(HexieConstants.asResource("get_region"));
    public static final StreamCodec<FriendlyByteBuf, GetRegionPayload> CODEC = StreamCodec.of(
            (buf, p) -> RegionPayload.writeKey(buf, p.key), buf -> new GetRegionPayload(RegionPayload.readKey(buf)));

    @Override public Type<GetRegionPayload> type() { return TYPE; }
}
