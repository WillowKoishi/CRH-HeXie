package willow.train.hexie.compat.mixins.client.ui;

import java.util.Collection;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import willow.train.hexie.bridge.HexieBridges;

@Mixin(CreativeModeInventoryScreen.class)
public class CarriageFlowCreativeScreenMixin {
    @Inject(method = "selectTab", at = @At("HEAD"))
    private void hexie$saveGroupScroll(CreativeModeTab tab, CallbackInfo ci) {
        CreativeModeInventoryScreen self = (CreativeModeInventoryScreen) (Object) this;
        var ui = HexieBridges.Client.clientCarriageUi();
        ui.carriageFlowClient().captureOriginal(self);
        ui.hxInventoryEvents().captureLegacyScroll(self);
        // Inventory-tab switching snapshots the slot list; restore the native
        // creative slot before vanilla takes that snapshot.
        ui.carriageFlowClient().restoreSlots(self);
    }

    @Inject(
            method = "selectTab",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/CreativeModeInventoryScreen$ItemPickerMenu;scrollTo(F)V"
            )
    )
    private void hexie$applySectionedTabLayout(CreativeModeTab tab, CallbackInfo ci) {
        HexieBridges.Client.clientCarriageUi().hxInventoryEvents()
                .applySectionedTabLayout((CreativeModeInventoryScreen) (Object) this, tab);
    }

    @Inject(
            method = "refreshCurrentTabContents",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/CreativeModeInventoryScreen$ItemPickerMenu;getScrollForRowIndex(I)F"
            )
    )
    private void hexie$refreshSectionedTabLayout(Collection<ItemStack> items, CallbackInfo ci) {
        HexieBridges.Client.clientCarriageUi().hxInventoryEvents()
                .applySectionedTabLayout((CreativeModeInventoryScreen) (Object) this, CreativeModeInventoryScreen.selectedTab);
    }

    @Inject(method = "renderBg", at = @At("TAIL"))
    private void hexie$carriageContent(GuiGraphics gui, float partial, int x, int y, CallbackInfo ci) {
        CreativeModeInventoryScreen self = (CreativeModeInventoryScreen) (Object) this;
        var ui = HexieBridges.Client.clientCarriageUi();
        ui.carriageFlowClient().render(self, gui, x, y);
        ui.hxInventoryEvents().renderSectionBanners(self, gui);
    }

    @Inject(method = "insideScrollbar", at = @At("HEAD"), cancellable = true)
    private void hexie$disablePreviewScrollbar(double x, double y, CallbackInfoReturnable<Boolean> cir) {
        // Vanilla consumes left clicks here even when the item list cannot scroll.
        if (HexieBridges.Client.clientCarriageUi().carriageFlowClient().active((CreativeModeInventoryScreen) (Object) this)) cir.setReturnValue(false);
    }

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void hexie$noHiddenSlotClicks(Slot slot, int slotId, int button, ClickType type, CallbackInfo ci) {
        CreativeModeInventoryScreen self = (CreativeModeInventoryScreen) (Object) this;
        var ui = HexieBridges.Client.clientCarriageUi();
        if (ui.carriageFlowClient().hiddenSlot(self, slot) || ui.hxInventoryEvents().isSectionBannerSlot(self, slot))
            ci.cancel();
    }

    @Inject(method = "removed", at = @At("TAIL"))
    private void hexie$closePreview(CallbackInfo ci) {
        HexieBridges.Client.clientCarriageUi().carriageFlowClient().removed((CreativeModeInventoryScreen) (Object) this);
    }
}
