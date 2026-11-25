package willow.train.hexie.sync.s2c;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.core.HexieConstants;

public record CouplerDefinitionsPacket(String json) implements CustomPacketPayload {
    public static final Type<CouplerDefinitionsPacket> TYPE = new Type<>(HexieConstants.asResource("coupler_definitions"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CouplerDefinitionsPacket> CODEC = StreamCodec.of(
            (buffer, packet) -> buffer.writeUtf(packet.json, 262144), buffer -> new CouplerDefinitionsPacket(buffer.readUtf(262144)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            HexieBridges.coupling().couplerDataRegistry().install(json);
            if (HexieBridges.modBootstrap().isCreateLoaded())
                HexieBridges.createCarriage().invalidateClientStructures(context.player().level());
        });
    }
}
