package willow.train.hexie.world.blocks.emu;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import willow.train.hexie.core.HexieConstants;
import willow.train.hexie.world.blocks.parts.PartMemberBlock;
import willow.train.hexie.world.blocks.parts.PartStructures;
import willow.train.hexie.world.placement.PartInstallation;

/** Restore legacy CRH1A door ownership without replacing either visible half or unrelated blocks. */
@EventBusSubscriber(modid = HexieConstants.MOD_ID)
public final class CRH1DoorRepair {
    public enum Result { COMPLETE, REPAIRED, DEFERRED, BLOCKED, NOT_APPLICABLE }

    private CRH1DoorRepair() {}

    @SubscribeEvent public static void loaded(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel world) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        // Both visible halves have block entities, including pending saved NBT during chunk loading.
        // Use that small inventory instead of scanning every block or polling every door each tick.
        var roots = new ArrayList<BlockPos>();
        for (var pos : chunk.getBlockEntitiesPos()) {
            var state = chunk.getBlockState(pos);
            if (state.getBlock() instanceof CRH1DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER)
                roots.add(pos.immutable());
        }
        if (!roots.isEmpty()) world.getServer().execute(() -> roots.forEach(pos -> schedule(world, pos)));
    }

    /** Healthy doors never receive a repair tick; neighbour changes retry a previously blocked layout. */
    public static void schedule(ServerLevel world, BlockPos root) {
        if (PartInstallation.mutating() || !world.hasChunkAt(root)) return;
        var state = world.getBlockState(root);
        if (!(state.getBlock() instanceof CRH1DoorBlock door) || state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) return;
        BlockPos right = root.relative(state.getValue(DoorBlock.FACING).getClockWise());
        for (var pos : new BlockPos[]{right, right.above()}) {
            if (!world.hasChunkAt(pos)) {
                world.scheduleTick(root, door, 20);
                return;
            }
            var member = world.getBlockState(pos);
            if (!(member.getBlock() instanceof CRH1DoorMemberBlock) || !PartMemberBlock.owner(pos, member).equals(root)) {
                world.scheduleTick(root, door, 1);
                return;
            }
        }
    }

    /** Validate both destinations before atomically restoring only the second column. */
    public static CRH1DoorRepair.Result repair(ServerLevel world, BlockPos root) {
        if (PartInstallation.mutating() || !world.hasChunkAt(root)) return CRH1DoorRepair.Result.DEFERRED;
        var state = world.getBlockState(root);
        if (!(state.getBlock() instanceof CRH1DoorBlock door) || state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER)
            return CRH1DoorRepair.Result.NOT_APPLICABLE;
        if (!world.getBlockState(root.above()).equals(state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)))
            return CRH1DoorRepair.Result.NOT_APPLICABLE;

        var previous = new LinkedHashMap<BlockPos, BlockState>();
        var expected = new LinkedHashMap<BlockPos, BlockState>();
        boolean changed = false;
        for (var entry : door.partCells(world, root, state).entrySet()) {
            if (!(entry.getValue().getBlock() instanceof CRH1DoorMemberBlock)) continue;
            BlockPos pos = entry.getKey();
            if (!world.hasChunkAt(pos)) return CRH1DoorRepair.Result.DEFERRED;
            var current = world.getBlockState(pos);
            previous.put(pos, current);
            expected.put(pos, entry.getValue());
            if (current.equals(entry.getValue())) continue;
            changed = true;
            if (world.getBlockEntity(pos) != null) return CRH1DoorRepair.Result.BLOCKED;
            if (current.isAir()) continue;
            // Even replaceable plants/water are preserved. A member owned by another valid door
            // must also remain intact; an unloaded claimed owner cannot yet be classified as orphaned.
            if (!(current.getBlock() instanceof CRH1DoorMemberBlock)) return CRH1DoorRepair.Result.BLOCKED;
            if (!world.hasChunkAt(PartMemberBlock.owner(pos, current))) return CRH1DoorRepair.Result.DEFERRED;
            if (PartStructures.resolve(world, pos, current) != null) return CRH1DoorRepair.Result.BLOCKED;
        }
        if (!changed) return CRH1DoorRepair.Result.COMPLETE;
        return PartInstallation.replace(world, previous, expected, pos -> false, pos -> true)
                ? CRH1DoorRepair.Result.REPAIRED : CRH1DoorRepair.Result.BLOCKED;
    }
}
