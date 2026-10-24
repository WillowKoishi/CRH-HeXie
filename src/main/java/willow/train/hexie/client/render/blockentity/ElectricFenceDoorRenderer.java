package willow.train.hexie.client.render.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;

import willow.train.hexie.blocks.slidingdoor.ElectricFenceDoor;
import willow.train.hexie.blockentity.ElectricFenceDoorEntity;
import willow.train.hexie.client.model.ElectricFenceDoorRightModel;

public class ElectricFenceDoorRenderer implements BlockEntityRenderer<ElectricFenceDoorEntity> {
    ElectricFenceDoorRightModel modelR;

    public static float STEP = 0.005f;

    public ElectricFenceDoorRenderer(BlockEntityRendererProvider.Context pContext) {
        modelR =
                new ElectricFenceDoorRightModel(
                        pContext.bakeLayer(ElectricFenceDoorRightModel.RIGHT_LAYER));
    }

    @Override
    public void render(
            ElectricFenceDoorEntity pBlockEntity,
            float pPartialTick,
            PoseStack pPoseStack,
            MultiBufferSource pBufferSource,
            int pPackedLight,
            int pPackedOverlay) {
        BlockState blockState = pBlockEntity.getBlockState();

        pPoseStack.pushPose();

        pPoseStack.translate(0.5d, -0.5d, 0.5d);
        float f = -blockState.getValue(ElectricFenceDoor.FACING).getOpposite().toYRot();
        pPoseStack.mulPose(Axis.YP.rotationDegrees(f + 180));
        pPoseStack.translate(0.0d, 2d, -0.0d);
        pPoseStack.scale(1.0f, -1.0f, 1.0f);

        if (blockState.getValue(ElectricFenceDoor.HINGE) == DoorHingeSide.RIGHT) {
            pPoseStack.scale(-1.0f, 1.0f, 1.0f);
        }
        if (blockState.getValue(ElectricFenceDoor.OPEN)
                && pBlockEntity.x_offset < ElectricFenceDoorEntity.MAX_OFFSET) {
            pBlockEntity.x_offset += STEP;
        } else if (blockState.getValue(ElectricFenceDoor.OPEN)) {
            pBlockEntity.x_offset = ElectricFenceDoorEntity.MAX_OFFSET;
        }
        if (!blockState.getValue(ElectricFenceDoor.OPEN)
                && pBlockEntity.x_offset > ElectricFenceDoorEntity.MIN_OFFSET) {
            pBlockEntity.x_offset -= STEP;
        } else if (!blockState.getValue(ElectricFenceDoor.OPEN)) {
            pBlockEntity.x_offset = ElectricFenceDoorEntity.MIN_OFFSET;
        }
        pPoseStack.translate(pBlockEntity.x_offset, 0.0, 0.0);
        modelR.renderToBuffer(
                pPoseStack,
                pBufferSource.getBuffer(
                        RenderType.entityTranslucent(
                                ElectricFenceDoorRightModel.RIGHT_LAYER.getModel())),
                getModelLight(pBlockEntity, pPackedLight),
                pPackedOverlay);
        pPoseStack.popPose();
    }

    private static int getModelLight(ElectricFenceDoorEntity blockEntity, int packedLight) {
        if (blockEntity.getLevel() == null) {
            return packedLight;
        }

        // The block itself is intentionally non-occluding, but the tall door model also reaches
        // into the block above. Sample there as well so the model does not inherit a zero light
        // value from the block position when the light engine treats that position as enclosed.
        int lightAbove = LevelRenderer.getLightColor(
                blockEntity.getLevel(), blockEntity.getBlockPos().above());

        int blockLight = Math.max(packedLight & 0xF0, lightAbove & 0xF0);
        int skyLight = Math.max(packedLight & 0xF00000, lightAbove & 0xF00000);
        return blockLight | skyLight;
    }
}
