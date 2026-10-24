package willow.train.hexie.blocks.carriage;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorShapes;
import com.simibubi.create.foundation.block.IBE;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.Main;
import willow.train.hexie.init.BlockEntitiesInit;
import willow.train.hexie.blockentity.CustomRenderedDoorEntity;
import willow.train.hexie.blocks.api.ContraptionAnimatableBlockEntity;

import java.util.List;

public class CustomRenderedDoorBlock extends DoorBlock implements IBE<CustomRenderedDoorEntity> {
    @Override
    public com.mojang.serialization.MapCodec<? extends DoorBlock> codec() {
        return com.mojang.serialization.codecs.RecordCodecBuilder.<CustomRenderedDoorBlock>mapCodec(instance -> instance.group(
                propertiesCodec(),
                Couple.codec(ResourceLocation.CODEC).fieldOf("left_door_models").forGetter(block -> block.leftDoorModelLocations),
                Couple.codec(ResourceLocation.CODEC).fieldOf("right_door_models").forGetter(block -> block.rightDoorModelLocations),
                com.mojang.serialization.Codec.STRING.xmap(RenderShape::valueOf, RenderShape::name)
                        .fieldOf("render_shape").forGetter(block -> block.renderShape),
                com.mojang.serialization.Codec.BOOL.fieldOf("slide_door").forGetter(block -> block.isSlideDoor)
        ).apply(instance, CustomRenderedDoorBlock::new));
    }

    private final Couple<ResourceLocation> leftDoorModelLocations;
    private final Couple<ResourceLocation> rightDoorModelLocations;
    boolean isSlideDoor;
    final Couple<PartialModel> leftDoorModels;
    final Couple<PartialModel> rightDoorModels;
    final RenderShape renderShape;

    public CustomRenderedDoorBlock(Properties pProperties,
                                   Couple<ResourceLocation> leftDoorModels,
                                   Couple<ResourceLocation> rightDoorModels,
                                   RenderShape renderShape, boolean isSlideDoor) {
        super(net.minecraft.world.level.block.state.properties.BlockSetType.OAK, pProperties);
        this.leftDoorModelLocations = leftDoorModels.copy();
        this.rightDoorModelLocations = rightDoorModels.copy();
        this.leftDoorModels = Couple.create(block(leftDoorModels.get(true).getPath()), block(leftDoorModels.get(false).getPath()));
        this.rightDoorModels = Couple.create(block(rightDoorModels.get(true).getPath()), block(rightDoorModels.get(false).getPath()));
        this.renderShape = renderShape;
        this.isSlideDoor = isSlideDoor;
    }

    public CustomRenderedDoorBlock(Properties pProperties,
                                   CustomRenderedDoorBlock modelFrom,
                                   RenderShape renderShape, boolean isSlideDoor) {
        this(pProperties, modelFrom.leftDoorModelLocations, modelFrom.rightDoorModelLocations, renderShape, isSlideDoor);
    }

    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        if(!isSlideDoor || !pState.getValue(OPEN))
            return super.getShape(pState, pLevel, pPos, pContext);
        else
            return SlidingDoorShapes.get(pState.getValue(DoorBlock.FACING), pState.getValue(DoorBlock.HINGE) == DoorHingeSide.RIGHT, false);
    }

    public Couple<PartialModel> getLeftDoorModels() {
        return leftDoorModels;
    }

    public Couple<PartialModel> getRightDoorModels() {
        return rightDoorModels;
    }

    private static PartialModel block(String path) {
        return PartialModel.of(Main.asResource("block/" + path));
    }

    @Override
    public boolean isPathfindable(
            BlockState pState, PathComputationType pType) {
        return true;
    }

    @Override
    public boolean canSurvive(BlockState pState, LevelReader pLevel, BlockPos pPos) {
        return true;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder pBuilder) {
        if(state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER)
            return List.of();
        return super.getDrops(state, pBuilder);
    }

    @Override
    public Class<CustomRenderedDoorEntity> getBlockEntityClass() {
        return CustomRenderedDoorEntity.class;
    }

    @Override
    public BlockEntityType<CustomRenderedDoorEntity> getBlockEntityType() {
        return BlockEntitiesInit.CUSTOM_RENDERED_DOOR_ENTITY.get();
    }

    public InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, InteractionHand pHand, BlockHitResult pHit) {
        boolean open = pState.getValue(DoorBlock.OPEN);
        if(pLevel.getBlockEntity(pPos) instanceof ContraptionAnimatableBlockEntity entity) {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("open", !open);
            entity.onNormalUse(pLevel, pPlayer, pHand, pPos, pState, tag);
        }
        return super.useWithoutItem(pState, pLevel, pPos, pPlayer, pHit);
    }

    @Override
    public RenderShape getRenderShape(BlockState pState) {
        return renderShape;
    }

    public boolean isSlideDoor() {
        return isSlideDoor;
    }

    @Override
    public net.minecraft.world.InteractionResult useWithoutItem(net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
        return use(state, level, pos, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
    }

    @Override
    public net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,
            net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos, net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (stack.isEmpty()) return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        net.minecraft.world.InteractionResult result = use(state, level, pos, player, hand, hit);
        return willow.train.hexie.compat.InteractionResults.toItemResult(result);
    }
}
