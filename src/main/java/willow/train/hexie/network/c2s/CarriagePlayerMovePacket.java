package willow.train.hexie.network.c2s;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.Main;
import willow.train.hexie.compat.create.CarriagePlayerSupport;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;
import willow.train.hexie.network.s2c.CarriagePlayerStatePacket;
import willow.train.hexie.vehicle.player.CarriedPlayerConnection;

/** Replaces, rather than supplements, the corresponding vanilla movement packet. */
public record CarriagePlayerMovePacket(int carriageId, UUID carriageUuid, Vec3 position,
        float yaw, float pitch, boolean onGround) implements CustomPacketPayload {
    public static final Type<CarriagePlayerMovePacket> TYPE = new Type<>(Main.asResource("carriage_player_move"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CarriagePlayerMovePacket> CODEC = StreamCodec.of(
            (b, p) -> { b.writeVarInt(p.carriageId); b.writeUUID(p.carriageUuid);
                b.writeDouble(p.position.x); b.writeDouble(p.position.y); b.writeDouble(p.position.z);
                b.writeFloat(p.yaw); b.writeFloat(p.pitch); b.writeBoolean(p.onGround); },
            b -> new CarriagePlayerMovePacket(b.readVarInt(), b.readUUID(),
                    new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), b.readFloat(), b.readFloat(), b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> { if (context.player() instanceof ServerPlayer player) apply(player); });
    }
    public void apply(ServerPlayer player) {
        if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch) || player.isPassenger()) return;
        if (player.connection instanceof CarriedPlayerConnection c && !c.hexie$canCarry()) return;
        SnapshotCarriageEntity car = carriageId < 0 ? null
                : player.level().getEntity(carriageId) instanceof SnapshotCarriageEntity found
                    && found.getUUID().equals(carriageUuid) ? found : null;
        if (carriageId >= 0 && car == null) { CarriagePlayerSupport.detach(player); return; }
        if (car != null) CarriagePlayerSupport.advance(car);
        else CarriagePlayerSupport.detach(player);
        Vec3 world = car == null ? position : CarriagePlayerSupport.frame(car, 1).world(position);
        // Vanilla still checks the player's own movement and world collisions. Only known
        // platform displacement was added to its baselines by advance(), never the requested walk.
        player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.PosRot(
                world.x, world.y, world.z, yaw, pitch, onGround));
        boolean accepted = player.position().distanceToSqr(world) < 1e-6;
        if (accepted && car != null && !player.getAbilities().flying && !player.isFallFlying()
                && !player.isSpectator() && !CarriagePlayerSupport.touchesWorld(player)
                && (CarriagePlayerSupport.carriage(player) == car || CarriagePlayerSupport.touchesCarriage(car, player))) {
            CarriagePlayerSupport.accept(player, car, position);
            if (player.connection instanceof CarriedPlayerConnection connection) connection.hexie$grounded();
        }
        // A corrected movement packet does not end an established attachment.
        // carriage() clears it on world contact, flight or invalid lifecycle state.
        SnapshotCarriageEntity support = CarriagePlayerSupport.carriage(player);
        PacketDistributor.sendToPlayersTrackingEntity(player, new CarriagePlayerStatePacket(player.getId(),
                support == null ? -1 : support.getId(), support == null ? new UUID(0, 0) : support.getUUID(),
                support == null ? player.position() : CarriagePlayerSupport.localPosition(player)));
    }
}
