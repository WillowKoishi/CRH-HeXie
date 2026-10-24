package willow.train.hexie.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TooltipAppendableItem extends BlockItem {

    final String[] tooltipKeys;
    public TooltipAppendableItem(Block pBlock, Properties pProperties, String... tooltipKeys) {
        super(pBlock, pProperties);
        this.tooltipKeys = tooltipKeys;
    }


    @Override
    public void appendHoverText(
            ItemStack pStack, @Nullable TooltipContext pLevel, List<Component> pTooltip, TooltipFlag pFlag) {
        for(String key : tooltipKeys) {
            Component component = Component.translatable(key);
            if(!component.getString().equals("")) {
                pTooltip.add(component);
            }
        }
        super.appendHoverText(pStack, pLevel, pTooltip, pFlag);
    }

    @Override
    public ItemStack getDefaultInstance() {
        return super.getDefaultInstance();
    }
}
