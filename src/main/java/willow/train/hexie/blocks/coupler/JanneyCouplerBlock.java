package willow.train.hexie.blocks.coupler;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint.Axis;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint.Point;

/** Standard knuckle head with one movable tongue and the shared coupling travel allowance. */
public final class JanneyCouplerBlock extends AbstractCouplerBlock {
    public static final MapCodec<JanneyCouplerBlock> CODEC = simpleCodec(JanneyCouplerBlock::new);
    private final CouplerGeometry geometry = CouplerGeometry.load("/data/hexie/general_janney_coupler_geometry.json");

    public JanneyCouplerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.OPEN, true));
    }

    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BlockStateProperties.OPEN);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var support = context.getLevel().getBlockState(context.getClickedPos().below());
        Direction face = context.getClickedFace();
        Direction facing = face.getAxis().isHorizontal() ? face
                : support.hasProperty(FACING) ? support.getValue(FACING) : context.getHorizontalDirection();
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override public String model() { return "railways:knuckle"; }
    @Override public CouplerGeometry geometry() { return geometry; }
    @Override public Axis axis() {
        return new Axis(geometry.modelAxis().x() - .5, geometry.modelAxis().z() - .5);
    }
    @Override public Point start() { return complete(); }
    @Override public Point complete() {
        var contact = geometry.contact();
        return new Point(contact.x - .5, contact.y - .5, contact.z - .5);
    }
    @Override protected VoxelShape couplerShape(BlockState state) {
        return geometry.shape(facing(state), state.getValue(BlockStateProperties.OPEN));
    }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(net.neoforged.neoforge.common.Tags.Items.TOOLS_WRENCH))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        var surface = CouplerInteraction.classifyHit(level, pos, state, hit);
        // Base and pins have no cover action. Let the existing adapter item handle head hits.
        return surface != null && surface.head() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                : ItemInteractionResult.FAIL;
    }

}
