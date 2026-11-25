package willow.train.hexie.railway.bridge;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import willow.train.hexie.bridge.railway.TrackRegistryBridge;
import willow.train.hexie.core.api.railway.RailwayDefinition;
import willow.train.hexie.core.api.railway.RailwayHandle;
import willow.train.hexie.railway.graph.RailwayCatalog;
import willow.train.hexie.railway.track.registry.BogeyBlockRegistration;
import willow.train.hexie.railway.track.registry.LocomotiveFrontBlockRegistration;

final class TrackRegistryPort implements TrackRegistryBridge {
    private final RailwayCatalog catalog = new RailwayCatalog();

    private final TrackBlockInitOperations trackBlockInit = new TrackBlockInitOperations() {
        @Override public Block df11gFrontBlock() { return LocomotiveFrontBlockRegistration.DF11G_FRONT_BLOCK.get(); }
        @Override public Block hxd3dFrontBlock() { return LocomotiveFrontBlockRegistration.HXD3D_FRONT_BLOCK.get(); }

        @Override
        public Block hxStandardBogey() {
            return BogeyBlockRegistration.HX_STANDARD_BOGEY.get();
        }

        @Override
        public Block df11gBogey() {
            return BogeyBlockRegistration.DF11G_BOGEY.get();
        }

        @Override
        public Block ss7dFrontBogey() {
            return BogeyBlockRegistration.SS7D_FRONT_BOGEY.get();
        }

        @Override
        public Block df11gBackwardBogey() {
            return BogeyBlockRegistration.DF11G_BACKWARD_BOGEY.get();
        }

        @Override
        public Block pk209pBogey() {
            return BogeyBlockRegistration.PK209P_BOGEY.get();
        }

        @Override
        public Block pk209pNoMotorBogey() {
            return BogeyBlockRegistration.PK209P_NO_MOTOR_BOGEY.get();
        }

        @Override
        public Block pk209pBackwardBogey() {
            return BogeyBlockRegistration.PK209P_BACKWARD_BOGEY.get();
        }

        @Override
        public Block sw220kBogey() {
            return BogeyBlockRegistration.SW220K_BOGEY.get();
        }

        @Override
        public Block sw220kBackwardBogey() {
            return BogeyBlockRegistration.SW220K_BACKWARD_BOGEY.get();
        }

        @Override
        public Block qjBogey() {
            return BogeyBlockRegistration.QJ_BOGEY.get();
        }

        @Override
        public Block qjBackwardBogey() {
            return BogeyBlockRegistration.QJ_BACKWARD_BOGEY.get();
        }

        @Override
        public Block sw160Bogey() {
            return BogeyBlockRegistration.SW160_BOGEY.get();
        }

        @Override
        public Block dt206Bogey() {
            return BogeyBlockRegistration.DT206_BOGEY.get();
        }

        @Override
        public Block sktb200Bogey() {
            return BogeyBlockRegistration.SKTB200_BOGEY.get();
        }

        @Override
        public Block crh1aMotorBogey() {
            return BogeyBlockRegistration.CRH1A_MOTOR_BOGEY.get();
        }

        @Override
        public Block crh1aTrailerBogey() {
            return BogeyBlockRegistration.CRH1A_TRAILER_BOGEY.get();
        }
    };

    @Override
    public TrackBlockInitOperations trackBlockInit() {
        return trackBlockInit;
    }

    @Override
    public RailwayHandle register(IEventBus bus, ResourceLocation id, RailwayDefinition definition) {
        return catalog.register(bus, id, definition);
    }

    @Override
    public List<RailwayHandle> entries() {
        return catalog.entries();
    }
}
