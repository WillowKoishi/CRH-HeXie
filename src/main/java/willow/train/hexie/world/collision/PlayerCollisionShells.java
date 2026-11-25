package willow.train.hexie.world.collision;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Optional large shells contribute coarse player-only collision to the shared query. */
public final class PlayerCollisionShells {
    private static final List<BiFunction<BlockGetter, AABB, List<VoxelShape>>> PROVIDERS = new CopyOnWriteArrayList<>();
    private PlayerCollisionShells() {}
    public static void register(BiFunction<BlockGetter, AABB, List<VoxelShape>> provider) {
        PROVIDERS.add(Objects.requireNonNull(provider));
    }
    public static List<VoxelShape> collisions(BlockGetter world, AABB box) {
        var result = new ArrayList<VoxelShape>();
        for (var provider : PROVIDERS) result.addAll(provider.apply(world, box));
        return result;
    }
}
