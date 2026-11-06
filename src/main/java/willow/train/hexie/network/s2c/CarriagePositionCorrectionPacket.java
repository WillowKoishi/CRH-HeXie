package willow.train.hexie.network.s2c;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.Main;
import willow.train.hexie.compat.create.CarriagePlayerSupport;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;

/** A vanilla position confirmation whose authoritative destination is on a moving carriage. */
public record CarriagePositionCorrectionPacket(int carriageId, UUID carriageUuid, Vec3 feet,
        ClientboundPlayerPositionPacket correction) implements CustomPacketPayload {
    public static final Type<CarriagePositionCorrectionPacket> TYPE = new Type<>(Main.asResource("carriage_position_correction"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CarriagePositionCorrectionPacket> CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeVarInt(packet.carriageId);
                buf.writeUUID(packet.carriageUuid);
                buf.writeVec3(packet.feet);
                ClientboundPlayerPositionPacket.STREAM_CODEC.encode(buf, packet.correction);
            },
            buf -> new CarriagePositionCorrectionPacket(buf.readVarInt(), buf.readUUID(), buf.readVec3(),
                    ClientboundPlayerPositionPacket.STREAM_CODEC.decode(buf)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (!(player.level().getEntity(carriageId) instanceof SnapshotCarriageEntity car)
                    || !car.getUUID().equals(carriageUuid) || player.isPassenger()) {
                correction.handle((ClientGamePacketListener) context.connection().getPacketListener());
                return;
            }
            Vec3 world = CarriagePlayerSupport.placeStanding(player, car, feet);
            player.xOld = player.xo = world.x;
            player.yOld = player.yo = world.y;
            player.zOld = player.zo = world.z;
            player.setDeltaMovement(Vec3.ZERO);
            player.setYRot(correction.getYRot());
            player.setXRot(correction.getXRot());
            player.yRotO = player.getYRot();
            player.xRotO = player.getXRot();
            context.connection().send(new ServerboundAcceptTeleportationPacket(correction.getId()));
            // The next normal movement report carries local feet. An absolute echo from
            // this client's older carriage pose would immediately trigger another correction.
        });
    }
}
