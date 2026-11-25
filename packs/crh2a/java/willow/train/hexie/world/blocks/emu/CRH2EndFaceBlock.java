package willow.train.hexie.world.blocks.emu;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.CouplingBridge;
import willow.train.hexie.bridge.world.CarriageBlockBridge;
import willow.train.hexie.core.api.vehicle.Point3;
import willow.train.hexie.world.blockinteract.EmuBlockInteract;
import willow.train.hexie.world.blocks.coupler.AbstractCouplerBlock;
import willow.train.hexie.world.collision.EmuCollision;
import willow.train.hexie.world.placement.EmuPlacement;

/** End wall, gangways and coupler share only the central floor installation block. */
public final class CRH2EndFaceBlock extends AbstractCouplerBlock implements CarriageBlockBridge.CRH2EndFaceBlockView {
    public enum Type implements StringRepresentable {
        ORIGINAL("original"), TILTED("tilted"), HORIZONTAL_WIRED("horizontal_wired"), HORIZONTAL_PLAIN("horizontal_plain");

        private final String name;
        Type(String name) { this.name = name; }
        @Override public String getSerializedName() { return name; }
        public boolean isHorizontal() { return this == HORIZONTAL_WIRED || this == HORIZONTAL_PLAIN; }
        public boolean blackCableCompatible(CRH2EndFaceBlock.Type other) {
            return this != ORIGINAL && other != null && other != ORIGINAL && isHorizontal() == other.isHorizontal();
        }
        public Component displayName() {
            return Component.translatable("block.hexie.crh2a_endface.variant." + name);
        }
    }

    public static final EnumProperty<CarriageBlockBridge.CRH2EndFaceBlockType> VARIANT = EnumProperty.create("variant", CarriageBlockBridge.CRH2EndFaceBlockType.class);
    public static final MapCodec<CRH2EndFaceBlock> CODEC = simpleCodec(CRH2EndFaceBlock::new);
    public static final CouplingBridge.CouplingEndpointAxisView AXIS = HexieBridges.coupling().createCouplingEndpointAxis(0, -.08125);
    public static final double BELLOWS_BASE_Z = -1 / 16d;
    public static final double BELLOWS_FRONT_Z = -5 / 16d;
    // User-selected maximum (model units), shared by free-state rendering and contact onset.
    public static final double MAX_BELLOWS_THICKNESS = 5 / 16d;

    public CRH2EndFaceBlock(BlockBehaviour.Properties properties) {
        super(properties);
        // NBT states without the new property decode from this default as well.
        registerDefaultState(defaultBlockState().setValue(VARIANT, CarriageBlockBridge.CRH2EndFaceBlockType.ORIGINAL));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(VARIANT);
    }
    public static CRH2EndFaceBlock.Type type(BlockState state) {
        return Type.valueOf(state.getOptionalValue(VARIANT).orElse(CarriageBlockBridge.CRH2EndFaceBlockType.ORIGINAL).name());
    }
    public static boolean blackCableCompatible(BlockState first, BlockState second) {
        return first.getBlock() instanceof CRH2EndFaceBlock && second.getBlock() instanceof CRH2EndFaceBlock
                && type(first).blackCableCompatible(type(second));
    }
    public static void showVariant(Player player, BlockState state) {
        EmuBlockInteract.ForCRH2EndFaceBlock.showVariant(player, state);
    }

    @Override public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return EmuBlockInteract.ForCRH2EndFaceBlock.useItemOn(stack, state, level, pos, player, hand, hit, () -> super.useItemOn(stack, state, level, pos, player, hand, hit));
    }
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
    @Override public VoxelShape couplerShape(BlockState state) {
        return EmuCollision.ForCRH2EndFaceBlock.couplerShape(state);
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return EmuCollision.ForCRH2EndFaceBlock.getShape(state, world, pos, context);
    }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return EmuCollision.ForCRH2EndFaceBlock.getCollisionShape(state, world, pos, context);
    }
    @Override public String model() { return "hexie:crh2_semiauto"; }
    @Override public CouplingBridge.CouplingEndpointAxisView axis() { return AXIS; }
    @Override public Point3 start() { return new Point3(0, -.05, BELLOWS_BASE_Z - MAX_BELLOWS_THICKNESS - .5); }
    @Override public Point3 complete() { return new Point3(0, -.05, -.8125); }
    @Override public double maxExtension() {
        return 2 * (MAX_BELLOWS_THICKNESS - (BELLOWS_BASE_Z - BELLOWS_FRONT_Z));
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return EmuPlacement.ForCRH2EndFaceBlock.getStateForPlacement(this, context);
    }

    @Override public void onBlockStateChange(LevelReader world, BlockPos pos, BlockState oldState, BlockState newState) {
        if (world instanceof Level level && level.isClientSide)
            HexieBridges.Client.carriageRender().crh2StaticSections().changed(world, pos, oldState, newState);
    }

    @Override public BlockState carriageRenderState(BlockState state, boolean shiftedBase) {
        // The wall follows the carriage floor. Only the arm is placed by the coupler animation.
        return state.setValue(ASSEMBLED, true);
    }

    public static List<AABB> playerBoxes(Direction facing) {
        return EmuCollision.ForCRH2EndFaceBlock.playerBoxes(facing);
    }
    public static AABB bounds(Direction facing) {
        return EmuCollision.ForCRH2EndFaceBlock.bounds(facing);
    }

    public static Map<Direction, List<AABB>> playerBoxes() {
        return EmuCollision.ForCRH2EndFaceBlock.playerBoxes();
    }
}
