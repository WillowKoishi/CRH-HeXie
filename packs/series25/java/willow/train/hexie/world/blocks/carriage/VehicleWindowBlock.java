package willow.train.hexie.world.blocks.carriage;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.core.api.interaction.HexieWrenchable;
import willow.train.hexie.world.blockinteract.CarriageBlockInteract;
import willow.train.hexie.world.blocks.parts.LegacyPartLayout;
import willow.train.hexie.world.blocks.parts.PartStructure;
import willow.train.hexie.world.collision.CarriageCollisionShapes;
import willow.train.hexie.world.placement.CarriagePlacement;
import willow.train.hexie.world.placement.PartInstallation;

/** Old window state IDs retain their meaning while using the shared occupied-layout lifecycle. */
public class VehicleWindowBlock extends TrapDoorBlock implements PartStructure,HexieWrenchable {
    protected int type;
    @Override public BlockState mirror(BlockState state,Mirror mirror) {
        return mirror==Mirror.NONE?state:super.mirror(state,mirror).cycle(HALF);
    }
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        return CarriageCollisionShapes.ForVehicleWindowBlock.getShape(this, state, level, pos, context);
    }
    public Half getHinge(BlockPlaceContext pContext) {
        return CarriagePlacement.ForVehicleWindowBlock.getHinge(pContext);
    }

    public VehicleWindowBlock(BlockSetType type,BlockBehaviour.Properties properties) {
        super(type,properties);registerDefaultState(defaultBlockState().setValue(LegacyPartLayout.OCCUPIED,false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) {
        super.createBlockStateDefinition(builder);builder.add(LegacyPartLayout.OCCUPIED);
    }
    @Override public BlockPos partRoot(BlockGetter level,BlockPos pos,BlockState state) { return pos; }
    @Override public Map<BlockPos,BlockState> partCells(BlockGetter level,BlockPos root,BlockState state) { return LegacyPartLayout.cells(level,root,state); }
    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState previous,boolean moving) {
        CarriagePlacement.ForVehicleWindowBlock.onPlace(state, level, pos, previous, moving, () -> super.onPlace(state,level,pos,previous,moving));
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving) {
        LegacyPartLayout.removed(level,pos,state,next,moving);super.onRemove(state,level,pos,next,moving);
    }
    @Override public void onBlockStateChange(LevelReader world,BlockPos pos,BlockState old,BlockState next) {
        if(world instanceof Level level)PartInstallation.stateChanged(level,pos,old,next);
    }
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos from,boolean moving) {
        super.neighborChanged(state,level,pos,block,from,moving);LegacyPartLayout.schedule(level,pos,state,moving);
    }
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random) {
        LegacyPartLayout.upgrade(level,pos,state);
    }
    @Override public InteractionResult onWrenched(BlockState state,UseOnContext context) {
        return CarriageBlockInteract.ForVehicleWindowBlock.onWrenched(this, state, context);
    }

    public final int variantType() { return type; }
}
