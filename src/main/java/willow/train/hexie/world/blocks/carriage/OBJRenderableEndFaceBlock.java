package willow.train.hexie.world.blocks.carriage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.bridge.world.WorldBlockBridge.OBJRenderableEndFaceBlockView;
import willow.train.hexie.core.api.interaction.HexieWrenchable;
import willow.train.hexie.core.api.render.DoorModelLocations;
import willow.train.hexie.world.blockentity.door.OBJRenderableEndFaceEntity;
import willow.train.hexie.world.blockinteract.CarriageBlockInteract;
import willow.train.hexie.world.collision.CarriageCollisionShapes;
import willow.train.hexie.world.placement.CarriagePlacement;
import willow.train.hexie.world.registry.core.BlockEntitiesInit;

public class OBJRenderableEndFaceBlock extends BaseEntityBlock implements HexieWrenchable, OBJRenderableEndFaceBlockView {
    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return RecordCodecBuilder.<OBJRenderableEndFaceBlock>mapCodec(instance -> instance.group(
                propertiesCodec(),
                DoorModelLocations.CODEC.fieldOf("door_models").forGetter(block -> block.doorModelResourceLocations),
                Codec.BOOL.fieldOf("rotate_door").forGetter(block -> block.isRotateDoor)
        ).apply(instance, OBJRenderableEndFaceBlock::new));
    }

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = DoorBlock.OPEN;
    public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private final DoorModelLocations doorModelResourceLocations;
    public final boolean isRotateDoor;

    public OBJRenderableEndFaceBlock(BlockBehaviour.Properties pProperties, DoorModelLocations doorModelResourceLocations, boolean isRotateDoor) {
        super(pProperties);
        this.registerDefaultState(
                this.stateDefinition
                        .any()
                        .setValue(FACING, Direction.NORTH)
                        .setValue(OPEN, Boolean.valueOf(false))
                        .setValue(HALF, Half.BOTTOM)
                        .setValue(POWERED, Boolean.valueOf(false))
                        .setValue(WATERLOGGED, Boolean.valueOf(false)));
        this.doorModelResourceLocations = doorModelResourceLocations;
        this.isRotateDoor = isRotateDoor;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        super.createBlockStateDefinition(pBuilder);
        pBuilder.add(FACING, OPEN, HALF, POWERED, WATERLOGGED);
    }

    public RenderShape getRenderShape(BlockState pState) {
        return RenderShape.MODEL;
    }

    @Override
    public DoorModelLocations getDoorModelLocations() {
        return doorModelResourceLocations;
    }

    public boolean isRotateDoor(){return isRotateDoor;}

    public VoxelShape getShape(
            BlockState pState, BlockGetter p_54373_, BlockPos p_54374_, CollisionContext p_54375_) {
        return CarriageCollisionShapes.ForOBJRenderableEndFaceBlock.getShape(pState, p_54373_, p_54374_, p_54375_);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos) {
        return CarriageCollisionShapes.ForOBJRenderableEndFaceBlock.getOcclusionShape(pState, pLevel, pPos);
    }

    public Half getHinge(BlockPlaceContext pContext) {
        return CarriagePlacement.ForOBJRenderableEndFaceBlock.getHinge(pContext);
    }

    @javax.annotation.Nullable
    public BlockState getStateForPlacement(BlockPlaceContext pContext) {
        return CarriagePlacement.ForOBJRenderableEndFaceBlock.getStateForPlacement(this, pContext);
    }

    @Override
    public void setPlacedBy(
            Level pLevel,
            BlockPos pPos,
            BlockState pState,
            LivingEntity pPlacer,
            ItemStack pStack) {
        CarriagePlacement.ForOBJRenderableEndFaceBlock.setPlacedBy(pLevel, pPos, pState, pPlacer, pStack);
    }

    public InteractionResult use(
            BlockState pState,
            Level pLevel,
            BlockPos pPos,
            Player pPlayer,
            InteractionHand pHand,
            BlockHitResult pHit) {
        return CarriageBlockInteract.ForOBJRenderableEndFaceBlock.use(this, pState, pLevel, pPos, pPlayer, pHand, pHit);
    }

    public boolean isOpen(BlockState pState) {
        return pState.getValue(OPEN);
    }

    @Override
    public BlockState updateShape(
            BlockState pState,
            Direction pFacing,
            BlockState pFacingState,
            LevelAccessor pLevel,
            BlockPos pCurrentPos,
            BlockPos pFacingPos) {
        return CarriagePlacement.ForOBJRenderableEndFaceBlock.updateShape(pState, pFacing, pFacingState, pLevel, pCurrentPos, pFacingPos);
    }

    public BlockState rotate(BlockState pState, Rotation pRotation) {
        return pState.setValue(FACING, pRotation.rotate(pState.getValue(FACING)));
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, BlockEntitiesInit.OBJ_RENDERABLE_END_FACE_BLOCK_ENTITY.get(),
                (world, pos, current, entity) -> entity.tick());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return new OBJRenderableEndFaceEntity(pPos, pState);
    }
    @Override
    public InteractionResult useWithoutItem(BlockState state,
            Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        return CarriageBlockInteract.ForOBJRenderableEndFaceBlock.useWithoutItem(this, state, level, pos, player, hit);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack,
            BlockState state, Level level,
            BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return CarriageBlockInteract.ForOBJRenderableEndFaceBlock.useItemOn(this, stack, state, level, pos, player, hand, hit);
    }
}
