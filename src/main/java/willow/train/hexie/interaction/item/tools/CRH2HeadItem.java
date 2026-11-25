package willow.train.hexie.interaction.item.tools;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH2HeadBlockView;

public final class CRH2HeadItem extends BlockItem {
    public CRH2HeadItem(Block b,Properties p) { super(b,p); }
    @Override public BlockPlaceContext updatePlacementContext(BlockPlaceContext context) {
        return ((CRH2HeadBlockView) getBlock()).placementContext(context);
    }
    @Override protected boolean placeBlock(BlockPlaceContext c,BlockState s) {
        var w=c.getLevel();var root=c.getClickedPos();
        return HexieBridges.partBlock().partInstallation().install(w,((CRH2HeadBlockView)getBlock()).partCells(w,root,s),
                p->false,p->c.getPlayer()==null||c.getPlayer().mayUseItemAt(p,c.getClickedFace(),c.getItemInHand()));
    }
}
