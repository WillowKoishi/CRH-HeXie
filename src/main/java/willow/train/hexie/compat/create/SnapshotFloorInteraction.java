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
        // Client block-entity tags can lag behind a moving carriage's synchronized floor pose.
        double offset = entity.couplerAnimation().floorOffset();
        int height = Math.clamp((int) Math.round(BogeyFloorData.DEFAULT_HEIGHT + offset * 10),
                BogeyFloorData.MIN_HEIGHT, BogeyFloorData.MAX_HEIGHT);
        FloorSwitcherItem.beginUsing(player, hand, entity.getUUID(), position, height);
        return true;
    }

    /** Apply a confirmed slider value or a short-click setting to the whole selected carriage. */
    public static void apply(Player player, UUID carriage, BlockPos position, InteractionHand hand, int height) {
        if (!(player.level() instanceof ServerLevel level) || player.isSpectator() || !player.mayBuild()
                || height < 10 || height > 30
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
}
