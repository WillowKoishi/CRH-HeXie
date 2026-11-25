package willow.train.hexie.bridge.render;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import willow.train.hexie.bridge.compat.CreateCarriageBridge;

public interface SimurailClientBridge {
    CRH1EndFacePhysicalRenderOperations crh1EndFacePhysicalRender();

    interface CRH1EndFacePhysicalRenderOperations {
        CreateCarriageBridge.SnapshotCouplerAnimationParametersView parameters(BlockEntity display,
                float partialTick);
    }

    PhysicalHeadClientOperations physicalHeadClient();

    interface PhysicalHeadClientOperations {
        void registerRenderers(EntityRenderersEvent.RegisterRenderers event);
        void registerVisuals();
    }
}
