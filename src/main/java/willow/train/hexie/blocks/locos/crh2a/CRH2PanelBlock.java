package willow.train.hexie.blocks.locos.crh2a;

import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;

/** Uses the same handed placement and mirror rules as existing carriage panels. */
public final class CRH2PanelBlock extends CRH2PartBlock implements EntityBlock, willow.train.hexie.blocks.parts.VehicleMarkingSurface {
    @Override public net.minecraft.core.BlockPos markingRoot(net.minecraft.world.level.BlockGetter level,net.minecraft.core.BlockPos pos,BlockState state) { return root(pos,state); }
    @Override public net.minecraft.world.phys.AABB markingBounds(BlockState state) { return placementBounds(state.setValue(FACING,net.minecraft.core.Direction.NORTH)); }
    @Override public net.minecraft.world.phys.AABB markingRenderBounds(BlockState state) { return placementBounds(state); }
    @Override public double markingDepth(double y) { return HarmonyMarking.surfaceDepth(y); }
    public static final EnumProperty<DoorHingeSide> HINGE=BlockStateProperties.DOOR_HINGE;
    public CRH2PanelBlock(Properties properties,String model) {
        super(properties,model); registerDefaultState(defaultBlockState().setValue(HINGE,DoorHingeSide.LEFT));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b) {
        super.createBlockStateDefinition(b);b.add(HINGE);
    }
    @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return super.getStateForPlacement(context).setValue(HINGE,willow.train.hexie.blocks.base.SideMirrorBlockBase.getHinge(context));
    }
    @Override public BlockState mirror(BlockState s,Mirror mirror) {
        return mirror==Mirror.NONE?s:super.mirror(s,mirror).cycle(HINGE);
    }
    // All four panels share the exterior lower face at local z=3/16.
    public double markingDepth() { return 3/16d; }
    @Override public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(net.minecraft.core.BlockPos pos,BlockState state) {
        return new willow.train.hexie.blockentity.CRH2PartEntity(pos,state);
    }
    private willow.train.hexie.blockentity.CRH2PartEntity markingEntity(net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos,BlockState state) {
        var owner=root(pos,state);var ownerState=level.getBlockState(owner);
        if(!ownerState.is(this)||ownerState.getValue(PART)!=0)return null;
        if(!level.isClientSide&&level.getBlockEntity(owner)==null)
            level.setBlockEntity(new willow.train.hexie.blockentity.CRH2PartEntity(owner,ownerState));
        return level.getBlockEntity(owner) instanceof willow.train.hexie.blockentity.CRH2PartEntity entity?entity:null;
    }
    @Override public net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,
            BlockState state,net.minecraft.world.level.Level level,net.minecraft.core.BlockPos pos,
            net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand,
            net.minecraft.world.phys.BlockHitResult hit) {
        if(!(stack.getItem() instanceof willow.train.hexie.item.editable_panels.ColoredBrush))
            return super.useItemOn(stack,state,level,pos,player,hand,hit);
        if(level!=player.level()||!player.mayBuild()||player.isSpectator())return net.minecraft.world.ItemInteractionResult.FAIL;
        if(level.isClientSide)return net.minecraft.world.ItemInteractionResult.SUCCESS;
        var entity=markingEntity(level,pos,state);
        if(entity==null)return net.minecraft.world.ItemInteractionResult.FAIL;
        entity.selectMarkingSlot(HarmonyMarking.slot(this,state,entity.getBlockPos(),hit.getLocation()));
        if(player instanceof net.minecraft.server.level.ServerPlayer server)server.openMenu(entity,buf->{
            buf.writeBlockPos(entity.getBlockPos());for(String field:entity.fields())buf.writeUtf(field,128);
            buf.writeBoolean(entity.descriptionLeft());
        });
        return net.minecraft.world.ItemInteractionResult.CONSUME;
    }
    @Override public net.minecraft.world.InteractionResult useWithoutItem(BlockState state,net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos,net.minecraft.world.entity.player.Player player,net.minecraft.world.phys.BlockHitResult hit) {
        if(level!=player.level()||!player.mayBuild()||player.isSpectator()||!player.getMainHandItem().isEmpty()||!player.getOffhandItem().isEmpty())
            return net.minecraft.world.InteractionResult.PASS;
        var entity=markingEntity(level,pos,state);
        if(entity==null)return net.minecraft.world.InteractionResult.PASS;
        int logoSlot=HarmonyMarking.slot(this,state,entity.getBlockPos(),hit.getLocation());
        if(entity.harmony(logoSlot)!=0) {
            if(!level.isClientSide)entity.toggleHarmony(logoSlot);
            return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
        }
        if(java.util.Arrays.stream(entity.fields()).allMatch(String::isBlank))return net.minecraft.world.InteractionResult.PASS;
        if(!level.isClientSide) {
            entity.selectMarkingSlot(logoSlot);
            entity.toggleDescription();
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }
}
