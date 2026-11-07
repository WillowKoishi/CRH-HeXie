package willow.train.hexie.api.interaction;

import net.minecraft.world.entity.player.Player;

/** Optional block-entity storage. Stateless blocks can implement their host's operateControl directly. */
public interface BlockControlInstance {
    ControlState surfaceState();
    void operateControl(Player player, BlockControl control, int value);
}
