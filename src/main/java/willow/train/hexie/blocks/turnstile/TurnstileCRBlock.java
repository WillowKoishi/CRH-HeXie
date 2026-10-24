package willow.train.hexie.blocks.turnstile;

import com.simibubi.create.content.equipment.wrench.IWrenchable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import willow.train.hexie.compat.ItemData;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import willow.train.hexie.Main;
import willow.train.hexie.init.BlockEntitiesInit;
import willow.train.hexie.init.block.BlockInit;
import willow.train.hexie.init.SoundsInit;
import willow.train.hexie.item.tickets.CardItem;
import willow.train.hexie.item.tickets.TicketItem;
import willow.train.hexie.item.tickets.Ticketable;
import willow.train.hexie.blockentity.TurnstileBlockEntity;


public class TurnstileCRBlock extends BaseEntityBlock implements IWrenchable {
    @Override
    protected com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.block.BaseEntityBlock> codec() {
        return simpleCodec(TurnstileCRBlock::new);
    }

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    public static final IntegerProperty HINGE = IntegerProperty.create("hinge", 0, 2);

    private static final VoxelShape S_BASE_L = Block.box(15, 0, -5, 16, 18, 21);
    private static final VoxelShape S_BASE_C = Block.box(4, 0, 9, 15, 18, 10);
    private static final VoxelShape S_BASE_R = Block.box(0, 0, -5, 4, 18, 21);

    private static final VoxelShape N_BASE_L = Block.box(12, 0, -5, 16, 18, 21);
    private static final VoxelShape N_BASE_C = Block.box(1, 0, 6, 12, 18, 7);
    private static final VoxelShape N_BASE_R = Block.box(0, 0, -5, 1, 18, 21);

    private static final VoxelShape E_BASE_L = Block.box(-5, 0, 15, 21, 18, 16);
    private static final VoxelShape E_BASE_C = Block.box(6, 0, 4, 7, 18, 15);
    private static final VoxelShape E_BASE_R = Block.box(-5, 0, 0, 21, 18, 4);

    private static final VoxelShape W_BASE_L = Block.box(-5, 0, 0, 21, 18, 1);
    private static final VoxelShape W_BASE_C = Block.box(9, 0, 1, 10, 18, 12);
    private static final VoxelShape W_BASE_R = Block.box(-5, 0, 12, 21, 18, 16);

    private static final VoxelShape S_OPEN = Shapes.join(S_BASE_L, S_BASE_R, BooleanOp.OR);
    private static final VoxelShape S_CLOSED = Shapes.join(S_OPEN, S_BASE_C, BooleanOp.OR);
    private static final VoxelShape N_OPEN = Shapes.join(N_BASE_L, N_BASE_R, BooleanOp.OR);
    private static final VoxelShape N_CLOSED = Shapes.join(N_OPEN, N_BASE_C, BooleanOp.OR);
    private static final VoxelShape E_OPEN = Shapes.join(E_BASE_L, E_BASE_R, BooleanOp.OR);
    private static final VoxelShape E_CLOSED = Shapes.join(E_OPEN, E_BASE_C, BooleanOp.OR);
    private static final VoxelShape W_OPEN = Shapes.join(W_BASE_L, W_BASE_R, BooleanOp.OR);
    private static final VoxelShape W_CLOSED = Shapes.join(W_OPEN, W_BASE_C, BooleanOp.OR);

