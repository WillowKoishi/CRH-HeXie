package willow.train.hexie.compat.create.interaction;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import java.util.UUID;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.interaction.ToolOpsBridge.FloorSwitcherItemMode;
import willow.train.hexie.bridge.interaction.ToolOpsBridge.FloorSwitcherItemView;
import willow.train.hexie.bridge.vehicle.VehicleBridge.AssemblyBogeySupportView;
import willow.train.hexie.compat.create.motion.SnapshotCarriageEntity;

/** One assembled carriage always changes floor setting as a whole. */
public final class SnapshotFloorInteraction {
    private SnapshotFloorInteraction() {}

    private static AssemblyBogeySupportView support(SnapshotCarriageEntity entity, BlockPos position) {
        return HexieBridges.vehicle().assemblyBogeySupport().resolve(entity.getContraption().getBlocks(), position);
    }

    private static void write(SnapshotCarriageEntity entity, AssemblyBogeySupportView support, CompoundTag data) {
        var owner = entity.getContraption().getBlocks().get(support.owner());
        entity.setBlock(support.owner(), new StructureBlockInfo(support.owner(), owner.state(), data));
    }

    static boolean handle(SnapshotCarriageEntity entity, Player player, BlockPos position, InteractionHand hand) {
        if (!(player.getItemInHand(hand).getItem() instanceof FloorSwitcherItemView)
                || player.isSpectator() || !player.mayBuild() || entity.getContraption() == null) return false;
        var support = support(entity, position);
        if (support == null
                || !entity.canInteractWithBlock(player, position, 4)) return false;
        if (player.isShiftKeyDown()) { HexieBridges.toolOps().floorSwitcherItem().switchMode(player, hand); return true; }
        if (HexieBridges.toolOps().floorSwitcherItem().mode(player.getItemInHand(hand)) == FloorSwitcherItemMode.REVERSE) {
            if (!entity.level().isClientSide) {
                var data = support.entityData() == null ? new CompoundTag() : support.entityData();
                HexieBridges.worldBlock().bogeyFloorData().writeReversed(data, !HexieBridges.worldBlock().bogeyFloorData().reversedFromEntity(data));
                write(entity, support, data);
                entity.syncCarriage();
            }
            return true;
        }
        // Client block-entity tags can lag behind a moving carriage's synchronized floor pose.
        double offset = entity.couplerAnimation().floorOffset();
        int height = HexieBridges.worldBlock().bogeyFloorData().heightFromOffset(offset);
        HexieBridges.toolOps().floorSwitcherItem().beginUsing(player, hand, entity.getUUID(), position,
                HexieBridges.toolOps().floorSwitcherItem().mode(player.getItemInHand(hand)) == FloorSwitcherItemMode.HEIGHT ? height
                        : HexieBridges.worldBlock().bogeyFloorData().longitudinalFromEntity(support.entityData()));
        return true;
    }

    /** Apply a confirmed slider value or a short-click setting to the whole selected carriage. */
    public static void apply(Player player, UUID carriage, BlockPos position, InteractionHand hand, int height) {
        if (!(player.level() instanceof ServerLevel level) || player.isSpectator() || !player.mayBuild()
                || !HexieBridges.worldBlock().bogeyFloorData().valid(height)
                || !(player.getItemInHand(hand).getItem() instanceof FloorSwitcherItemView)
                || !(level.getEntity(carriage) instanceof SnapshotCarriageEntity entity)
                || entity.isRemoved() || entity.getContraption() == null
                || !entity.canInteractWithBlock(player, position, 4)) return;
        if (support(entity, position) == null) return;
        var motion = entity.motion();
        if (motion == null) return;
        motion.runtime().setFloorOffset(entity.getUUID(), HexieBridges.worldBlock().bogeyFloorData().offset(height));
        for (var local : ContraptionInteractionTargets.bogeys(entity.getContraption())) {
            var support = support(entity, local);
            if (support == null) continue;
            CompoundTag data = support.entityData() == null ? new CompoundTag() : support.entityData();
            HexieBridges.worldBlock().bogeyFloorData().write(data, height);
            write(entity, support, data);
        }
        motion.applyCurrent(entity);
        entity.syncCarriage();
        player.displayClientMessage(HexieBridges.toolOps().floorSwitcherItem().message(height), true);
    }

    public static void applyLongitudinal(Player player, UUID carriage, BlockPos position, InteractionHand hand, int value) {
        if (!(player.level() instanceof ServerLevel level) || player.isSpectator() || !player.mayBuild()
                || !HexieBridges.worldBlock().bogeyFloorData().validLongitudinal(value)
                || !(player.getItemInHand(hand).getItem() instanceof FloorSwitcherItemView)
                || !(level.getEntity(carriage) instanceof SnapshotCarriageEntity entity)
                || entity.isRemoved() || entity.getContraption() == null || !entity.canInteractWithBlock(player, position, 4)) return;
        var support = support(entity, position);
        if (support == null) return;
        var motion = entity.motion();
        var snapshot = HexieBridges.vehicle().carriageSnapshotStore().get(level).find(carriage).orElse(null);
        if (motion == null || snapshot == null) return;
        int index = snapshot.supports().indexOf(position);
        if (index < 0) return;
        int previous = HexieBridges.worldBlock().bogeyFloorData().longitudinalFromEntity(support.entityData());
        var shift = HexieBridges.worldBlock().bogeyFloorData().displacement(support.state(), value).subtract(HexieBridges.worldBlock().bogeyFloorData().displacement(support.state(), previous));
        try {
            motion.runtime().shiftBogeyMount(carriage, index,
                    HexieBridges.vehicle().vehicleAssemblyPreparer().northForward(shift, motion.forward(carriage)));
        } catch (RuntimeException failure) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("gui.hexie.bogey_offset.failed"), true);
            return;
        }
        var data = support.entityData() == null ? new CompoundTag() : support.entityData();
        HexieBridges.worldBlock().bogeyFloorData().writeLongitudinal(data, value);
        write(entity, support, data);
        motion.applyCurrent(entity); entity.syncCarriage();
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("gui.hexie.bogey_offset.value", value), true);
    }
}
