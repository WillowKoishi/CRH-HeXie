package willow.train.hexie.mixins.mixin.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import willow.train.hexie.client.carriage.browser.CarriageBrowserClient;

@Mixin(CreativeModeInventoryScreen.class)
public class CarriageCreativeScreenMixin {
    @Inject(method = "selectTab", at = @At("HEAD"))
    private void hexie$saveGroupScroll(net.minecraft.world.item.CreativeModeTab tab, CallbackInfo ci) {
        CarriageBrowserClient.captureOriginal((CreativeModeInventoryScreen) (Object) this);
        willow.train.hexie.event.client.HXInventoryEvents.captureLegacyScroll((CreativeModeInventoryScreen) (Object) this);
    }

    @Inject(method = "renderBg", at = @At("TAIL"))
    private void hexie$carriageContent(GuiGraphics gui, float partial, int x, int y, CallbackInfo ci) {
        CarriageBrowserClient.render((CreativeModeInventoryScreen) (Object) this, gui, x, y);
    }

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void hexie$noHiddenSlotClicks(Slot slot, int slotId, int button, ClickType type, CallbackInfo ci) {
        if (slot != null && slot.index < 45 && CarriageBrowserClient.active((CreativeModeInventoryScreen) (Object) this))
            ci.cancel();
    }

    @Inject(method = "removed", at = @At("TAIL"))
    private void hexie$closePreview(CallbackInfo ci) {
        CarriageBrowserClient.removed((CreativeModeInventoryScreen) (Object) this);
    }
}
