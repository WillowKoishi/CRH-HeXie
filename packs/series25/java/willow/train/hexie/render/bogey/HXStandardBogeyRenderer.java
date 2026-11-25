package willow.train.hexie.render.bogey;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import net.createmod.catnip.data.Iterate;
import net.minecraft.nbt.CompoundTag;
import static willow.train.hexie.render.init.PartialModelInit.*;
import willow.train.hexie.bridge.render.BogeyRenderBridge.HXStandardBogeyRendererSmallHXStandardBogeyRendererView;

// split from willow/train/hexie/client/render/bogey/HXStandardBogeyRenderer.java


public class HXStandardBogeyRenderer {

    public static class SmallHXStandardBogeyRenderer extends SimpleBogeyRenderer implements HXStandardBogeyRendererSmallHXStandardBogeyRendererView {

        /**

         */
        
        @Override
        public BogeySizes.BogeySize getSize() {
            return BogeySizes.SMALL;
        }

        /**
         * 方法变更
         *
         * <p>1. Transform -> BogeyModelData
         *
         * <p>2. getTransformPartialModel -> getTransForm
         *
         * <p>3. finalize() 已经被弃用 -> 调用 BogeyModelData.render()
         *
         * <p>
         *
         * @param bogeyData Custom data stored on the bogey able to be used for rendering
         * @param wheelAngle The angle of the wheel
         * @param ms The posestack to render to
         * @param light (Optional) Light used for in-world rendering
         * @param vb (Optional) Vertex Consumer used for in-world rendering
         * @param inContraption
         */
        @Override
        public void render(
                CompoundTag bogeyData,
                float wheelAngle,
                PoseStack ms,
                int light,
                VertexConsumer vb,
                boolean inContraption) {

            boolean inInstancedContraption = vb == null;
            BogeyModelData frame = getTransform(HXSTANDARDBOGEY_FRAME, ms, inInstancedContraption);
            frame.render(ms, light, vb);
            BogeySuspensionPartsRenderer.render(this, BogeySuspensionModels.CW2, bogeyData, ms, light, vb);

            BogeyModelData[] wheels =
                    getTransform(HXSTANDARDBOGEY_WHEEL, ms, inInstancedContraption, 2);

            for (int side : Iterate.positiveAndNegative) {
                if (!inInstancedContraption) ms.pushPose();
                BogeyModelData wheel = wheels[(side + 1) / 2];
                wheel.translate(0, 0.805, ((double) side) * 1.2d).rotateX(wheelAngle);
                wheel.render(ms, light, vb);
                if (!inInstancedContraption) ms.popPose();
            }
        }
    }

}
