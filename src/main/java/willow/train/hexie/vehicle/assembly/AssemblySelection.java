package willow.train.hexie.vehicle.assembly;

import java.util.List;
import net.minecraft.core.BlockPos;

/** Discovery only: world positions, not a Create carriage, and not yet removed from the world. */
public record AssemblySelection(List<Component> components) {
    public AssemblySelection { components = List.copyOf(components); }

    public record Component(BlockPos origin, List<BlockPos> blocks, List<BlockPos> supports) {
        public Component {
            origin = origin.immutable();
            blocks = blocks.stream().map(BlockPos::immutable).toList();
            supports = supports.stream().map(BlockPos::immutable).toList();
        }
    }
}
