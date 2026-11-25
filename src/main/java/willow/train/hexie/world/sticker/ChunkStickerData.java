package willow.train.hexie.world.sticker;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;
import willow.train.hexie.core.sticker.StickerEntry;
import willow.train.hexie.core.sticker.StickerEntry.AnchorKey;

/**
 * Chunk-attached storage for custom windows and decals on arbitrary blocks (world or physical structure).
 * Maintains both primary storage keyed by (anchorPos, facing) and a reverse index by affected BlockPos
 * so cross-block / offset windows and decals can be queried, rendered, and cleared cleanly.
 */
public final class ChunkStickerData implements INBTSerializable<CompoundTag> {
    private final Map<AnchorKey, StickerEntry> entries = new LinkedHashMap<>();
    private final Map<BlockPos, Set<AnchorKey>> reverseIndex = new HashMap<>();

    public synchronized Optional<StickerEntry> findExact(BlockPos anchorPos, Direction facing) {
        return Optional.ofNullable(entries.get(new AnchorKey(anchorPos, facing)));
    }

    public synchronized List<StickerEntry> allEntries() {
        return List.copyOf(entries.values());
    }

    public synchronized List<StickerEntry> entriesAffecting(BlockPos pos) {
        var keys = reverseIndex.get(pos.immutable());
        if (keys == null || keys.isEmpty()) return List.of();
        var out = new ArrayList<StickerEntry>(keys.size());
        for (var k : keys) {
            StickerEntry e = entries.get(k);
            if (e != null) out.add(e);
        }
        return List.copyOf(out);
    }

    public synchronized void put(StickerEntry entry) {
        AnchorKey key = entry.anchorKey();
        StickerEntry previous = entries.put(key, entry);
        if (previous != null) unindex(previous);
        index(entry);
    }

    public synchronized Optional<StickerEntry> removeExact(BlockPos anchorPos, Direction facing) {
        AnchorKey key = new AnchorKey(anchorPos, facing);
        StickerEntry removed = entries.remove(key);
        if (removed != null) unindex(removed);
        return Optional.ofNullable(removed);
    }

    /** Removes any entry anchored at or covering (pos, facing) stored in this chunk. */
    public synchronized List<StickerEntry> removeTouching(BlockPos pos, Direction facing) {
        var removed = new ArrayList<StickerEntry>();
        removeExact(pos, facing).ifPresent(removed::add);
        var keys = reverseIndex.get(pos.immutable());
        if (keys != null) {
            for (var k : List.copyOf(keys)) {
                if (k.facing() == facing) {
                    StickerEntry e = entries.remove(k);
                    if (e != null) {
                        unindex(e);
                        removed.add(e);
                    }
                }
            }
        }
        return removed;
    }

    public synchronized void replaceAll(List<StickerEntry> next) {
        entries.clear();
        reverseIndex.clear();
        for (var e : next) {
            entries.put(e.anchorKey(), e);
            index(e);
        }
    }

    public synchronized boolean isEmpty() {
        return entries.isEmpty();
    }

    private void index(StickerEntry entry) {
        AnchorKey key = entry.anchorKey();
        reverseIndex.computeIfAbsent(entry.anchorPos(), p -> new LinkedHashSet<>()).add(key);
        for (BlockPos affected : entry.affectedPositions()) {
            reverseIndex.computeIfAbsent(affected.immutable(), p -> new LinkedHashSet<>()).add(key);
        }
    }

    private void unindex(StickerEntry entry) {
        AnchorKey key = entry.anchorKey();
        removeReverse(entry.anchorPos(), key);
        for (BlockPos affected : entry.affectedPositions()) {
            removeReverse(affected, key);
        }
    }

    private void removeReverse(BlockPos pos, AnchorKey key) {
        var set = reverseIndex.get(pos.immutable());
        if (set != null) {
            set.remove(key);
            if (set.isEmpty()) reverseIndex.remove(pos.immutable());
        }
    }

    @Override
    public synchronized CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();
        for (var e : entries.values()) list.add(e.save());
        root.put("Entries", list);
        return root;
    }

    @Override
    public synchronized void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
        entries.clear();
        reverseIndex.clear();
        if (nbt == null || !nbt.contains("Entries", Tag.TAG_LIST)) return;
        ListTag list = nbt.getList("Entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            StickerEntry e = StickerEntry.load(list.getCompound(i));
            entries.put(e.anchorKey(), e);
            index(e);
        }
    }
}
