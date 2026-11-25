package willow.train.hexie.compat.bridge;

import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.compat.cee.PantographBaseModels;
import willow.train.hexie.compat.common.HeXieSimulatedClientCompat;
import willow.train.hexie.compat.sable.SableRenderEmbedding;

final class CompatClientBridgeBootstrap {
    private CompatClientBridgeBootstrap() {}

    static void install() {
        HexieBridges.Client.registerIris(new IrisPorts());
        HexieBridges.Client.registerCompatClient(() -> HeXieSimulatedClientCompat::register);
    }

    static final class CreateClientBridges {
        static void install() {
            HexieBridges.Client.registerCreateCarriageClient(() -> (carriage, stack, partial) -> carriage.asEntity().applyLocalTransforms(stack, partial));
            HexieBridges.Client.registerCreateTrainClient(new CreateTrainClientPorts());
            HexieBridges.Client.registerSableClient(() -> SableRenderEmbedding::find);
            HexieBridges.Client.registerSimurailClient(new SimurailClientPorts());
            HexieBridges.Client.registerCeeClient(() -> PantographBaseModels::onBaking);
        }
    }
}
