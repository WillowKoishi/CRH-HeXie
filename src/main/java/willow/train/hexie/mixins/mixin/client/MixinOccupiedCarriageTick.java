package willow.train.hexie.mixins.mixin.client;

import java.util.function.Consumer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTickList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import willow.train.hexie.compat.create.CarriagePlayerSupport;

@Mixin(ClientLevel.class)
public abstract class MixinOccupiedCarriageTick {
    @WrapOperation(method = "tickEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/entity/EntityTickList;forEach(Ljava/util/function/Consumer;)V"))
    private void hexie$tickOccupiedCarriage(EntityTickList entities, Consumer<Entity> tick, Operation<Void> original) {
        var player = Minecraft.getInstance().player;
        var car = player == null || player.level() != (Object) this ? null : CarriagePlayerSupport.mountedOrStanding(player);
        if (car == null || entities.contains(car)) {
            original.call(entities, tick);
            return;
        }

        // Terrain controls membership in the vanilla tick list. Supplement only this pass
        // so leaving the carriage or receiving its chunk needs no tick-list cleanup.
        int age = car.tickCount;
        original.call(entities, tick);
        if (car.tickCount == age && CarriagePlayerSupport.mountedOrStanding(player) == car) tick.accept(car);
    }
}
