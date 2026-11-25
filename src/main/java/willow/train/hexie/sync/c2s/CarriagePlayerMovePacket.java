package willow.train.hexie.sync.c2s;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.SnapshotCarriageView;
import willow.train.hexie.bridge.vehicle.PlayerCarriageBridge.CarriedPlayerConnectionView;
import willow.train.hexie.core.HexieConstants;

/** Client-resolved local feet while attached, followed by one world-space exit report. */
public record CarriagePlayerMovePacket(int carriageId, UUID carriageUuid, Vec3 position, boolean attached,
        float yaw, float pitch, boolean onGround) implements CustomPacketPayload {
    public static final Type<CarriagePlayerMovePacket> TYPE = new Type<>(HexieConstants.asResource("carriage_player_move"));
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
                || !(player.connection instanceof CarriedPlayerConnectionView connection) || !connection.hexie$canCarry()) return;

        SnapshotCarriageView owner = HexieBridges.createPlayer().attachedCarriage(player);
        if (!attached) {
            // Sync or lifecycle cleanup can already have removed Standing. The last
            // accepted identity still permits exactly one exit, until any vanilla
            // movement or server teleport revokes it.
            boolean owned = owner != null && owner.asEntity().getId() == carriageId && owner.asEntity().getUUID().equals(carriageUuid);
            if ((!owned && !connection.hexie$wasCarriedBy(carriageId, carriageUuid)) || !worldPosition(position)) return;
            var terrain = HexieBridges.createCarriage().carriageChunkAccess().enter(owner == null ? player : owner.asEntity());
            try {
                if (connection.hexie$acceptCarriageMovement(position, Vec3.ZERO, Vec3.ZERO, yaw, pitch, onGround)) {
                    HexieBridges.createPlayer().detach(player);
                    connection.hexie$forgetCarriage();
                }
            } finally { HexieBridges.createCarriage().carriageChunkAccess().exit(terrain); }
            return;
        }

        if (player.isSpectator() || player.getAbilities().flying || player.isFallFlying()) return;
        SnapshotCarriageView car = player.level().getEntity(carriageId) instanceof SnapshotCarriageView found
                && found.asEntity().getUUID().equals(carriageUuid) ? found : null;
        // Occupied entities can temporarily leave the terrain entity lookup. Only
        // the already-owned identity may bridge that lifecycle gap.
        if (car == null && owner != null && owner.asEntity().getId() == carriageId && owner.asEntity().getUUID().equals(carriageUuid))
            car = owner;
        if (car == null || car.asEntity().isRemoved() || car.asEntity().level() != player.level() || car.asEntity().getContraption() == null) return;

        var frame = HexieBridges.createPlayer().frame(car, 1);
        Vec3 world = frame.world(position);
        if (!worldPosition(world)) return;
        // A seat handoff owns the body but has no earlier client-local feet. Its first
        // position is placement, not a walking/jumping delta from the old world position.
        Vec3 localMovement = HexieBridges.createPlayer().carriage(player) == car
                ? position.subtract(HexieBridges.createPlayer().localPosition(player)) : Vec3.ZERO;
        Vec3 worldMovement = frame.x().scale(localMovement.x).add(frame.y().scale(localMovement.y)).add(frame.z().scale(localMovement.z));
        if (!finite(localMovement) || !finite(worldMovement)) return;
        var terrain = HexieBridges.createCarriage().carriageChunkAccess().enter(car.asEntity());
        try {
            if (connection.hexie$acceptCarriageMovement(world, localMovement, worldMovement, yaw, pitch, onGround)) {
                HexieBridges.createPlayer().accept(player, car, position);
                connection.hexie$rememberCarriage(carriageId, carriageUuid);
            }
        } finally { HexieBridges.createCarriage().carriageChunkAccess().exit(terrain); }
    }

    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    private static boolean worldPosition(Vec3 value) {
        // Keep the vanilla coordinate domain without performing movement validation.
        return finite(value) && Math.abs(value.x) <= 3.0E7 && Math.abs(value.y) <= 2.0E7 && Math.abs(value.z) <= 3.0E7;
    }
}
