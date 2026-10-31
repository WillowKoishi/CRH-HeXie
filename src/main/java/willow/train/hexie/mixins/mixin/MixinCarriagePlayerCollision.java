package willow.train.hexie.mixins.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ContraptionCollider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import willow.train.hexie.compat.create.CarriagePlayerSupport;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;

/** Keep Create's shape collision, replace only platform movement for our supported players. */
@Mixin(ContraptionCollider.class)
public abstract class MixinCarriagePlayerCollision {
    @Inject(method = "collideEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;setPos(DDD)V", ordinal = 0,
            shift = At.Shift.AFTER), remap = false)
    private static void hexie$touchBody(AbstractContraptionEntity contraption, CallbackInfo ci,
            @Local(name = "entity") Entity entity) {
        if (contraption instanceof SnapshotCarriageEntity car) CarriagePlayerSupport.contact(car, entity);
    }

    @Inject(method = "collideEntities", at = @At("HEAD"), remap = false)
    private static void hexie$carry(AbstractContraptionEntity entity, CallbackInfo ci) {
        if (entity instanceof SnapshotCarriageEntity car) CarriagePlayerSupport.advance(car);
    }

    @Inject(method = "collideEntities", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/contraptions/ContraptionCollider;getPlayerType(Lnet/minecraft/world/entity/Entity;)Lcom/simibubi/create/content/contraptions/ContraptionCollider$PlayerType;"), remap = false)
    private static void hexie$relativeCollision(AbstractContraptionEntity contraption, CallbackInfo ci,
            @Local(name = "entity") Entity entity, @Local(name = "contraptionMotion") LocalRef<Vec3> motion) {
        if (!(contraption instanceof SnapshotCarriageEntity car)) return;
        // This local is shared by the loop: restore it for every non-supported entity too.
        motion.set(CarriagePlayerSupport.supportedBy(entity, car) ? Vec3.ZERO
                : car.position().subtract(car.getPrevPositionVec()));
    }

    @WrapOperation(method = "collideEntities", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;getContactPointMotion(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"), remap = false)
    private static Vec3 hexie$noSecondCarry(AbstractContraptionEntity contraption, Vec3 point,
            Operation<Vec3> original, @Local(name = "entity") Entity entity) {
        if (contraption instanceof SnapshotCarriageEntity && entity instanceof Player) return Vec3.ZERO;
        return original.call(contraption, point);
    }

    @Inject(method = "collideEntities", at = @At("RETURN"), remap = false)
    private static void hexie$finish(AbstractContraptionEntity entity, CallbackInfo ci) {
        if (entity instanceof SnapshotCarriageEntity car) CarriagePlayerSupport.afterCollision(car);
    }
}
