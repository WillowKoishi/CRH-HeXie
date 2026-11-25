package willow.train.hexie.interaction.item.tools;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.interaction.ToolOpsBridge.FloorSwitcherItemMode;
import willow.train.hexie.bridge.vehicle.VehicleBridge.AssemblyBogeySupportView;
import willow.train.hexie.bridge.world.BlockEntityViews.CRH2HeadEntityView;
import willow.train.hexie.bridge.world.BlockEntityViews.CustomRenderedDoorEntityView;
import willow.train.hexie.bridge.world.BlockEntityViews.HexieBogeyBlockEntityView;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH2DoorBlockView;
import willow.train.hexie.interaction.item.tools.FloorSwitcherItem.Slider;

// split from willow/train/hexie/item/FloorSwitcherItem.java
final class FloorSwitcherTargets {
    // Server-only, temporary interaction state. No target is written to player saves or item data.
    private static final Map<Player, Target> PENDING = new WeakHashMap<>();
    private static final Map<Player, Target> CONFIGURING = new WeakHashMap<>();
    // Accessed only on the client thread, independently of the integrated server's target maps.
    private static final Map<Player, Slider> CLIENT_SETTINGS = new WeakHashMap<>();
    private record Target(InteractionHand hand, ItemStack tool, ResourceKey<Level> dimension,
                          UUID carriage, BlockPos position, Slider slider, CustomRenderedDoorEntityView door) {}

    public static void beginUsing(Player player, InteractionHand hand, UUID carriage, BlockPos position) {
        var support = HexieBridges.vehicle().assemblyBogeySupport().resolve(player.level(), position);
        int current = support != null
                ? FloorSwitcherSettings.mode(player.getItemInHand(hand)) == FloorSwitcherItemMode.HEIGHT ? HexieBridges.worldBlock().bogeyFloorData().value(support.entityData())
                : HexieBridges.worldBlock().bogeyFloorData().longitudinalFromEntity(support.entityData()) : FloorSwitcherSettings.height(player.getItemInHand(hand));
        beginUsing(player, hand, carriage, position, current);
    }

    public static void beginUsing(Player player, InteractionHand hand, UUID carriage, BlockPos position, int currentHeight) {
        beginUsing(player, hand, carriage, position, new Slider(FloorSwitcherSettings.mode(player.getItemInHand(hand)), currentHeight), null);
    }

    public static void beginUsingDoor(Player player, InteractionHand hand, CustomRenderedDoorEntityView door) {
        beginUsing(player, hand, null, door.asBlockEntity().getBlockPos(), new Slider(FloorSwitcherItemMode.DOOR_DIRECTION, door.hasReverseSlide() ? 1 : 0), door);
    }

    private static void beginUsing(Player player, InteractionHand hand, UUID carriage, BlockPos position,
                                   Slider slider, CustomRenderedDoorEntityView door) {
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
        return new Slider(FloorSwitcherSettings.mode(tool), FloorSwitcherSettings.height(tool));
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

    private static boolean canUseDoor(Player player, Target target) {
        Level level = player.level();
        BlockPos pos = target.position();
        if (!canUseTarget(player, target) || !level.hasChunkAt(pos)
                || !level.mayInteract(player, pos) || !player.canInteractWithBlock(pos, 1))
            return false;
        var state = level.getBlockState(pos);
        return level.getBlockEntity(pos) == target.door() && state.getBlock() instanceof CRH2DoorBlockView block
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
                || target.slider().mode() != FloorSwitcherSettings.mode(stack) || !FloorSwitcherSettings.valid(stack, height)) return;
        if (target.carriage() != null) {
            if (!HexieBridges.modBootstrap().isCreateLoaded()) return;
            if (target.slider().mode() == FloorSwitcherItemMode.LONGITUDINAL)
                HexieBridges.createCarriage().snapshotFloorInteraction().applyLongitudinal(player, target.carriage(), target.position(), target.hand(), height);
            else HexieBridges.createCarriage().snapshotFloorInteraction().apply(player, target.carriage(), target.position(), target.hand(), height);
            return;
        }
        BlockPos pos = target.position();
        if (!level.hasChunkAt(pos) || !level.mayInteract(player, pos) || !player.canInteractWithBlock(pos, 1)) return;
        var support = HexieBridges.vehicle().assemblyBogeySupport().resolve(level, pos);
        if (support == null || !level.mayInteract(player, support.owner())) return;
        var data = support.entityData();
        if (target.slider().mode() == FloorSwitcherItemMode.HEIGHT) HexieBridges.worldBlock().bogeyFloorData().write(data, height);
        else HexieBridges.worldBlock().bogeyFloorData().writeLongitudinal(data, height);
        writeSupport(level, support, data);
        player.displayClientMessage(target.slider().mode() == FloorSwitcherItemMode.HEIGHT ? FloorSwitcherSettings.message(height)
                : Component.translatable("gui.hexie.bogey_offset.value", height), true);
    }

    static void writeSupport(Level level, AssemblyBogeySupportView support, CompoundTag data) {
        var owner = level.getBlockEntity(support.owner());
        if (owner instanceof CRH2HeadEntityView head)
            head.updateBogey(true, data.getCompound("BogeyData"));
        else if (owner instanceof HexieBogeyBlockEntityView bogey)
            bogey.syncBogeyData(data.getCompound("BogeyData"));
    }
    static void release(Player player, ItemStack stack, boolean held) {
        Target target = PENDING.remove(player);
        if (target == null) return;
        if (held) {
            CONFIGURING.put(player, target);
            return;
        }
        if (target.door() != null) {
            if (target.tool() == stack && canUseDoor(player, target)) target.door().togglePassengerFrame();
        } else applyTarget(player, stack, target, FloorSwitcherSettings.height(stack));
    }
}
