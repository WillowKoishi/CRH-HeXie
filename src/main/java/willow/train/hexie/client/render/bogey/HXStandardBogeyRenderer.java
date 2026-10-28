package willow.train.hexie.client.render.bogey;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import willow.train.hexie.datafix.Constants;
import willow.train.hexie.init.bogeys.BogeySizesInit;

import static willow.train.hexie.init.client.PartialModelInit.*;

public class HXStandardBogeyRenderer {

    public static class SmallHXStandardBogeyRenderer extends SimpleBogeyRenderer {

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

    public static class PK209PBogeyRender extends SimpleBogeyRenderer {
        

        @Override
        public BogeySizes.BogeySize getSize() {
            return BogeySizesInit.PK209P;
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

    public static class PK209PNoMotorBogeyRender extends SimpleBogeyRenderer {
        

        @Override
        public BogeySizes.BogeySize getSize() {
            return BogeySizesInit.PK209P_NO_MOTOR;
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

    public static class PK209PBackwardBogeyRender extends SimpleBogeyRenderer {
        

        @Override
        public BogeySizes.BogeySize getSize() {
            return BogeySizesInit.PK209P_BACKWARD;
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

    public static class SW220KBogeyRenderer extends SimpleBogeyRenderer {

        

        @Override
        public BogeySizes.BogeySize getSize() {
            return BogeySizesInit.SW220K;
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

            for (int side : Iterate.positiveAndNegative) {
                if (!inInstancedContraption) ms.pushPose();
                BogeyModelData wheel = wheels[(side + 1) / 2];
                wheel.translate(0, 0.805, ((double) side) * 1.25d).rotateX(wheelAngle);
                wheel.render(ms, light, vb);
                if (!inInstancedContraption) ms.popPose();
            }
        }
    }

    public static class SW220KBackwardBogeyRenderer extends SimpleBogeyRenderer {

        

        @Override
        public BogeySizes.BogeySize getSize() {
            return BogeySizesInit.SW220K_BACKWARD;
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

    public static class SW160BogeyRenderer extends SimpleBogeyRenderer {

        

        @Override
        public BogeySizes.BogeySize getSize() {
            return BogeySizesInit.SW160;
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

            for (int side : Iterate.positiveAndNegative) {
                if (!inInstancedContraption) ms.pushPose();
                BogeyModelData wheel = wheels[(side + 1) / 2];
                wheel.translate(0, 0.805, ((double) side) * 1.28d).rotateX(wheelAngle);
                wheel.render(ms, light, vb);
                if (!inInstancedContraption) ms.popPose();
            }
        }
    }
    public static class DT206BogeyRenderer extends SimpleBogeyRenderer {
        // Exported wheel tread radius: 6.88/16; axle centres: Z = +/-20/16.
        private static final double AXLE_HEIGHT = 5.4816 / 16.0 + 0.43;
        @Override public BogeySizes.BogeySize getSize() { return BogeySizesInit.DT206; }
        @Override public void render(CompoundTag data, float wheelAngle, PoseStack pose,
                int light, VertexConsumer buffer, boolean inContraption) {
            boolean instanced = buffer == null;
            getTransform(DT206_FRAME, pose, instanced)
                    .translate(0, AXLE_HEIGHT + 4.9 / 16.0, 0).render(pose, light, buffer);
            BogeyModelData[] wheels = getTransform(DT206_WHEEL, pose, instanced, 2);
            for (int side : Iterate.positiveAndNegative) {
                if (!instanced) pose.pushPose();
                wheels[(side + 1) / 2].translate(0, AXLE_HEIGHT, side * 1.25)
                        .rotateX(wheelAngle).render(pose, light, buffer);
                if (!instanced) pose.popPose();
            }
        }
    }
}
