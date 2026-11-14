package willow.train.hexie.blocks.bogeys;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.equipment.wrench.WrenchItem;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import com.simibubi.create.content.trains.bogey.BogeyStyle;
import com.simibubi.create.content.trains.track.TrackMaterial;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import willow.train.hexie.init.track.TrackMaterialInit;
import willow.train.hexie.menu.BogeySelectionMenu;

public abstract class HexieBogeyBlock<T extends AbstractBogeyBlockEntity> extends AbstractBogeyBlock<T> {
    private final double wheelPointSpacing;
    private final double wheelRadius;
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty HEAD_MOUNTED =
            net.minecraft.world.level.block.state.properties.BooleanProperty.create("head_mounted");
    @Override protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HEAD_MOUNTED);
    }
    /** The floor owns the block/collision; neither top nor the swivelling frame moves this footprint. */
    @Override public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,
            net.minecraft.world.level.BlockGetter world, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        if (state.getValue(HEAD_MOUNTED)) {
            var shift = world.getBlockEntity(pos) instanceof HexieBogeyBlockEntity be
                    ? BogeyFloorData.displacement(state, BogeyFloorData.longitudinal(be.getBogeyData()))
                    : Vec3.ZERO;
            if (world.getBlockEntity(pos) instanceof HexieBogeyBlockEntity be && !be.isCarriageView())
                shift = shift.add(0, BogeyFloorData.offset(BogeyFloorData.get(be)), 0);
            return net.minecraft.world.phys.shapes.Shapes.box(0, 5/16d, 0, 1, 1, 1).move(shift.x, shift.y, shift.z);
        }
        double height = world instanceof willow.train.hexie.vehicle.assembly.CarriageFloorView ? 0
                : world.getBlockEntity(pos) instanceof HexieBogeyBlockEntity be && !be.isCarriageView()
                ? BogeyFloorData.offset(BogeyFloorData.get(be)) : 0;
        return net.minecraft.world.phys.shapes.Shapes.block().move(0, height, 0);
    }
    @Override public net.minecraft.world.phys.shapes.VoxelShape getCollisionShape(BlockState state,
            net.minecraft.world.level.BlockGetter world, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        if (state.getValue(HEAD_MOUNTED)) return net.minecraft.world.phys.shapes.Shapes.empty();
        return getShape(state, world, pos, context);
    }
    protected HexieBogeyBlock(Properties properties, BogeySizes.BogeySize size) {
        this(properties, size, 2.5, .915);
    }

    protected HexieBogeyBlock(Properties properties, BogeySizes.BogeySize size,
                              double wheelPointSpacing, double wheelRadius) {
        super(properties.dynamicShape(), size);
        this.wheelPointSpacing = wheelPointSpacing;
        this.wheelRadius = wheelRadius;
        registerDefaultState(defaultBlockState().setValue(HEAD_MOUNTED, false));
    }

    @Override public java.util.List<net.minecraft.world.level.block.state.properties.Property<?>> propertiesToCopy() {
        var properties = new java.util.ArrayList<>(super.propertiesToCopy());
        properties.add(HEAD_MOUNTED);
        return properties;
    }

    @Override
    public final TrackMaterial.TrackType getTrackType(BogeyStyle style) {
        return TrackMaterialInit.HEXIE_STANDARD;
    }

    @Override
    public final double getWheelPointSpacing() {
        return wheelPointSpacing;
    }

    @Override
    public final double getWheelRadius() {
        return wheelRadius;
    }

    @Override
    protected final Vec3 getConnectorAnchorOffset() {
        return new Vec3(0, 7.0 / 32.0, 1);
    }

    @Override
    public final ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return AllBlocks.RAILWAY_CASING.asStack();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        // Handle the top before Create's useItemOn cycles the style on the server.
        if (!player.isShiftKeyDown() && hit.getDirection() == Direction.UP && stack.getItem() instanceof WrenchItem) {
            if (player.isSpectator() || !player.mayBuild() || state.getValue(HEAD_MOUNTED))
                return ItemInteractionResult.FAIL;
            if (player instanceof ServerPlayer server && (!(level.getBlockEntity(pos) instanceof HexieBogeyBlockEntity bogey)
                    || !BogeySelectionMenu.open(server, bogey, hand))) return ItemInteractionResult.FAIL;
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    public final BlockState getRotatedBlockState(BlockState state, Direction targetedFace) {
        return state;
    }
}
