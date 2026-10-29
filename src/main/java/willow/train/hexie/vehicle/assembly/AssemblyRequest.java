package willow.train.hexie.vehicle.assembly;

import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Input shared by station adapters and future HeXie selection/trigger implementations. */
public record AssemblyRequest(AssemblyRegion region, List<BlockPos> supports, Direction forward) {
    public AssemblyRequest {
        Objects.requireNonNull(region);
        Objects.requireNonNull(forward);
        supports = supports.stream().map(BlockPos::immutable).distinct().toList();
        if (supports.isEmpty()) throw new IllegalArgumentException("No assembly supports");
        for (BlockPos support : supports)
            if (!region.contains(support.getX(), support.getY(), support.getZ()))
                throw new IllegalArgumentException("Support outside assembly selection: " + support);
    }
}
