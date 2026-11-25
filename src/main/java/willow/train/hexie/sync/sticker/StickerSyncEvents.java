package willow.train.hexie.sync.sticker;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import willow.train.hexie.bridge.HexieBridges;

/** Pushes chunk sticker entries to players whenever a chunk starts being tracked. */
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
        }
    }
}
