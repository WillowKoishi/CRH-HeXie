package willow.train.hexie.render.rendererbridge;

import java.util.List;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import willow.train.hexie.core.sticker.StickerEntry;

/**
 * Built-in §5.6 RenderBridge facade for HeXie's embedded rendering engine (Rule A5 boundary).
 * All renderer source code lives directly inside HEXIE-v2 (renderer/java) and is always active on the client.
 */
public final class HexieRendererBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("HeXie/Renderer");
    private static boolean installed;

    private HexieRendererBridge() {}

    public static boolean isLoaded() {
        return true;
    }

    public static void install(IEventBus modBus) {
        if (installed) return;
        installed = true;
        HexieRendererAdapter.install(modBus);
        LOGGER.info("HeXie built-in rendering engine initialized (multi-light + Sodium/Iris/Sable adapters)");
    }

    public static void registerShellModel(String modelKey, BakedModel model, BlockState state) {
        HexieRendererAdapter.registerShellModel(modelKey, model, state);
    }

    public static void publishShellSection(ClientLevel level, String familyId, BlockPos sectionPos,
                                           Map<BlockPos, String> ownersInSection, List<StickerEntry> windowsInSection) {
        HexieRendererAdapter.publishShellSection(level, familyId, sectionPos, ownersInSection, windowsInSection);
    }

    public static void clearShellFamily(String familyId) {
        HexieRendererAdapter.clearShellFamily(familyId);
    }

    public static void publishChunkStickers(Level level, int chunkX, int chunkZ, List<StickerEntry> entries) {
        HexieRendererAdapter.publishChunkStickers(level, chunkX, chunkZ, entries);
    }

    public static void onLevelUnload() {
        HexieRendererAdapter.onLevelUnload();
    }
}
