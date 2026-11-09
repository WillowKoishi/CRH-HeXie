package willow.train.hexie.compat.create;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import java.util.UUID;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import willow.train.hexie.blocks.bogeys.BogeyFloorData;
import willow.train.hexie.blocks.bogeys.HexieBogeyBlock;
import willow.train.hexie.item.FloorSwitcherItem;
import willow.train.hexie.vehicle.assembly.AssemblyBogeySupport;

/** One assembled carriage always changes floor setting as a whole. */
public final class SnapshotFloorInteraction {
    private SnapshotFloorInteraction() {}

    private static AssemblyBogeySupport support(SnapshotCarriageEntity entity, BlockPos position) {
        return AssemblyBogeySupport.resolve(entity.getContraption().getBlocks(), position);
    }

    private static void write(SnapshotCarriageEntity entity, AssemblyBogeySupport support, CompoundTag data) {
        var owner = entity.getContraption().getBlocks().get(support.owner());
        entity.setBlock(support.owner(), new StructureBlockInfo(support.owner(), owner.state(), data));
    }

    static boolean handle(SnapshotCarriageEntity entity, Player player, BlockPos position, InteractionHand hand) {
        if (!(player.getItemInHand(hand).getItem() instanceof FloorSwitcherItem)
                || player.isSpectator() || !player.mayBuild() || entity.getContraption() == null) return false;
        var support = support(entity, position);
        if (support == null
                || !entity.canInteractWithBlock(player, position, 4)) return false;
        if (player.isShiftKeyDown()) { FloorSwitcherItem.switchMode(player, hand); return true; }
        if (FloorSwitcherItem.mode(player.getItemInHand(hand)) == FloorSwitcherItem.Mode.REVERSE) {
            if (!entity.level().isClientSide) {
                var data = support.entityData() == null ? new CompoundTag() : support.entityData();
                BogeyFloorData.writeReversed(data, !BogeyFloorData.reversedFromEntity(data));
                write(entity, support, data);
                entity.syncCarriage();
            }
            return true;
        }
        // Client block-entity tags can lag behind a moving carriage's synchronized floor pose.
        double offset = entity.couplerAnimation().floorOffset();
        int height = BogeyFloorData.heightFromOffset(offset);
        FloorSwitcherItem.beginUsing(player, hand, entity.getUUID(), position,
                FloorSwitcherItem.mode(player.getItemInHand(hand)) == FloorSwitcherItem.Mode.HEIGHT ? height
                        : BogeyFloorData.longitudinalFromEntity(support.entityData()));
        return true;
    }

    /** Apply a confirmed slider value or a short-click setting to the whole selected carriage. */
    public static void apply(Player player, UUID carriage, BlockPos position, InteractionHand hand, int height) {
        if (!(player.level() instanceof ServerLevel level) || player.isSpectator() || !player.mayBuild()
                || !BogeyFloorData.valid(height)
                || !(player.getItemInHand(hand).getItem() instanceof FloorSwitcherItem)
                || !(level.getEntity(carriage) instanceof SnapshotCarriageEntity entity)
                || entity.isRemoved() || entity.getContraption() == null
                || !entity.canInteractWithBlock(player, position, 4)) return;
        if (support(entity, position) == null) return;
        var motion = entity.motion();
        if (motion == null) return;
        motion.runtime().setFloorOffset(entity.getUUID(), BogeyFloorData.offset(height));
        for (var local : ContraptionInteractionTargets.bogeys(entity.getContraption())) {
            var support = support(entity, local);
            if (support == null) continue;
            CompoundTag data = support.entityData() == null ? new CompoundTag() : support.entityData();
            BogeyFloorData.write(data, height);
            write(entity, support, data);
        }
        motion.applyCurrent(entity);
        entity.syncCarriage();
        player.displayClientMessage(FloorSwitcherItem.message(height), true);
    }

    public static void applyLongitudinal(Player player, UUID carriage, BlockPos position, InteractionHand hand, int value) {
        if (!(player.level() instanceof ServerLevel level) || player.isSpectator() || !player.mayBuild()
                || !BogeyFloorData.validLongitudinal(value)
                || !(player.getItemInHand(hand).getItem() instanceof FloorSwitcherItem)
                || !(level.getEntity(carriage) instanceof SnapshotCarriageEntity entity)
                || entity.isRemoved() || entity.getContraption() == null || !entity.canInteractWithBlock(player, position, 4)) return;
        var support = support(entity, position);
        if (support == null) return;
        var motion = entity.motion();
        var snapshot = willow.train.hexie.vehicle.assembly.CarriageSnapshotStore.get(level).find(carriage).orElse(null);
        if (motion == null || snapshot == null) return;
        int index = snapshot.supports().indexOf(position);
        if (index < 0) return;
        int previous = BogeyFloorData.longitudinalFromEntity(support.entityData());
        var shift = BogeyFloorData.displacement(support.state(), value).subtract(BogeyFloorData.displacement(support.state(), previous));
        try {
            motion.runtime().shiftBogeyMount(carriage, index,
                    willow.train.hexie.vehicle.assembly.VehicleAssemblyPreparer.northForward(shift, motion.forward(carriage)));
        } catch (RuntimeException failure) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("gui.hexie.bogey_offset.failed"), true);
            return;
        }
        var data = support.entityData() == null ? new CompoundTag() : support.entityData();
        BogeyFloorData.writeLongitudinal(data, value);
        write(entity, support, data);
        motion.applyCurrent(entity); entity.syncCarriage();
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("gui.hexie.bogey_offset.value", value), true);
    }
}
