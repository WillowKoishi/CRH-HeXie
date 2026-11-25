package willow.train.hexie.render.bogey;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import static willow.train.hexie.render.init.PartialModelInit.*;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.render.BogeyRenderBridge.HXStandardBogeyRendererPK209PBackwardBogeyRenderView;
import willow.train.hexie.bridge.render.BogeyRenderBridge.HXStandardBogeyRendererPK209PBogeyRenderView;
import willow.train.hexie.bridge.render.BogeyRenderBridge.HXStandardBogeyRendererPK209PNoMotorBogeyRenderView;
import willow.train.hexie.core.datafix.Constants;

// split from willow/train/hexie/client/render/bogey/HXStandardBogeyRenderer.java, lines 75-269
public final class PK209PBogeyRenderers {
    public static class PK209PBogeyRender extends SimpleBogeyRenderer implements HXStandardBogeyRendererPK209PBogeyRenderView {
        

        @Override
        public BogeySizes.BogeySize getSize() {
            return HexieBridges.createTrain().bogeySizesInit().pk209p();
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
            // 转向架架体
            BogeyModelData main = getTransform(PK209P_MAIN, ms, inInstancedContraption);
            // 发电轮对
            BogeyModelData wheel = getTransform(PK209P_WHEEL, ms, inInstancedContraption);
            // 普通轮对
            BogeyModelData wheel2 = getTransform(PK209P_WHEEL2, ms, inInstancedContraption);
            // 发电机小轮
            BogeyModelData motorWheel = getTransform(PK209P_MOTORWHEEL, ms, inInstancedContraption);

            BogeySuspensionPartsRenderer.render(this, BogeySuspensionModels.PK209P, bogeyData, ms, light, vb,
                    !inContraption && (direction == Direction.SOUTH || direction == Direction.EAST) ? 180 : 0);

            if (direction == Direction.SOUTH || direction == Direction.EAST) {
                if (inContraption) {
                    main.translate(0, 0.91, 0).render(ms, light, vb);
                    wheel.translate(0, 0.8, 1.2).rotateX(wheelAngle).render(ms, light, vb);
                    wheel2.translate(0, 0.8, -1.2).rotateX(wheelAngle).render(ms, light, vb);
                    motorWheel
                            .translate(1.117, 0.82, 2.165)
                            .rotateX(wheelAngle * 3.256)
                            .render(ms, light, vb);
                } else {
                    main.rotateY(180).translate(0, 0.91, 0).render(ms, light, vb);
                    wheel.translate(0, 0.8, -1.2)
                            .rotateY(180)
                            .rotateX(-wheelAngle)
                            .render(ms, light, vb);
                    wheel2.translate(0, 0.8, 1.2)
                            .rotateY(180)
                            .rotateX(-wheelAngle)
                            .render(ms, light, vb);
                    motorWheel
                            .translate(-1.117, 0.82, -2.165)
                            .rotateY(180)
                            .rotateX(-wheelAngle * 3.256)
                            .render(ms, light, vb);
                }
            } else {
                main.translate(0, 0.91, 0).render(ms, light, vb);
                wheel.translate(0, 0.8, 1.2).rotateX(wheelAngle).render(ms, light, vb);
                wheel2.translate(0, 0.8, -1.2).rotateX(wheelAngle).render(ms, light, vb);
                motorWheel
                        .translate(1.117, 0.82, 2.165)
                        .rotateX(wheelAngle * 3.256)
                        .render(ms, light, vb);
            }
        }
    }

    public static class PK209PNoMotorBogeyRender extends SimpleBogeyRenderer implements HXStandardBogeyRendererPK209PNoMotorBogeyRenderView {
        

        @Override
        public BogeySizes.BogeySize getSize() {
            return HexieBridges.createTrain().bogeySizesInit().pk209pNoMotor();
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
            // 无发电机转向架架体
            BogeyModelData main = getTransform(PK209P_NO_MOTOR, ms, inInstancedContraption);
            BogeySuspensionPartsRenderer.render(this, BogeySuspensionModels.PK209P, bogeyData, ms, light, vb);
            // 普通轮对
            BogeyModelData[] wheel2s = getTransform(PK209P_WHEEL2, ms, inInstancedContraption, 2);

            main.translate(0, 0.91, 0).render(ms, light, vb);

            for (int side : Iterate.positiveAndNegative) {
                if (!inInstancedContraption) ms.pushPose();
                BogeyModelData wheel = wheel2s[(side + 1) / 2];
                wheel.translate(0, 0.8, ((double) side) * 1.2d).rotateX(wheelAngle);
                wheel.render(ms, light, vb);
                if (!inInstancedContraption) ms.popPose();
            }
        }
    }

    public static class PK209PBackwardBogeyRender extends SimpleBogeyRenderer implements HXStandardBogeyRendererPK209PBackwardBogeyRenderView {
        

        @Override
        public BogeySizes.BogeySize getSize() {
            return HexieBridges.createTrain().bogeySizesInit().pk209pBackward();
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
            // 转向架架体
            BogeyModelData main = getTransform(PK209P_MAIN, ms, inInstancedContraption);
            // 发电轮对
            BogeyModelData wheel = getTransform(PK209P_WHEEL, ms, inInstancedContraption);
            // 普通轮对
            BogeyModelData wheel2 = getTransform(PK209P_WHEEL2, ms, inInstancedContraption);
            // 发电机小轮
            BogeyModelData motorWheel = getTransform(PK209P_MOTORWHEEL, ms, inInstancedContraption);

            BogeySuspensionPartsRenderer.render(this, BogeySuspensionModels.PK209P, bogeyData, ms, light, vb,
                    !inContraption && (direction == Direction.SOUTH || direction == Direction.EAST) ? 0 : 180);

            // 反方向渲染
            if (direction == Direction.SOUTH || direction == Direction.EAST) {
                if (inContraption) {
                    main.rotateY(180).translate(0, 0.91, 0).render(ms, light, vb);
                    wheel.translate(0, 0.8, -1.2)
                            .rotateY(180)
                            .rotateX(-wheelAngle)
                            .render(ms, light, vb);
                    wheel2.translate(0, 0.8, 1.2)
                            .rotateY(180)
                            .rotateX(-wheelAngle)
                            .render(ms, light, vb);
                    motorWheel
                            .translate(-1.117, 0.82, -2.165)
                            .rotateY(180)
                            .rotateX(-wheelAngle * 3.256)
                            .render(ms, light, vb);
                } else {
                    main.translate(0, 0.91, 0).render(ms, light, vb);
                    wheel.translate(0, 0.8, 1.2).rotateX(wheelAngle).render(ms, light, vb);
                    wheel2.translate(0, 0.8, -1.2).rotateX(wheelAngle).render(ms, light, vb);
                    motorWheel
                            .translate(1.117, 0.82, 2.165)
                            .rotateX(wheelAngle * 3.256)
                            .render(ms, light, vb);
                }
            } else {
                main.rotateY(180).translate(0, 0.91, 0).render(ms, light, vb);
                wheel.translate(0, 0.8, -1.2)
                        .rotateY(180)
                        .rotateX(-wheelAngle)
                        .render(ms, light, vb);
                wheel2.translate(0, 0.8, 1.2)
                        .rotateY(180)
                        .rotateX(-wheelAngle)
                        .render(ms, light, vb);
                motorWheel
                        .translate(-1.117, 0.82, -2.165)
                        .rotateY(180)
                        .rotateX(-wheelAngle * 3.256)
                        .render(ms, light, vb);
            }
        }
    }
}
