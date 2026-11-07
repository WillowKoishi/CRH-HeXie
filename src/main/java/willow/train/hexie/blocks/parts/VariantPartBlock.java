package willow.train.hexie.blocks.parts;

import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import willow.train.hexie.blocks.emu.crh2a.CRH2PartBlock;
import willow.train.hexie.item.FloorSwitcherItem;

/** Centre-roof appearances share one item, occupied structure and placement geometry. */
public class VariantPartBlock extends CRH2PartBlock {
    public enum Appearance implements StringRepresentable {
        ANTENNA("false"),
        ANTENNA_2("true"),
        CABLE("cable"),
        FLAT("flat");

        private final String serializedName;

        Appearance(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override public String getSerializedName() {
            return serializedName;
        }
    }

    // Keep the property name and the original two values readable in existing worlds and schematics.
    public static final EnumProperty<Appearance> ALTERNATE = EnumProperty.create("alternate", Appearance.class);

    public VariantPartBlock(Properties properties, String model) {
        super(properties, model);
        registerDefaultState(defaultBlockState().setValue(ALTERNATE, Appearance.ANTENNA));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ALTERNATE);
    }

    @Override public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof FloorSwitcherItem) || player.isShiftKeyDown())
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        if (player.isSpectator() || !player.mayBuild()) return ItemInteractionResult.FAIL;
        var root = PartStructures.resolve(level, pos, state);
        if (root == null || root.structure() != this
                || !PartInstallation.complete(level, root.pos(), root.state(), this)) return ItemInteractionResult.FAIL;
        var before = partCells(level, root.pos(), root.state());
        var after = partCells(level, root.pos(), root.state().cycle(ALTERNATE));
        boolean changed = PartInstallation.replace(level, before, after, at -> false,
                at -> level.mayInteract(player, at) && player.mayUseItemAt(at, hit.getDirection(), stack));
        return changed ? ItemInteractionResult.sidedSuccess(level.isClientSide) : ItemInteractionResult.FAIL;
    }
}
