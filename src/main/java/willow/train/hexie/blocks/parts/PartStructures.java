package willow.train.hexie.blocks.parts;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.blocks.emu.head.*;

/** Resolve the owner before dispatching installation, editing or assembly. */
public final class PartStructures {
    public record Root(BlockPos pos,BlockState state,PartStructure structure) {}
    private PartStructures() {}
    public static Root resolve(BlockGetter level,BlockPos pos) {
        if(level instanceof Level world&&!world.hasChunkAt(pos))return null;
        return resolve(level,pos,level.getBlockState(pos));
    }
    /** The explicit state also validates removals after the member cell has already changed to air. */
    public static Root resolve(BlockGetter level,BlockPos pos,BlockState state) {
        BlockPos root=pos;
        if(state.getBlock() instanceof PartMemberBlock)root=PartMemberBlock.owner(pos,state);
        else if(state.getBlock() instanceof HeadFloorBlock)root=pos.subtract(HeadGeometry.offset(state.getValue(HeadFloorBlock.PART),state.getValue(CRH2HeadBlock.FACING)));
        else if(state.getBlock() instanceof willow.train.hexie.blocks.bogeys.HexieBogeyBlock<?>
                &&state.getValue(willow.train.hexie.blocks.bogeys.HexieBogeyBlock.HEAD_MOUNTED)) {
            root=HeadGeometry.owner(level,pos);if(root==null)return null;
        }
        else if(state.getBlock() instanceof PartStructure part)root=part.partRoot(level,pos,state);
        if(level instanceof Level world&&!world.hasChunkAt(root))return null;
        var owner=level.getBlockState(root);
        if(!(owner.getBlock() instanceof PartStructure part)||!part.partRoot(level,root,owner).equals(root))return null;
        var expected=part.partCells(level,root,owner).get(pos);
        return state.equals(expected)?new Root(root,owner,part):null;
    }
}
