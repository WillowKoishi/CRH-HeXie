package willow.train.hexie.blocks.emu.head;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.*;

/** Invisible floor occupancy belonging to one head item; no blocks above the floor. */
public final class HeadFloorBlock extends Block implements com.simibubi.create.api.schematic.requirement.SpecialBlockItemRequirement {
    @Override public com.simibubi.create.content.schematics.requirement.ItemRequirement getRequiredItems(BlockState state,net.minecraft.world.level.block.entity.BlockEntity entity) {
        return com.simibubi.create.content.schematics.requirement.ItemRequirement.NONE;
    }
    public static final IntegerProperty PART=IntegerProperty.create("part",0,HeadGeometry.PARTS-1);
    public HeadFloorBlock(Properties p) {
        super(p.dynamicShape());
        registerDefaultState(stateDefinition.any().setValue(CRH2HeadBlock.FACING,Direction.NORTH).setValue(PART,0));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b) { b.add(CRH2HeadBlock.FACING,PART); }
    @Override public BlockState rotate(BlockState s,Rotation r) { return s.setValue(CRH2HeadBlock.FACING,r.rotate(s.getValue(CRH2HeadBlock.FACING))); }
    @Override public BlockState mirror(BlockState s,Mirror m) {
        if(m==Mirror.NONE)return s;
        int part=s.getValue(PART);
        return rotate(s,m.getRotation(s.getValue(CRH2HeadBlock.FACING))).setValue(PART,part/5*5+4-part%5);
    }
    @Override public RenderShape getRenderShape(BlockState s) { return RenderShape.INVISIBLE; }
    @Override public VoxelShape getShape(BlockState s,BlockGetter w,BlockPos p,CollisionContext c) { return HeadGeometry.floor(s.getValue(PART),s.getValue(CRH2HeadBlock.FACING)); }
    @Override public VoxelShape getCollisionShape(BlockState s,BlockGetter w,BlockPos p,CollisionContext c) { return getShape(s,w,p,c); }
    @Override public List<ItemStack> getDrops(BlockState s,LootParams.Builder b) {
        var origin=b.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN);
        var root=origin==null?null:HeadGeometry.owner(b.getLevel(),BlockPos.containing(origin));
        return root==null?List.of():List.of(b.getLevel().getBlockState(root).getBlock().asItem().getDefaultInstance());
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving) {
        boolean installingBogeySupport = false;
        if (state.getValue(PART) == HeadGeometry.BOGEY_PART
                && next.getBlock() instanceof willow.train.hexie.blocks.bogeys.HexieBogeyBlock<?> bogey
                && next.getValue(willow.train.hexie.blocks.bogeys.HexieBogeyBlock.HEAD_MOUNTED)) {
            var root = pos.subtract(HeadGeometry.offset(state.getValue(PART), state.getValue(CRH2HeadBlock.FACING)));
            var rootState = level.getBlockState(root);
            installingBogeySupport = rootState.getBlock() instanceof CRH2HeadBlock
                    && rootState.getValue(CRH2HeadBlock.FACING) == state.getValue(CRH2HeadBlock.FACING);
        }
        if(!moving&&!state.is(next.getBlock())&&!installingBogeySupport
                && !willow.train.hexie.blocks.parts.PartInstallation.mutating()) {
            var root=pos.subtract(HeadGeometry.offset(state.getValue(PART),state.getValue(CRH2HeadBlock.FACING)));
            if(level.hasChunkAt(root) && level.getBlockState(root).getBlock() instanceof CRH2HeadBlock head)
                willow.train.hexie.blocks.parts.PartInstallation.removeOtherCells(level,pos,head.partCells(level,root,level.getBlockState(root)));
        }
        super.onRemove(state,level,pos,next,moving);
    }
    @Override public ItemStack getCloneItemStack(LevelReader w,BlockPos p,BlockState s) { return willow.train.hexie.init.block.BlockInit.CRH2A_HEAD.get().asItem().getDefaultInstance(); }
    @Override public BlockState playerWillDestroy(Level w,BlockPos p,BlockState s,Player player) {
        var root=HeadGeometry.owner(w,p);
        if(!w.isClientSide && root!=null) w.destroyBlock(root,!player.isCreative(),player);
        return super.playerWillDestroy(w,p,s,player);
    }
    @Override protected void neighborChanged(BlockState s,Level w,BlockPos p,Block block,BlockPos from,boolean moving) {
        var root=HeadGeometry.owner(w,p);
        if(!w.isClientSide && root!=null) w.scheduleTick(root,w.getBlockState(root).getBlock(),1);
    }
}
