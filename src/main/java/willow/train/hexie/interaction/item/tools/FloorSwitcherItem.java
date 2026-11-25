package willow.train.hexie.interaction.item.tools;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.interaction.ToolOpsBridge.FloorSwitcherItemMode;
import willow.train.hexie.bridge.interaction.ToolOpsBridge.FloorSwitcherItemSliderView;
import willow.train.hexie.bridge.interaction.ToolOpsBridge.FloorSwitcherItemView;
import willow.train.hexie.bridge.world.BlockEntityViews.CustomRenderedDoorEntityView;

// split from willow/train/hexie/item/FloorSwitcherItem.java


/** Keeps short-release and slider actions bound to the target where the wrench press began. */
public final class FloorSwitcherItem extends Item implements FloorSwitcherItemView {
    public record Slider(FloorSwitcherItemMode mode, int value) implements FloorSwitcherItemSliderView {}
    public static final int HOLD_TICKS = 10;
    private static final int USE_DURATION = 72000;

    public FloorSwitcherItem(Properties properties) { super(properties); }

    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return USE_DURATION; }

    @Override public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) {
            if (context.getPlayer().isSpectator() || !context.getPlayer().mayBuild()) return InteractionResult.FAIL;
            switchMode(context.getPlayer(), context.getHand());
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var support = HexieBridges.vehicle().assemblyBogeySupport().resolve(level, pos);
        if (support == null) return InteractionResult.PASS;
        var player = context.getPlayer();
        if (player == null || player.isSpectator() || !player.mayBuild()) return InteractionResult.FAIL;
        if (mode(context.getItemInHand()) == FloorSwitcherItemMode.REVERSE) {
            if (!level.isClientSide && level.mayInteract(player, pos) && level.mayInteract(player, support.owner())) {
                var data = support.entityData();
                var floorData = HexieBridges.worldBlock().bogeyFloorData();
                floorData.writeReversed(data, !floorData.reversedFromEntity(data));
                FloorSwitcherTargets.writeSupport(level, support, data);
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
        if (mode(player.getItemInHand(hand)) == FloorSwitcherItemMode.REVERSE) return InteractionResultHolder.pass(player.getItemInHand(hand));
        beginUsing(player, hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        if (!level.isClientSide && entity instanceof Player player && USE_DURATION - remainingUseDuration >= HOLD_TICKS)
            beginConfiguration(player);
    }

    @Override public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (level.isClientSide || !(entity instanceof Player player)) return;
        FloorSwitcherTargets.release(player, stack, USE_DURATION - timeLeft >= HOLD_TICKS);
    }

    public static FloorSwitcherItemMode mode(ItemStack stack) {
        return FloorSwitcherSettings.mode(stack);
    }

    public static boolean valid(ItemStack stack, int value) {
        return FloorSwitcherSettings.valid(stack, value);
    }

    public static void switchMode(Player player, InteractionHand hand) {
        FloorSwitcherSettings.switchMode(player, hand);
    }

    public static int height(ItemStack stack) {
        return FloorSwitcherSettings.height(stack);
    }

    public static void setHeight(ItemStack stack, int height) {
        FloorSwitcherSettings.setHeight(stack, height);
    }

    public static Component message(int height) {
        return FloorSwitcherSettings.message(height);
    }

    public static void beginUsing(Player player, InteractionHand hand, UUID carriage, BlockPos position) {
        FloorSwitcherTargets.beginUsing(player, hand, carriage, position);
    }

    public static void beginUsing(Player player, InteractionHand hand, UUID carriage, BlockPos position, int currentHeight) {
        FloorSwitcherTargets.beginUsing(player, hand, carriage, position, currentHeight);
    }

    public static void beginUsingDoor(Player player, InteractionHand hand, CustomRenderedDoorEntityView door) {
        FloorSwitcherTargets.beginUsingDoor(player, hand, door);
    }

    public static void beginUsing(Player player, InteractionHand hand) {
        FloorSwitcherTargets.beginUsing(player, hand);
    }

    public static int configurationHeight(Player player) {
        return FloorSwitcherTargets.configurationHeight(player);
    }

    public static Slider configuration(Player player) {
        return FloorSwitcherTargets.configuration(player);
    }

    public static void cancelPending(Player player) {
        FloorSwitcherTargets.cancelPending(player);
    }

    public static void beginConfiguration(Player player) {
        FloorSwitcherTargets.beginConfiguration(player);
    }

    public static void confirmConfiguration(Player player, InteractionHand hand, int height) {
        FloorSwitcherTargets.confirmConfiguration(player, hand, height);
    }

    public static boolean confirmDoorDirection(Player player, InteractionHand hand, int value) {
        return FloorSwitcherTargets.confirmDoorDirection(player, hand, value);
    }
}
