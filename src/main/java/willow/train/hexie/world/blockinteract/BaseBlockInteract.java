package willow.train.hexie.world.blockinteract;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import willow.train.hexie.core.api.interaction.HexieWrenchable;
import willow.train.hexie.world.blocks.base.HorizontalBlockBase;
import willow.train.hexie.world.placement.PartInstallation;
import willow.train.hexie.world.placement.TrainPanelPlacement;

public final class BaseBlockInteract {
    private BaseBlockInteract() {}

    static void playWrenchRotateSound(Level level, BlockPos pos) {
        HexieWrenchable.playRotateSound(level, pos);
    }

    public static final class ForHorizontalBlockBase {
        public static InteractionResult onWrenched(HorizontalBlockBase block, BlockState state,UseOnContext context) {
            var rotated=block.getRotatedBlockState(state,context.getClickedFace());
            if(rotated.equals(state))return InteractionResult.PASS;
            var level=context.getLevel();var pos=context.getClickedPos();
            if(!PartInstallation.replace(level,block.partCells(level,pos,state),block.partCells(level,pos,rotated),
                    at->false,at->context.getPlayer()==null||context.getPlayer().mayUseItemAt(at,context.getClickedFace(),context.getItemInHand())))
                return InteractionResult.FAIL;
            if(!level.isClientSide)playWrenchRotateSound(level, pos);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        public static ItemInteractionResult useItemOn(ItemStack stack,
                BlockState state, Level level,
                BlockPos pos, Player player,
                InteractionHand hand, BlockHitResult hit, Supplier<ItemInteractionResult> baseUseItemOn) {
            var placement = TrainPanelPlacement.place(stack, state, level, pos, player, hand, hit);
            return placement != null ? placement : baseUseItemOn.get();
        }
    }
}
