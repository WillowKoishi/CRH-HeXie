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

/** One assembled carriage always changes floor setting as a whole. */
public final class SnapshotFloorInteraction {
    private SnapshotFloorInteraction() {}

    static boolean handle(SnapshotCarriageEntity entity, Player player, BlockPos position, InteractionHand hand) {
        if (!(player.getItemInHand(hand).getItem() instanceof FloorSwitcherItem)
                || player.isSpectator() || !player.mayBuild() || entity.getContraption() == null) return false;
        var info = entity.getContraption().getBlocks().get(position);
        if (info == null || !(info.state().getBlock() instanceof HexieBogeyBlock<?>)
                || !entity.canInteractWithBlock(player, position, 4)) return false;
        if (player.isShiftKeyDown()) { FloorSwitcherItem.switchMode(player, hand); return true; }
        if (FloorSwitcherItem.mode(player.getItemInHand(hand)) == FloorSwitcherItem.Mode.REVERSE) {
            if (!entity.level().isClientSide) {
                var data = info.nbt() == null ? new CompoundTag() : info.nbt().copy();
                BogeyFloorData.writeReversed(data, !BogeyFloorData.reversedFromEntity(data));
                entity.setBlock(position, new StructureBlockInfo(position, info.state(), data));
                entity.syncCarriage();
            }
            return true;
        }
        // Client block-entity tags can lag behind a moving carriage's synchronized floor pose.
        double offset = entity.couplerAnimation().floorOffset();
        int height = BogeyFloorData.heightFromOffset(offset);
        FloorSwitcherItem.beginUsing(player, hand, entity.getUUID(), position,
                FloorSwitcherItem.mode(player.getItemInHand(hand)) == FloorSwitcherItem.Mode.HEIGHT ? height
                        : BogeyFloorData.longitudinalFromEntity(info.nbt()));
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
        var info = entity.getContraption().getBlocks().get(position);
        if (info == null || !(info.state().getBlock() instanceof HexieBogeyBlock<?>)) return;
        var motion = entity.motion();
        if (motion == null) return;
        motion.runtime().setFloorOffset(entity.getUUID(), BogeyFloorData.offset(height));
        for (var block : entity.getContraption().getBlocks().values().stream()
                .filter(b -> b.state().getBlock() instanceof HexieBogeyBlock<?>).toList()) {
            BlockPos local = block.pos();
            CompoundTag data = block.nbt() == null ? new CompoundTag() : block.nbt().copy();
            BogeyFloorData.write(data, height);
            entity.setBlock(local, new StructureBlockInfo(local, block.state(), data));
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
        var info = entity.getContraption().getBlocks().get(position);
        if (info == null || !(info.state().getBlock() instanceof HexieBogeyBlock<?>)) return;
        var motion = entity.motion();
        var snapshot = willow.train.hexie.vehicle.assembly.CarriageSnapshotStore.get(level).find(carriage).orElse(null);
        if (motion == null || snapshot == null) return;
        int index = snapshot.supports().indexOf(position);
        if (index < 0) return;
        int previous = BogeyFloorData.longitudinalFromEntity(info.nbt());
        var shift = BogeyFloorData.displacement(info.state(), value).subtract(BogeyFloorData.displacement(info.state(), previous));
        try {
            motion.runtime().shiftBogeyMount(carriage, index,
                    willow.train.hexie.vehicle.assembly.VehicleAssemblyPreparer.northForward(shift, motion.forward(carriage)));
        } catch (RuntimeException failure) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("gui.hexie.bogey_offset.failed"), true);
            return;
        }
        var data = info.nbt() == null ? new CompoundTag() : info.nbt().copy();
        BogeyFloorData.writeLongitudinal(data, value);
        entity.setBlock(position, new StructureBlockInfo(position, info.state(), data));
        motion.applyCurrent(entity); entity.syncCarriage();
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("gui.hexie.bogey_offset.value", value), true);
    }
}
