package willow.train.hexie.mixins.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
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
            target = "Lnet/minecraft/server/level/ChunkMap;isChunkTracked(Lnet/minecraft/server/level/ServerPlayer;II)Z"))
    private boolean hexie$trackOccupiedCarriage(ChunkMap map, ServerPlayer player, int x, int z,
            Operation<Boolean> original) {
        if (original.call(map, player, x, z)) return true;
        // The player still needs the carriage floor while its terrain chunk is queued for delivery.
        return entity instanceof SnapshotCarriageEntity car && player.getChunkTrackingView().contains(x, z)
                && CarriagePlayerSupport.mountedOrStanding(player) == car;
    }
}
