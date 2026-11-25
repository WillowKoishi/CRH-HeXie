package willow.train.hexie.world.blocks.signs;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.SignBlockBridge;
import willow.train.hexie.core.api.interaction.HexieWrenchable;
import willow.train.hexie.core.util.enums.PanelTypes;
import willow.train.hexie.world.blockentity.sign.CarriageTypeSignEntity;
import willow.train.hexie.world.blockinteract.SignBlockInteract;
import willow.train.hexie.world.blocks.base.SignBlockBase;
import willow.train.hexie.world.collision.SignCollision;
import willow.train.hexie.world.placement.SignPlacement;

public class TrainPanelBlock extends SignBlockBase implements HexieWrenchable, SignBlockBridge.TrainPanelBlockView {

    public static final EnumProperty<DoorHingeSide> HINGE = BlockStateProperties.DOOR_HINGE;

    public static final BooleanProperty MIRROR = BlockStateProperties.OPEN;
    public static final EnumProperty<PanelTypes> TYPE =
            EnumProperty.create("type", PanelTypes.class);

    public TrainPanelBlock(BlockBehaviour.Properties p_49795_) {
        super(p_49795_);
    }

    public VoxelShape getShape(
            BlockState p_54372_,
            BlockGetter p_54373_,
            BlockPos p_54374_,
            CollisionContext p_54375_) {
        return SignCollision.ForTrainPanelBlock.getShape(p_54372_, p_54373_, p_54374_, p_54375_);
    }

    // 猪
    public DoorHingeSide getHinge(BlockPlaceContext pContext) {
        return SignPlacement.ForTrainPanelBlock.getHinge(pContext);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return SignPlacement.ForTrainPanelBlock.getStateForPlacement(this, context, () -> super.getStateForPlacement(context));
    }

    public BlockState getStateForPlacement(
            BlockPlaceContext context, PanelTypes type, DoorHingeSide hinge) {
        return SignPlacement.ForTrainPanelBlock.getStateForPlacement(context, type, hinge, () -> super.getStateForPlacement(context));
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        super.createBlockStateDefinition(pBuilder);
        pBuilder.add(HINGE, TYPE, MIRROR);
    }

    @Override
    public void destroy(LevelAccessor pLevel, BlockPos pPos, BlockState pState) {
        super.destroy(pLevel, pPos, pState);
        if (pLevel.getBlockEntity(pPos) != null) pLevel.getBlockEntity(pPos).setRemoved();
    }

    @Override
    public void wasExploded(Level pLevel, BlockPos pPos, Explosion pExplosion) {
        super.wasExploded(pLevel, pPos, pExplosion);
        if (pLevel.getBlockEntity(pPos) != null) pLevel.getBlockEntity(pPos).setRemoved();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return new CarriageTypeSignEntity(pPos, pState);
    }

    public InteractionResult use(
            BlockState pState,
            Level pLevel,
            BlockPos pPos,
            Player pPlayer,
            InteractionHand pHand,
            BlockHitResult pHit) {
        return SignBlockInteract.ForTrainPanelBlock.use(this, pState, pLevel, pPos, pPlayer, pHand, pHit);
    }

    public boolean isMirrored(BlockState pState) {
        return pState.getValue(MIRROR);
    }

    @Override
    public ItemStack getCloneItemStack(
            BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        PanelTypes types = state.getValue(TYPE);
        return HexieBridges.modBootstrap().panelTypes().matchBottomPanel2Block(types).asItem().getDefaultInstance();
    }

    @Override
    public List<ItemStack> getDrops(BlockState pState, LootParams.Builder pBuilder) {
        return List.of(HexieBridges.modBootstrap().panelTypes().matchBottomPanel2Block(pState.getValue(TYPE)).asItem().getDefaultInstance());
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state,
            Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        return SignBlockInteract.ForTrainPanelBlock.useWithoutItem(this, state, level, pos, player, hit);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack,
            BlockState state, Level level,
            BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return SignBlockInteract.ForTrainPanelBlock.useItemOn(this, stack, state, level, pos, player, hand, hit);
    }
}
