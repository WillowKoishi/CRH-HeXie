package willow.train.hexie.world.blocks.locos;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import willow.train.hexie.core.api.interaction.HexieItemRequirementProvider;
import willow.train.hexie.world.blocks.parts.PartStructure;
import willow.train.hexie.world.blocks.parts.PartStructures;
import willow.train.hexie.world.placement.PartInstallation;

/** Collision cells installed with and owned by one cab. */
public final class SS7DHeadFloorBlock extends Block implements PartStructure, HexieItemRequirementProvider {
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 5);
    public SS7DHeadFloorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(SS7DHeadBlock.FACING, Direction.NORTH).setValue(PART, 0));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SS7DHeadBlock.FACING, PART);
    }
    @Override public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(SS7DHeadBlock.FACING, rotation.rotate(state.getValue(SS7DHeadBlock.FACING)));
    }
    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) return state;
        int part = state.getValue(PART);
        return rotate(state, mirror.getRotation(state.getValue(SS7DHeadBlock.FACING)))
                .setValue(PART, part / 3 * 3 + 2 - part % 3);
    }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override public boolean isPartMember() { return true; }
    @Override public BlockPos partRoot(BlockGetter level, BlockPos pos, BlockState state) {
        return pos.subtract(SS7DHeadBlock.offset(state.getValue(PART), state.getValue(SS7DHeadBlock.FACING)));
    }
    @Override public Map<BlockPos, BlockState> partCells(BlockGetter level, BlockPos root, BlockState state) {
        var owner = level.getBlockState(root);
        return owner.getBlock() instanceof SS7DHeadBlock head ? head.partCells(level, root, owner) : Map.of();
    }
    @Override public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        var root = PartStructures.resolve(level, pos, state);
        return root == null ? ItemStack.EMPTY : root.state().getBlock().asItem().getDefaultInstance();
    }
    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        var root = PartStructures.resolve(level, pos, state);
        if (!level.isClientSide && root != null) level.destroyBlock(root.pos(), !player.isCreative(), player);
        return super.playerWillDestroy(level, pos, state, player);
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!level.isClientSide && !moving && !state.is(next.getBlock()) && !PartInstallation.mutating()) {
            var root = PartStructures.resolve(level, pos, state);
            if (root != null)
                PartInstallation.removeOtherCells(level, pos, root.structure().partCells(level, root.pos(), root.state()));
        }
        super.onRemove(state, level, pos, next, moving);
    }
    @Override public Requirement getRequiredItems(BlockState state, BlockEntity entity) { return Requirement.NONE; }
}
