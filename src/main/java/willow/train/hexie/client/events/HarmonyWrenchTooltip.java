package willow.train.hexie.client.events;

import com.simibubi.create.foundation.item.ItemDescription;
import net.createmod.catnip.lang.FontHelper.Palette;
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
import willow.train.hexie.item.FloorSwitcherItem;

/** Uses Create's Shift description layout without a separate summary paragraph. */
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
        if (!(event.getItemStack().getItem() instanceof FloorSwitcherItem)) return;
        var builder = new ItemDescription.Builder(Palette.STANDARD_CREATE);
        ItemDescription.fillBuilder(builder, "item.hexie.harmony_wrench.tooltip");
        event.getToolTip().addAll(1, builder.build().getCurrentLines());
    }
}
