package willow.train.hexie.mixins.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import willow.train.hexie.client.screens.TextInputScreen;

@Mixin(KeyboardHandler.class)
public abstract class MixinTextInputKeyboard {
    /** Fullscreen and screenshot bindings run before screen dispatch, even when rebound to letters. */
    @ModifyExpressionValue(method = "keyPress", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/KeyMapping;matches(II)Z"))
    private boolean hexie$textBeforeGlobalBindings(boolean matches) {
        return matches && !(Minecraft.getInstance().screen instanceof TextInputScreen<?>);
    }

    /** F3+C starts its crash countdown before screen dispatch as well. */
    @ModifyExpressionValue(method = "keyPress", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/InputConstants;isKeyDown(JI)Z"))
    private boolean hexie$textBeforeDebugKeys(boolean down) {
        return down && !(Minecraft.getInstance().screen instanceof TextInputScreen<?>);
    }

    /** Keep Ctrl+B in the editor even when its focus is temporarily on a button. */
    @ModifyExpressionValue(method = "keyPress", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;hasControlDown()Z"))
    private boolean hexie$textBeforeNarratorShortcut(boolean controlDown) {
        return controlDown && !(Minecraft.getInstance().screen instanceof TextInputScreen<?>);
    }
}
