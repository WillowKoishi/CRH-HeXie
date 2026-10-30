package willow.train.hexie.network.s2c;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.Main;
import willow.train.hexie.vehicle.assembly.CouplerDataRegistry;

public record CouplerDefinitionsPacket(String json) implements CustomPacketPayload {
    public static final Type<CouplerDefinitionsPacket> TYPE = new Type<>(Main.asResource("coupler_definitions"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CouplerDefinitionsPacket> CODEC = StreamCodec.of(
            (buffer, packet) -> buffer.writeUtf(packet.json, 262144), buffer -> new CouplerDefinitionsPacket(buffer.readUtf(262144)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            CouplerDataRegistry.install(json);
            for (var reference : com.simibubi.create.content.contraptions.ContraptionHandler.loadedContraptions
                    .get(context.player().level()).values()) {
                if (reference.get() instanceof willow.train.hexie.compat.create.SnapshotCarriageEntity carriage
                        && carriage.getContraption() != null)
                    carriage.getContraption().invalidateClientContraptionStructure();
            }
        });
    }
}
