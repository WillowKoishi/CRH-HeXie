package willow.train.hexie.bridge.sticker;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import willow.train.hexie.core.sticker.StickerEntry;

/** Cross-domain contract for chunk-persisted custom windows/decals and uploaded decal images. */
public interface StickerBridge {
    Optional<StickerEntry> findExact(Level level, BlockPos anchorPos, Direction facing);

    List<StickerEntry> entriesInChunk(Level level, int chunkX, int chunkZ);

    List<StickerEntry> entriesAffecting(Level level, BlockPos pos);

    boolean upsertServer(ServerLevel level, StickerEntry entry);

    boolean removeAtServer(ServerLevel level, BlockPos pos, Direction facing);

    void applyChunkSyncClient(Level level, int chunkX, int chunkZ, List<StickerEntry> entries);

    boolean hasImageServer(ServerLevel level, String hash);

    Optional<byte[]> loadImageServer(ServerLevel level, String hash);

    String saveImageServer(ServerLevel level, byte[] pngBytes);

    Optional<byte[]> clientImage(String hash);

    void putClientImage(String hash, byte[] pngBytes);
}
