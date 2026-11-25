package willow.train.hexie.bridge.render;

import com.mojang.blaze3d.vertex.PoseStack;
import willow.train.hexie.bridge.compat.SnapshotCarriageView;

public interface CreateCarriageClientBridge {
    SnapshotCarriageEntityOperations snapshotCarriageEntity();

    interface SnapshotCarriageEntityOperations {
        void applyLocalTransforms(SnapshotCarriageView value, PoseStack stack, float partialTicks);
    }
}
