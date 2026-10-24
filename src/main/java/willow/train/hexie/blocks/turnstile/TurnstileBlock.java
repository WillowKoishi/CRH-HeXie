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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import willow.train.hexie.Main;
import willow.train.hexie.init.BlockEntitiesInit;
import willow.train.hexie.init.SoundsInit;
import willow.train.hexie.item.tickets.CardItem;
import willow.train.hexie.item.tickets.TicketItem;
import willow.train.hexie.item.tickets.Ticketable;
import willow.train.hexie.blockentity.TurnstileBlockEntity;


public class TurnstileBlock extends BaseEntityBlock implements IWrenchable {
    private static final VoxelShape NORTH_OPEN = Shapes.or(
            Block.box(0, 0, 0, 1, 14, 16), Block.box(12.5, 0, 0, 16, 14, 16));
    private static final VoxelShape SOUTH_OPEN = Shapes.or(
            Block.box(0, 0, 0, 3.5, 14, 16), Block.box(15, 0, 0, 16, 14, 16));
    private static final VoxelShape EAST_OPEN = Shapes.or(
            Block.box(0, 0, 0, 16, 14, 1), Block.box(0, 0, 12.5, 16, 14, 16));
    private static final VoxelShape WEST_OPEN = Shapes.or(
            Block.box(0, 0, 0, 16, 14, 3.5), Block.box(0, 0, 15, 16, 14, 16));
    private static final VoxelShape NORTH_CLOSED = Shapes.or(
            Block.box(0, 0, 0, 1, 24, 16), Block.box(12.5, 0, 0, 16, 24, 16),
            Block.box(0, 0, 6, 16, 24, 10));
    private static final VoxelShape SOUTH_CLOSED = Shapes.or(
            Block.box(0, 0, 0, 3.5, 24, 16), Block.box(15, 0, 0, 16, 24, 16),
            Block.box(0, 0, 6, 16, 24, 10));
    private static final VoxelShape EAST_CLOSED = Shapes.or(
            Block.box(0, 0, 0, 16, 24, 1), Block.box(0, 0, 12.5, 16, 24, 16),
            Block.box(6, 0, 0, 10, 24, 16));
    private static final VoxelShape WEST_CLOSED = Shapes.or(
            Block.box(0, 0, 0, 16, 24, 3.5), Block.box(0, 0, 15, 16, 24, 16),
            Block.box(6, 0, 0, 10, 24, 16));
    @Override
    protected com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.block.BaseEntityBlock> codec() {
        return simpleCodec(TurnstileBlock::new);
    }

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BooleanProperty.create("open");

    public TurnstileBlock(Properties arg) {
        super(arg);
        registerDefaultState(
                this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
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
        stateManager.add(FACING).add(OPEN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState()
                .setValue(FACING, ctx.getHorizontalDirection().getOpposite())
                .setValue(OPEN, false);
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
    public VoxelShape getCollisionShape(
            BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        Direction dir = state.getValue(FACING);
        boolean open = state.getValue(OPEN);
        return switch (dir) {
            case SOUTH -> open ? SOUTH_OPEN : SOUTH_CLOSED;
            case NORTH -> open ? NORTH_OPEN : NORTH_CLOSED;
            case EAST -> open ? EAST_OPEN : EAST_CLOSED;
            case WEST -> open ? WEST_OPEN : WEST_CLOSED;
            default -> Shapes.block();
        };
    }

    @Override
    public VoxelShape getShape(
            BlockState state, BlockGetter view, BlockPos pos, CollisionContext ctx) {
        Direction dir = state.getValue(FACING);
        return switch (dir) {
            case SOUTH -> SOUTH_OPEN;
            case NORTH -> NORTH_OPEN;
            case EAST -> EAST_OPEN;
            case WEST -> WEST_OPEN;
            default -> Shapes.block();
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
