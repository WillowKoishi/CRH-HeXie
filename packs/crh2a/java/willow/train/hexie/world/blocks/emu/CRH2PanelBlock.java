package willow.train.hexie.world.blocks.emu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import willow.train.hexie.bridge.world.CarriageBlockBridge;
import willow.train.hexie.world.blockentity.panel.CRH2PartEntity;
import willow.train.hexie.world.blockinteract.EmuBlockInteract;
import willow.train.hexie.world.blocks.parts.VehicleMarkingSurface;
import willow.train.hexie.world.blocks.signs.PassengerDisplayPanel;
import willow.train.hexie.world.placement.EmuPlacement;

/** Uses the same handed placement and mirror rules as existing carriage panels. */
public class CRH2PanelBlock extends CRH2PartBlock implements EntityBlock, VehicleMarkingSurface,
        PassengerDisplayPanel, CarriageBlockBridge.CRH2PanelBlockView {
    @Override public BlockPos markingRoot(BlockGetter level,BlockPos pos,BlockState state) { return root(pos,state); }
    @Override public AABB markingBounds(BlockState state) { return placementBounds(state.setValue(FACING,Direction.NORTH)); }
    @Override public AABB markingRenderBounds(BlockState state) { return placementBounds(state); }
    @Override public double markingDepth(double y) { return HarmonyMarking.surfaceDepth(y); }
    public static final EnumProperty<DoorHingeSide> HINGE=BlockStateProperties.DOOR_HINGE;
    public CRH2PanelBlock(BlockBehaviour.Properties properties,String model) {
        super(properties,model); registerDefaultState(defaultBlockState().setValue(HINGE,DoorHingeSide.LEFT));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b) {
        super.createBlockStateDefinition(b);b.add(HINGE);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return EmuPlacement.ForCRH2PanelBlock.getStateForPlacement(context, () -> super.getStateForPlacement(context));
    }
    @Override public BlockState mirror(BlockState s,Mirror mirror) {
        return mirror==Mirror.NONE?s:super.mirror(s,mirror).cycle(HINGE);
    }
    // All four panels share the exterior lower face at local z=3/16.
    public double markingDepth() { return 3/16d; }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) {
        return new CRH2PartEntity(pos,state);
    }
    public CRH2PartEntity markingEntity(Level level,
            BlockPos pos,BlockState state) {
        return EmuBlockInteract.ForCRH2PanelBlock.markingEntity(this, level, pos, state);
    }
    @Override public ItemInteractionResult useItemOn(ItemStack stack,
            BlockState state,Level level,BlockPos pos,
            Player player,InteractionHand hand,
            BlockHitResult hit) {
        return EmuBlockInteract.ForCRH2PanelBlock.useItemOn(this, stack, state, level, pos, player, hand, hit, () -> super.useItemOn(stack,state,level,pos,player,hand,hit));
    }
    @Override public InteractionResult useWithoutItem(BlockState state,Level level,
            BlockPos pos,Player player,BlockHitResult hit) {
        return EmuBlockInteract.ForCRH2PanelBlock.useWithoutItem(this, state, level, pos, player, hit);
    }
}
