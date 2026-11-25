package willow.train.hexie.bridge.vehicle;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public interface CarriageSnapshotView {
    CompoundTag save(RegistryAccess registries);
    UUID id();
    ResourceKey<Level> dimension();
    BlockPos origin();
    List<? extends VehicleBridge.CarriageSnapshotBlockView> blocks();
    List<BlockPos> supports();
    List<AABB> glue();
}
