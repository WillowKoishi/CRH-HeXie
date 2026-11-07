package willow.train.hexie.blocks.base;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HorizontalBlockBase extends Block implements IWrenchable, willow.train.hexie.blocks.parts.PartStructure {
    @Override public net.minecraft.core.BlockPos partRoot(net.minecraft.world.level.BlockGetter level,net.minecraft.core.BlockPos pos,BlockState state) { return pos; }
    @Override public java.util.Map<net.minecraft.core.BlockPos,BlockState> partCells(net.minecraft.world.level.BlockGetter level,net.minecraft.core.BlockPos root,BlockState state) {
        return willow.train.hexie.blocks.parts.LegacyPartLayout.cells(level,root,state);
    }
    protected static final Map<Block, Map<Direction, VoxelShape>> SHAPS =
            new HashMap<Block, Map<Direction, VoxelShape>>();
    public static final DirectionProperty HORIZENTAL_FACING =
            BlockStateProperties.HORIZONTAL_FACING;
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public HorizontalBlockBase(Properties p_49795_) {
        super(p_49795_);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(willow.train.hexie.blocks.parts.LegacyPartLayout.OCCUPIED,false));
    }

    public BlockState rotate(BlockState p_54360_, Rotation p_54361_) {
        return p_54360_.setValue(FACING, p_54361_.rotate(p_54360_.getValue(FACING)));
    }

    public BlockState mirror(BlockState p_54357_, Mirror p_54358_) {
        return willow.train.hexie.blocks.signs.PanelDisplayColumn.mirror(
                p_54357_.rotate(p_54358_.getRotation(p_54357_.getValue(FACING))), p_54358_);
    }

    public BlockState getStateForPlacement(BlockPlaceContext context) {

        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_54370_) {
        p_54370_.add(FACING,willow.train.hexie.blocks.parts.LegacyPartLayout.OCCUPIED);
        if (this instanceof willow.train.hexie.blocks.signs.PassengerDisplayPanel)
            p_54370_.add(willow.train.hexie.blocks.signs.PanelDisplayMount.PROPERTY,
                    willow.train.hexie.blocks.signs.PanelDisplayColumn.PROPERTY);
    }

    @Override
    public List<ItemStack> getDrops(BlockState pState, LootParams.Builder pBuilder) {
        return willow.train.hexie.blocks.signs.PassengerDisplayPanel.withDisplayDrops(
                pState, List.of(this.asItem().getDefaultInstance()));
    }
    @Override protected void onRemove(BlockState state,net.minecraft.world.level.Level level,net.minecraft.core.BlockPos pos,BlockState next,boolean moving) {
        willow.train.hexie.blocks.parts.LegacyPartLayout.removed(level,pos,state,next,moving);
        super.onRemove(state,level,pos,next,moving);
    }
    @Override public void onBlockStateChange(net.minecraft.world.level.LevelReader world,net.minecraft.core.BlockPos pos,BlockState old,BlockState next) {
        if(world instanceof net.minecraft.world.level.Level level)willow.train.hexie.blocks.parts.PartInstallation.stateChanged(level,pos,old,next);
    }
    @Override protected void onPlace(BlockState state,net.minecraft.world.level.Level level,net.minecraft.core.BlockPos pos,BlockState previous,boolean moving) {
        super.onPlace(state,level,pos,previous,moving);
        willow.train.hexie.blocks.parts.LegacyPartLayout.schedule(level,pos,state,moving);
    }
    @Override protected void neighborChanged(BlockState state,net.minecraft.world.level.Level level,net.minecraft.core.BlockPos pos,Block block,net.minecraft.core.BlockPos from,boolean moving) {
        willow.train.hexie.blocks.parts.LegacyPartLayout.schedule(level,pos,state,moving);
    }
    @Override protected void tick(BlockState state,net.minecraft.server.level.ServerLevel level,net.minecraft.core.BlockPos pos,net.minecraft.util.RandomSource random) {
        willow.train.hexie.blocks.parts.LegacyPartLayout.upgrade(level,pos,state);
    }
    @Override public net.minecraft.world.InteractionResult onWrenched(BlockState state,net.minecraft.world.item.context.UseOnContext context) {
        var rotated=getRotatedBlockState(state,context.getClickedFace());
        if(rotated.equals(state))return net.minecraft.world.InteractionResult.PASS;
        var level=context.getLevel();var pos=context.getClickedPos();
        if(!willow.train.hexie.blocks.parts.PartInstallation.replace(level,partCells(level,pos,state),partCells(level,pos,rotated),
                at->false,at->context.getPlayer()==null||context.getPlayer().mayUseItemAt(at,context.getClickedFace(),context.getItemInHand())))
            return net.minecraft.world.InteractionResult.FAIL;
        if(!level.isClientSide)IWrenchable.playRotateSound(level,pos);
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override
    public net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,
            net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos, net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        var placement = willow.train.hexie.blocks.carriage.TrainPanelPlacement.place(stack, state, level, pos, player, hand, hit);
        return placement != null ? placement : super.useItemOn(stack, state, level, pos, player, hand, hit);
    }
}
