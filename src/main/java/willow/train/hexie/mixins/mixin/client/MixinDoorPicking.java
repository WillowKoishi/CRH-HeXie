package willow.train.hexie.mixins.mixin.client;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import willow.train.hexie.client.DoorInteractionPicking;

@Mixin(Entity.class)
public abstract class MixinDoorPicking {
    @Inject(method = "pick", at = @At("RETURN"), cancellable = true)
    private void hexie$pickExtendedDoor(double reach, float partial, boolean fluids,
                                       CallbackInfoReturnable<HitResult> cir) {
        Entity viewer = (Entity) (Object) this;
        if (viewer.level().isClientSide)
            cir.setReturnValue(DoorInteractionPicking.world(viewer, reach, partial, cir.getReturnValue()));
    }
}
