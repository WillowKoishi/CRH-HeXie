package willow.train.hexie.world.blocks.locos;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.CreatePanelBridge;
import willow.train.hexie.bridge.vehicle.CouplingBridge;
import willow.train.hexie.bridge.world.CarriageBlockBridge;
import willow.train.hexie.core.api.interaction.BlockControl;
import willow.train.hexie.core.api.interaction.BlockControlContext;
import willow.train.hexie.core.api.interaction.BlockControlHit;
import willow.train.hexie.core.api.interaction.BlockControlHost;
import willow.train.hexie.core.api.interaction.BlockControlUse;
import willow.train.hexie.core.api.interaction.ControlState;
import willow.train.hexie.core.api.interaction.ControlSurface;
import willow.train.hexie.core.api.vehicle.Point3;
import willow.train.hexie.world.blockinteract.LocoBlockInteract;
import willow.train.hexie.world.blocks.coupler.CouplerBlock;
import willow.train.hexie.world.blocks.parts.ModelRenderBounds;
import willow.train.hexie.world.blocks.parts.PartStructure;
import willow.train.hexie.world.collision.CouplerGeometry;
import willow.train.hexie.world.collision.LocoCollision;
import willow.train.hexie.world.collision.PlayerCollisionShell;
import willow.train.hexie.world.placement.LocoPlacement;
import willow.train.hexie.world.placement.PartInstallation;
import willow.train.hexie.world.registry.blocks.BlockInit;
import willow.train.hexie.world.registry.core.BlockEntitiesInit;

public final class SS7DHeadBlock extends Block implements PartStructure, EntityBlock, CouplerBlock, BlockControlHost,
        ModelRenderBounds, PlayerCollisionShell, CarriageBlockBridge.SS7DHeadBlockView {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    static final int ROOT = 4;

    static { LocoCollision.ForSS7DHeadBlock.initialize(); }

    public enum Control implements BlockControl.Button {
        NUMBER, COUPLER;
        @Override public String id() { return this == NUMBER ? "ss7d_number" : "coupler_head"; }
        @Override public Component nameText() {
            return Component.translatable(this == NUMBER ? "gui.hexie.ss7d_head_number" : "interaction.hexie.coupler_head");
        }
        @Override public Component valueText(ControlState state) { return Component.empty(); }
    }

    public SS7DHeadBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(BlockStateProperties.OPEN, true));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, BlockStateProperties.OPEN);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return LocoPlacement.ForSS7DHeadBlock.getStateForPlacement(this, context);
    }
    @Override public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(facing(state)));
    }
    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(facing(state)));
    }
    static BlockPos offset(int part, Direction facing) {
        var local = new BlockPos(part % 3 - 1, 0, part / 3 - 1);
        return local.rotate(switch (facing) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        });
    }
    @Override public boolean schematicRoot() { return true; }
    @Override public BlockPos partRoot(BlockGetter level, BlockPos pos, BlockState state) { return pos; }
    @Override public Map<BlockPos, BlockState> partCells(BlockGetter level, BlockPos root, BlockState state) {
        var cells = new LinkedHashMap<BlockPos, BlockState>();
        for (int i = 0; i < 6; i++) {
            var position = root.offset(offset(i, facing(state)));
            cells.put(position, i == ROOT ? state : BlockInit.SS7D_HEAD_FLOOR.get().defaultBlockState()
                    .setValue(FACING, facing(state)).setValue(SS7DHeadFloorBlock.PART, i));
        }
        return cells;
    }
    @Override public void onBlockStateChange(LevelReader world, BlockPos pos, BlockState before, BlockState after) {
        if (world instanceof Level level) PartInstallation.stateChanged(level, pos, before, after);
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!moving && !state.is(next.getBlock()))
            PartInstallation.removeOtherCells(level, pos, partCells(level, pos, state));
        super.onRemove(state, level, pos, next, moving);
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return LocoCollision.ForSS7DHeadBlock.getShape(this, state, world, pos, context);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SS7DHeadEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide && type == BlockEntitiesInit.SS7D_HEAD.get()
                ? (world, pos, blockState, entity) -> ((SS7DHeadEntity) entity).tick() : null;
    }
    @Override public Direction facing(BlockState state) { return state.getValue(FACING); }
    @Override public String model() { return BlockInit.GENERAL_JANNEY_COUPLER.get().model(); }
    @Override public CouplerGeometry geometry() { return SS7DHeadGeometry.COUPLER; }
    @Override public CouplingBridge.CouplingEndpointAxisView axis() { return HexieBridges.coupling().createCouplingEndpointAxis(geometry().modelAxis().x() - .5, geometry().modelAxis().z() - .5); }
    @Override public Point3 start() { return complete(); }
    @Override public Point3 complete() {
        var contact = geometry().contact();
        return new Point3(contact.x - .5, contact.y - .5, contact.z - .5);
    }
    @Override public double maxExtension() { return 0; }
    @Override public boolean allowsAdapters() { return false; }
    @Override public AABB modelRenderBounds(BlockState state) { return SS7DHeadGeometry.bounds(facing(state)); }
    @Override public List<AABB> playerCollisionBoxes(BlockState state) {
        return LocoCollision.ForSS7DHeadBlock.playerCollisionBoxes(this, state);
    }
    @Override public AABB controlBounds(BlockState state) {
        return LocoBlockInteract.ForSS7DHeadBlock.controlBounds(this, state);
    }
    @Override public ControlSurface surfaceGeometry(BlockState state) {
        return LocoBlockInteract.ForSS7DHeadBlock.surfaceGeometry(state);
    }

    @Override public BlockControlHit pickControl(BlockControlContext target, Vec3 from, Vec3 to) {
        return LocoBlockInteract.ForSS7DHeadBlock.pickControl(this, target, from, to);
    }

    public static boolean hasSidePlates(BlockControlContext target) {
        var panel = CreatePanelBridge.forCarrier(target.carrier());
        if (panel != null && panel.hasCapturedBlocks(target.carrier())) {
            var nbt = panel.blockEntityNbt(target.carrier(), target.position());
            return nbt == null || SS7DHeadEntity.hasSidePlates(nbt.getString(SS7DHeadEntity.NUMBER_TAG));
        }
        return !(target.blocks().getBlockEntity(target.position()) instanceof SS7DHeadEntity head)
                || head.hasSidePlates();
    }

    @Override public InteractionResult operateControl(BlockControlUse use, BlockControl control, int value) {
        return LocoBlockInteract.ForSS7DHeadBlock.operateControl(this, use, control, value);
    }

    @Override public Block asBlock() { return this; }
}
