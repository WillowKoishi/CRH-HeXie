package willow.train.hexie.mixins.mixin;

import java.util.LinkedHashMap;
import java.util.function.Consumer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTickList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import willow.train.hexie.compat.create.CarriagePlayerSupport;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;

/** Supplement occupied carriers and their players when terrain excludes them from the ordinary pass. */
@Mixin(ServerLevel.class)
public abstract class MixinOccupiedCarriageServerTick {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/entity/EntityTickList;forEach(Ljava/util/function/Consumer;)V"))
    private void hexie$tickOccupied(EntityTickList entities, Consumer<Entity> tick, Operation<Void> original) {
        ServerLevel level = (ServerLevel) (Object) this;
        var cars = new LinkedHashMap<SnapshotCarriageEntity, Integer>();
        var players = new LinkedHashMap<ServerPlayer, Integer>();
        for (ServerPlayer player : level.players()) {
            var car = CarriagePlayerSupport.attachedCarriage(player);
            if (car == null) continue;
            cars.put(car, car.tickCount);
            players.put(player, player.tickCount);
        }
        original.call(entities, tick);
        cars.forEach((car, age) -> {
            if (!car.isRemoved() && !car.isPassenger() && car.tickCount == age
                    && CarriagePlayerSupport.hasSupportedPlayers(car) && !level.tickRateManager().isEntityFrozen(car))
                level.guardEntityTick(level::tickNonPassenger, car);
        });
        players.forEach((player, age) -> {
            if (!player.isRemoved() && !player.isPassenger() && player.tickCount == age
                    && CarriagePlayerSupport.attachedCarriage(player) != null && !level.tickRateManager().isEntityFrozen(player))
                level.guardEntityTick(level::tickNonPassenger, player);
        });
    }
}
