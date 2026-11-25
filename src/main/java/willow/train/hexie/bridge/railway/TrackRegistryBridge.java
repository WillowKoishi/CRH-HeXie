package willow.train.hexie.bridge.railway;

import net.minecraft.world.level.block.Block;
import willow.train.hexie.core.api.railway.RailwayRegistry;

/** Railway registration and the track blocks and materials bound during startup. */
public interface TrackRegistryBridge extends RailwayRegistry.Backend {
    double RAIL_SURFACE_HEIGHT = 0.75;
    double RAIL_HEIGHT_INCREASE = 0.4074;

    TrackBlockInitOperations trackBlockInit();

    interface TrackBlockInitOperations {
        Block df11gFrontBlock();
        Block hxd3dFrontBlock();
        Block hxStandardBogey();
        Block df11gBogey();
        Block ss7dFrontBogey();
        Block df11gBackwardBogey();
        Block pk209pBogey();
        Block pk209pNoMotorBogey();
        Block pk209pBackwardBogey();
        Block sw220kBogey();
        Block sw220kBackwardBogey();
        Block qjBogey();
        Block qjBackwardBogey();
        Block sw160Bogey();
        Block dt206Bogey();
        Block sktb200Bogey();
        Block crh1aMotorBogey();
        Block crh1aTrailerBogey();
    }
}
