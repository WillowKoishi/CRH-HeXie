package willow.train.hexie.blocks.locos.head;

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
public final class HeadFloorBlock extends Block {
    public static final IntegerProperty PART=IntegerProperty.create("part",0,HeadGeometry.PARTS-1);
    public HeadFloorBlock(Properties p) {
        super(p.dynamicShape());
        registerDefaultState(stateDefinition.any().setValue(CRH2HeadBlock.FACING,Direction.NORTH).setValue(PART,0));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b) { b.add(CRH2HeadBlock.FACING,PART); }
    @Override public BlockState rotate(BlockState s,Rotation r) { return s.setValue(CRH2HeadBlock.FACING,r.rotate(s.getValue(CRH2HeadBlock.FACING))); }
    @Override public BlockState mirror(BlockState s,Mirror m) { return rotate(s,m.getRotation(s.getValue(CRH2HeadBlock.FACING))); }
    @Override public RenderShape getRenderShape(BlockState s) { return RenderShape.INVISIBLE; }
    @Override public VoxelShape getShape(BlockState s,BlockGetter w,BlockPos p,CollisionContext c) { return HeadGeometry.floor(s.getValue(PART),s.getValue(CRH2HeadBlock.FACING)); }
    @Override public VoxelShape getCollisionShape(BlockState s,BlockGetter w,BlockPos p,CollisionContext c) { return getShape(s,w,p,c); }
    @Override public List<ItemStack> getDrops(BlockState s,LootParams.Builder b) { return List.of(); }
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
