package willow.train.hexie.compat.mixins.create;

import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.StructureTransform;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import willow.train.hexie.bridge.sticker.ContraptionStickerHost;
import willow.train.hexie.compat.create.assembly.ContraptionStickers;
import willow.train.hexie.core.sticker.StickerEntry;

/**
 * Attaches contraption-local custom stickers (windows & decals) to Create Contraptions across
 * assembly, NBT save/sync, and disassembly without modifying CarriageSnapshot.
 */
@Mixin(value = Contraption.class, remap = false)
public abstract class MixinContraptionStickers implements ContraptionStickerHost {
    @Shadow public BlockPos anchor;
    @Shadow protected Map<BlockPos, StructureBlockInfo> blocks;
    @Shadow public abstract void invalidateClientContraptionStructure();

    @Unique private List<StickerEntry> hexie$stickers = List.of();
    @Unique private long hexie$stickerRevision = 0L;

    @Override
    public List<StickerEntry> hexie$stickers() {
        return hexie$stickers;
    }

    @Override
    public void hexie$setStickers(List<StickerEntry> stickers) {
        List<StickerEntry> next = (stickers == null || stickers.isEmpty()) ? List.of() : List.copyOf(stickers);
        if (!hexie$stickers.equals(next)) {
            hexie$stickers = next;
            hexie$stickerRevision++;
        }
    }

    @Override
    public long hexie$stickerRevision() {
        return hexie$stickerRevision;
    }

    @Inject(method = "writeNBT", at = @At("RETURN"))
    private void hexie$writeStickers(HolderLookup.Provider registries, boolean spawnPacket, CallbackInfoReturnable<CompoundTag> cir) {
        if (!hexie$stickers.isEmpty()) {
            CompoundTag tag = cir.getReturnValue();
            if (tag != null) {
                tag.put(ContraptionStickers.NBT_KEY, ContraptionStickers.writeListTag(hexie$stickers));
            }
        }
    }

    @Inject(method = "readNBT", at = @At("TAIL"))
    private void hexie$readStickers(Level world, CompoundTag nbt, boolean spawnData, CallbackInfo ci) {
        List<StickerEntry> loaded = ContraptionStickers.readListTag(nbt, ContraptionStickers.NBT_KEY);
        if (!hexie$stickers.equals(loaded)) {
            hexie$stickers = loaded;
            hexie$stickerRevision++;
            if (world != null && world.isClientSide) {
                invalidateClientContraptionStructure();
            }
        }
    }

    @Inject(method = "removeBlocksFromWorld", at = @At("HEAD"))
    private void hexie$captureStickersOnRemove(Level world, BlockPos offset, CallbackInfo ci) {
        if (!(world instanceof ServerLevel server) || !hexie$stickers.isEmpty() || anchor == null || blocks == null || blocks.isEmpty()) {
            return;
        }
        BlockPos origin = offset == null ? anchor : anchor.offset(offset);
        var captured = ContraptionStickers.captureFromWorld(server, origin, blocks.keySet());
        if (!captured.isEmpty()) {
            hexie$setStickers(captured.localEntries());
            ContraptionStickers.removeWorldStickers(server, captured.worldEntries());
        }
    }

    @Inject(method = "addBlocksToWorld", at = @At("TAIL"))
    private void hexie$restoreStickersOnDisassemble(Level world, StructureTransform transform, CallbackInfo ci) {
        if (world instanceof ServerLevel server && !hexie$stickers.isEmpty()) {
            ContraptionStickers.restoreToWorld(server, transform, hexie$stickers);
        }
    }
}
