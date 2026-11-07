package willow.train.hexie.mixins.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Set;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import willow.train.hexie.compat.create.CarriagePlayerSupport;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class MixinCarriagePassengerTracking {
    @Shadow @Final private Entity entity;

    @WrapOperation(method = "updatePlayer", at = @At(value = "INVOKE",
            target = "Ljava/util/Set;remove(Ljava/lang/Object;)Z"))
    private boolean hexie$retainVisibleBoardingHistory(Set<?> observers, Object connection,
            Operation<Boolean> original, @Local(argsOnly = true) ServerPlayer player,
            @Local(ordinal = 0) double range) {
        // A delayed client can still board a pose inside the retained history. Do not
        // queue an untrack between that visible contact and its authoritative acceptance.
        // This only retains an existing pairing; real entity removal uses its own path.
        if (entity instanceof SnapshotCarriageEntity car && observers.contains(connection) && entity.broadcastToPlayer(player)
                && car.retainTrackingForBoarding(player, range)) return false;
        return original.call(observers, connection);
    }

    @WrapOperation(method = "updatePlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ChunkMap;isChunkTracked(Lnet/minecraft/server/level/ServerPlayer;II)Z"))
    private boolean hexie$trackOccupiedCarriage(ChunkMap map, ServerPlayer player, int x, int z,
            Operation<Boolean> original) {
        if (original.call(map, player, x, z)) return true;
        // The player still needs the carriage floor while its terrain chunk is queued for delivery.
        return entity instanceof SnapshotCarriageEntity car
                && CarriagePlayerSupport.attachedCarriage(player) == car;
    }
}
