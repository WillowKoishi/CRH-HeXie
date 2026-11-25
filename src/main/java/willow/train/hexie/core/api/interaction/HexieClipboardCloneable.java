package willow.train.hexie.core.api.interaction;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

public interface HexieClipboardCloneable {
    String getClipboardKey();
    boolean writeToClipboard(HolderLookup.Provider registries, CompoundTag tag, Direction side);
    boolean readFromClipboard(HolderLookup.Provider registries, CompoundTag tag, Player player, Direction side, boolean simulate);
}
