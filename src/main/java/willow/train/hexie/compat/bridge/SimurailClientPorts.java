package willow.train.hexie.compat.bridge;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import willow.train.hexie.bridge.render.SimurailClientBridge;
import willow.train.hexie.compat.simurail.client.CRH1EndFacePhysicalRender;
import willow.train.hexie.compat.simurail.client.PhysicalHeadClient;

final class SimurailClientPorts implements SimurailClientBridge {
    private final PhysicalHeadClientOperations physicalHeadClient = new PhysicalHeadClientOperations() {
        @Override public void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            PhysicalHeadClient.registerRenderers(event);
        }

        @Override public void registerVisuals() {
            PhysicalHeadClient.registerVisuals();
        }
    };

    @Override public CRH1EndFacePhysicalRenderOperations crh1EndFacePhysicalRender() {
        return CRH1EndFacePhysicalRender::parameters;
    }

    @Override public PhysicalHeadClientOperations physicalHeadClient() {
        return physicalHeadClient;
    }
}
