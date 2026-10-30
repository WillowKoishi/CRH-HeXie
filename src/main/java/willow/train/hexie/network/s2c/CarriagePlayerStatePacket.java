package willow.train.hexie.network.s2c;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.Main;
import willow.train.hexie.compat.create.CarriagePlayerSupport;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;

public record CarriagePlayerStatePacket(int playerId, int carriageId, UUID carriageUuid, Vec3 local)
        implements CustomPacketPayload {
    public static final Type<CarriagePlayerStatePacket> TYPE = new Type<>(Main.asResource("carriage_player_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CarriagePlayerStatePacket> CODEC = StreamCodec.of(
            (b, p) -> { b.writeVarInt(p.playerId); b.writeVarInt(p.carriageId); b.writeUUID(p.carriageUuid);
                b.writeDouble(p.local.x); b.writeDouble(p.local.y); b.writeDouble(p.local.z); },
            b -> new CarriagePlayerStatePacket(b.readVarInt(), b.readVarInt(), b.readUUID(),
                    new Vec3(b.readDouble(), b.readDouble(), b.readDouble())));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            var level = context.player().level();
            if (!(level.getEntity(playerId) instanceof Player player) || player.isLocalPlayer()) return;
            if (level.getEntity(carriageId) instanceof SnapshotCarriageEntity car && car.getUUID().equals(carriageUuid))
                CarriagePlayerSupport.acceptRemote(player, car, local);
            else CarriagePlayerSupport.detach(player, false);
        });
    }
}
