package willow.train.hexie.item;

import java.util.LinkedHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.blocks.emu.head.*;
import willow.train.hexie.init.block.BlockInit;

public final class CRH2HeadItem extends BlockItem {
    public CRH2HeadItem(Block b,Properties p) { super(b,p); }
    @Override protected boolean placeBlock(BlockPlaceContext c,BlockState s) {
        var w=c.getLevel();var root=c.getClickedPos();
        return willow.train.hexie.blocks.parts.PartInstallation.install(w,((CRH2HeadBlock)getBlock()).partCells(w,root,s),
                p->false,p->c.getPlayer()==null||c.getPlayer().mayUseItemAt(p,c.getClickedFace(),c.getItemInHand()));
    }
}
