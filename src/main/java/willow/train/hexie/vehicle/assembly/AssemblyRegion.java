package willow.train.hexie.vehicle.assembly;

import java.util.List;
import willow.train.hexie.bridge.vehicle.VehicleBridge;

/** The trigger supplies the selection; the scanner must not grow it. Coordinates are inclusive. */
@FunctionalInterface
public interface AssemblyRegion extends VehicleBridge.AssemblyRegionView {
    boolean contains(int x, int y, int z);

    static AssemblyRegion boxes(List<Box> boxes) {
        var snapshot = List.copyOf(boxes);
        return (x, y, z) -> snapshot.stream().anyMatch(box -> box.contains(x, y, z));
    }

    record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) implements AssemblyRegion, VehicleBridge.AssemblyRegionBoxView {
        public Box {
            if (minX > maxX || minY > maxY || minZ > maxZ)
                throw new IllegalArgumentException("Inverted assembly region");
        }

        @Override
        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
    }
}
