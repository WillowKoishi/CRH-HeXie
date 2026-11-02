package willow.train.hexie.blocks.locos.crh2a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;
import willow.train.hexie.blocks.base.HorizontalBlockBase;

/** Ordinary single block, with an exterior side slab like the existing carriage panels. */
public class CRH2PartBlock extends HorizontalBlockBase {
    private final CRH2PartShapes geometry;
    public CRH2PartBlock(Properties properties, String model) {
        super(properties.dynamicShape());
        geometry = new CRH2PartShapes(model);
    }
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty PART = net.minecraft.world.level.block.state.properties.IntegerProperty.create("part",0,15);
    private static final ThreadLocal<Boolean> CLEANING=ThreadLocal.withInitial(()->false);
    @Override protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<net.minecraft.world.level.block.Block,BlockState> b) {
        super.createBlockStateDefinition(b);b.add(PART);
    }
    public boolean hasRemoteShape() { return geometry.remote; }
    private static boolean right(BlockState s) {
        return s.hasProperty(CRH2PanelBlock.HINGE)&&s.getValue(CRH2PanelBlock.HINGE)==net.minecraft.world.level.block.state.properties.DoorHingeSide.RIGHT;
    }
    public BlockPos offset(int i,BlockState state) {
        var p=geometry.cells.get(i);
        if(right(state))p=new BlockPos(-p.getX(),p.getY(),p.getZ());
        return CRH2PartShapes.rotate(p,state.getValue(FACING));
    }
    public int partCount() { return geometry.cells.size(); }
    public BlockPos offset(int i,Direction facing) { return CRH2PartShapes.rotate(geometry.cells.get(i),facing); }
    public BlockPos root(BlockPos pos,BlockState state) {
        int i=state.getValue(PART);return i<partCount()?pos.subtract(offset(i,state)):pos;
    }
    @Override public net.minecraft.world.level.block.RenderShape getRenderShape(BlockState s) {
        return s.getValue(PART)==0?net.minecraft.world.level.block.RenderShape.MODEL:net.minecraft.world.level.block.RenderShape.INVISIBLE;
    }
    @Override public void onBlockStateChange(net.minecraft.world.level.LevelReader world, BlockPos pos, BlockState oldState, BlockState newState) {
        if (world instanceof Level level && level.isClientSide)
            willow.train.hexie.client.render.CRH2StaticSections.changed(world, pos, oldState, newState);
    }
    public AABB placementBounds(BlockState state) { return geometry.bounds(state.getValue(FACING),right(state)); }
    @Override public VoxelShape getShape(BlockState s, BlockGetter w, BlockPos p, CollisionContext c) {
        return geometry.piece(s.getValue(PART),s.getValue(FACING),right(s));
    }
    @Override public VoxelShape getCollisionShape(BlockState s, BlockGetter w, BlockPos p, CollisionContext c) { return getShape(s,w,p,c); }

    /** Only the two-block window adds a side-plane obstruction check. */
    public boolean canFitSide(Level level, BlockPos pos, BlockState state) {
        if (!geometry.checkSide) return true;
        var side = geometry.get(state.getValue(FACING),right(state)).move(pos.getX(),pos.getY(),pos.getZ());
        var bounds = side.bounds();
        for (var at : BlockPos.betweenClosed((int)Math.floor(bounds.minX+1e-7),(int)Math.floor(bounds.minY+1e-7),(int)Math.floor(bounds.minZ+1e-7),
                (int)Math.ceil(bounds.maxX-1e-7)-1,(int)Math.ceil(bounds.maxY-1e-7)-1,(int)Math.ceil(bounds.maxZ-1e-7)-1)) {
            if (at.equals(pos)) continue;
            var other = level.getBlockState(at);
            var obstacle = other.getCollisionShape(level,at).move(at.getX(),at.getY(),at.getZ());
            if (Shapes.joinIsNotEmpty(side,obstacle,BooleanOp.AND)) return false;
        }
        return true;
    }

    public void fillSide(Level level,BlockPos root,BlockState state) {
        for(int i=1;i<partCount();i++) {
            var at=root.offset(offset(i,state));
            if(level.getBlockState(at).canBeReplaced()) level.setBlock(at,state.setValue(PART,i),3);
        }
    }
    @Override protected void onRemove(BlockState s,Level level,BlockPos pos,BlockState next,boolean moving) {
        if(!level.isClientSide&&!moving&&!s.is(next.getBlock())&&!CLEANING.get()) {
            CLEANING.set(true);
            try {
                var root=root(pos,s);var facing=s.getValue(FACING);
                for(int i=0;i<partCount();i++) {
                    var at=root.offset(offset(i,s));var other=level.getBlockState(at);
                    if(!at.equals(pos)&&other.is(this)&&other.equals(s.setValue(PART,i)))level.removeBlock(at,false);
                }
            } finally { CLEANING.set(false); }
        }
        super.onRemove(s,level,pos,next,moving);
    }
    @Override public java.util.List<net.minecraft.world.item.ItemStack> getDrops(BlockState s,net.minecraft.world.level.storage.loot.LootParams.Builder b) {
        return s.getValue(PART)==0?super.getDrops(s,b):java.util.List.of();
    }
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState s,net.minecraft.world.entity.player.Player player) {
        if(!level.isClientSide&&s.getValue(PART)!=0)level.destroyBlock(root(pos,s),!player.isCreative(),player);
        return super.playerWillDestroy(level,pos,s,player);
    }

    @Override public net.minecraft.world.InteractionResult onWrenched(BlockState s,net.minecraft.world.item.context.UseOnContext c) {
        var level=c.getLevel();var root=root(c.getClickedPos(),s);var owner=level.getBlockState(root);
        if(!owner.is(this)||owner.getValue(PART)!=0)return net.minecraft.world.InteractionResult.PASS;
        var rotated=getRotatedBlockState(owner,c.getClickedFace());
        if(rotated.equals(owner))return net.minecraft.world.InteractionResult.PASS;
        if(level.isClientSide)return net.minecraft.world.InteractionResult.SUCCESS;
        var saved=new java.util.LinkedHashMap<BlockPos,BlockState>();
        for(int i=1;i<partCount();i++) {
            var at=root.offset(offset(i,owner));var other=level.getBlockState(at);
            if(other.is(this)&&other.equals(owner.setValue(PART,i)))saved.put(at,other);
        }
        saved.keySet().forEach(at->level.setBlock(at,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2|64));
        if(!canFitSide(level,root,rotated)) {
            saved.forEach((at,old)->level.setBlock(at,old,2|64));return net.minecraft.world.InteractionResult.FAIL;
        }
        level.setBlock(root,rotated,3);fillSide(level,root,rotated);
        com.simibubi.create.content.equipment.wrench.IWrenchable.playRotateSound(level,root);
        return net.minecraft.world.InteractionResult.SUCCESS;
    }
    @Override public net.minecraft.world.InteractionResult onSneakWrenched(BlockState s,net.minecraft.world.item.context.UseOnContext c) {
        var root=root(c.getClickedPos(),s);var owner=c.getLevel().getBlockState(root);
        if(!owner.is(this))return net.minecraft.world.InteractionResult.PASS;
        return super.onSneakWrenched(owner,new net.minecraft.world.item.context.UseOnContext(c.getLevel(),c.getPlayer(),c.getHand(),c.getItemInHand(),
            new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(root),c.getClickedFace(),root,false)));
    }

    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState previous,boolean moving) {
        super.onPlace(state,level,pos,previous,moving);
        if(!level.isClientSide&&(state.getValue(PART)==0||hasRemoteShape())&&!moving)level.scheduleTick(pos,this,1);
    }
    @Override protected void tick(BlockState state,net.minecraft.server.level.ServerLevel level,BlockPos pos,net.minecraft.util.RandomSource random) {
        if(hasRemoteShape()&&state.getValue(PART)!=0) {level.removeBlock(pos,false);return;}
        if(state.getValue(PART)==0)fillSide(level,pos,state);
    }
}
