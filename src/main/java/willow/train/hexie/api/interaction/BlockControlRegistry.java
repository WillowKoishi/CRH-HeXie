package willow.train.hexie.api.interaction;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Register providers during common setup, including for blocks whose classes cannot implement the host interface. */
public final class BlockControlRegistry {
    private static final Map<Block, BlockControlHost> HOSTS = new IdentityHashMap<>();
    private BlockControlRegistry() {}
    public static void register(Block block, BlockControlHost host) {
        if (HOSTS.putIfAbsent(Objects.requireNonNull(block), Objects.requireNonNull(host)) != null)
            throw new IllegalArgumentException("Controls already registered for " + block);
    }
    public static BlockControlHost find(BlockState state) {
        var registered = HOSTS.get(state.getBlock());
        return registered != null ? registered : state.getBlock() instanceof BlockControlHost host ? host : null;
    }
}
