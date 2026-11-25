package willow.train.hexie.core.api.interaction;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/** The subset of block entity data that may be printed by a schematic. */
public interface HexieSchematicData {
    void writeSafe(CompoundTag tag, HolderLookup.Provider registries);
}
