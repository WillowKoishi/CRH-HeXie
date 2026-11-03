package willow.train.hexie.blocks.parts;

import java.util.List;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Invisible occupied side-plane cell. Its relative owner coordinate survives structure transforms. */
public final class PartMemberBlock extends Block implements com.simibubi.create.api.schematic.requirement.SpecialBlockItemRequirement,
        com.simibubi.create.content.equipment.wrench.IWrenchable {
    private static final int OFFSET_BIAS=3;
    // IntegerProperty cannot contain negative values; 0..6 encodes actual offsets -3..3.
    public static final IntegerProperty X=IntegerProperty.create("owner_x",0,6),Y=IntegerProperty.create("owner_y",0,6),Z=IntegerProperty.create("owner_z",0,6);
    public PartMemberBlock(Properties properties) { super(properties.dynamicShape());registerDefaultState(withOwnerOffset(stateDefinition.any(),BlockPos.ZERO)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(X,Y,Z); }
    public static BlockPos ownerOffset(BlockState state) {
        return new BlockPos(state.getValue(X)-OFFSET_BIAS,state.getValue(Y)-OFFSET_BIAS,state.getValue(Z)-OFFSET_BIAS);
    }
    public static BlockState withOwnerOffset(BlockState state,BlockPos offset) {
        return state.setValue(X,offset.getX()+OFFSET_BIAS).setValue(Y,offset.getY()+OFFSET_BIAS).setValue(Z,offset.getZ()+OFFSET_BIAS);
    }
    public static BlockPos owner(BlockPos pos,BlockState state) { return pos.offset(ownerOffset(state)); }
    @Override public BlockState rotate(BlockState state,Rotation rotation) {
        return withOwnerOffset(state,ownerOffset(state).rotate(rotation));
    }
    @Override public BlockState mirror(BlockState state,Mirror mirror) {
        var offset=ownerOffset(state);
        return switch(mirror) {
            case FRONT_BACK -> withOwnerOffset(state,new BlockPos(-offset.getX(),offset.getY(),offset.getZ()));
            case LEFT_RIGHT -> withOwnerOffset(state,new BlockPos(offset.getX(),offset.getY(),-offset.getZ()));
            default -> state;
        };
    }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        var owner=PartStructures.resolve(level,pos,state);
        if(owner==null)return Shapes.empty();
        var root=owner.pos();
        return Shapes.join(owner.state().getShape(level,root).move(root.getX()-pos.getX(),root.getY()-pos.getY(),root.getZ()-pos.getZ()),Shapes.block(),BooleanOp.AND);
    }
    @Override public List<ItemStack> getDrops(BlockState state,LootParams.Builder builder) {
        var origin=builder.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN);
        if(origin==null)return List.of();
        var owner=PartStructures.resolve(builder.getLevel(),BlockPos.containing(origin),state);
        return owner==null?List.of():owner.state().getDrops(builder);
    }
    @Override public ItemStack getCloneItemStack(LevelReader level,BlockPos pos,BlockState state) {
        var owner=PartStructures.resolve(level,pos,state);
        return owner==null?ItemStack.EMPTY:owner.state().getBlock().asItem().getDefaultInstance();
    }
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player) {
        var owner=PartStructures.resolve(level,pos,state);
        if(!level.isClientSide&&owner!=null)level.destroyBlock(owner.pos(),!player.isCreative(),player);
        return super.playerWillDestroy(level,pos,state,player);
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving) {
        if(!moving&&!state.is(next.getBlock())&&!PartInstallation.mutating()) {
            var owner=PartStructures.resolve(level,pos,state);
            if(owner!=null)PartInstallation.removeOtherCells(level,pos,owner.structure().partCells(level,owner.pos(),owner.state()));
        }
        super.onRemove(state,level,pos,next,moving);
    }
    /** Unloaded owners remain unresolved; only an already-loaded, confirmed orphan can be removed. */
    public static boolean checkOwner(net.minecraft.server.level.ServerLevel level,BlockPos pos,BlockState state) {
        if(!level.hasChunkAt(owner(pos,state)))return false;
        if(PartStructures.resolve(level,pos,state)==null&&level.getBlockState(pos).equals(state))level.removeBlock(pos,false);
        return true;
    }
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos from,boolean moving) {
        if(!level.isClientSide&&!moving&&!PartInstallation.mutating())level.scheduleTick(pos,this,1);
    }
    @Override protected void tick(BlockState state,net.minecraft.server.level.ServerLevel level,BlockPos pos,net.minecraft.util.RandomSource random) {
        if(!checkOwner(level,pos,state))level.scheduleTick(pos,this,20);
    }
    private BlockHitResult hit(BlockPos root,BlockHitResult hit) { return new BlockHitResult(hit.getLocation(),hit.getDirection(),root,hit.isInside()); }
    @Override public ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        var owner=PartStructures.resolve(level,pos,state);
        return owner==null?ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION:owner.state().useItemOn(stack,level,player,hand,hit(owner.pos(),hit));
    }
    @Override public InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        var owner=PartStructures.resolve(level,pos,state);
        return owner==null?InteractionResult.PASS:owner.state().useWithoutItem(level,player,hit(owner.pos(),hit));
    }
    @Override public com.simibubi.create.content.schematics.requirement.ItemRequirement getRequiredItems(BlockState state,net.minecraft.world.level.block.entity.BlockEntity entity) {
        return com.simibubi.create.content.schematics.requirement.ItemRequirement.NONE;
    }
    private InteractionResult wrench(BlockState state,net.minecraft.world.item.context.UseOnContext context,boolean remove) {
        var owner=PartStructures.resolve(context.getLevel(),context.getClickedPos(),state);
        if(owner==null||!(owner.state().getBlock() instanceof com.simibubi.create.content.equipment.wrench.IWrenchable wrench))return InteractionResult.PASS;
        var root=owner.pos();var source=owner.state();
        var redirected=new net.minecraft.world.item.context.UseOnContext(context.getLevel(),context.getPlayer(),context.getHand(),context.getItemInHand(),
                new BlockHitResult(context.getClickLocation(),context.getClickedFace(),root,false));
        return remove?wrench.onSneakWrenched(source,redirected):wrench.onWrenched(source,redirected);
    }
    @Override public InteractionResult onWrenched(BlockState state,net.minecraft.world.item.context.UseOnContext context) { return wrench(state,context,false); }
    @Override public InteractionResult onSneakWrenched(BlockState state,net.minecraft.world.item.context.UseOnContext context) { return wrench(state,context,true); }
}
