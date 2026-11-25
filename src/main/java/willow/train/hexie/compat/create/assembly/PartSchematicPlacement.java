package willow.train.hexie.compat.create.assembly;

import java.util.*;
import com.simibubi.create.api.schematic.state.SchematicStateFilter;
import com.simibubi.create.api.schematic.state.SchematicStateFilterRegistry;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH2HeadBlockView;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH2PartBlockView;
import willow.train.hexie.bridge.world.CarriageBlockBridge.HeadFloorBlockView;
import willow.train.hexie.bridge.world.PartBlockBridge.PartMemberBlockView;
import willow.train.hexie.bridge.world.PartBlockBridge.PartStructureView;

/** Create's printer supplies target states; all member writes belong to the same item transaction. */
public final class PartSchematicPlacement {
    public enum Result { UNHANDLED, INSTALLED, RETRY }
    private PartSchematicPlacement() {}
    public static boolean member(BlockState state) {
        return state.getBlock() instanceof HeadFloorBlockView||state.getBlock() instanceof PartMemberBlockView
                ||state.getBlock() instanceof PartStructureView part&&part.isPartMember()
                ||state.getBlock() instanceof CRH2PartBlockView&&state.getValue(HexieBridges.carriageBlock().crh2PartBlock().part())!=0
                ||state.getBlock() instanceof PartStructureView&&state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock
                    &&state.getValue(net.minecraft.world.level.block.DoorBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER;
    }
    public static boolean root(BlockState state) {
        return state.getBlock() instanceof CRH2HeadBlockView||state.getBlock() instanceof CRH2PartBlockView&&!member(state)
                ||state.getBlock() instanceof PartStructureView part&&part.schematicRoot()&&!member(state)||HexieBridges.partBlock().legacyPartLayout().supports(state);
    }
    /** Missing members in old schematics still belong to their declared root, never to an AIR removal shot. */
    public static Set<BlockPos> targetMembers(SchematicLevel schematic) {
        if(schematic==null)return Set.of();
        var members=new HashSet<BlockPos>();
        for(var entry:schematic.getBlockMap().entrySet()) {
            if(!root(entry.getValue()))continue;
            var pos=entry.getKey().offset(schematic.anchor);
            var target=HexieBridges.partBlock().legacyPartLayout().placed(entry.getValue());
            for(var cell:((PartStructureView)target.getBlock()).partCells(schematic,pos,target).keySet())
                if(!cell.equals(pos))members.add(cell.immutable());
        }
        return Set.copyOf(members);
    }
    private static Map<BlockPos,BlockState> previous(Level level,BlockPos pos) {
        var owner=HexieBridges.partBlock().partStructures().resolve(level,pos);
        return owner==null?Map.of():owner.structure().partCells(level,owner.pos(),owner.state());
    }
    /** Keep BlockHelper's filter precedence and transient state cleanup before planning any occupied cells. */
    private static BlockState target(Level level,BlockPos pos,BlockState state) {
        var filter=SchematicStateFilterRegistry.REGISTRY.get(state);
        var existing=level.getBlockEntity(pos);
        if(filter!=null)state=filter.filterStates(existing,state);
        else if(state.getBlock() instanceof SchematicStateFilter blockFilter)state=blockFilter.filterStates(existing,state);
        if(state.hasProperty(BlockStateProperties.EXTENDED))state=state.setValue(BlockStateProperties.EXTENDED,false);
        if(state.hasProperty(BlockStateProperties.WATERLOGGED))state=state.setValue(BlockStateProperties.WATERLOGGED,false);
        return HexieBridges.partBlock().legacyPartLayout().placed(state);
    }
    private static Map<BlockPos,BlockState> cells(Level level,BlockPos pos,BlockState target) {
        return target.getBlock() instanceof PartStructureView part?part.partCells(level,pos,target):Map.of(pos,target);
    }
    public static boolean canPlace(Level level,BlockPos pos,BlockState state) {
        if(member(state))return false;
        if(!root(state))return true;
        if(!level.hasChunkAt(pos)||level.isOutsideBuildHeight(pos)||!level.getWorldBorder().isWithinBounds(pos))return false;
        var target=target(level,pos,state);
        var before=previous(level,pos);
        return canPlaceTarget(level,pos,target,before);
    }
    private static boolean canPlaceTarget(Level level,BlockPos pos,BlockState target,Map<BlockPos,BlockState> before) {
        if(before.keySet().stream().anyMatch(at->!level.hasChunkAt(at)||level.getBlockState(at).getDestroySpeed(level,at)<0))return false;
        return HexieBridges.partBlock().partInstallation().available(level,cells(level,pos,target),
                at->level.getBlockState(at).equals(before.get(at))||at.equals(pos)&&level.getBlockState(at).getDestroySpeed(level,at)>=0,at->true);
    }
    public static boolean place(Level level,BlockPos pos,BlockState state,ItemStack stack,CompoundTag data) {
        return placeResult(level,pos,state,stack,data)!=Result.UNHANDLED;
    }
    /** A launched block retains its already-consumed item until the whole layout commits. */
    public static Result placeResult(Level level,BlockPos pos,BlockState state,ItemStack stack,CompoundTag data) {
        if(member(state))return Result.INSTALLED;
        if(!root(state))return Result.UNHANDLED;
        if(!level.hasChunkAt(pos)||level.isOutsideBuildHeight(pos)||!level.getWorldBorder().isWithinBounds(pos))return Result.RETRY;
        var target=target(level,pos,state);
        var before=previous(level,pos);
        if(!canPlaceTarget(level,pos,target,before))return Result.RETRY;
        boolean installed=HexieBridges.partBlock().partInstallation().replace(level,before,cells(level,pos,target),
                at->at.equals(pos)&&level.getBlockState(at).getDestroySpeed(level,at)>=0,at->true,()->{
                    var entity=level.getBlockEntity(pos);
                    if(entity!=null&&data!=null) {
                        var copied=data.copy();copied.putInt("x",pos.getX());copied.putInt("y",pos.getY());copied.putInt("z",pos.getZ());
                        entity.loadWithComponents(copied,level.registryAccess());entity.setChanged();
                        level.sendBlockUpdated(pos,target,target,3);
                    }
                    target.getBlock().setPlacedBy(level,pos,target,null,stack);
                });
        return installed?Result.INSTALLED:Result.RETRY;
    }
}
