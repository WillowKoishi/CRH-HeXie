package willow.train.hexie.item;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import willow.train.hexie.blockentity.CustomRenderedDoorEntity;
import willow.train.hexie.blocks.emu.crh2a.CRH2DoorBlock;
import willow.train.hexie.blocks.bogeys.BogeyFloorData;
import willow.train.hexie.blocks.bogeys.HexieBogeyBlock;
import willow.train.hexie.blocks.bogeys.HexieBogeyBlockEntity;
import willow.train.hexie.compat.create.SnapshotFloorInteraction;

/** Keeps short-release and slider actions bound to the target where the wrench press began. */
public final class FloorSwitcherItem extends Item {
    public enum Mode { HEIGHT, LONGITUDINAL, DOOR_DIRECTION, REVERSE }
    public record Slider(Mode mode, int value) {}
    public static Mode mode(ItemStack stack) {
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (data.getBoolean("ReverseMode")) return Mode.REVERSE;
        return data.getBoolean("LongitudinalMode")
                ? Mode.LONGITUDINAL : Mode.HEIGHT;
    }
    public static boolean valid(ItemStack stack, int value) {
        return switch (mode(stack)) {
            case HEIGHT -> BogeyFloorData.valid(value);
            case LONGITUDINAL -> BogeyFloorData.validLongitudinal(value);
            default -> false;
        };
    }
    public static void switchMode(Player player, InteractionHand hand) {
        cancelPending(player);
        if (player.level().isClientSide) return;
        var stack = player.getItemInHand(hand);
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        var previous = mode(stack);
        data.putBoolean("LongitudinalMode", previous == Mode.HEIGHT);
        data.putBoolean("ReverseMode", previous == Mode.LONGITUDINAL);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        player.displayClientMessage(Component.translatable(switch (mode(stack)) {
            case LONGITUDINAL -> "gui.hexie.bogey_offset.title";
            case REVERSE -> "gui.hexie.bogey_reverse.title";
            default -> "gui.hexie.floor_height.title";
        }), true);
        if (player instanceof net.minecraft.server.level.ServerPlayer server)
            willow.train.hexie.network.ServerHeldItemSync.afterInteraction(server, hand);
    }
    public static final int HOLD_TICKS = 10;
    private static final int USE_DURATION = 72000;
    // Server-only, temporary interaction state. No target is written to player saves or item data.
    private static final Map<Player, Target> PENDING = new WeakHashMap<>();
    private static final Map<Player, Target> CONFIGURING = new WeakHashMap<>();
    // Accessed only on the client thread, independently of the integrated server's target maps.
    private static final Map<Player, Slider> CLIENT_SETTINGS = new WeakHashMap<>();
    private record Target(InteractionHand hand, ItemStack tool, ResourceKey<Level> dimension,
                          UUID carriage, BlockPos position, Slider slider, CustomRenderedDoorEntity door) {}

    public FloorSwitcherItem(Properties properties) { super(properties); }

