package willow.train.hexie.compat.create;

import java.util.Collection;
import java.util.List;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import net.minecraft.world.phys.AABB;
import willow.train.hexie.vehicle.assembly.AssemblyRegion;

/** Takes the ranges selected by the caller; does not invent a radius or grow a selection. */
public final class CreateAssemblyRegions {
    private CreateAssemblyRegions() {}

    public static AssemblyRegion fromGlue(Collection<SuperGlueEntity> selectedGlue) {
        return fromBoxes(selectedGlue.stream().map(SuperGlueEntity::getBoundingBox).toList());
    }

    public static AssemblyRegion fromBoxes(Collection<AABB> selectedBoxes) {
        List<AABB> boxes = List.copyOf(selectedBoxes);
        // Match SuperGlueEntity.contains(BlockPos), including its half-open upper boundary.
        return (x, y, z) -> boxes.stream().anyMatch(box -> box.contains(x + .5, y + .5, z + .5));
    }
}
