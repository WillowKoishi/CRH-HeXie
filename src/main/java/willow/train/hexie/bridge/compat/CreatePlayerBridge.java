package willow.train.hexie.bridge.compat;

import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.vehicle.PlayerCarriageBridge;

public interface CreatePlayerBridge {
    CreateSeatOperations seats();

    interface CreateSeatOperations {
        Entity createHexieSeatEntity(EntityType<?> type, Level level);
        boolean isHexieSeat(Entity entity);

        Block createSeatYZ(BlockBehaviour.Properties properties);

        Block createRZSeat(BlockBehaviour.Properties properties);

        Block createCabSeat(BlockBehaviour.Properties properties);

        Block createDriverSeat(BlockBehaviour.Properties properties);
    }

    void queueClientRestore(Player player, int id, UUID uuid, Vec3 local, long token);

    void reportClientPlacement(Player player, int carriageId, UUID carriageUuid, long token);

    void startClientFollowing(Player player, SnapshotCarriageView car, Vec3 local, long restoreToken);

    PlayerCarriageBridge.SupportFrameView frame(SnapshotCarriageView carriage, float partial);

    SnapshotCarriageView carriage(Player player);

    SnapshotCarriageView mountedOrStanding(Player player);

    SnapshotCarriageView attachedCarriage(Player player);

    void detach(Player player);

    Vec3 localPosition(Player player);

    void accept(Player player, SnapshotCarriageView car, Vec3 local);

    Vec3 renderOffset(Player player, float partial);

    Vec3 renderOffset(Player player, float partial, Vec3 vanillaPosition);

    void receiveRemote(Level level, int playerId, int carriageId, UUID carriageUuid, Vec3 local, long sampleTick);

    void resolveRemote(Level level);

    void beginClientTick();

    void placeRemote(Player player);
}
