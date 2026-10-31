package willow.train.hexie.mixins.mixin.client;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import willow.train.hexie.client.events.DrivingConsoleClient;

@Mixin(KeyboardHandler.class)
public abstract class MixinDrivingConsoleKeyboard {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void hexie$cabKeyboard(long window, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        if (window == Minecraft.getInstance().getWindow().getWindow() && DrivingConsoleClient.handleKey(key, scanCode, action))
            ci.cancel();
    }
}
