package willow.train.hexie.compat.mixins.client.input;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import willow.train.hexie.bridge.HexieBridges;

@Mixin(Entity.class)
public abstract class MixinDrivingConsoleLook {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void hexie$cabRelativeLook(double horizontal, double vertical, CallbackInfo ci) {
        // A direct slider drag owns only this pointer gesture, without entering cab camera mode.
        if ((Object) this == net.minecraft.client.Minecraft.getInstance().player
                && HexieBridges.Client.surfaceInputClient().surfaceInputClient().dragging()) {
            ci.cancel();
            return;
        }
        if (HexieBridges.Client.clientCarriageUi().carriageCamera().turn((Entity) (Object) this, horizontal, vertical)) ci.cancel();
    }
}
