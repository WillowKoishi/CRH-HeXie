package willow.train.hexie.ui.events.inventory;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH2DoorBlockView;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH2EndFaceBlockView;
import willow.train.hexie.bridge.world.PartBlockBridge.VariantPartBlockView;
import willow.train.hexie.core.HexieConstants;

/** Adds the short Harmony Wrench edit hint to compatible parts. */
@EventBusSubscriber(modid = HexieConstants.MOD_ID, value = Dist.CLIENT)
public final class HarmonyWrenchTooltip {
    private HarmonyWrenchTooltip() {}

    @SubscribeEvent
    public static void addTooltip(ItemTooltipEvent event) {
        if (event.getItemStack().getItem() instanceof BlockItem blockItem) {
            var block = blockItem.getBlock();
            if (block instanceof CRH2DoorBlockView door && door.supportsPassengerFrame()
                    || block instanceof CRH2EndFaceBlockView
                    || block instanceof VariantPartBlockView
                    || block == HexieBridges.worldRegistry().blockInit().crh2aCarriageNumberDisplay())
                event.getToolTip().add(Component.translatable("tooltip.hexie.harmony_wrench.edit")
                        .withStyle(ChatFormatting.GRAY));
        }
    }
}
