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
import willow.train.hexie.blocks.bogeys.BogeyFloorData;
import willow.train.hexie.blocks.bogeys.HexieBogeyBlock;
import willow.train.hexie.blocks.bogeys.HexieBogeyBlockEntity;
import willow.train.hexie.compat.create.SnapshotFloorInteraction;

/** Short release applies the stored height; a held slider applies its confirmed value to the same target. */
public final class FloorSwitcherItem extends Item {
    public enum Mode { HEIGHT, LONGITUDINAL }
    public static Mode mode(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean("LongitudinalMode")
                ? Mode.LONGITUDINAL : Mode.HEIGHT;
    }
    public static boolean valid(ItemStack stack, int value) {
        return mode(stack) == Mode.HEIGHT ? BogeyFloorData.valid(value) : BogeyFloorData.validLongitudinal(value);
    }
    public static void switchMode(Player player, InteractionHand hand) {
        cancelPending(player);
        if (player.level().isClientSide) return;
        var stack = player.getItemInHand(hand);
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        data.putBoolean("LongitudinalMode", mode(stack) == Mode.HEIGHT);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        player.displayClientMessage(Component.translatable(mode(stack) == Mode.HEIGHT
                ? "gui.hexie.floor_height.title" : "gui.hexie.bogey_offset.title"), true);
        if (player instanceof net.minecraft.server.level.ServerPlayer server)
            willow.train.hexie.network.ServerHeldItemSync.afterInteraction(server, hand);
    }
    public static final int HOLD_TICKS = 10;
    private static final int USE_DURATION = 72000;
    // Server-only, temporary interaction state. No target is written to player saves or item data.
    private static final Map<Player, Target> PENDING = new WeakHashMap<>();
    private static final Map<Player, Target> CONFIGURING = new WeakHashMap<>();
    // Accessed only on the client thread, independently of the integrated server's target maps.
    private static final Map<Player, Integer> CLIENT_INITIAL_HEIGHTS = new WeakHashMap<>();
    private record Target(InteractionHand hand, ItemStack tool, ResourceKey<Level> dimension,
                          UUID carriage, BlockPos position, int initialHeight, Mode mode) {}

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
                : Component.translatable("item.hexie.floor_switcher.height", height / 10 + "." + height % 10);
    }

    public static void beginUsing(Player player, InteractionHand hand, UUID carriage, BlockPos position) {
        int current = player.level().getBlockEntity(position) instanceof HexieBogeyBlockEntity bogey
                ? mode(player.getItemInHand(hand)) == Mode.HEIGHT ? BogeyFloorData.get(bogey)
                : BogeyFloorData.longitudinal(bogey.getBogeyData()) : height(player.getItemInHand(hand));
        beginUsing(player, hand, carriage, position, current);
    }

    public static void beginUsing(Player player, InteractionHand hand, UUID carriage, BlockPos position, int currentHeight) {
        cancelPending(player);
        if (player.level().isClientSide)
            CLIENT_INITIAL_HEIGHTS.put(player, currentHeight);
        else
            PENDING.put(player, new Target(hand, player.getItemInHand(hand), player.level().dimension(),
                    carriage, position.immutable(), currentHeight, mode(player.getItemInHand(hand))));
        player.startUsingItem(hand);
    }

    public static void beginUsing(Player player, InteractionHand hand) {
        cancelPending(player);
        player.startUsingItem(hand);
    }

    /** The pressed target supplies the initial slider value; opening must not copy a previous target's setting. */
    public static int configurationHeight(Player player) {
        if (player.level().isClientSide)
            return CLIENT_INITIAL_HEIGHTS.getOrDefault(player, height(player.getUseItem()));
        Target target = PENDING.get(player);
        if (target == null) target = CONFIGURING.get(player);
        return target == null ? height(player.getUseItem()) : target.initialHeight();
    }

    public static void cancelPending(Player player) {
        if (player.level().isClientSide) CLIENT_INITIAL_HEIGHTS.remove(player);
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
        if (target != null && target.hand() == hand) applyTarget(player, target.tool(), target, height);
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
                || !(level.getBlockEntity(pos) instanceof HexieBogeyBlockEntity)) return InteractionResult.PASS;
        var player = context.getPlayer();
        if (player == null || player.isSpectator() || !player.mayBuild()) return InteractionResult.FAIL;
        beginUsing(player, context.getHand(), null, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player.isSpectator() || !player.mayBuild()) return InteractionResultHolder.fail(player.getItemInHand(hand));
        if (player.isShiftKeyDown()) {
            switchMode(player, hand);
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        }
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
        applyTarget(player, stack, target, height(stack));
    }

    private static void applyTarget(Player player, ItemStack stack, Target target, int height) {
        Level level = player.level();
        if (level.isClientSide || player.isSpectator() || !player.mayBuild()
                || !target.dimension().equals(level.dimension()) || player.getItemInHand(target.hand()) != target.tool()
                || target.tool() != stack || !(stack.getItem() instanceof FloorSwitcherItem)
                || target.mode() != mode(stack) || !valid(stack, height)) return;
        if (target.carriage() != null) {
            if (target.mode() == Mode.LONGITUDINAL)
                SnapshotFloorInteraction.applyLongitudinal(player, target.carriage(), target.position(), target.hand(), height);
            else SnapshotFloorInteraction.apply(player, target.carriage(), target.position(), target.hand(), height);
            return;
        }
        BlockPos pos = target.position();
        if (!level.hasChunkAt(pos) || !level.mayInteract(player, pos) || !player.canInteractWithBlock(pos, 1)
                || !(level.getBlockState(pos).getBlock() instanceof HexieBogeyBlock<?>)
                || !(level.getBlockEntity(pos) instanceof HexieBogeyBlockEntity bogey)) return;
        if (target.mode() == Mode.HEIGHT) BogeyFloorData.set(bogey, height);
        else BogeyFloorData.setLongitudinal(bogey, height);
        player.displayClientMessage(target.mode() == Mode.HEIGHT ? message(height)
                : Component.translatable("gui.hexie.bogey_offset.value", height), true);
    }
}