    public TurnstileCRBlock(Properties arg) {
        super(arg);
        registerDefaultState(
                this.stateDefinition
                        .any()
                        .setValue(FACING, Direction.NORTH)
                        .setValue(OPEN, false)
                        .setValue(HINGE, 0));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> stateManager) {
        stateManager.add(FACING, OPEN, HINGE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState()
                .setValue(FACING, ctx.getHorizontalDirection().getOpposite())
                .setValue(OPEN, false)
                .setValue(HINGE, 0);
    }

    public BlockState getStateForPlacement(Direction direction, int hinge) {
        return this.defaultBlockState()
                .setValue(FACING, direction)
                .setValue(OPEN, false)
                .setValue(HINGE, hinge);
    }

    public InteractionResult use(
            BlockState state,
            Level world,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        if (world.isClientSide()) return InteractionResult.SUCCESS;
        ItemStack stack = player.getItemInHand(hand);
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof TurnstileBlockEntity turnstile) {
            if (!turnstile.exit) {
                if (stack.getItem() instanceof TicketItem) {
                    CompoundTag nbt = ItemData.getOrCreateTagElement(stack, "ticketInfo");
                    if (!nbt.getBoolean("used")) {
                        nbt.putBoolean("used", true);
                        ItemData.setTagElement(stack, "ticketInfo", nbt);
                        ItemData.updateTagElement(stack, "stationInfo", data -> data.putBoolean("enteredStation", true));
                        turnstile.setTimer(60);
                        world.playSound(
                                null,
                                pos,
                                SoundsInit.BEEP_TURNSTILE.get(),
                                SoundSource.BLOCKS,
                                1f,
                                1f);
                        return InteractionResult.SUCCESS;
                    }
                } else if (stack.getItem() instanceof CardItem) {
                    CompoundTag nbt = ItemData.getOrCreateTagElement(stack, "cardInfo");
                    if (nbt.getLong("money") >= 100) {
                        nbt.putLong("money", nbt.getLong("money") - 100);
                        ItemData.setTagElement(stack, "cardInfo", nbt);
                        ItemData.updateTagElement(stack, "stationInfo", data -> data.putBoolean("enteredStation", true));
                        turnstile.setTimer(60);
                        world.playSound(
                                null,
                                pos,
                                SoundsInit.BEEP_TURNSTILE.get(),
                                SoundSource.BLOCKS,
                                1f,
                                1f);
                        return InteractionResult.SUCCESS;
                    }
                }
            } else {
                if (stack.getItem() instanceof Ticketable) {
                    CompoundTag nbt = ItemData.getOrCreateTagElement(stack, "stationInfo");
                    if (nbt.getBoolean("enteredStation")) {
                        turnstile.setTimer(60);
                        nbt.putBoolean("enteredStation", false);
                        ItemData.setTagElement(stack, "stationInfo", nbt);
                        world.playSound(
                                null,
                                pos,
                                SoundsInit.BEEP_TURNSTILE.get(),
                                SoundSource.BLOCKS,
                                1f,
                                1f);
                        return InteractionResult.SUCCESS;
                    }
                }
            }
        }
        return super.useWithoutItem(state, world, pos, player, hit);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, @NotNull UseOnContext context) {
        Level world = context.getLevel();
        Player player = context.getPlayer();
        if (player != null) {
            BlockEntity be = world.getBlockEntity(context.getClickedPos());
            if (be instanceof TurnstileBlockEntity turnstile) {
                turnstile.exit = !turnstile.exit;
                turnstile.setChanged();
                player.displayClientMessage(
                        Component.translatable(
                                "msg." + Main.MOD_ID + ".set_exit." + turnstile.exit),
                        true);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        Level world = context.getLevel();
        Player player = context.getPlayer();
        if (player != null) {
            if (world.isClientSide) return InteractionResult.SUCCESS;
            world.setBlock(
                    context.getClickedPos(),
                    BlockInit.TURNS_B
                            .get()
                            .getStateForPlacement(state.getValue(FACING), nextHinge(state)),
                    2);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.FAIL;
    }

    public int nextHinge(BlockState pState) {
        int hinge = pState.getValue(HINGE);
        if (hinge < 2) {
            return hinge + 1;
        }
        return 0;
    }

    public int prevHinge(BlockState pState) {
        int hinge = pState.getValue(HINGE);
        if (hinge > 0) {
            return hinge - 1;
        }
        return 2;
    }

    @Override
    public VoxelShape getShape(
            BlockState state, BlockGetter view, BlockPos pos, CollisionContext ctx) {
        Direction dir = state.getValue(FACING);
        boolean close = !state.getValue(OPEN);
        return switch (dir) {
            case NORTH -> close ? S_CLOSED : S_OPEN;
            case SOUTH -> close ? N_CLOSED : N_OPEN;
            case EAST -> close ? E_CLOSED : E_OPEN;
            case WEST -> close ? W_CLOSED : W_OPEN;
            default -> close ? S_CLOSED : S_OPEN;
        };
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TurnstileBlockEntity(BlockEntitiesInit.TURNS_A.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level world, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(
                type, BlockEntitiesInit.TURNS_A.get(), TurnstileBlockEntity::tick);
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
