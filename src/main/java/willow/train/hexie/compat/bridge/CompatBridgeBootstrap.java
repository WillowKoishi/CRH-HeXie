package willow.train.hexie.compat.bridge;

import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.compat.cee.CeePantographCompat;
import willow.train.hexie.compat.create.CreateBootstrap;
import willow.train.hexie.compat.create.railx.RailxTracks;

public final class CompatBridgeBootstrap {
    private CompatBridgeBootstrap() {}

    public static void install() {
        HexieBridges.registerCompat(shape -> shape.shape);
        HexieBridges.registerSable(new SablePorts());
        if (CreateBootstrap.isCreateLoaded()) CreateBridges.install();
    }

    public static void installClient() {
        CompatClientBridgeBootstrap.install();
        if (CreateBootstrap.isCreateLoaded()) CreateBridges.installClient();
    }

    private static final class CreateBridges {
        static void install() {
            HexieBridges.registerCreateCarriage(new CreateCarriagePorts());
            HexieBridges.registerCreateDoor(new CreateDoorPorts());
            HexieBridges.registerCreatePanel(new CreatePanelPorts());
            HexieBridges.registerCreatePlayer(new CreatePlayerPorts());
            HexieBridges.registerCreateTrain(new CreateTrainPorts());
            HexieBridges.registerSimurail(new SimurailPort());
            HexieBridges.registerCee(properties -> CeePantographCompat.createBlock(properties));
            HexieBridges.registerRailx(state -> RailxTracks.isFlexible(state));
            HexieBridges.registerCreateTrack(new CreateTrackPort());
            HexieBridges.registerSignalRegistry(new SignalRegistryPort());
        }

        static void installClient() {
            CompatClientBridgeBootstrap.CreateClientBridges.install();
        }
    }
}
