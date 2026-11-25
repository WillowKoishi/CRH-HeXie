package willow.train.hexie.world.blocks.coupler;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.CouplingBridge;
import willow.train.hexie.bridge.world.CouplerBlockBridge;
import willow.train.hexie.core.api.vehicle.Point3;
import willow.train.hexie.world.blockinteract.CouplerBlockInteract;
import willow.train.hexie.world.collision.CouplerCollision;
import willow.train.hexie.world.collision.CouplerGeometry;
import willow.train.hexie.world.placement.CouplerPlacement;

/** Standard knuckle head with one movable tongue and the shared coupling travel allowance. */
public final class JanneyCouplerBlock extends AbstractCouplerBlock implements CouplerBlockBridge.JanneyCouplerBlockView {
    public static final MapCodec<JanneyCouplerBlock> CODEC = simpleCodec(JanneyCouplerBlock::new);
    private final CouplerGeometry geometry = CouplerGeometry.load("/data/hexie/general_janney_coupler_geometry.json");

    public JanneyCouplerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.OPEN, true));
    }

    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BlockStateProperties.OPEN);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return CouplerPlacement.ForJanneyCouplerBlock.getStateForPlacement(this, context);
    }

    @Override public String model() { return "railways:knuckle"; }
    @Override public CouplerGeometry geometry() { return geometry; }
    @Override public CouplingBridge.CouplingEndpointAxisView axis() {
        return HexieBridges.coupling().createCouplingEndpointAxis(geometry.modelAxis().x() - .5, geometry.modelAxis().z() - .5);
    }
    @Override public Point3 start() { return complete(); }
    @Override public Point3 complete() {
        var contact = geometry.contact();
        return new Point3(contact.x - .5, contact.y - .5, contact.z - .5);
    }
    @Override public VoxelShape couplerShape(BlockState state) {
        return CouplerCollision.ForJanneyCouplerBlock.couplerShape(this, state);
    }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return CouplerBlockInteract.ForJanneyCouplerBlock.useItemOn(stack, state, level, pos, player, hand, hit);
    }

}
