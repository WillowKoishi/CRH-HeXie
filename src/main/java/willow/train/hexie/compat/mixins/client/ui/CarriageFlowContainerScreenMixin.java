package willow.train.hexie.compat.mixins.client.ui;

import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import willow.train.hexie.bridge.HexieBridges;

@Mixin(AbstractContainerScreen.class)
public class CarriageFlowContainerScreenMixin {
    @Shadow
    @Nullable
    protected Slot hoveredSlot;

    @Inject(method = "renderSlot", at = @At("HEAD"), cancellable = true)
    private void hexie$hidePreviewSlots(GuiGraphics gui, Slot slot, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        var ui = HexieBridges.Client.clientCarriageUi();
        if (ui.carriageFlowClient().hiddenSlot(self, slot) || ui.hxInventoryEvents().isSectionBannerSlot(self, slot))
            ci.cancel();
    }

    @Inject(method = "renderSlotHighlight(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/inventory/Slot;IIF)V",
            at = @At("HEAD"), cancellable = true)
    private void hexie$hidePreviewSlotHighlight(GuiGraphics gui, Slot slot, int x, int y, float partial, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        var ui = HexieBridges.Client.clientCarriageUi();
        if (ui.carriageFlowClient().hiddenSlot(self, slot) || ui.hxInventoryEvents().isSectionBannerSlot(self, slot))
            ci.cancel();
    }

    @Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
    private void hexie$hideBannerTooltip(GuiGraphics gui, int x, int y, CallbackInfo ci) {
        if (HexieBridges.Client.clientCarriageUi().hxInventoryEvents().isSectionBannerSlot((Screen) (Object) this, this.hoveredSlot))
            ci.cancel();
    }
}
