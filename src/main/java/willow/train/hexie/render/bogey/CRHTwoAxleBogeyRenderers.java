package willow.train.hexie.render.bogey;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.data.Iterate;
import net.minecraft.nbt.CompoundTag;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.render.BogeyRenderBridge.HXStandardBogeyRendererCrhTwoAxleRendererView;

// split from willow/train/hexie/client/render/bogey/HXStandardBogeyRenderer.java, lines 379-422
public final class CRHTwoAxleBogeyRenderers {
    public static class CrhTwoAxleRenderer extends SimpleBogeyRenderer implements HXStandardBogeyRendererCrhTwoAxleRendererView {
        private final BogeySizes.BogeySize size;
        private final PartialModel frame;
        private final PartialModel wheel;
        private final double frameOffset;
        private final double axleHeight;
        private final double axleHalfSpacing;
        private final BogeySuspensionModels.Model suspension;

        public CrhTwoAxleRenderer(BogeySizes.BogeySize size, PartialModel frame, PartialModel wheel, double frameOffset) {
            this(size, frame, wheel, AXLE_HEIGHT, frameOffset, 1.25, size.equals(HexieBridges.createTrain().bogeySizesInit().dt206())
                    ? BogeySuspensionModels.DT206 : BogeySuspensionModels.SKTB200);
        }

        public CrhTwoAxleRenderer(BogeySizes.BogeySize size, PartialModel frame, PartialModel wheel,
                                  double axleHeight, double frameOffset, double axleHalfSpacing,
                                  BogeySuspensionModels.Model suspension) {
            this.size = size;
            this.frame = frame;
            this.wheel = wheel;
            this.frameOffset = frameOffset;
            this.axleHeight = axleHeight;
            this.axleHalfSpacing = axleHalfSpacing;
            this.suspension = suspension;
        }
        // Exported wheel tread radius: 6.88/16; axle centres: Z = +/-20/16.
        // Original model-space alignment; SimpleBogeyRenderer applies the height increase once.
        private static final double AXLE_HEIGHT = 0.7726;
        @Override public BogeySizes.BogeySize getSize() { return size; }
        @Override public void render(CompoundTag data, float wheelAngle, PoseStack pose,
                int light, VertexConsumer buffer, boolean inContraption) {
            boolean instanced = buffer == null;
            getTransform(frame, pose, instanced)
                    .translate(0, axleHeight + frameOffset, 0).render(pose, light, buffer);
            BogeySuspensionPartsRenderer.render(this, suspension, data, pose, light, buffer);
            BogeyModelData[] wheels = getTransform(wheel, pose, instanced, 2);
            for (int side : Iterate.positiveAndNegative) {
                if (!instanced) pose.pushPose();
                wheels[(side + 1) / 2].translate(0, axleHeight, side * axleHalfSpacing)
                        .rotateX(wheelAngle).render(pose, light, buffer);
                if (!instanced) pose.popPose();
            }
        }
    }
}
