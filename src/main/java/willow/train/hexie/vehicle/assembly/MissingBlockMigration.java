package willow.train.hexie.vehicle.assembly;

import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

/** Removes unregistered blocks only after preserving the complete original payload. */
public final class MissingBlockMigration {
    private MissingBlockMigration() {}

    public static CompoundTag migrate(ServerLevel level, CompoundTag original, String name) {
        return migrate(level, original, name, java.util.Set.of());
    }

    public static CompoundTag migrate(ServerLevel level, CompoundTag original, String name,
                                     java.util.Set<Tag> missingPositions) {
        var migrated = original.copy();
        boolean changed = remove(migrated, level);
        for (String key : new String[] {"Actors", "ChangedBlocks"}) {
            var entries = migrated.getList(key, Tag.TAG_COMPOUND);
            for (int i = entries.size() - 1; i >= 0; i--) {
                if (missingPositions.contains(entries.getCompound(i).get("Pos"))) {
                    entries.remove(i);
                    changed = true;
                }
            }
        }
        if (!changed) return original;
        var directory = level.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("hexie-backups").resolve("missing-blocks");
        try {
            Files.createDirectories(directory);
            NbtIo.writeCompressed(original, directory.resolve(name + "-" + UUID.randomUUID() + ".nbt"));
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot back up carriage data before removing missing blocks", failure);
        }
        return migrated;
    }

    private static boolean missing(CompoundTag block, ServerLevel level) {
        if (!block.contains("State", Tag.TAG_COMPOUND)) return false;
        var id = ResourceLocation.tryParse(block.getCompound("State").getString("Name"));
        return id != null && !level.registryAccess().registryOrThrow(Registries.BLOCK).containsKey(id);
    }

    private static boolean remove(Tag tag, ServerLevel level) {
        boolean changed = false;
        if (tag instanceof CompoundTag compound) {
            // Snapshot positions remain as air so origin/support metadata and persisted motion
            // stay aligned. The missing block and its block entity are no longer present.
            if (compound.contains("Supports", Tag.TAG_LIST) && compound.contains("Blocks", Tag.TAG_LIST)) {
                for (var value : compound.getList("Blocks", Tag.TAG_COMPOUND)) {
                    var block = (CompoundTag) value;
                    if (!missing(block, level)) continue;
                    var air = new CompoundTag();
                    air.putString("Name", "minecraft:air");
                    block.put("State", air);
                    block.remove("BlockEntity");
                    changed = true;
                }
            }
            var removed = new java.util.HashSet<Tag>();
            var changes = compound.getList("ChangedBlocks", Tag.TAG_COMPOUND);
            for (int i = changes.size() - 1; i >= 0; i--) {
                var block = changes.getCompound(i);
                if (!missing(block, level)) continue;
                if (block.contains("Pos")) removed.add(block.get("Pos").copy());
                changes.remove(i);
                changed = true;
            }
            var actors = compound.getList("Actors", Tag.TAG_COMPOUND);
            for (int i = actors.size() - 1; i >= 0; i--) {
                if (removed.contains(actors.getCompound(i).get("Pos"))) {
                    actors.remove(i);
                    changed = true;
                }
            }
            // Walk only our structure containers, not arbitrary inventory/block-entity NBT.
            for (String key : new String[] {"Carriages", "WorkingState"})
                if (compound.contains(key)) changed |= remove(compound.get(key), level);
        } else if (tag instanceof ListTag list) {
            for (var value : list) changed |= remove(value, level);
        }
        return changed;
    }
}
