package willow.train.hexie.network.c2s;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.Main;
import willow.train.hexie.compat.create.CarriageChunkAccess;
import willow.train.hexie.compat.create.CarriagePlayerSupport;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;
import willow.train.hexie.vehicle.player.CarriedPlayerConnection;

/** Client-resolved local feet while attached, followed by one world-space exit report. */
public record CarriagePlayerMovePacket(int carriageId, UUID carriageUuid, Vec3 position, boolean attached,
        float yaw, float pitch, boolean onGround) implements CustomPacketPayload {
    public static final Type<CarriagePlayerMovePacket> TYPE = new Type<>(Main.asResource("carriage_player_move"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CarriagePlayerMovePacket> CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeVarInt(p.carriageId); b.writeUUID(p.carriageUuid); b.writeVec3(p.position);
                b.writeBoolean(p.attached); b.writeFloat(p.yaw); b.writeFloat(p.pitch); b.writeBoolean(p.onGround);
            },
            b -> new CarriagePlayerMovePacket(b.readVarInt(), b.readUUID(), b.readVec3(), b.readBoolean(),
                    b.readFloat(), b.readFloat(), b.readBoolean()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> { if (context.player() instanceof ServerPlayer player) apply(player); });
    }

    public void apply(ServerPlayer player) {
        if (carriageId < 0 || !finite(position) || !Float.isFinite(yaw) || !Float.isFinite(pitch)
                || !player.isAlive() || player.isRemoved() || player.isPassenger() || player.wonGame
                || player.isSleeping() || player.isChangingDimension()
                || !(player.connection instanceof CarriedPlayerConnection connection) || !connection.hexie$canCarry()) return;

        SnapshotCarriageEntity owner = CarriagePlayerSupport.attachedCarriage(player);
        if (!attached) {
            // Sync or lifecycle cleanup can already have removed Standing. The last
            // accepted identity still permits exactly one exit, until any vanilla
            // movement or server teleport revokes it.
            boolean owned = owner != null && owner.getId() == carriageId && owner.getUUID().equals(carriageUuid);
            if ((!owned && !connection.hexie$wasCarriedBy(carriageId, carriageUuid)) || !worldPosition(position)) return;
            var terrain = CarriageChunkAccess.enter(owner == null ? player : owner);
            try {
                if (connection.hexie$acceptCarriageMovement(position, Vec3.ZERO, Vec3.ZERO, yaw, pitch, onGround)) {
                    CarriagePlayerSupport.detach(player);
                    connection.hexie$forgetCarriage();
                }
            } finally { CarriageChunkAccess.exit(terrain); }
            return;
        }

        if (player.isSpectator() || player.getAbilities().flying || player.isFallFlying()) return;
        SnapshotCarriageEntity car = player.level().getEntity(carriageId) instanceof SnapshotCarriageEntity found
                && found.getUUID().equals(carriageUuid) ? found : null;
        // Occupied entities can temporarily leave the terrain entity lookup. Only
        // the already-owned identity may bridge that lifecycle gap.
        if (car == null && owner != null && owner.getId() == carriageId && owner.getUUID().equals(carriageUuid))
            car = owner;
        if (car == null || car.isRemoved() || car.level() != player.level() || car.getContraption() == null) return;

        var frame = CarriagePlayerSupport.frame(car, 1);
        Vec3 world = frame.world(position);
        if (!worldPosition(world)) return;
        // A seat handoff owns the body but has no earlier client-local feet. Its first
        // position is placement, not a walking/jumping delta from the old world position.
        Vec3 localMovement = CarriagePlayerSupport.carriage(player) == car
                ? position.subtract(CarriagePlayerSupport.localPosition(player)) : Vec3.ZERO;
        Vec3 worldMovement = frame.x().scale(localMovement.x).add(frame.y().scale(localMovement.y)).add(frame.z().scale(localMovement.z));
        if (!finite(localMovement) || !finite(worldMovement)) return;
        var terrain = CarriageChunkAccess.enter(car);
        try {
            if (connection.hexie$acceptCarriageMovement(world, localMovement, worldMovement, yaw, pitch, onGround)) {
                CarriagePlayerSupport.accept(player, car, position);
                connection.hexie$rememberCarriage(carriageId, carriageUuid);
            }
        } finally { CarriageChunkAccess.exit(terrain); }
    }

    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    private static boolean worldPosition(Vec3 value) {
        // Keep the vanilla coordinate domain without performing movement validation.
        return finite(value) && Math.abs(value.x) <= 3.0E7 && Math.abs(value.y) <= 2.0E7 && Math.abs(value.z) <= 3.0E7;
    }
}
