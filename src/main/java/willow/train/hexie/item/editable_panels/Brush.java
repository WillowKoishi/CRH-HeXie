package willow.train.hexie.item.editable_panels;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import willow.train.hexie.blocks.carriage.OrdinaryTrainPanelBlock;
import willow.train.hexie.blockentity.CarriageTypeSignEntity;

import java.util.List;

public class Brush extends Item {
    public Brush(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public void appendHoverText(
            @NotNull ItemStack p_41421_,
            @Nullable TooltipContext p_41422_,
            List<Component> tooltips,
            TooltipFlag p_41424_) {
        tooltips.add(Component.translatable("item.hexie.brush.tooltip"));
        super.appendHoverText(p_41421_, p_41422_, tooltips, p_41424_);
    }

    @Override
    public InteractionResult useOn(UseOnContext pContext) {
        BlockState pBlockState = pContext.getPlayer().getBlockStateOn();
        if (pContext.getLevel().isClientSide) return InteractionResult.SUCCESS;
        if (pBlockState.getBlock().getClass().equals(OrdinaryTrainPanelBlock.class)) {
            ((ServerPlayer) pContext.getPlayer()).openMenu(
                    (CarriageTypeSignEntity)
                            pContext.getLevel().getBlockEntity(pContext.getClickedPos()));
        }
        return InteractionResult.CONSUME;
    }
}
