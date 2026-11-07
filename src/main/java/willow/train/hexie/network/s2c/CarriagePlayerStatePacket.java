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

public record CarriagePlayerStatePacket(int playerId, int carriageId, UUID carriageUuid, Vec3 local,
        boolean restore, long sampleTick, long restoreToken)
        implements CustomPacketPayload {
    public CarriagePlayerStatePacket(int playerId, int carriageId, UUID carriageUuid, Vec3 local) {
        this(playerId, carriageId, carriageUuid, local, false, -1, 0);
    }
    public CarriagePlayerStatePacket(int playerId, int carriageId, UUID carriageUuid, Vec3 local, boolean restore) {
        this(playerId, carriageId, carriageUuid, local, restore, -1, 0);
    }
    public CarriagePlayerStatePacket(int playerId, int carriageId, UUID carriageUuid, Vec3 local,
            boolean restore, long sampleTick) {
        this(playerId, carriageId, carriageUuid, local, restore, sampleTick, 0);
    }
    public static final Type<CarriagePlayerStatePacket> TYPE = new Type<>(Main.asResource("carriage_player_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CarriagePlayerStatePacket> CODEC = StreamCodec.of(
            (b, p) -> { b.writeVarInt(p.playerId); b.writeVarInt(p.carriageId); b.writeUUID(p.carriageUuid);
                b.writeDouble(p.local.x); b.writeDouble(p.local.y); b.writeDouble(p.local.z); b.writeBoolean(p.restore);
                b.writeVarLong(p.sampleTick);
                if (p.restore) b.writeVarLong(p.restoreToken); },
            b -> {
                int playerId = b.readVarInt(), carriageId = b.readVarInt();
                UUID carriageUuid = b.readUUID();
                Vec3 local = new Vec3(b.readDouble(), b.readDouble(), b.readDouble());
                boolean restore = b.readBoolean();
                long sampleTick = b.readVarLong();
                return new CarriagePlayerStatePacket(playerId, carriageId, carriageUuid, local,
                        restore, sampleTick, restore ? b.readVarLong() : 0);
            });
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            var level = context.player().level();
            if (level.getEntity(playerId) instanceof Player player && player.isLocalPlayer()) {
                if (restore) CarriagePlayerSupport.queueClientRestore(player, carriageId, carriageUuid, local, restoreToken);
                return;
            }
            CarriagePlayerSupport.receiveRemote(level, playerId, carriageId, carriageUuid, local,
                    sampleTick >= 0 ? sampleTick : level.getGameTime());
        });
    }
}
