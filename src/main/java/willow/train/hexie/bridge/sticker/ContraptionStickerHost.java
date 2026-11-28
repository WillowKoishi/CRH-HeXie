package willow.train.hexie.bridge.sticker;

import java.util.List;
import willow.train.hexie.core.sticker.StickerEntry;

/**
 * Contraption-attached local-space custom stickers (windows & decals).
 * Implemented on Create's Contraption via MixinContraptionStickers so both compat and render domains
 * can query or update contraption stickers without cross-domain imports.
 */
public interface ContraptionStickerHost {
    List<StickerEntry> hexie$stickers();

    void hexie$setStickers(List<StickerEntry> stickers);

    long hexie$stickerRevision();
}
