package willow.train.hexie.render.bogey;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import net.createmod.catnip.data.Iterate;
import net.minecraft.nbt.CompoundTag;
import static willow.train.hexie.render.init.PartialModelInit.*;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.render.BogeyRenderBridge.HXStandardBogeyRendererSW160BogeyRendererView;

// split from willow/train/hexie/client/render/bogey/HXStandardBogeyRenderer.java, lines 344-378
public final class SW160BogeyRenderers {
    public static class SW160BogeyRenderer extends SimpleBogeyRenderer implements HXStandardBogeyRendererSW160BogeyRendererView {

        

        @Override
        public BogeySizes.BogeySize getSize() {
            return HexieBridges.createTrain().bogeySizesInit().sw160();
        }

        @Override
        public void render(
                CompoundTag bogeyData,
                float wheelAngle,
                PoseStack ms,
                int light,
                VertexConsumer vb,
                boolean inContraption) {

            boolean inInstancedContraption = vb == null;

            BogeyModelData frame = getTransform(SW160_FRAME, ms, inInstancedContraption);
            BogeyModelData[] wheels = getTransform(SW160_WHEEL, ms, inInstancedContraption, 2);

            frame.translate(0, 0.264, 0).render(ms, light, vb);
            BogeySuspensionPartsRenderer.render(this, BogeySuspensionModels.SW160, bogeyData, ms, light, vb);

            for (int side : Iterate.positiveAndNegative) {
                if (!inInstancedContraption) ms.pushPose();
                BogeyModelData wheel = wheels[(side + 1) / 2];
                wheel.translate(0, 0.805, ((double) side) * 1.28d).rotateX(wheelAngle);
                wheel.render(ms, light, vb);
                if (!inInstancedContraption) ms.popPose();
            }
        }
    }
}
