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
import willow.train.hexie.bridge.interaction.ToolOpsBridge;
import willow.train.hexie.core.api.interaction.HexieWrenchable;
import willow.train.hexie.world.blocks.parts.PartMemberBlock;
import willow.train.hexie.world.blocks.parts.PartStructures;
import willow.train.hexie.world.blocks.parts.VariantPartBlock;
import willow.train.hexie.world.placement.PartInstallation;

public final class PartBlockInteract {
    private PartBlockInteract() {}

    public static final class ForPartMemberBlock {
        public static BlockHitResult hit(BlockPos root,BlockHitResult hit) { return new BlockHitResult(hit.getLocation(),hit.getDirection(),root,hit.isInside()); }

        public static ItemInteractionResult useItemOn(PartMemberBlock block, ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
            var owner=PartStructures.resolve(level,pos,state);
            return owner==null?ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION:owner.state().useItemOn(stack,level,player,hand,block.hit(owner.pos(),hit));
        }

        public static InteractionResult useWithoutItem(PartMemberBlock block, BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
            var owner=PartStructures.resolve(level,pos,state);
            return owner==null?InteractionResult.PASS:owner.state().useWithoutItem(level,player,block.hit(owner.pos(),hit));
        }

        public static InteractionResult wrench(BlockState state,UseOnContext context,boolean remove) {
            var owner=PartStructures.resolve(context.getLevel(),context.getClickedPos(),state);
            if(owner==null||!(owner.state().getBlock() instanceof HexieWrenchable wrench))return InteractionResult.PASS;
            var root=owner.pos();var source=owner.state();
            var redirected=new UseOnContext(context.getLevel(),context.getPlayer(),context.getHand(),context.getItemInHand(),
                    new BlockHitResult(context.getClickLocation(),context.getClickedFace(),root,false));
            return remove?wrench.onSneakWrenched(source,redirected):wrench.onWrenched(source,redirected);
        }

        public static InteractionResult onWrenched(PartMemberBlock block, BlockState state,UseOnContext context) { return block.wrench(state,context,false); }

        public static InteractionResult onSneakWrenched(PartMemberBlock block, BlockState state,UseOnContext context) { return block.wrench(state,context,true); }
    }

    public static final class ForVariantPartBlock {
        public static ItemInteractionResult useItemOn(VariantPartBlock block, ItemStack stack, BlockState state, Level level,
                BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit, Supplier<ItemInteractionResult> baseUseItemOn) {
            if (!(stack.getItem() instanceof ToolOpsBridge.FloorSwitcherItemView) || player.isShiftKeyDown())
                return baseUseItemOn.get();
            if (player.isSpectator() || !player.mayBuild()) return ItemInteractionResult.FAIL;
            var root = PartStructures.resolve(level, pos, state);
            if (root == null || root.structure() != block
                    || !PartInstallation.complete(level, root.pos(), root.state(), block)) return ItemInteractionResult.FAIL;
            var before = block.partCells(level, root.pos(), root.state());
            var after = block.partCells(level, root.pos(), root.state().cycle(VariantPartBlock.ALTERNATE));
            boolean changed = PartInstallation.replace(level, before, after, at -> false,
                    at -> level.mayInteract(player, at) && player.mayUseItemAt(at, hit.getDirection(), stack));
            return changed ? ItemInteractionResult.sidedSuccess(level.isClientSide) : ItemInteractionResult.FAIL;
        }
    }
}
