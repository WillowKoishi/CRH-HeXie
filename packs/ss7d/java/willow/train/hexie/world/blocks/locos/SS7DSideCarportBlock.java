package willow.train.hexie.world.blocks.locos;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import willow.train.hexie.world.blockinteract.LocoBlockInteract;

/** A single SS7D roof item with independent appearance and left/right states. */
public final class SS7DSideCarportBlock extends SS7DCarportBlock {
    // The shared roof placement helper already preserves OPEN and HINGE while extending.
    public static final BooleanProperty ALTERNATE = BlockStateProperties.OPEN;
    public static final EnumProperty<DoorHingeSide> SIDE = BlockStateProperties.DOOR_HINGE;
    public SS7DSideCarportBlock(BlockBehaviour.Properties properties) {
        super(properties, true);
        registerDefaultState(defaultBlockState().setValue(ALTERNATE, false).setValue(SIDE, DoorHingeSide.LEFT));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ALTERNATE, SIDE);
    }

    @Override public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                      Player player, InteractionHand hand, BlockHitResult hit) {
        return LocoBlockInteract.ForSS7DSideCarportBlock.useItemOn(stack, state, level, pos, player, hand, hit, () -> super.useItemOn(stack, state, level, pos, player, hand, hit));
    }

    @Override public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                       Player player, BlockHitResult hit) {
        return LocoBlockInteract.ForSS7DSideCarportBlock.useWithoutItem(state, level, pos, player, hit);
    }

    public static boolean allowed(Level level, BlockPos pos, Player player, BlockHitResult hit, ItemStack stack) {
        return LocoBlockInteract.ForSS7DSideCarportBlock.allowed(level, pos, player, hit, stack);
    }
}
