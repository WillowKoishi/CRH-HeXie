package willow.train.hexie.compat.create.bogey;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import com.simibubi.create.content.trains.bogey.BogeyStyle;
import com.simibubi.create.content.trains.track.TrackMaterial;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.CreateTrainBridgePort.CreateBogeyBlockView;
import willow.train.hexie.bridge.vehicle.BogeyRegistryBridge.BuiltinBogeyModel;

public abstract class HexieBogeyBlock<T extends AbstractBogeyBlockEntity> extends AbstractBogeyBlock<T> implements CreateBogeyBlockView {
    private final double wheelPointSpacing;
    private final double wheelRadius;
    public static final BooleanProperty HEAD_MOUNTED =
            BooleanProperty.create("head_mounted");
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HEAD_MOUNTED);
    }
    protected HexieBogeyBlock(BlockBehaviour.Properties properties, BogeySizes.BogeySize size) {
        this(properties, size, 2.5, .915);
    }

    protected HexieBogeyBlock(BlockBehaviour.Properties properties, BogeySizes.BogeySize size,
                              double wheelPointSpacing, double wheelRadius) {
        super(properties.dynamicShape(), size);
        this.wheelPointSpacing = wheelPointSpacing;
        this.wheelRadius = wheelRadius;
        registerDefaultState(defaultBlockState().setValue(HEAD_MOUNTED, false));
    }

    @Override public BuiltinBogeyModel builtinModel() {
        return HexieBridges.bogeyRegistry().builtinBogeyModel().find(getDefaultStyle().id, getSize().id());
    }

    @Override public List<Property<?>> propertiesToCopy() {
        var properties = new ArrayList<>(super.propertiesToCopy());
        properties.add(HEAD_MOUNTED);
        return properties;
    }

    @Override
    public final TrackMaterial.TrackType getTrackType(BogeyStyle style) {
        return HexieBridges.createTrack().hexieStandard();
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
    public final BlockState getRotatedBlockState(BlockState state, Direction targetedFace) {
        return state;
    }

    @Override public Block asBlock() { return this; }

    /** The floor owns the block/collision; neither top nor the swivelling frame moves this footprint. */
    @Override public VoxelShape getShape(BlockState state,
            BlockGetter world, BlockPos pos, CollisionContext context) {
        return CreateBogeyCollision.getShape(state, world, pos);
    }

    @Override public VoxelShape getCollisionShape(BlockState state,
            BlockGetter world, BlockPos pos, CollisionContext context) {
        return CreateBogeyCollision.getCollisionShape(this, state, world, pos, context);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        return BogeyBlockInteract.useItemOn(stack, state, level, pos, player, hand, hit,
                () -> super.useItemOn(stack, state, level, pos, player, hand, hit));
    }
}
