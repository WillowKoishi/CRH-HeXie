package willow.train.hexie.bridge.compat;

import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;

public interface CompatBridge {
    DiscreteVoxelShape discreteShape(VoxelShape shape);
}
