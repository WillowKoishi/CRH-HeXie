package willow.train.hexie.vehicle.bridge;

import willow.train.hexie.bridge.HexieBridges;

public final class VehicleBridgeBootstrap {
    private VehicleBridgeBootstrap() {}

    public static void install() {
        HexieBridges.registerVehicle(new VehiclePort());
        HexieBridges.registerCabControl(new CabControlPort());
        HexieBridges.registerCoupling(new CouplingPort());
        HexieBridges.registerBogeyRegistry(new BogeyRegistryPort());
        HexieBridges.registerPlayerCarriage(new PlayerCarriagePort());
    }

    public static void installClient() {
        HexieBridges.Client.registerBogeyRegistryClient(new ClientBogeyRegistryPort());
    }
}
