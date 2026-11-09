package willow.train.hexie.mixins.mixin.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import willow.train.hexie.client.carriage.flow.CarriageFlowClient;

@Mixin(CreativeModeInventoryScreen.class)
public class CarriageFlowCreativeScreenMixin {
    @Inject(method = "selectTab", at = @At("HEAD"))
    private void hexie$saveGroupScroll(net.minecraft.world.item.CreativeModeTab tab, CallbackInfo ci) {
        CarriageFlowClient.captureOriginal((CreativeModeInventoryScreen) (Object) this);
        willow.train.hexie.event.client.HXInventoryEvents.captureLegacyScroll((CreativeModeInventoryScreen) (Object) this);
        // Inventory-tab switching snapshots the slot list; restore the native
        // creative slot before vanilla takes that snapshot.
        CarriageFlowClient.restoreSlots((CreativeModeInventoryScreen) (Object) this);
    }

    @Inject(method = "renderBg", at = @At("TAIL"))
    private void hexie$carriageContent(GuiGraphics gui, float partial, int x, int y, CallbackInfo ci) {
        CarriageFlowClient.render((CreativeModeInventoryScreen) (Object) this, gui, x, y);
    }

    @Inject(method = "insideScrollbar", at = @At("HEAD"), cancellable = true)
    private void hexie$disablePreviewScrollbar(double x, double y, CallbackInfoReturnable<Boolean> cir) {
        // Vanilla consumes left clicks here even when the item list cannot scroll.
        if (CarriageFlowClient.active((CreativeModeInventoryScreen) (Object) this)) cir.setReturnValue(false);
    }

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void hexie$noHiddenSlotClicks(Slot slot, int slotId, int button, ClickType type, CallbackInfo ci) {
        if (CarriageFlowClient.hiddenSlot((CreativeModeInventoryScreen) (Object) this, slot))
            ci.cancel();
    }

    @Inject(method = "removed", at = @At("TAIL"))
    private void hexie$closePreview(CallbackInfo ci) {
        CarriageFlowClient.removed((CreativeModeInventoryScreen) (Object) this);
    }
}
