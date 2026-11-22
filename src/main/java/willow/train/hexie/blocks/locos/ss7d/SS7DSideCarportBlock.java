package willow.train.hexie.blocks.locos.ss7d;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import willow.train.hexie.item.editable_panels.ColoredBrush;

/** A single SS7D roof item with independent appearance and left/right states. */
public final class SS7DSideCarportBlock extends SS7DCarportBlock {
    // The shared roof placement helper already preserves OPEN and HINGE while extending.
    public static final BooleanProperty ALTERNATE = BlockStateProperties.OPEN;
    public static final EnumProperty<DoorHingeSide> SIDE = BlockStateProperties.DOOR_HINGE;
    public SS7DSideCarportBlock(Properties properties) {
        super(properties, true);
        registerDefaultState(defaultBlockState().setValue(ALTERNATE, false).setValue(SIDE, DoorHingeSide.LEFT));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ALTERNATE, SIDE);
    }

    @Override public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                      Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof ColoredBrush))
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        if (!allowed(level, pos, player, hit, stack)) return ItemInteractionResult.FAIL;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (!level.setBlock(pos, state.cycle(ALTERNATE), Block.UPDATE_ALL)) return ItemInteractionResult.FAIL;
        if (!player.isCreative()) player.setItemInHand(hand, ColoredBrush.onEffectiveUse(stack));
        return ItemInteractionResult.CONSUME;
    }

    @Override public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                       Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) return InteractionResult.PASS;
        if (!allowed(level, pos, player, hit, ItemStack.EMPTY)) return InteractionResult.FAIL;
        if (!level.isClientSide && !level.setBlock(pos, state.cycle(SIDE), Block.UPDATE_ALL))
            return InteractionResult.FAIL;
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static boolean allowed(Level level, BlockPos pos, Player player, BlockHitResult hit, ItemStack stack) {
        return player.level() == level && player.mayBuild() && !player.isSpectator()
                && level.mayInteract(player, pos) && player.mayUseItemAt(pos, hit.getDirection(), stack);
    }
}
