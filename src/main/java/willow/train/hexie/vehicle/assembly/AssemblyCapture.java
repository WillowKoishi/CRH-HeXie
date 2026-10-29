package willow.train.hexie.vehicle.assembly;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Server-thread capture phase. Produces data only; no block removal, entities or station hook. */
public final class AssemblyCapture {
    private AssemblyCapture() {}

    public static List<CarriageSnapshot> capture(ServerLevel level, AssemblySelection selection) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Server thread required");
        if (selection.components().isEmpty()) throw new IllegalArgumentException("Empty assembly selection");
        var result = new ArrayList<CarriageSnapshot>();
        var claimed = new HashSet<BlockPos>();
        for (var component : selection.components()) {
            var blocks = new ArrayList<CarriageSnapshot.Block>();
            for (BlockPos world : component.blocks()) {
                if (!claimed.add(world)) throw new IllegalArgumentException("Duplicate selected position: " + world);
                if (level.isOutsideBuildHeight(world) || !level.isLoaded(world))
                    throw new IllegalArgumentException("Selected block is outside loaded world: " + world);
                var state = level.getBlockState(world);
                var entity = level.getBlockEntity(world);
                blocks.add(new CarriageSnapshot.Block(world.subtract(component.origin()), state,
                        entity == null ? null : entity.saveWithFullMetadata(level.registryAccess())));
            }
            var supports = component.supports().stream().map(pos -> pos.subtract(component.origin())).toList();
            result.add(new CarriageSnapshot(UUID.randomUUID(), level.dimension(), component.origin(), blocks, supports));
        }
        return List.copyOf(result);
    }
}
