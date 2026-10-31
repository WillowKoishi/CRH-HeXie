package willow.train.hexie.mixins.mixin.client;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import willow.train.hexie.client.events.DrivingConsoleCamera;

@Mixin(Entity.class)
public abstract class MixinDrivingConsoleLook {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void hexie$cabRelativeLook(double horizontal, double vertical, CallbackInfo ci) {
        if (DrivingConsoleCamera.turn((Entity) (Object) this, horizontal, vertical)) ci.cancel();
    }
}
