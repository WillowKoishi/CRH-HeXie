package willow.train.hexie.world.blocks.emu;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.bridge.world.CarriageBlockBridge;
import willow.train.hexie.core.api.interaction.HexieItemRequirementProvider;
import willow.train.hexie.world.collision.EmuCollision;
import willow.train.hexie.world.placement.PartInstallation;

/** Invisible floor occupancy belonging to one head item; no blocks above the floor. */
public final class HeadFloorBlock extends Block implements HexieItemRequirementProvider, CarriageBlockBridge.HeadFloorBlockView {
    @Override public Requirement getRequiredItems(BlockState state,BlockEntity entity) {
        return Requirement.NONE;
    }
    public static final IntegerProperty PART=IntegerProperty.create("part",0,HeadGeometry.PARTS-1);
    public HeadFloorBlock(BlockBehaviour.Properties p) {
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
    @Override public VoxelShape getShape(BlockState s,BlockGetter w,BlockPos p,CollisionContext c) {
        return EmuCollision.ForHeadFloorBlock.getShape(s, w, p, c);
    }
    @Override public VoxelShape getCollisionShape(BlockState s,BlockGetter w,BlockPos p,CollisionContext c) {
        return EmuCollision.ForHeadFloorBlock.getCollisionShape(this, s, w, p, c);
    }
    @Override public List<ItemStack> getDrops(BlockState s,LootParams.Builder b) {
        var origin=b.getOptionalParameter(LootContextParams.ORIGIN);
        var root=origin==null?null:HeadGeometry.owner(b.getLevel(),BlockPos.containing(origin));
        return root==null?List.of():List.of(b.getLevel().getBlockState(root).getBlock().asItem().getDefaultInstance());
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving) {
        boolean installingBogeySupport = false;
        if (state.getValue(PART) == HeadGeometry.BOGEY_PART
                && HeadBogey.isHeadMountedBogey(next)) {
            var root = pos.subtract(HeadGeometry.offset(state.getValue(PART), state.getValue(CRH2HeadBlock.FACING)));
            var rootState = level.getBlockState(root);
            installingBogeySupport = rootState.getBlock() instanceof CRH2HeadBlock
                    && rootState.getValue(CRH2HeadBlock.FACING) == state.getValue(CRH2HeadBlock.FACING);
            if (installingBogeySupport && !level.isClientSide)
                level.scheduleTick(root, rootState.getBlock(), 1);
        }
        if(!moving&&!state.is(next.getBlock())&&!installingBogeySupport
                && !PartInstallation.mutating()) {
            var root=pos.subtract(HeadGeometry.offset(state.getValue(PART),state.getValue(CRH2HeadBlock.FACING)));
            if(level.hasChunkAt(root) && level.getBlockState(root).getBlock() instanceof CRH2HeadBlock head)
                PartInstallation.removeOtherCells(level,pos,head.partCells(level,root,level.getBlockState(root)));
        }
        super.onRemove(state,level,pos,next,moving);
    }
    @Override public ItemStack getCloneItemStack(LevelReader w,BlockPos p,BlockState s) {
        var root=HeadGeometry.owner(w,p);
        return root==null?ItemStack.EMPTY:w.getBlockState(root).getBlock().asItem().getDefaultInstance();
    }
    @Override public BlockState playerWillDestroy(Level w,BlockPos p,BlockState s,Player player) {
        var root=HeadGeometry.owner(w,p);
        if(!w.isClientSide && root!=null) w.destroyBlock(root,!player.isCreative(),player);
        return super.playerWillDestroy(w,p,s,player);
    }
    @Override protected void neighborChanged(BlockState s,Level w,BlockPos p,Block block,BlockPos from,boolean moving) {
        var root=HeadGeometry.owner(w,p);
        if(!w.isClientSide && root!=null) w.scheduleTick(root,w.getBlockState(root).getBlock(),1);
    }

    @Override public Block asBlock() { return this; }
}
