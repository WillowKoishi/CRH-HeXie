package willow.train.hexie.blocks.locos.crh2a;

import java.util.*;
import net.minecraft.core.BlockPos;




/** Removes obsolete skirt helper cells from older saves. */
@net.neoforged.fml.common.EventBusSubscriber(modid=willow.train.hexie.Main.MOD_ID)
public final class CRH2RemoteShape {
    private CRH2RemoteShape() {}
    /** Remove only the obsolete helper cells written by the previous skirt implementation. */
    @net.neoforged.bus.api.SubscribeEvent
    public static void loaded(net.neoforged.neoforge.event.level.ChunkEvent.Load event) {
        if(!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level))return;
        var chunk=event.getChunk();var stale=new ArrayList<BlockPos>();var sections=chunk.getSections();
        for(int i=0;i<sections.length;i++) {
            var section=sections[i];
            if(!section.maybeHas(s->s.getBlock() instanceof CRH2PartBlock b&&b.hasRemoteShape()&&s.getValue(CRH2PartBlock.PART)!=0))continue;
            int baseY=chunk.getMinBuildHeight()+i*16;
            for(int x=0;x<16;x++)for(int y=0;y<16;y++)for(int z=0;z<16;z++) {
                var s=section.getBlockState(x,y,z);
                if(s.getBlock() instanceof CRH2PartBlock b&&b.hasRemoteShape()&&s.getValue(CRH2PartBlock.PART)!=0)
                    stale.add(new BlockPos(chunk.getPos().getMinBlockX()+x,baseY+y,chunk.getPos().getMinBlockZ()+z));
            }
        }
        if(!stale.isEmpty())level.getServer().execute(()->stale.forEach(pos->{
            if(!level.hasChunkAt(pos))return;
            var s=level.getBlockState(pos);
            if(s.getBlock() instanceof CRH2PartBlock b&&b.hasRemoteShape()&&s.getValue(CRH2PartBlock.PART)!=0)level.removeBlock(pos,false);
        }));
    }
}
