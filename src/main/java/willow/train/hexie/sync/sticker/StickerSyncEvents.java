package willow.train.hexie.sync.sticker;

import java.util.HashSet;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.core.sticker.StickerMode;

/** Pushes chunk sticker entries (and referenced decal images) to players whenever a chunk starts being tracked. */
public final class StickerSyncEvents {
    private StickerSyncEvents() {}

    public static void install() {
        NeoForge.EVENT_BUS.addListener(StickerSyncEvents::onChunkSent);
    }

    private static void onChunkSent(ChunkWatchEvent.Sent event) {
        var chunk = event.getChunk();
        var entries = HexieBridges.sticker().entriesInChunk(event.getLevel(), chunk.getPos().x, chunk.getPos().z);
        if (!entries.isEmpty()) {
            PacketDistributor.sendToPlayer(event.getPlayer(),
                    new StickerChunkSyncPacket(chunk.getPos().x, chunk.getPos().z, entries));
            var sentHashes = new HashSet<String>();
            for (var e : entries) {
                if (e.mode() == StickerMode.DECAL && e.decal().hasImage() && sentHashes.add(e.decal().imageHash())) {
                    String hash = e.decal().imageHash();
                    HexieBridges.sticker().loadImageServer(event.getLevel(), hash).ifPresent(
                            bytes -> PacketDistributor.sendToPlayer(event.getPlayer(), new StickerImageSyncPacket(hash, bytes)));
                }
            }
        }
    }
}
