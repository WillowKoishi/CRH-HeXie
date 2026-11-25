package willow.train.hexie.world.blocks.carriage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.bridge.world.WorldBlockBridge;
import willow.train.hexie.core.api.render.DoorModelLocations;
import willow.train.hexie.world.blockentity.door.CustomRenderedDoorEntity;
import willow.train.hexie.world.blockinteract.CarriageBlockInteract;
import willow.train.hexie.world.collision.CarriageCollisionShapes;
import willow.train.hexie.world.placement.CarriagePlacement;
import willow.train.hexie.world.registry.core.BlockEntitiesInit;

public class CustomRenderedDoorBlock extends DoorBlock implements EntityBlock, WorldBlockBridge.CustomRenderedDoorBlockView {
    @Override
    public MapCodec<? extends DoorBlock> codec() {
        return RecordCodecBuilder.<CustomRenderedDoorBlock>mapCodec(instance -> instance.group(
                propertiesCodec(),
                DoorModelLocations.CODEC.fieldOf("left_door_models").forGetter(block -> block.leftDoorModelLocations),
                DoorModelLocations.CODEC.fieldOf("right_door_models").forGetter(block -> block.rightDoorModelLocations),
                Codec.STRING.xmap(RenderShape::valueOf, RenderShape::name)
                        .fieldOf("render_shape").forGetter(block -> block.renderShape),
                Codec.BOOL.fieldOf("slide_door").forGetter(block -> block.isSlideDoor)
        ).apply(instance, CustomRenderedDoorBlock::new));
    }

    private final DoorModelLocations leftDoorModelLocations;
    private final DoorModelLocations rightDoorModelLocations;
    public boolean isSlideDoor;
    final RenderShape renderShape;

    public CustomRenderedDoorBlock(BlockBehaviour.Properties pProperties,
                                   DoorModelLocations leftDoorModels,
                                   DoorModelLocations rightDoorModels,
                                   RenderShape renderShape, boolean isSlideDoor) {
        super(BlockSetType.OAK, pProperties);
        this.leftDoorModelLocations = leftDoorModels;
        this.rightDoorModelLocations = rightDoorModels;
        this.renderShape = renderShape;
        this.isSlideDoor = isSlideDoor;
    }

    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return CarriageCollisionShapes.ForCustomRenderedDoorBlock.getShape(this, pState, pLevel, pPos, pContext, () -> super.getShape(pState, pLevel, pPos, pContext));
    }

    @Override
    public DoorModelLocations getLeftDoorModelLocations() {
        return leftDoorModelLocations;
    }

    @Override
    public DoorModelLocations getRightDoorModelLocations() {
        return rightDoorModelLocations;
    }

    @Override
    public boolean isPathfindable(
            BlockState pState, PathComputationType pType) {
        return true;
    }

    @Override
    public boolean canSurvive(BlockState pState, LevelReader pLevel, BlockPos pPos) {
        return CarriagePlacement.ForCustomRenderedDoorBlock.canSurvive(pState, pLevel, pPos);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder pBuilder) {
        if(state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER)
            return List.of();
        return super.getDrops(state, pBuilder);
    }

    @Override
    public CustomRenderedDoorEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CustomRenderedDoorEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == BlockEntitiesInit.CUSTOM_RENDERED_DOOR_ENTITY.get()
                ? (world, pos, current, entity) -> ((CustomRenderedDoorEntity) entity).tick() : null;
    }

    public InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, InteractionHand pHand, BlockHitResult pHit) {
        return CarriageBlockInteract.ForCustomRenderedDoorBlock.use(pState, pLevel, pPos, pPlayer, pHand, pHit, () -> super.useWithoutItem(pState, pLevel, pPos, pPlayer, pHit));
    }

    @Override
    public RenderShape getRenderShape(BlockState pState) {
        return renderShape;
    }

    public boolean isSlideDoor() {
        return isSlideDoor;
    }

    /** A positive duration opts into exact tick-counted animation; zero retains the existing chaser. */
    public int animationDurationTicks() {
        return 0;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state,
            Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        return CarriageBlockInteract.ForCustomRenderedDoorBlock.useWithoutItem(this, state, level, pos, player, hit);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack,
            BlockState state, Level level,
            BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return CarriageBlockInteract.ForCustomRenderedDoorBlock.useItemOn(this, stack, state, level, pos, player, hand, hit);
    }

    @Override public Block asBlock() { return this; }
}
