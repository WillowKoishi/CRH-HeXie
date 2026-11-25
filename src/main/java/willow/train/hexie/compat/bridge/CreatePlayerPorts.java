package willow.train.hexie.compat.bridge;

import java.util.UUID;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.compat.CreatePlayerBridge;
import willow.train.hexie.bridge.compat.SnapshotCarriageView;
import willow.train.hexie.bridge.vehicle.PlayerCarriageBridge;
import willow.train.hexie.compat.create.motion.SnapshotCarriageEntity;
import willow.train.hexie.compat.create.player.CarriageClientPlacement;
import willow.train.hexie.compat.create.player.CarriagePlayerSupport;
import willow.train.hexie.compat.create.seat.CreateSeats;

final class CreatePlayerPorts implements CreatePlayerBridge {
    @Override public CreateSeatOperations seats() { return CreateSeats.INSTANCE; }

    @Override public void queueClientRestore(Player player, int id, UUID uuid, Vec3 local, long token) {
        CarriageClientPlacement.queueClientRestore(player, id, uuid, local, token);
    }

    @Override public void reportClientPlacement(Player player, int carriageId, UUID carriageUuid, long token) {
        CarriageClientPlacement.reportClientPlacement(player, carriageId, carriageUuid, token);
    }

    @Override public void startClientFollowing(Player player, SnapshotCarriageView car, Vec3 local, long restoreToken) {
        CarriageClientPlacement.startClientFollowing(player, (SnapshotCarriageEntity) car, local, restoreToken);
    }

    @Override public PlayerCarriageBridge.SupportFrameView frame(SnapshotCarriageView carriage, float partial) {
        return CarriagePlayerSupport.frame((SnapshotCarriageEntity) carriage, partial);
    }

    @Override public SnapshotCarriageView carriage(Player player) {
        return CarriagePlayerSupport.carriage(player);
    }

    @Override public SnapshotCarriageView mountedOrStanding(Player player) {
        return CarriagePlayerSupport.mountedOrStanding(player);
    }

    @Override public SnapshotCarriageView attachedCarriage(Player player) {
        return CarriagePlayerSupport.attachedCarriage(player);
    }

    @Override public void detach(Player player) {
        CarriagePlayerSupport.detach(player);
    }

    @Override public Vec3 localPosition(Player player) {
        return CarriagePlayerSupport.localPosition(player);
    }

    @Override public void accept(Player player, SnapshotCarriageView car, Vec3 local) {
        CarriagePlayerSupport.accept(player, (SnapshotCarriageEntity) car, local);
    }

    @Override public Vec3 renderOffset(Player player, float partial) {
        return CarriageClientPlacement.renderOffset(player, partial);
    }

    @Override public Vec3 renderOffset(Player player, float partial, Vec3 vanillaPosition) {
        return CarriageClientPlacement.renderOffset(player, partial, vanillaPosition);
    }

    @Override public void receiveRemote(Level level,
                    int playerId,
                    int carriageId,
                    UUID carriageUuid,
                    Vec3 local,
                    long sampleTick) {
        CarriageClientPlacement.receiveRemote(level, playerId, carriageId, carriageUuid, local, sampleTick);
    }

    @Override public void resolveRemote(Level level) {
        CarriageClientPlacement.resolveRemote(level);
    }

    @Override public void beginClientTick() {
        CarriageClientPlacement.beginClientTick();
    }

    @Override public void placeRemote(Player player) {
        CarriageClientPlacement.placeRemote(player);
    }
}
