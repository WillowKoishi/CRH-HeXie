package willow.train.hexie.blocks.emu.crh1a;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import willow.train.hexie.blocks.emu.crh2a.CRH2PartBlock;
import willow.train.hexie.blocks.parts.PartInstallation;
import willow.train.hexie.blocks.parts.PartStructures;
import willow.train.hexie.item.FloorSwitcherItem;

/** One ordinary floor cell; its optional bogey skirts are visual only. */
public final class CRH1FloorBlock extends CRH2PartBlock {
    public static final BooleanProperty BOGEY_SKIRT = BooleanProperty.create("bogey_skirt");

    public CRH1FloorBlock(Properties properties) {
        super(properties, "crh1a_floor", true);
        registerDefaultState(defaultBlockState().setValue(BOGEY_SKIRT, false));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BOGEY_SKIRT);
    }

    @Override public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof FloorSwitcherItem) || player.isShiftKeyDown()
                || !hit.getDirection().getAxis().isHorizontal())
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        if (player.isSpectator() || !player.mayBuild() || player.level() != level)
            return ItemInteractionResult.FAIL;
        var root = PartStructures.resolve(level, pos, state);
        if (root == null || root.structure() != this
                || !PartInstallation.complete(level, root.pos(), root.state(), this))
            return ItemInteractionResult.FAIL;
        boolean changed = PartInstallation.replace(level, partCells(level, root.pos(), root.state()),
                partCells(level, root.pos(), root.state().cycle(BOGEY_SKIRT)), at -> false,
                at -> level.mayInteract(player, at) && player.mayUseItemAt(at, hit.getDirection(), stack));
        return changed ? ItemInteractionResult.sidedSuccess(level.isClientSide) : ItemInteractionResult.FAIL;
    }
}
