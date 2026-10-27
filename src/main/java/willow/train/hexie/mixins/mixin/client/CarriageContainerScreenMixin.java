package willow.train.hexie.mixins.mixin.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import willow.train.hexie.client.carriage.browser.CarriageBrowserClient;

@Mixin(AbstractContainerScreen.class)
public class CarriageContainerScreenMixin {
    @Inject(method = "renderSlot", at = @At("HEAD"), cancellable = true)
    private void hexie$hidePreviewSlots(GuiGraphics gui, Slot slot, CallbackInfo ci) {
        if (slot.index < 45 && CarriageBrowserClient.active((Screen) (Object) this)) ci.cancel();
    }

    @Inject(method = "renderSlotHighlight(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/inventory/Slot;IIF)V",
            at = @At("HEAD"), cancellable = true)
    private void hexie$hidePreviewSlotHighlight(GuiGraphics gui, Slot slot, int x, int y, float partial, CallbackInfo ci) {
        if (slot.index < 45 && CarriageBrowserClient.active((Screen) (Object) this)) ci.cancel();
    }
}
