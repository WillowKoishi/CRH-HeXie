package willow.train.hexie.mixins.mixin;

import java.util.stream.Stream;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import willow.train.hexie.compat.create.CarriagePlayerSupport;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;
import willow.train.hexie.vehicle.player.CarriedPlayerConnection;

/** Leave client-resolved seat exits out of the tracker's redundant world teleport. */
@Mixin(ServerEntity.class)
public abstract class MixinCarriageDismountTracking {
    @Shadow @Final private Entity entity;

    @ModifyExpressionValue(method = "sendChanges", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerEntity;removedPassengers(Ljava/util/List;Ljava/util/List;)Ljava/util/stream/Stream;"))
    private Stream<Entity> hexie$clientResolvedSeatExit(Stream<Entity> removed) {
        if (!(entity instanceof SnapshotCarriageEntity car)) return removed;
        // sendChanges has already broadcast the actual passenger list. Its following
        // forEach only teleports departing players to their server position; that would
        // clear our pending local exit on both sides before the client can place it.
        // Filter only this call site: real teleports still cancel carriage following.
        return removed.filter(passenger -> !(passenger instanceof ServerPlayer player)
                || !(CarriagePlayerSupport.attachedCarriage(player) == car
                || player.connection instanceof CarriedPlayerConnection connection
                && connection.hexie$wasCarriedBy(car.getId(), car.getUUID())));
    }
}
