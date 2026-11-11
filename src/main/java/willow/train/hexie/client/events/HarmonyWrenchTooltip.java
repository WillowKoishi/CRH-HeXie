package willow.train.hexie.client.events;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import willow.train.hexie.Main;
import willow.train.hexie.blocks.emu.crh2a.CRH2DoorBlock;
import willow.train.hexie.blocks.emu.crh2a.CRH2EndFaceBlock;
import willow.train.hexie.blocks.parts.VariantPartBlock;
import willow.train.hexie.init.block.BlockInit;

/** Adds the short Harmony Wrench edit hint to compatible parts. */
@EventBusSubscriber(modid = Main.MOD_ID, value = Dist.CLIENT)
public final class HarmonyWrenchTooltip {
    private HarmonyWrenchTooltip() {}

    @SubscribeEvent
    public static void addTooltip(ItemTooltipEvent event) {
        if (event.getItemStack().getItem() instanceof BlockItem blockItem) {
            var block = blockItem.getBlock();
            if (block instanceof CRH2DoorBlock door && door.supportsPassengerFrame()
                    || block instanceof CRH2EndFaceBlock
                    || block instanceof VariantPartBlock
                    || block == BlockInit.CRH2A_CARRIAGE_NUMBER_DISPLAY.get())
                event.getToolTip().add(Component.translatable("tooltip.hexie.harmony_wrench.edit")
                        .withStyle(ChatFormatting.GRAY));
        }
    }
}
