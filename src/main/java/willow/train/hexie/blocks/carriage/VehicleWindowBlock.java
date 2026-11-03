package willow.train.hexie.blocks.carriage;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import willow.train.hexie.blocks.parts.*;

/** Old window state IDs retain their meaning while using the shared occupied-layout lifecycle. */
public class VehicleWindowBlock extends TrapDoorBlock implements PartStructure,com.simibubi.create.content.equipment.wrench.IWrenchable {
    protected int type;
    @Override public BlockState mirror(BlockState state,Mirror mirror) {
        return mirror==Mirror.NONE?state:super.mirror(state,mirror).cycle(HALF);
    }
    @Override public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,net.minecraft.world.phys.shapes.CollisionContext context) {
        return PartShapeDefinitions.get(type==0?"window_single":state.getValue(HALF)==Half.TOP?"window_double_right":"window_double_left",state.getValue(FACING));
    }
    protected Half getHinge(BlockPlaceContext pContext) {
        BlockGetter blockgetter = pContext.getLevel();
        BlockPos blockpos = pContext.getClickedPos();
        Direction direction = pContext.getHorizontalDirection();
        BlockPos blockpos1 = blockpos.above();
        Direction direction1 = direction.getCounterClockWise();
        BlockPos blockpos2 = blockpos.relative(direction1);
        BlockState blockstate = blockgetter.getBlockState(blockpos2);
        BlockPos blockpos3 = blockpos1.relative(direction1);
        BlockState blockstate1 = blockgetter.getBlockState(blockpos3);
        Direction direction2 = direction.getClockWise();
        BlockPos blockpos4 = blockpos.relative(direction2);
        BlockState blockstate2 = blockgetter.getBlockState(blockpos4);
        BlockPos blockpos5 = blockpos1.relative(direction2);
        BlockState blockstate3 = blockgetter.getBlockState(blockpos5);
        int i =
                (blockstate.isCollisionShapeFullBlock(blockgetter, blockpos2) ? -1 : 0)
                        + (blockstate1.isCollisionShapeFullBlock(blockgetter, blockpos3) ? -1 : 0)
                        + (blockstate2.isCollisionShapeFullBlock(blockgetter, blockpos4) ? 1 : 0)
                        + (blockstate3.isCollisionShapeFullBlock(blockgetter, blockpos5) ? 1 : 0);
        if (i <= 0) {
            if (i >= 0) {
                int j = direction.getStepX();
                int k = direction.getStepZ();
                Vec3 vec3 = pContext.getClickLocation();
                double d0 = vec3.x - (double) blockpos.getX();
                double d1 = vec3.z - (double) blockpos.getZ();
                return (j >= 0 || !(d1 < 0.5D))
                                && (j <= 0 || !(d1 > 0.5D))
                                && (k >= 0 || !(d0 > 0.5D))
                                && (k <= 0 || !(d0 < 0.5D))
                        ? Half.BOTTOM
                        : Half.TOP;
            } else {
                return Half.BOTTOM;
            }
        } else {
            return Half.TOP;
        }
    }

    public VehicleWindowBlock(BlockSetType type,Properties properties) {
        super(type,properties);registerDefaultState(defaultBlockState().setValue(LegacyPartLayout.OCCUPIED,false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) {
        super.createBlockStateDefinition(builder);builder.add(LegacyPartLayout.OCCUPIED);
    }
    @Override public BlockPos partRoot(BlockGetter level,BlockPos pos,BlockState state) { return pos; }
    @Override public Map<BlockPos,BlockState> partCells(BlockGetter level,BlockPos root,BlockState state) { return LegacyPartLayout.cells(level,root,state); }
    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState previous,boolean moving) {
        super.onPlace(state,level,pos,previous,moving);LegacyPartLayout.schedule(level,pos,state,moving);
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving) {
        LegacyPartLayout.removed(level,pos,state,next,moving);super.onRemove(state,level,pos,next,moving);
    }
    @Override public void onBlockStateChange(LevelReader world,BlockPos pos,BlockState old,BlockState next) {
        if(world instanceof Level level)PartInstallation.stateChanged(level,pos,old,next);
    }
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos from,boolean moving) {
        super.neighborChanged(state,level,pos,block,from,moving);LegacyPartLayout.schedule(level,pos,state,moving);
    }
    @Override protected void tick(BlockState state,net.minecraft.server.level.ServerLevel level,BlockPos pos,net.minecraft.util.RandomSource random) {
        LegacyPartLayout.upgrade(level,pos,state);
    }
    @Override public net.minecraft.world.InteractionResult onWrenched(BlockState state,net.minecraft.world.item.context.UseOnContext context) {
        var next=getRotatedBlockState(state,context.getClickedFace());var pos=context.getClickedPos();var level=context.getLevel();
        if(state.equals(next))return net.minecraft.world.InteractionResult.PASS;
        if(!PartInstallation.replace(level,partCells(level,pos,state),partCells(level,pos,next),p->false,
                p->context.getPlayer()==null||context.getPlayer().mayUseItemAt(p,context.getClickedFace(),context.getItemInHand())))return net.minecraft.world.InteractionResult.FAIL;
        if(!level.isClientSide)com.simibubi.create.content.equipment.wrench.IWrenchable.playRotateSound(level,pos);
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }
}
