package willow.train.hexie.item;

import java.util.LinkedHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.blocks.locos.head.*;
import willow.train.hexie.init.block.BlockInit;

public final class CRH2HeadItem extends BlockItem {
    public CRH2HeadItem(Block b,Properties p) { super(b,p); }
    @Override protected boolean placeBlock(BlockPlaceContext c,BlockState s) {
        var w=c.getLevel();var root=c.getClickedPos();var facing=s.getValue(CRH2HeadBlock.FACING);
        var before=new LinkedHashMap<BlockPos,BlockState>();
        for(int i=0;i<HeadGeometry.PARTS;i++) {
            var p=root.offset(HeadGeometry.offset(i,facing));
            if(w.isOutsideBuildHeight(p)||!w.hasChunkAt(p)||!w.getWorldBorder().isWithinBounds(p)) return false;
            var old=w.getBlockState(p);
            if(!old.canBeReplaced() || (c.getPlayer()!=null && !c.getPlayer().mayUseItemAt(p,c.getClickedFace(),c.getItemInHand()))) return false;
            before.put(p,old);
        }
        if(!super.placeBlock(c,s)) return false;
        for(int i=0;i<HeadGeometry.PARTS;i++) {
            if(i==HeadGeometry.ROOT_PART)continue;
            var p=root.offset(HeadGeometry.offset(i,facing));
            var part=BlockInit.CRH2A_HEAD_FLOOR.get().defaultBlockState().setValue(CRH2HeadBlock.FACING,facing).setValue(HeadFloorBlock.PART,i);
            if(!w.setBlock(p,part,Block.UPDATE_ALL)) {
                // Roll back this single placement, including replaceable plants/fluids.
                for(var entry:before.entrySet()) w.setBlock(entry.getKey(),entry.getValue(),Block.UPDATE_ALL|Block.UPDATE_MOVE_BY_PISTON);
                return false;
            }
        }
        return true;
    }
}
