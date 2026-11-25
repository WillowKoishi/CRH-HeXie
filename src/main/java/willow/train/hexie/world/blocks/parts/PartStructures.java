package willow.train.hexie.world.blocks.parts;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.bridge.world.PartBlockBridge.PartStructureView;
import willow.train.hexie.bridge.world.PartBlockBridge;
import willow.train.hexie.world.blocks.emu.CRH2HeadBlock;
import willow.train.hexie.world.blocks.emu.HeadBogey;
import willow.train.hexie.world.blocks.emu.HeadFloorBlock;
import willow.train.hexie.world.blocks.emu.HeadGeometry;

/** Resolve the owner before dispatching installation, editing or assembly. */
public final class PartStructures {
    public record Root(BlockPos pos,BlockState state,PartStructureView structure) implements PartBlockBridge.PartStructuresRootView {}
    private PartStructures() {}
    public static PartStructures.Root resolve(BlockGetter level,BlockPos pos) {
        if(level instanceof Level world&&!world.hasChunkAt(pos))return null;
        return resolve(level,pos,level.getBlockState(pos));
    }
    /** The explicit state also validates removals after the member cell has already changed to air. */
    public static PartStructures.Root resolve(BlockGetter level,BlockPos pos,BlockState state) {
        BlockPos root=pos;
        if(state.getBlock() instanceof PartMemberBlock)root=PartMemberBlock.owner(pos,state);
        else if(state.getBlock() instanceof HeadFloorBlock)root=pos.subtract(HeadGeometry.offset(state.getValue(HeadFloorBlock.PART),state.getValue(CRH2HeadBlock.FACING)));
        else if(HeadBogey.isHeadMountedBogey(state)) {
            root=HeadGeometry.owner(level,pos);if(root==null)return null;
        }
        else if(state.getBlock() instanceof PartStructureView part)root=part.partRoot(level,pos,state);
        if(level instanceof Level world&&!world.hasChunkAt(root))return null;
        var owner=level.getBlockState(root);
        if(!(owner.getBlock() instanceof PartStructureView part)||!part.partRoot(level,root,owner).equals(root))return null;
        var expected=part.partCells(level,root,owner).get(pos);
        return state.equals(expected)?new PartStructures.Root(root,owner,part):null;
    }
}
