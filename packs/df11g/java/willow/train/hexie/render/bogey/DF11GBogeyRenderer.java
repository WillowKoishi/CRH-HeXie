package willow.train.hexie.render.bogey;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import static willow.train.hexie.render.init.PartialModelInit.*;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.render.BogeyRenderBridge.DF11GBogeyRendererBackwardDF11GBogeyRendererView;
import willow.train.hexie.bridge.render.BogeyRenderBridge.DF11GBogeyRendererSmallDF11GBogeyRendererView;
import willow.train.hexie.core.datafix.Constants;

// split from willow/train/hexie/client/render/bogey/DF11GBogeyRenderer.java


public class DF11GBogeyRenderer {

    public static class SmallDF11GBogeyRenderer extends SimpleBogeyRenderer implements DF11GBogeyRendererSmallDF11GBogeyRendererView {

        // public static LerpedFloat pitch;

        @Override
        public BogeySizes.BogeySize getSize() {
            return BogeySizes.SMALL;
        }

        @Override
        public void render(
                CompoundTag bogeyData,
                float wheelAngle,
                PoseStack ms,
                int light,
                VertexConsumer vb,
                boolean inContraption) {

            Direction direction =
                    bogeyData.contains(Constants.BOGEY_ASSEMBLY_DIRECTION_KEY)
                            ? NBTHelper.readEnum(
                                    bogeyData,
                                    Constants.BOGEY_ASSEMBLY_DIRECTION_KEY,
                                    Direction.class)
                            : Direction.NORTH;

            boolean inInstancedContraption = vb == null;

            BogeyModelData frame = getTransform(DF11G_FRAME, ms, inInstancedContraption);
            BogeyModelData[] wheels = getTransform(DF11G_WHEEL, ms, inInstancedContraption, 3);
            // These rods were authored in the shared body top, also used by the backward variant.
            BogeySuspensionPartsRenderer.render(this, BogeySuspensionModels.DF11G, bogeyData, ms, light, vb);

            if (direction == Direction.SOUTH || direction == Direction.EAST) {
                if (inContraption) {
                    frame.translate(0, 0.375, 0).render(ms, light, vb);

                    for (int side = -1; side < 2; side++) {
                        if (!inInstancedContraption) ms.pushPose();
                        BogeyModelData wheel = wheels[side + 1];
                        wheel.translate(0, 0.88, ((double) side) * 2d)
                                .rotateX(wheelAngle)
                                .render(ms, light, vb);
                        if (!inInstancedContraption) ms.popPose();
                    }
                } else {
                    frame.rotateY(180).translate(0, 0.375, 0).render(ms, light, vb);

                    for (int side = -1; side < 2; side++) {
                        if (!inInstancedContraption) ms.pushPose();
                        BogeyModelData wheel = wheels[side + 1];
                        wheel.translate(0, 0.88, ((double) side) * 2d)
                                .rotateX(wheelAngle)
                                .render(ms, light, vb);
                        if (!inInstancedContraption) ms.popPose();
                    }
                }
            } else {
                frame.translate(0, 0.375, 0).render(ms, light, vb);

                for (int side = -1; side < 2; side++) {
                    if (!inInstancedContraption) ms.pushPose();
                    BogeyModelData wheel = wheels[side + 1];
                    wheel.translate(0, 0.88, ((double) side) * 2d)
                            .rotateX(wheelAngle)
                            .render(ms, light, vb);
                    if (!inInstancedContraption) ms.popPose();
                }
            }
        }
    }

    public static class BackwardDF11GBogeyRenderer extends SimpleBogeyRenderer implements DF11GBogeyRendererBackwardDF11GBogeyRendererView {

        @Override
        public void render(
                CompoundTag bogeyData,
                float wheelAngle,
                PoseStack ms,
                int light,
                VertexConsumer vb,
                boolean inContraption) {

            Direction direction =
                    bogeyData.contains(Constants.BOGEY_ASSEMBLY_DIRECTION_KEY)
                            ? NBTHelper.readEnum(
                                    bogeyData,
                                    Constants.BOGEY_ASSEMBLY_DIRECTION_KEY,
                                    Direction.class)
                            : Direction.NORTH;

            wheelAngle = -wheelAngle;
            boolean inInstancedContraption = vb == null;

            BogeyModelData frame = getTransform(DF11G_FRAME, ms, inInstancedContraption);
            BogeyModelData[] wheels = getTransform(DF11G_WHEEL, ms, inInstancedContraption, 3);
            BogeySuspensionPartsRenderer.render(this, BogeySuspensionModels.DF11G, bogeyData, ms, light, vb);

            if (direction == Direction.SOUTH || direction == Direction.EAST) {
                if (inContraption) {
                    frame.rotateY(180).translate(0, 0.375, 0).render(ms, light, vb);

                    for (int side = -1; side < 2; side++) {
                        if (!inInstancedContraption) ms.pushPose();
                        BogeyModelData wheel = wheels[side + 1];
                        wheel.translate(0, 0.88, ((double) side) * 2d)
                                .rotateX(-wheelAngle)
                                .render(ms, light, vb);
                        if (!inInstancedContraption) ms.popPose();
                    }
                } else {
                    frame.translate(0, 0.375, 0).render(ms, light, vb);

                    for (int side = -1; side < 2; side++) {
                        if (!inInstancedContraption) ms.pushPose();
                        BogeyModelData wheel = wheels[side + 1];
                        wheel.translate(0, 0.88, ((double) side) * 2d)
                                .rotateX(wheelAngle)
                                .render(ms, light, vb);
                        if (!inInstancedContraption) ms.popPose();
                    }
                }
            } else {
                frame.rotateY(180).translate(0, 0.375, 0).render(ms, light, vb);

                for (int side = -1; side < 2; side++) {
                    if (!inInstancedContraption) ms.pushPose();
                    BogeyModelData wheel = wheels[side + 1];
                    wheel.translate(0, 0.88, ((double) side) * 2d)
                            .rotateX(-wheelAngle)
                            .render(ms, light, vb);
                    if (!inInstancedContraption) ms.popPose();
                }
            }
        }

        @Override
        public BogeySizes.BogeySize getSize() {
            return HexieBridges.createTrain().bogeySizesInit().backward();
        }

    }

}
