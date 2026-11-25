package willow.train.hexie.world.blocks.parts;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.bridge.world.PartBlockBridge;
import willow.train.hexie.core.api.interaction.HexieItemRequirementProvider;
import willow.train.hexie.core.api.interaction.HexieWrenchable;
import willow.train.hexie.world.blockinteract.PartBlockInteract;
import willow.train.hexie.world.collision.PartCollision;
import willow.train.hexie.world.placement.PartInstallation;

/** Invisible occupied side-plane cell. Its relative owner coordinate survives structure transforms. */
public final class PartMemberBlock extends Block implements HexieItemRequirementProvider,
        HexieWrenchable, PartBlockBridge.PartMemberBlockView {
    private static final int OFFSET_BIAS=3;
    // IntegerProperty cannot contain negative values; 0..6 encodes actual offsets -3..3.
    public static final IntegerProperty X=IntegerProperty.create("owner_x",0,6),Y=IntegerProperty.create("owner_y",0,6),Z=IntegerProperty.create("owner_z",0,6);
    public PartMemberBlock(BlockBehaviour.Properties properties) { super(properties.dynamicShape());registerDefaultState(withOwnerOffset(stateDefinition.any(),BlockPos.ZERO)); }
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
        return PartCollision.ForPartMemberBlock.getShape(this, state, level, pos, context);
    }
    @Override public List<ItemStack> getDrops(BlockState state,LootParams.Builder builder) {
        var origin=builder.getOptionalParameter(LootContextParams.ORIGIN);
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
    public static boolean checkOwner(ServerLevel level,BlockPos pos,BlockState state) {
        if(!level.hasChunkAt(owner(pos,state)))return false;
        if(PartStructures.resolve(level,pos,state)==null&&level.getBlockState(pos).equals(state))level.removeBlock(pos,false);
        return true;
    }
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos from,boolean moving) {
        if(!level.isClientSide&&!moving&&!PartInstallation.mutating())level.scheduleTick(pos,this,1);
    }
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random) {
        if(!checkOwner(level,pos,state))level.scheduleTick(pos,this,20);
    }
    public BlockHitResult hit(BlockPos root,BlockHitResult hit) {
        return PartBlockInteract.ForPartMemberBlock.hit(root, hit);
    }
    @Override public ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        return PartBlockInteract.ForPartMemberBlock.useItemOn(this, stack, state, level, pos, player, hand, hit);
    }
    @Override public InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        return PartBlockInteract.ForPartMemberBlock.useWithoutItem(this, state, level, pos, player, hit);
    }
    @Override public Requirement getRequiredItems(BlockState state,BlockEntity entity) {
        return Requirement.NONE;
    }
    public InteractionResult wrench(BlockState state,UseOnContext context,boolean remove) {
        return PartBlockInteract.ForPartMemberBlock.wrench(state, context, remove);
    }
    @Override public InteractionResult onWrenched(BlockState state,UseOnContext context) {
        return PartBlockInteract.ForPartMemberBlock.onWrenched(this, state, context);
    }
    @Override public InteractionResult onSneakWrenched(BlockState state,UseOnContext context) {
        return PartBlockInteract.ForPartMemberBlock.onSneakWrenched(this, state, context);
    }

    @Override public Block asBlock() { return this; }
}
