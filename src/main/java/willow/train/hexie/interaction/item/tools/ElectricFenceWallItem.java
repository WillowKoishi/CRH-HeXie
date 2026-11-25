package willow.train.hexie.interaction.item.tools;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
import willow.train.hexie.core.HexieConstants;

public class ElectricFenceWallItem extends BlockItem {
    public ElectricFenceWallItem(Block pBlock, Properties pProperties) {
        super(pBlock, pProperties);
    }

    @Override
    public void appendHoverText(
            ItemStack pStack, @Nullable TooltipContext pLevel, List<Component> pTooltip, TooltipFlag pFlag) {
        pTooltip.add(
                Component.translatable("item." + HexieConstants.MOD_ID + ".electric_fence_wall_tooltip"));
        super.appendHoverText(pStack, pLevel, pTooltip, pFlag);
    }
}
