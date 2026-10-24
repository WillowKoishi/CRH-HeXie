package willow.train.hexie.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ToolTipsItemHelper extends BlockItem {
    public ToolTipsItemHelper(Block pBlock, Properties pProperties) {
        super(pBlock, pProperties);
    }


    @Override
    public void appendHoverText(
            ItemStack pStack, @Nullable TooltipContext pLevel, List<Component> pTooltip, TooltipFlag pFlag) {
        Component translatableComponent =
                Component.translatable(pStack.getDescriptionId() + ".tooltip");
        Component translatableComponent2 =
                Component.translatable(pStack.getDescriptionId() + ".tooltip2");
        Component translatableComponent3 =
                Component.translatable(pStack.getDescriptionId() + ".tooltip3");
        if (!translatableComponent.getString().equals("")
                && !translatableComponent
                        .getString()
                        .equals(pStack.getDescriptionId() + ".tooltip"))
            pTooltip.add(translatableComponent);
        if (!translatableComponent2.getString().equals("")
                && !translatableComponent2
                        .getString()
                        .equals(pStack.getDescriptionId() + ".tooltip2"))
            pTooltip.add(translatableComponent2);
        if (!translatableComponent3.getString().equals("")
                && !translatableComponent3
                        .getString()
                        .equals(pStack.getDescriptionId() + ".tooltip3"))
            pTooltip.add(translatableComponent3);
        super.appendHoverText(pStack, pLevel, pTooltip, pFlag);
    }

    @Override
    public ItemStack getDefaultInstance() {
        return super.getDefaultInstance();
    }
}
