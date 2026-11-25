package willow.train.hexie.render.bogey;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import net.createmod.catnip.data.Iterate;
import net.minecraft.nbt.CompoundTag;
import static willow.train.hexie.render.init.PartialModelInit.*;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.render.BogeyRenderBridge.HXStandardBogeyRendererSW220KBackwardBogeyRendererView;
import willow.train.hexie.bridge.render.BogeyRenderBridge.HXStandardBogeyRendererSW220KBogeyRendererView;

// split from willow/train/hexie/client/render/bogey/HXStandardBogeyRenderer.java, lines 271-342
public final class SW220KBogeyRenderers {
    public static class SW220KBogeyRenderer extends SimpleBogeyRenderer implements HXStandardBogeyRendererSW220KBogeyRendererView {

        

        @Override
        public BogeySizes.BogeySize getSize() {
            return HexieBridges.createTrain().bogeySizesInit().sw220k();
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

            BogeyModelData frame = getTransform(SW220K, ms, inInstancedContraption);
            BogeyModelData[] wheels = getTransform(PK209P_WHEEL2, ms, inInstancedContraption, 2);

            frame.translate(0, 0.635, 0).render(ms, light, vb);
            BogeySuspensionPartsRenderer.render(this, BogeySuspensionModels.SW220K, bogeyData, ms, light, vb);

            for (int side : Iterate.positiveAndNegative) {
                if (!inInstancedContraption) ms.pushPose();
                BogeyModelData wheel = wheels[(side + 1) / 2];
                wheel.translate(0, 0.805, ((double) side) * 1.25d).rotateX(wheelAngle);
                wheel.render(ms, light, vb);
                if (!inInstancedContraption) ms.popPose();
            }
        }
    }

    public static class SW220KBackwardBogeyRenderer extends SimpleBogeyRenderer implements HXStandardBogeyRendererSW220KBackwardBogeyRendererView {

        

        @Override
        public BogeySizes.BogeySize getSize() {
            return HexieBridges.createTrain().bogeySizesInit().sw220kBackward();
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

            BogeyModelData frame = getTransform(SW220K, ms, inInstancedContraption);
            BogeyModelData[] wheels = getTransform(PK209P_WHEEL2, ms, inInstancedContraption, 2);

            // 渲染架体
            frame.translate(0, 0.635, 0).render(ms, light, vb);
            BogeySuspensionPartsRenderer.render(this, BogeySuspensionModels.SW220K, bogeyData, ms, light, vb);
            // 渲染轮对
            for (int side : Iterate.positiveAndNegative) {
                if (!inInstancedContraption) ms.pushPose();
                BogeyModelData wheel = wheels[(side + 1) / 2];
                wheel.translate(0, 0.805, ((double) side) * 1.25d).rotateX(wheelAngle);
                wheel.render(ms, light, vb);
                if (!inInstancedContraption) ms.popPose();
            }
        }
    }
}
