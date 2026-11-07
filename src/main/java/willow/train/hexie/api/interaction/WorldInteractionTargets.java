package willow.train.hexie.api.interaction;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import willow.train.hexie.Main;

/**
 * Only cross-cell world targets. A level's storage coordinates are never mixed
 * with contraption-local coordinates. Holders contain no reference to the level.
 */
@EventBusSubscriber(modid = Main.MOD_ID)
public final class WorldInteractionTargets {
    private static final Map<Level, Holder> LEVELS = new WeakHashMap<>();

    private WorldInteractionTargets() {}

    /** Called from the world's existing cell traversal, not once per target/ray. */
    public static List<BlockPos> lookup(Level level, BlockPos cell) {
        Holder holder = holder(level, false);
        if (holder == null) return List.of();
        synchronized (holder) {
            return holder.index.lookup(cell);
        }
    }

    /** Reads only the supplied loaded chunk after its block-state mutation. */
    public static void changed(LevelChunk chunk, BlockPos position) {
        BlockState state = chunk.getBlockState(position);
        AABB bounds = extendedBounds(state, BlockControlHost.find(state) == null ? null : chunk.getBlockEntity(position));
        Holder holder = holder(chunk.getLevel(), bounds != null);
        if (holder == null) return;
        synchronized (holder) {
            holder.update(position, bounds);
        }
    }

    /** Adapter installation and BE data packets change bounds without changing the block state. */
    public static void refresh(net.minecraft.world.level.block.entity.BlockEntity entity) {
        Level level = entity.getLevel();
        if (level == null) return;
        // Snapshot display BEs may carry the real level but use contraption-local positions.
        if (level.getBlockEntity(entity.getBlockPos()) != entity) return;
        // Render-only virtual worlds have no world index, and must never be registered in one.
        Holder holder = holder(level, false);
        if (holder == null) return;
        synchronized (holder) {
            holder.update(entity.getBlockPos(), extendedBounds(entity.getBlockState(), entity));
        }
    }

    @SubscribeEvent
    public static void loaded(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof Level level)) return;
        ChunkAccess chunk = event.getChunk();
        Holder holder = holder(level, true);
        synchronized (holder) {
            // Client full chunk packets also emit Load for an existing chunk.
            holder.removeChunk(chunk.getPos().toLong());
            var sections = chunk.getSections();
            for (int index = 0; index < sections.length; index++) {
                var section = sections[index];
                if (!section.maybeHas(state -> extendedBounds(state) != null)) continue;
                int baseY = chunk.getMinBuildHeight() + index * 16;
                for (int x = 0; x < 16; x++)
                    for (int y = 0; y < 16; y++)
                        for (int z = 0; z < 16; z++) {
                            AABB bounds = extendedBounds(section.getBlockState(x, y, z));
                            if (bounds == null) continue;
                            BlockPos position = new BlockPos(chunk.getPos().getMinBlockX() + x,
                                    baseY + y, chunk.getPos().getMinBlockZ() + z);
                            bounds = extendedBounds(section.getBlockState(x, y, z), chunk.getBlockEntity(position));
                            holder.update(position, bounds);
                        }
            }
        }
    }

    @SubscribeEvent
    public static void unloaded(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof Level level)) return;
        unloaded(level, event.getChunk().getPos());
    }

    /** Also used by plot backends whose chunk removal bypasses the platform unload event. */
    public static void unloaded(Level level, ChunkPos chunk) {
        Holder holder = holder(level, false);
        if (holder == null) return;
        synchronized (holder) {
            holder.removeChunk(chunk.toLong());
        }
    }

    @SubscribeEvent
    public static void unloaded(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof Level level)) return;
        synchronized (LEVELS) {
            LEVELS.remove(level);
        }
    }

    private static AABB extendedBounds(BlockState state) {
        return extendedBounds(state, null);
    }

    private static AABB extendedBounds(BlockState state, net.minecraft.world.level.block.entity.BlockEntity entity) {
        BlockControlHost host = BlockControlHost.find(state);
        if (host == null) return null;
        AABB bounds = host.controlBounds(state, entity);
        if (bounds == null || bounds.minX >= 0 && bounds.minY >= 0 && bounds.minZ >= 0
                && bounds.maxX <= 1 && bounds.maxY <= 1 && bounds.maxZ <= 1) return null;
        return bounds;
    }

    private static Holder holder(Level level, boolean create) {
        synchronized (LEVELS) {
            return create ? LEVELS.computeIfAbsent(level, ignored -> new Holder()) : LEVELS.get(level);
        }
    }

    private static final class Holder {
        private final InteractionSpatialIndex index = new InteractionSpatialIndex();
        private final Map<Long, Set<BlockPos>> chunkOwners = new HashMap<>();

        private void update(BlockPos position, AABB localBounds) {
            BlockPos owner = position.immutable();
            long chunk = ChunkPos.asLong(owner);
            if (localBounds == null) {
                index.remove(owner);
                Set<BlockPos> owners = chunkOwners.get(chunk);
                if (owners == null) return;
                owners.remove(owner);
                if (owners.isEmpty()) chunkOwners.remove(chunk);
            } else {
                index.put(owner, localBounds.move(owner));
                chunkOwners.computeIfAbsent(chunk, ignored -> new HashSet<>()).add(owner);
            }
        }

        private void removeChunk(long chunk) {
            Set<BlockPos> owners = chunkOwners.remove(chunk);
            if (owners != null) owners.forEach(index::remove);
        }
    }
}
