package willow.train.hexie.blocks.parts;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Upgrade loaded old roots atomically. Conflicting old builds stay intact until space is available. */
@EventBusSubscriber(modid=willow.train.hexie.Main.MOD_ID)
public final class PartLayoutMigration {
    private static final Map<ServerLevel,LinkedHashSet<BlockPos>> PENDING=new WeakHashMap<>();
    private PartLayoutMigration() {}
    @SubscribeEvent public static void loaded(ChunkEvent.Load event) {
        if(!(event.getLevel() instanceof ServerLevel level)||!(event.getChunk() instanceof LevelChunk chunk))return;
        var pending=PENDING.computeIfAbsent(level,ignored->new LinkedHashSet<>());
        var sections=chunk.getSections();var origin=chunk.getPos().getWorldPosition();
        for(int index=0;index<sections.length;index++) {
            var section=sections[index];
            if(!section.maybeHas(state->state.getBlock() instanceof PartStructure||state.getBlock() instanceof PartMemberBlock))continue;
            int baseY=chunk.getSectionYFromSectionIndex(index)*16;
            for(int x=0;x<16;x++)for(int y=0;y<16;y++)for(int z=0;z<16;z++) {
                var state=section.getBlockState(x,y,z);
                var pos=new BlockPos(origin.getX()+x,baseY+y,origin.getZ()+z);
                if(state.getBlock() instanceof PartMemberBlock) {pending.add(pos);continue;}
                if(!(state.getBlock() instanceof PartStructure part))continue;
                if(!part.partRoot(level,pos,state).equals(pos))continue;
                if(LegacyPartLayout.supports(state)||state.getBlock() instanceof willow.train.hexie.blocks.emu.crh2a.CRH2PartBlock
                        ||state.getBlock() instanceof willow.train.hexie.blocks.emu.head.CRH2HeadBlock)pending.add(pos);
            }
        }
    }
    @SubscribeEvent public static void tick(LevelTickEvent.Post event) {
        if(!(event.getLevel() instanceof ServerLevel level)||level.getGameTime()%20!=0)return;
        var pending=PENDING.get(level);if(pending==null||pending.isEmpty())return;
        var batch=pending.stream().limit(64).toList();pending.removeAll(batch);
        for(var pos:batch) {
            if(!level.hasChunkAt(pos))continue;
            var state=level.getBlockState(pos);
            if(state.getBlock() instanceof PartMemberBlock) {
                if(!PartMemberBlock.checkOwner(level,pos,state))pending.add(pos);
                continue;
            }
            if(!(state.getBlock() instanceof PartStructure part)||!part.partRoot(level,pos,state).equals(pos))continue;
            var target=LegacyPartLayout.placed(state);
            if(!PartInstallation.install(level,part.partCells(level,pos,target),pos::equals,at->true))pending.add(pos);
        }
    }
}
