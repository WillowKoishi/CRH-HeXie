package willow.train.hexie.world.blocks.signs;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.SignBlockBridge;
import willow.train.hexie.core.api.interaction.HexieWrenchable;
import willow.train.hexie.core.util.enums.UpperPanelTypes;
import willow.train.hexie.core.util.general.RandomTestUtil;
import willow.train.hexie.world.blockentity.sign.CarriageNoSignEntity;
import willow.train.hexie.world.blockinteract.SignBlockInteract;
import willow.train.hexie.world.blocks.base.SignBlockBase;
import willow.train.hexie.world.collision.SignCollision;
import willow.train.hexie.world.placement.SignPlacement;
import willow.train.hexie.world.registry.items.EditablePanelItemInit;

public class TrainNoBlock extends SignBlockBase implements HexieWrenchable, SignBlockBridge.TrainNoBlockView {

    public static final EnumProperty<DoorHingeSide> HINGE = BlockStateProperties.DOOR_HINGE;
    public static final EnumProperty<UpperPanelTypes> TYPE =
            EnumProperty.create("type", UpperPanelTypes.class);

    public TrainNoBlock(BlockBehaviour.Properties p_49795_) {
        super(p_49795_);
    }

    public VoxelShape getShape(
            BlockState p_54372_,
            BlockGetter p_54373_,
            BlockPos p_54374_,
            CollisionContext p_54375_) {
        return SignCollision.ForTrainNoBlock.getShape(p_54372_, p_54373_, p_54374_, p_54375_);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return SignPlacement.ForTrainNoBlock.getStateForPlacement(this, context, () -> super.getStateForPlacement(context));
    }

    public BlockState getStateForPlacement(
            BlockPlaceContext context, DoorHingeSide hinge, UpperPanelTypes types) {
        return SignPlacement.ForTrainNoBlock.getStateForPlacement(context, hinge, types, () -> super.getStateForPlacement(context));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return new CarriageNoSignEntity(pPos, pState);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pState) {
        super.createBlockStateDefinition(pState);
        pState.add(HINGE, TYPE);
    }

    public DoorHingeSide getHinge(BlockPlaceContext pContext) {
        return SignPlacement.ForTrainNoBlock.getHinge(pContext);
    }

    /**
     * 关于 use 方法的一些注意事项：如果要使玩家右键时方块实体直接响应，不需要判断是否为clientSide，就像下例中的 mirror() 的使用
     *
     * @param pState 方块状态
     * @param pLevel 世界
     * @param pPos 方块位置
     * @param pPlayer 操作玩家
     * @param pHand 操作手
     * @param pHit 点击事件
     * @return 交互结果
     */
    public InteractionResult use(
            BlockState pState,
            Level pLevel,
            BlockPos pPos,
            Player pPlayer,
            InteractionHand pHand,
            BlockHitResult pHit) {
        return SignBlockInteract.ForTrainNoBlock.use(pState, pLevel, pPos, pPlayer, pHand, pHit, () -> super.useWithoutItem(pState, pLevel, pPos, pPlayer, pHit));
    }

    @Override
    public ItemStack getCloneItemStack(
            BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return HexieBridges.modBootstrap().panelTypes()
                .matchUpperPanel2Block(state.getValue(TYPE))
                .asItem()
                .getDefaultInstance();
    }

    @Override
    public List<ItemStack> getDrops(BlockState pState, LootParams.Builder pBuilder) {
        int level = RandomTestUtil.negativeBinomialRandomExperiment(0.8f, 2);
        List<ItemStack> list;
        switch (level) {
            case 1 -> list = List.of(HexieBridges.modBootstrap().panelTypes().matchUpperPanel2Block(pState.getValue(TYPE)).asItem().getDefaultInstance(),
                        Items.PAPER.getDefaultInstance());
            case 2 -> list =  List.of(HexieBridges.modBootstrap().panelTypes().matchUpperPanel2Block(pState.getValue(TYPE)).asItem().getDefaultInstance(),
                    EditablePanelItemInit.Sticker.get().getDefaultInstance());
            default -> list = List.of(HexieBridges.modBootstrap().panelTypes().matchUpperPanel2Block(pState.getValue(TYPE)).asItem().getDefaultInstance());
        }
        return list;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state,
            Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        return SignBlockInteract.ForTrainNoBlock.useWithoutItem(this, state, level, pos, player, hit);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack,
            BlockState state, Level level,
            BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return SignBlockInteract.ForTrainNoBlock.useItemOn(this, stack, state, level, pos, player, hand, hit);
    }
}
