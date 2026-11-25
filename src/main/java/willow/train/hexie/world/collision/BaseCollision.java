package willow.train.hexie.world.collision;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class BaseCollision {
    private BaseCollision() {}

    public static final class ForHorizontalBlockBase {
        private static final Map<Block, Map<Direction, VoxelShape>> SHAPS =
                new HashMap<Block, Map<Direction, VoxelShape>>();
    }
}
