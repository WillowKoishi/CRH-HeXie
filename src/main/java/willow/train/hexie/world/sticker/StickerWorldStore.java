package willow.train.hexie.world.sticker;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.sticker.StickerBridge;
import willow.train.hexie.core.sticker.StickerEntry;

/** Implements StickerBridge over LevelChunk attachments and StickerImageStore. */
public final class StickerWorldStore implements StickerBridge {
    private static ChunkStickerData dataIfLoaded(Level level, int chunkX, int chunkZ) {
        if (level == null || !level.hasChunk(chunkX, chunkZ)) return null;
        LevelChunk chunk = level.getChunk(chunkX, chunkZ);
        return chunk.hasData(StickerAttachments.CHUNK_STICKERS.get())
                ? chunk.getData(StickerAttachments.CHUNK_STICKERS.get()) : null;
    }

    private static ChunkStickerData dataOrCreate(LevelChunk chunk) {
        return chunk.getData(StickerAttachments.CHUNK_STICKERS.get());
    }

    @Override
    public Optional<StickerEntry> findExact(Level level, BlockPos anchorPos, Direction facing) {
        ChunkStickerData data = dataIfLoaded(level, anchorPos.getX() >> 4, anchorPos.getZ() >> 4);
        return data == null ? Optional.empty() : data.findExact(anchorPos, facing);
    }

    @Override
    public List<StickerEntry> entriesInChunk(Level level, int chunkX, int chunkZ) {
        ChunkStickerData data = dataIfLoaded(level, chunkX, chunkZ);
        return data == null ? List.of() : data.allEntries();
    }

    @Override
    public List<StickerEntry> entriesAffecting(Level level, BlockPos pos) {
        var result = new ArrayList<StickerEntry>();
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        // Search 3x3 neighborhood chunks so cross-chunk windows/decals anchored nearby are found
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                ChunkStickerData data = dataIfLoaded(level, cx + dx, cz + dz);
                if (data != null) result.addAll(data.entriesAffecting(pos));
            }
        }
        return result;
    }

    @Override
    public boolean upsertServer(ServerLevel level, StickerEntry entry) {
        BlockPos pos = entry.anchorPos();
        if (!level.hasChunkAt(pos)) return false;
        LevelChunk chunk = level.getChunkAt(pos);
        ChunkStickerData data = dataOrCreate(chunk);
        data.put(entry);
        chunk.setUnsaved(true);
        HexieBridges.sync().sendStickerChunkToTracking(level, chunk.getPos(), data.allEntries());
        return true;
    }

    @Override
    public boolean removeAtServer(ServerLevel level, BlockPos pos, Direction facing) {
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        boolean changed = false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!level.hasChunk(cx + dx, cz + dz)) continue;
                LevelChunk chunk = level.getChunk(cx + dx, cz + dz);
                if (!chunk.hasData(StickerAttachments.CHUNK_STICKERS.get())) continue;
                ChunkStickerData data = chunk.getData(StickerAttachments.CHUNK_STICKERS.get());
                if (!data.removeTouching(pos, facing).isEmpty()) {
                    chunk.setUnsaved(true);
                    HexieBridges.sync().sendStickerChunkToTracking(level, chunk.getPos(), data.allEntries());
                    changed = true;
                }
            }
        }
        return changed;
    }

    @Override
    public void applyChunkSyncClient(Level level, int chunkX, int chunkZ, List<StickerEntry> entries) {
        if (level == null || !level.hasChunk(chunkX, chunkZ)) return;
        LevelChunk chunk = level.getChunk(chunkX, chunkZ);
        if (!entries.isEmpty() || chunk.hasData(StickerAttachments.CHUNK_STICKERS.get())) {
            dataOrCreate(chunk).replaceAll(entries);
        }
        var cutter = HexieBridges.Client.stickerMeshCutter();
        if (cutter != null) cutter.onChunkSynced(chunkX, chunkZ, entries);
    }

    @Override
    public boolean hasImageServer(ServerLevel level, String hash) {
        return StickerImageStore.hasServer(level, hash);
    }

    @Override
    public Optional<byte[]> loadImageServer(ServerLevel level, String hash) {
        return StickerImageStore.loadServer(level, hash);
    }

    @Override
    public String saveImageServer(ServerLevel level, byte[] pngBytes) {
        return StickerImageStore.saveServer(level, pngBytes);
    }

    @Override
    public Optional<byte[]> clientImage(String hash) {
        return StickerImageStore.getClient(hash);
    }

    @Override
    public void putClientImage(String hash, byte[] pngBytes) {
        StickerImageStore.putClient(hash, pngBytes);
    }
}
