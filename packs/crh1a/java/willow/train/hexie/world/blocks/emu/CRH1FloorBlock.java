package willow.train.hexie.world.blocks.emu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import willow.train.hexie.bridge.world.CarriageBlockBridge;
import willow.train.hexie.world.blockinteract.EmuBlockInteract;

/** One ordinary floor cell; its optional bogey skirts are visual only. */
public final class CRH1FloorBlock extends CRH2PartBlock implements CarriageBlockBridge.CRH1FloorBlockView {
    // Retain the previous saved property as the local -X (left) skirt.
    public static final BooleanProperty BOGEY_SKIRT_LEFT = BooleanProperty.create("bogey_skirt");
    public static final BooleanProperty BOGEY_SKIRT_RIGHT = BooleanProperty.create("bogey_skirt_right");

    public CRH1FloorBlock(BlockBehaviour.Properties properties) {
        super(properties, "crh1a_floor", true);
        registerDefaultState(defaultBlockState().setValue(BOGEY_SKIRT_LEFT, false).setValue(BOGEY_SKIRT_RIGHT, false));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BOGEY_SKIRT_LEFT, BOGEY_SKIRT_RIGHT);
    }

    /** Only the two lateral faces carry skirts; end faces and top/bottom do not toggle either side. */
    public static BooleanProperty skirtProperty(BlockState state, Direction side) {
        var facing = state.getValue(FACING);
        if (side == facing.getCounterClockWise()) return BOGEY_SKIRT_LEFT;
        if (side == facing.getClockWise()) return BOGEY_SKIRT_RIGHT;
        return null;
    }

    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        var mirrored = super.mirror(state, mirror);
        return mirror == Mirror.NONE ? mirrored : mirrored
                .setValue(BOGEY_SKIRT_LEFT, state.getValue(BOGEY_SKIRT_RIGHT))
                .setValue(BOGEY_SKIRT_RIGHT, state.getValue(BOGEY_SKIRT_LEFT));
    }

    @Override public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return EmuBlockInteract.ForCRH1FloorBlock.useItemOn(this, stack, state, level, pos, player, hand, hit, () -> super.useItemOn(stack, state, level, pos, player, hand, hit));
    }
}
