package willow.train.hexie.world.blocks.base;

import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import willow.train.hexie.bridge.world.WorldBlockBridge;
import willow.train.hexie.core.api.interaction.HexieWrenchable;
import willow.train.hexie.world.blockinteract.BaseBlockInteract;
import willow.train.hexie.world.blocks.parts.LegacyPartLayout;
import willow.train.hexie.world.blocks.parts.PartStructure;
import willow.train.hexie.world.blocks.signs.PanelDisplayColumn;
import willow.train.hexie.world.blocks.signs.PanelDisplayMount;
import willow.train.hexie.world.blocks.signs.PassengerDisplayPanel;
import willow.train.hexie.world.placement.BasePlacement;
import willow.train.hexie.world.placement.PartInstallation;

public class HorizontalBlockBase extends Block implements HexieWrenchable, PartStructure, WorldBlockBridge.HorizontalBlockBaseView {
    @Override public BlockPos partRoot(BlockGetter level,BlockPos pos,BlockState state) { return pos; }
    @Override public Map<BlockPos,BlockState> partCells(BlockGetter level,BlockPos root,BlockState state) {
        return LegacyPartLayout.cells(level,root,state);
    }

    public static final DirectionProperty HORIZENTAL_FACING =
            BlockStateProperties.HORIZONTAL_FACING;
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public HorizontalBlockBase(BlockBehaviour.Properties p_49795_) {
        super(p_49795_);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(LegacyPartLayout.OCCUPIED,false));
    }

    public BlockState rotate(BlockState p_54360_, Rotation p_54361_) {
        return p_54360_.setValue(FACING, p_54361_.rotate(p_54360_.getValue(FACING)));
    }

    public BlockState mirror(BlockState p_54357_, Mirror p_54358_) {
        return PanelDisplayColumn.mirror(
                p_54357_.rotate(p_54358_.getRotation(p_54357_.getValue(FACING))), p_54358_);
    }

    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return BasePlacement.ForHorizontalBlockBase.getStateForPlacement(this, context);
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_54370_) {
        p_54370_.add(FACING,LegacyPartLayout.OCCUPIED);
        if (this instanceof PassengerDisplayPanel)
            p_54370_.add(PanelDisplayMount.PROPERTY,
                    PanelDisplayColumn.PROPERTY);
    }

    @Override
    public List<ItemStack> getDrops(BlockState pState, LootParams.Builder pBuilder) {
        return PassengerDisplayPanel.withDisplayDrops(
                pState, List.of(this.asItem().getDefaultInstance()));
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving) {
        LegacyPartLayout.removed(level,pos,state,next,moving);
        super.onRemove(state,level,pos,next,moving);
    }
    @Override public void onBlockStateChange(LevelReader world,BlockPos pos,BlockState old,BlockState next) {
        if(world instanceof Level level)PartInstallation.stateChanged(level,pos,old,next);
    }
    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState previous,boolean moving) {
        BasePlacement.ForHorizontalBlockBase.onPlace(state, level, pos, previous, moving, () -> super.onPlace(state,level,pos,previous,moving));
    }
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos from,boolean moving) {
        LegacyPartLayout.schedule(level,pos,state,moving);
    }
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random) {
        LegacyPartLayout.upgrade(level,pos,state);
    }
    @Override public InteractionResult onWrenched(BlockState state,UseOnContext context) {
        return BaseBlockInteract.ForHorizontalBlockBase.onWrenched(this, state, context);
    }
    @Override
    public ItemInteractionResult useItemOn(ItemStack stack,
            BlockState state, Level level,
            BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return BaseBlockInteract.ForHorizontalBlockBase.useItemOn(stack, state, level, pos, player, hand, hit, () -> super.useItemOn(stack, state, level, pos, player, hand, hit));
    }

    @Override public Block asBlock() { return this; }
}
