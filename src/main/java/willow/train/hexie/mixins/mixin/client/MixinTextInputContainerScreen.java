package willow.train.hexie.mixins.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import willow.train.hexie.client.screens.TextInputScreen;

@Mixin(AbstractContainerScreen.class)
public abstract class MixinTextInputContainerScreen {
    /** Vanilla Screen already dispatched editing, Escape and focus navigation at this point. */
    @ModifyExpressionValue(method = "keyPressed", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;keyPressed(III)Z"))
    private boolean hexie$textScreenOwnsKeyboard(boolean handled) {
        return handled || (Object) this instanceof TextInputScreen<?>;
    }
}
