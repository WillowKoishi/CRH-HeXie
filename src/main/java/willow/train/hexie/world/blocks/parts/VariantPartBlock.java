package willow.train.hexie.world.blocks.parts;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import willow.train.hexie.bridge.world.PartBlockBridge;
import willow.train.hexie.world.blockinteract.PartBlockInteract;
import willow.train.hexie.world.blocks.emu.CRH2PartBlock;

/** Centre-roof appearances share one item, occupied structure and placement geometry. */
public class VariantPartBlock extends CRH2PartBlock implements PartBlockBridge.VariantPartBlockView {

    // Keep the property name and the original two values readable in existing worlds and schematics.
    public static final EnumProperty<PartBlockBridge.VariantPartBlockAppearance> ALTERNATE = EnumProperty.create("alternate", PartBlockBridge.VariantPartBlockAppearance.class);

    public VariantPartBlock(BlockBehaviour.Properties properties, String model) {
        super(properties, model);
        registerDefaultState(defaultBlockState().setValue(ALTERNATE, PartBlockBridge.VariantPartBlockAppearance.ANTENNA));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ALTERNATE);
    }

    @Override public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return PartBlockInteract.ForVariantPartBlock.useItemOn(this, stack, state, level, pos, player, hand, hit, () -> super.useItemOn(stack, state, level, pos, player, hand, hit));
    }
}
