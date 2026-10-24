package willow.train.hexie.item.editable_panels;

import net.minecraft.nbt.CompoundTag;
import willow.train.hexie.compat.ItemData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import willow.train.hexie.item.ToolTipsItemHelper;
import willow.train.hexie.util.enums.PanelTypes;

import java.util.List;

public class TrainPanelItem extends ToolTipsItemHelper {
    public TrainPanelItem(Block pBlock, Properties pProperties) {
        super(pBlock, pProperties);
    }

    public InteractionResult useOn(UseOnContext pContext) {

        if (pContext.getPlayer().isShiftKeyDown()) {
            return InteractionResult.FAIL;
        }

        InteractionResult interactionresult = this.place(new BlockPlaceContext(pContext));

        if (!interactionresult.consumesAction() && pContext.getItemInHand().has(net.minecraft.core.component.DataComponents.FOOD)) {
            InteractionResult interactionresult1 =
                    this.use(pContext.getLevel(), pContext.getPlayer(), pContext.getHand())
                            .getResult();
            return interactionresult1 == InteractionResult.CONSUME
                    ? InteractionResult.CONSUME_PARTIAL
                    : interactionresult1;
        } else {
            return interactionresult;
        }
    }

    @Override
    public ItemStack getDefaultInstance() {
        ItemStack stack = super.getDefaultInstance();
        ItemData.updateTagElement(stack, "type", data -> data.putString("type", "p25b"));
        return stack;
    }

    @Override
    public boolean useOnRelease(ItemStack pStack) {
        return super.useOnRelease(pStack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level pLevel, Player pPlayer, InteractionHand pUsedHand) {
        if (!pLevel.isClientSide()) {
            if (pPlayer.isShiftKeyDown()) {
                ItemStack stack = pPlayer.getItemInHand(pUsedHand);
                CompoundTag tag = ItemData.getOrCreateTagElement(stack, "type");
                PanelTypes[] types = PanelTypes.values();
                int x = 0;

                for (; x < types.length; x++) {
                    if (types[x].toString().equals(tag.getString("type"))) break;
                }

                if (x < types.length - 1) {
                    tag.putString("type", types[x + 1].toString());
                } else {
                    tag.putString("type", types[0].toString());
                }

                ItemData.setTagElement(stack, "type", tag);
                return InteractionResultHolder.sidedSuccess(pPlayer.getItemInHand(pUsedHand), true);
            }
        } else {
            if (pPlayer.isShiftKeyDown()) {
                return InteractionResultHolder.fail(pPlayer.getItemInHand(pUsedHand));
            }
            return super.use(pLevel, pPlayer, pUsedHand);
        }
        return super.use(pLevel, pPlayer, pUsedHand);
    }

    @Override
    public void appendHoverText(
            @NotNull ItemStack p_41421_,
            @Nullable TooltipContext p_41422_,
            List<Component> tooltips,
            TooltipFlag p_41424_) {
        tooltips.add(Component.translatable("item.hexie.train_panel_item.tooltip"));
        super.appendHoverText(p_41421_, p_41422_, tooltips, p_41424_);
    }
}
