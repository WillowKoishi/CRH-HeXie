package willow.train.hexie.world.blocks.signs;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.core.api.interaction.HexieWrenchable;
import willow.train.hexie.core.util.enums.SlabTypes;
import willow.train.hexie.world.blockentity.sign.TrainSpeedSignEntity;
import willow.train.hexie.world.blockinteract.SignBlockInteract;
import willow.train.hexie.world.blocks.base.SignBlockBase;
import willow.train.hexie.world.collision.SignCollision;
import willow.train.hexie.world.placement.SignPlacement;

public class TrainSpeedSignBlock extends SignBlockBase implements HexieWrenchable {

    public static final EnumProperty<SlabTypes> TYPE = EnumProperty.create("type", SlabTypes.class);

    public TrainSpeedSignBlock(BlockBehaviour.Properties p_49795_) {
        super(p_49795_);
        this.getStateDefinition().any().setValue(TYPE, SlabTypes.S25B);
    }

    @Override
    public VoxelShape getShape(
            BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return SignCollision.ForTrainSpeedSignBlock.getShape(pState, pLevel, pPos, pContext);
    }

    public InteractionResult use(
            BlockState pState,
            Level pLevel,
            BlockPos pPos,
            Player pPlayer,
            InteractionHand pHand,
            BlockHitResult pHit) {
        return SignBlockInteract.ForTrainSpeedSignBlock.use(pState, pLevel, pPos, pPlayer, pHand, pHit);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return new TrainSpeedSignEntity(pPos, pState);
    }

    public BlockState getStateForPlacement(
            BlockPlaceContext context, Direction direction, SlabTypes types) {
        return SignPlacement.ForTrainSpeedSignBlock.getStateForPlacement(context, direction, types, () -> super.getStateForPlacement(context));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return SignPlacement.ForTrainSpeedSignBlock.getStateForPlacement(context, () -> super.getStateForPlacement(context));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_54370_) {
        super.createBlockStateDefinition(p_54370_);
        p_54370_.add(TYPE);
    }

    @Override
    public ItemStack getCloneItemStack(
            BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return HexieBridges.modBootstrap().panelTypes()
                .matchSlabPanel2Block(state.getValue(TYPE))
                .asItem()
                .getDefaultInstance();
    }

    @Override
    public List<ItemStack> getDrops(BlockState pState, LootParams.Builder pBuilder) {
        return List.of(HexieBridges.modBootstrap().panelTypes().matchSlabPanel2Block(pState.getValue(TYPE)).asItem().getDefaultInstance());
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state,
            Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        return SignBlockInteract.ForTrainSpeedSignBlock.useWithoutItem(this, state, level, pos, player, hit);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack,
            BlockState state, Level level,
            BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return SignBlockInteract.ForTrainSpeedSignBlock.useItemOn(this, stack, state, level, pos, player, hand, hit);
    }
}
