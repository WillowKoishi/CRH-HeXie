package willow.train.hexie.blocks.parts;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import willow.train.hexie.init.block.BlockInit;

/** Explicit migration boundary: absent/false means the old single-root layout is still intact. */
public final class LegacyPartLayout {
    public static final BooleanProperty OCCUPIED=BooleanProperty.create("occupied");
    private LegacyPartLayout() {}
    public static BlockState placed(BlockState state) {
        return supports(state)?state.setValue(OCCUPIED,true):state;
    }
    public static boolean supports(BlockState state) {
        return !(state.getBlock() instanceof willow.train.hexie.blocks.emu.crh2a.CRH2PartBlock)
                &&state.hasProperty(OCCUPIED)&&PartDefinitions.get(state).extension()==PartDefinitions.Extension.SIDE;
    }
    public static Map<BlockPos,BlockState> cells(BlockGetter level,BlockPos root,BlockState state) {
        var result=new LinkedHashMap<BlockPos,BlockState>();result.put(root,state);
        if(!supports(state)||!state.getValue(OCCUPIED))return result;
        var shape=state.getShape(level,root);
        if(shape.isEmpty())return result;
        var bounds=shape.bounds();var normal=state.getValue(BlockStateProperties.HORIZONTAL_FACING).getAxis();
        var tangent=normal==net.minecraft.core.Direction.Axis.X?net.minecraft.core.Direction.Axis.Z:net.minecraft.core.Direction.Axis.X;
        for(int u=(int)Math.floor(bounds.min(tangent)+1e-6);u<(int)Math.ceil(bounds.max(tangent)-1e-6);u++)
            for(int y=(int)Math.floor(bounds.minY+1e-6);y<(int)Math.ceil(bounds.maxY-1e-6);y++) {
                if(u==0&&y==0)continue;
                if(Math.abs(u)>3||Math.abs(y)>3)throw new IllegalStateException("Vehicle side part exceeds its declared member range");
                var offset=normal==net.minecraft.core.Direction.Axis.X?new BlockPos(0,y,u):new BlockPos(u,y,0);
                result.put(root.offset(offset),PartMemberBlock.withOwnerOffset(
                        BlockInit.VEHICLE_PART_MEMBER.get().defaultBlockState(),new BlockPos(-offset.getX(),-offset.getY(),-offset.getZ())));
            }
        return result;
    }
    public static void removed(Level level,BlockPos pos,BlockState old,BlockState next,boolean moving) {
        if(!moving&&!old.is(next.getBlock())&&supports(old)&&old.getValue(OCCUPIED))
            PartInstallation.removeOtherCells(level,pos,cells(level,pos,old));
    }
    public static void schedule(Level level,BlockPos pos,BlockState state,boolean moving) {
        if(!level.isClientSide&&!moving&&!PartInstallation.mutating()&&supports(state))level.scheduleTick(pos,state.getBlock(),1);
    }
    public static boolean upgrade(Level level,BlockPos root,BlockState state) {
        if(!supports(state))return true;
        var installed=placed(state);
        // A conflict keeps the old flag and geometry. Never overwrite an existing overlapping build.
        return PartInstallation.install(level,cells(level,root,installed),root::equals,pos->true);
    }
}
