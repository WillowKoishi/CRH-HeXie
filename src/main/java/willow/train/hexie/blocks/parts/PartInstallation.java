package willow.train.hexie.blocks.parts;

import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Shared loaded-area preflight and rollback for items, rotations and schematic printing. */
public final class PartInstallation {
    private static final ThreadLocal<Integer> MUTATING = ThreadLocal.withInitial(() -> 0);
    private PartInstallation() {}
    public static boolean mutating() { return MUTATING.get() != 0; }
    private record Saved(BlockState state, CompoundTag data) {}

    public static boolean available(Level level, Map<BlockPos, BlockState> cells,
                                    Predicate<BlockPos> mayReplace, Predicate<BlockPos> permission) {
        for (var entry : cells.entrySet()) {
            var pos = entry.getKey();
            if (!level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos)
                    || !level.getWorldBorder().isWithinBounds(pos) || !permission.test(pos)) return false;
            var old = level.getBlockState(pos);
            if (!old.equals(entry.getValue()) && !mayReplace.test(pos) && !old.canBeReplaced()) return false;
        }
        return true;
    }

    public static boolean install(Level level, Map<BlockPos, BlockState> cells,
                                  Predicate<BlockPos> mayReplace, Predicate<BlockPos> permission) {
        return replace(level, Map.of(), cells, mayReplace, permission);
    }
    public static boolean install(Level level,Map<BlockPos,BlockState> cells,Predicate<BlockPos> mayReplace,
                                  Predicate<BlockPos> permission,Runnable installed) {
        return replace(level,Map.of(),cells,mayReplace,permission,installed);
    }

    public static boolean replace(Level level, Map<BlockPos, BlockState> previous, Map<BlockPos, BlockState> cells,
                                  Predicate<BlockPos> mayReplace, Predicate<BlockPos> permission) {
        return replace(level,previous,cells,mayReplace,permission,()->{});
    }
    public static boolean replace(Level level,Map<BlockPos,BlockState> previous,Map<BlockPos,BlockState> cells,
                                   Predicate<BlockPos> mayReplace,Predicate<BlockPos> permission,Runnable installed) {
        if (!available(level, cells, pos -> previous.get(pos) == level.getBlockState(pos) || mayReplace.test(pos), permission)) return false;
        for (var pos : previous.keySet()) if (!level.hasChunkAt(pos) || !permission.test(pos)) return false;
        if (level.isClientSide) return true;
        var saved = new LinkedHashMap<BlockPos, Saved>();
        var targets = new LinkedHashMap<BlockPos, BlockState>();
        previous.forEach((pos, expected) -> {
            if (!cells.containsKey(pos) && level.getBlockState(pos).equals(expected)) targets.put(pos, Blocks.AIR.defaultBlockState());
        });
        targets.putAll(cells);
        var changed=new LinkedHashSet<BlockPos>();
        targets.forEach((pos,state)->{if(!level.getBlockState(pos).equals(state))changed.add(pos);});
        targets.forEach((pos, state) -> {
            var be = level.getBlockEntity(pos);
            saved.put(pos, new Saved(level.getBlockState(pos), be == null ? null : be.saveWithFullMetadata(level.registryAccess())));
        });
        MUTATING.set(MUTATING.get() + 1);
        boolean committed = false;
        try {
            for (var entry : targets.entrySet()) {
                if (level.getBlockState(entry.getKey()).equals(entry.getValue())) continue;
                if (!level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_MOVE_BY_PISTON)) return false;
            }
            installed.run();
            committed = true;
        } finally {
            try {
                if (!committed) saved.forEach((pos, old) -> {
                    level.setBlock(pos, old.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_MOVE_BY_PISTON);
                    if (old.data() != null) {
                        var be = BlockEntity.loadStatic(pos, old.state(), old.data(), level.registryAccess());
                        if (be != null) level.setBlockEntity(be);
                    }
                });
            } finally { MUTATING.set(MUTATING.get() - 1); }
        }
        changed.forEach(pos -> level.updateNeighborsAt(pos, targets.get(pos).getBlock()));
        return true;
    }

    public static boolean complete(Level level, BlockPos root, BlockState state, PartStructure part) {
        for (var entry : part.partCells(level, root, state).entrySet())
            if (!level.hasChunkAt(entry.getKey()) || !part.ownsCell(level, root, state, entry.getKey(), entry.getValue())) return false;
        return true;
    }

    public static void removeOtherCells(Level level, BlockPos removed, Map<BlockPos, BlockState> cells) {
        if (level.isClientSide || mutating()) return;
        MUTATING.set(MUTATING.get() + 1);
        try {
            cells.forEach((pos, expected) -> {
                if (!pos.equals(removed) && level.hasChunkAt(pos) && level.getBlockState(pos).equals(expected)) level.removeBlock(pos, false);
            });
        } finally { MUTATING.set(MUTATING.get() - 1); }
    }
    public static void stateChanged(Level level,BlockPos pos,BlockState previous,BlockState next) {
        if(level.isClientSide||mutating()||!previous.is(next.getBlock())||!(next.getBlock() instanceof PartStructure part)
                ||!part.partRoot(level,pos,previous).equals(pos)||!part.partRoot(level,pos,next).equals(pos))return;
        var before=part.partCells(level,pos,previous);var after=part.partCells(level,pos,next);
        var oldMembers=new LinkedHashMap<>(before);oldMembers.remove(pos);
        var newMembers=new LinkedHashMap<>(after);newMembers.remove(pos);
        if(oldMembers.equals(newMembers))return;
        // External same-block rotations use the same preflight as the wrench. A blocked rotation restores its old root.
        if(!replace(level,before,after,at->false,at->true))install(level,Map.of(pos,previous),pos::equals,at->true);
    }
}
