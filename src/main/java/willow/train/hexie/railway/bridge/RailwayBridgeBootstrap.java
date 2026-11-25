package willow.train.hexie.railway.bridge;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.core.HexieConstants;

@EventBusSubscriber(modid = HexieConstants.MOD_ID)
public final class RailwayBridgeBootstrap {
    private RailwayBridgeBootstrap() {}

    public static void install() {
        HexieBridges.registerRailway(new RailwayPort(new RailwayGraphPort()));
        HexieBridges.registerTrackRegistry(new TrackRegistryPort());
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        HexieBridges.railway().railwayGraph().releaseLevel(event.getLevel());
    }
}