    public static int height(ItemStack stack) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (mode(stack) == Mode.LONGITUDINAL) return BogeyFloorData.longitudinal(data);
        int height = data.getInt(BogeyFloorData.KEY);
        return BogeyFloorData.valid(height) ? height : BogeyFloorData.DEFAULT_HEIGHT;
    }

    public static void setHeight(ItemStack stack, int height) {
        if (!valid(stack, height)) throw new IllegalArgumentException("Invalid wrench setting: " + height);
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        data.putInt(mode(stack) == Mode.HEIGHT ? BogeyFloorData.KEY : BogeyFloorData.LONGITUDINAL_KEY, height);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    }

    public static Component message(int height) {
        return height == 24 ? Component.translatable("gui.hexie.floor_height.original")
                : Component.translatable("item.hexie.harmony_wrench.height", height / 10 + "." + height % 10);
    }

    public static void beginUsing(Player player, InteractionHand hand, UUID carriage, BlockPos position) {
        int current = player.level().getBlockEntity(position) instanceof HexieBogeyBlockEntity bogey
                ? mode(player.getItemInHand(hand)) == Mode.HEIGHT ? BogeyFloorData.get(bogey)
                : BogeyFloorData.longitudinal(bogey.getBogeyData()) : height(player.getItemInHand(hand));
        beginUsing(player, hand, carriage, position, current);
    }

    public static void beginUsing(Player player, InteractionHand hand, UUID carriage, BlockPos position, int currentHeight) {
        beginUsing(player, hand, carriage, position, new Slider(mode(player.getItemInHand(hand)), currentHeight), null);
    }

    public static void beginUsingDoor(Player player, InteractionHand hand, CustomRenderedDoorEntity door) {
        beginUsing(player, hand, null, door.getBlockPos(), new Slider(Mode.DOOR_DIRECTION, door.hasReverseSlide() ? 1 : 0), door);
    }

    private static void beginUsing(Player player, InteractionHand hand, UUID carriage, BlockPos position,
                                   Slider slider, CustomRenderedDoorEntity door) {
        cancelPending(player);
        if (player.level().isClientSide)
            CLIENT_SETTINGS.put(player, slider);
        else
            PENDING.put(player, new Target(hand, player.getItemInHand(hand), player.level().dimension(),
                    carriage, position.immutable(), slider, door));
        player.startUsingItem(hand);
    }

    public static void beginUsing(Player player, InteractionHand hand) {
        cancelPending(player);
        player.startUsingItem(hand);
    }

    /** The pressed target supplies the initial slider value; opening must not copy a previous target's setting. */
    public static int configurationHeight(Player player) {
        return configuration(player).value();
    }

    public static Slider configuration(Player player) {
        if (player.level().isClientSide) {
            Slider slider = CLIENT_SETTINGS.get(player);
            if (slider != null) return slider;
        } else {
            Target target = PENDING.get(player);
            if (target == null) target = CONFIGURING.get(player);
            if (target != null) return target.slider();
        }
        ItemStack tool = player.getUseItem();
        return new Slider(mode(tool), height(tool));
    }

    public static void cancelPending(Player player) {
        if (player.level().isClientSide) CLIENT_SETTINGS.remove(player);
        else {
            PENDING.remove(player);
            CONFIGURING.remove(player);
        }
    }

    public static void beginConfiguration(Player player) {
        if (player.level().isClientSide) return;
        Target target = PENDING.remove(player);
        if (target != null) CONFIGURING.put(player, target);
    }

    public static void confirmConfiguration(Player player, InteractionHand hand, int height) {
        Target target = CONFIGURING.remove(player);
        if (target != null && target.door() == null && target.hand() == hand) applyTarget(player, target.tool(), target, height);
    }

    public static boolean confirmDoorDirection(Player player, InteractionHand hand, int value) {
        Target target = CONFIGURING.get(player);
        if (target == null || target.door() == null || target.hand() != hand || (value != 0 && value != 1))
            return false;
        CONFIGURING.remove(player);
        if (!canUseDoor(player, target)) return false;
        target.door().setReverseSlide(value == 1);
        return true;
    }

    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return USE_DURATION; }

    @Override public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) {
            if (context.getPlayer().isSpectator() || !context.getPlayer().mayBuild()) return InteractionResult.FAIL;
            switchMode(context.getPlayer(), context.getHand());
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        var level = context.getLevel();
        var pos = context.getClickedPos();
        if (!(level.getBlockState(pos).getBlock() instanceof HexieBogeyBlock<?>)
                || !(level.getBlockEntity(pos) instanceof HexieBogeyBlockEntity bogey)) return InteractionResult.PASS;
        var player = context.getPlayer();
        if (player == null || player.isSpectator() || !player.mayBuild()) return InteractionResult.FAIL;
        if (mode(context.getItemInHand()) == Mode.REVERSE) {
            if (!level.isClientSide && level.mayInteract(player, pos)) {
                var data = bogey.getBogeyData();
                data.putBoolean(BogeyFloorData.REVERSED, !data.getBoolean(BogeyFloorData.REVERSED));
                bogey.setChanged();
                bogey.sendData();
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        beginUsing(player, context.getHand(), null, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player.isSpectator() || !player.mayBuild()) return InteractionResultHolder.fail(player.getItemInHand(hand));
        if (player.isShiftKeyDown()) {
            switchMode(player, hand);
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        }
        if (mode(player.getItemInHand(hand)) == Mode.REVERSE) return InteractionResultHolder.pass(player.getItemInHand(hand));
        beginUsing(player, hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        if (!level.isClientSide && entity instanceof Player player && USE_DURATION - remainingUseDuration >= HOLD_TICKS)
            beginConfiguration(player);
    }

    @Override public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (level.isClientSide || !(entity instanceof Player player)) return;
        Target target = PENDING.remove(player);
        if (target == null) return;
        if (USE_DURATION - timeLeft >= HOLD_TICKS) {
            CONFIGURING.put(player, target);
            return;
        }
        if (target.door() != null) {
            if (target.tool() == stack && canUseDoor(player, target)) target.door().togglePassengerFrame();
        } else applyTarget(player, stack, target, height(stack));
    }

    private static boolean canUseDoor(Player player, Target target) {
        Level level = player.level();
        BlockPos pos = target.position();
        if (!canUseTarget(player, target) || !level.hasChunkAt(pos)
                || !level.mayInteract(player, pos) || !player.canInteractWithBlock(pos, 1))
            return false;
        var state = level.getBlockState(pos);
        return level.getBlockEntity(pos) == target.door() && state.getBlock() instanceof CRH2DoorBlock block
                && block.supportsPassengerFrame() && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
    }

    private static boolean canUseTarget(Player player, Target target) {
        return !player.level().isClientSide && !player.isSpectator() && player.mayBuild()
                && target.dimension().equals(player.level().dimension()) && player.getItemInHand(target.hand()) == target.tool()
                && target.tool().getItem() instanceof FloorSwitcherItem;
    }

    private static void applyTarget(Player player, ItemStack stack, Target target, int height) {
        Level level = player.level();
        if (!canUseTarget(player, target) || target.tool() != stack
                || target.slider().mode() != mode(stack) || !valid(stack, height)) return;
        if (target.carriage() != null) {
            if (target.slider().mode() == Mode.LONGITUDINAL)
                SnapshotFloorInteraction.applyLongitudinal(player, target.carriage(), target.position(), target.hand(), height);
            else SnapshotFloorInteraction.apply(player, target.carriage(), target.position(), target.hand(), height);
            return;
        }
        BlockPos pos = target.position();
        if (!level.hasChunkAt(pos) || !level.mayInteract(player, pos) || !player.canInteractWithBlock(pos, 1)
                || !(level.getBlockState(pos).getBlock() instanceof HexieBogeyBlock<?>)
                || !(level.getBlockEntity(pos) instanceof HexieBogeyBlockEntity bogey)) return;
        if (target.slider().mode() == Mode.HEIGHT) BogeyFloorData.set(bogey, height);
        else BogeyFloorData.setLongitudinal(bogey, height);
        player.displayClientMessage(target.slider().mode() == Mode.HEIGHT ? message(height)
                : Component.translatable("gui.hexie.bogey_offset.value", height), true);
    }
}
