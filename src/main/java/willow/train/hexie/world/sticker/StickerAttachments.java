package willow.train.hexie.world.sticker;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import willow.train.hexie.core.HexieConstants;

import java.util.function.Supplier;

/** Registers persistent NeoForge chunk attachment for arbitrary-block custom windows and decals. */
public final class StickerAttachments {
    private StickerAttachments() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, HexieConstants.MOD_ID);

    public static final Supplier<AttachmentType<ChunkStickerData>> CHUNK_STICKERS =
            ATTACHMENTS.register("chunk_stickers", () -> AttachmentType.serializable(ChunkStickerData::new).build());

    public static void register(IEventBus bus) {
        ATTACHMENTS.register(bus);
    }
}
