package willow.train.hexie.blocks.emu.crh2a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;
import willow.train.hexie.blocks.base.HorizontalBlockBase;

/** Ordinary single block, with an exterior side slab like the existing carriage panels. */
public class CRH2PartBlock extends HorizontalBlockBase implements willow.train.hexie.blocks.parts.PartStructure,
        com.simibubi.create.api.schematic.requirement.SpecialBlockItemRequirement {
    private final CRH2PartShapes geometry;
    public CRH2PartBlock(Properties properties, String model) {
        super(properties.dynamicShape());
        geometry = new CRH2PartShapes(model);
    }
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty PART = net.minecraft.world.level.block.state.properties.IntegerProperty.create("part",0,15);
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
    @Override public BlockPos partRoot(BlockGetter level, BlockPos pos, BlockState state) { return root(pos,state); }
    @Override public java.util.Map<BlockPos,BlockState> partCells(BlockGetter level,BlockPos root,BlockState state) {
        var cells=new java.util.LinkedHashMap<BlockPos,BlockState>();
        for(int i=0;i<partCount();i++) cells.put(root.offset(offset(i,state)),state.setValue(PART,i));
        return cells;
    }
    @Override public com.simibubi.create.content.schematics.requirement.ItemRequirement getRequiredItems(BlockState state, net.minecraft.world.level.block.entity.BlockEntity entity) {
        return state.getValue(PART)==0 ? new com.simibubi.create.content.schematics.requirement.ItemRequirement(
                com.simibubi.create.content.schematics.requirement.ItemRequirement.ItemUseType.CONSUME,asItem())
                : com.simibubi.create.content.schematics.requirement.ItemRequirement.NONE;
    }
    @Override public net.minecraft.world.level.block.RenderShape getRenderShape(BlockState s) {
        return s.getValue(PART)==0?net.minecraft.world.level.block.RenderShape.MODEL:net.minecraft.world.level.block.RenderShape.INVISIBLE;
    }
    @Override public void onBlockStateChange(net.minecraft.world.level.LevelReader world, BlockPos pos, BlockState oldState, BlockState newState) {
        super.onBlockStateChange(world,pos,oldState,newState);
        if (world instanceof Level level && level.isClientSide)
            willow.train.hexie.client.render.CRH2StaticSections.changed(world, pos, oldState, newState);
    }
    public AABB placementBounds(BlockState state) { return geometry.bounds(state.getValue(FACING),right(state)); }
    @Override public VoxelShape getShape(BlockState s, BlockGetter w, BlockPos p, CollisionContext c) {
        return geometry.piece(s.getValue(PART),s.getValue(FACING),right(s));
    }
    @Override public VoxelShape getCollisionShape(BlockState s, BlockGetter w, BlockPos p, CollisionContext c) { return getShape(s,w,p,c); }

    /** Check occupied cells, not the full visual bounds or just other collision shapes. */
    public boolean canFitSide(Level level, BlockPos pos, BlockState state) {
        return willow.train.hexie.blocks.parts.PartInstallation.available(level,partCells(level,pos,state),at->false,at->true);
    }

    public boolean fillSide(Level level,BlockPos root,BlockState state) {
        return willow.train.hexie.blocks.parts.PartInstallation.install(level,partCells(level,root,state),at->false,at->true);
    }
    @Override protected void onRemove(BlockState s,Level level,BlockPos pos,BlockState next,boolean moving) {
        if(!moving&&!s.is(next.getBlock())) willow.train.hexie.blocks.parts.PartInstallation.removeOtherCells(
                level,pos,partCells(level,root(pos,s),s.setValue(PART,0)));
        super.onRemove(s,level,pos,next,moving);
    }
    @Override public java.util.List<net.minecraft.world.item.ItemStack> getDrops(BlockState s,net.minecraft.world.level.storage.loot.LootParams.Builder b) {
        if(s.getValue(PART)==0)return super.getDrops(s,b);
        var origin=b.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN);
        if(origin==null)return java.util.List.of();
        var at=root(BlockPos.containing(origin),s);
        var owner=b.getLevel().getBlockState(at);
        return owner.is(this)&&owner.getValue(PART)==0 ? super.getDrops(owner,b) : java.util.List.of();
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
        if(!willow.train.hexie.blocks.parts.PartInstallation.replace(level,partCells(level,root,owner),partCells(level,root,rotated),
                at->false,at->c.getPlayer()==null||c.getPlayer().mayUseItemAt(at,c.getClickedFace(),c.getItemInHand())))
            return net.minecraft.world.InteractionResult.FAIL;
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
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,net.minecraft.world.level.block.Block block,BlockPos from,boolean moving) {
        if(!level.isClientSide&&!moving&&!willow.train.hexie.blocks.parts.PartInstallation.mutating()) {
            var root=root(pos,state);
            if(level.hasChunkAt(root)&&level.getBlockState(root).is(this))level.scheduleTick(root,this,1);
        }
    }
}
