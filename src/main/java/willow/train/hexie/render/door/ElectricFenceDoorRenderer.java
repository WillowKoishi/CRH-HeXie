package willow.train.hexie.render.door;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.BlockEntityViews.ElectricFenceDoorEntityView;
import willow.train.hexie.render.model.ElectricFenceDoorRightModel;

public class ElectricFenceDoorRenderer implements BlockEntityRenderer<BlockEntity> {
    ElectricFenceDoorRightModel modelR;

    public static float STEP = 0.005f;

    public ElectricFenceDoorRenderer(BlockEntityRendererProvider.Context pContext) {
        modelR =
                new ElectricFenceDoorRightModel(
                        pContext.bakeLayer(ElectricFenceDoorRightModel.RIGHT_LAYER));
    }

    @Override
    public void render(
            BlockEntity blockEntity,
            float pPartialTick,
            PoseStack pPoseStack,
            MultiBufferSource pBufferSource,
            int pPackedLight,
            int pPackedOverlay) {
        if (!(blockEntity instanceof ElectricFenceDoorEntityView pBlockEntity)) return;
        BlockState blockState = pBlockEntity.asBlockEntity().getBlockState();

        pPoseStack.pushPose();

        pPoseStack.translate(0.5d, -0.5d, 0.5d);
        float f = -blockState.getValue(HexieBridges.worldBlock().electricFenceDoor().facing()).getOpposite().toYRot();
        pPoseStack.mulPose(Axis.YP.rotationDegrees(f + 180));
        pPoseStack.translate(0.0d, 2d, -0.0d);
        pPoseStack.scale(1.0f, -1.0f, 1.0f);

        if (blockState.getValue(HexieBridges.worldBlock().electricFenceDoor().hinge()) == DoorHingeSide.RIGHT) {
            pPoseStack.scale(-1.0f, 1.0f, 1.0f);
        }
        if (blockState.getValue(HexieBridges.worldBlock().electricFenceDoor().open())
                && pBlockEntity.x_offset() < HexieBridges.worldBlock().electricFenceDoorEntity().maxOffset()) {
            pBlockEntity.x_offset(pBlockEntity.x_offset() + (STEP));
        } else if (blockState.getValue(HexieBridges.worldBlock().electricFenceDoor().open())) {
            pBlockEntity.x_offset(HexieBridges.worldBlock().electricFenceDoorEntity().maxOffset());
        }
        if (!blockState.getValue(HexieBridges.worldBlock().electricFenceDoor().open())
                && pBlockEntity.x_offset() > HexieBridges.worldBlock().electricFenceDoorEntity().minOffset()) {
            pBlockEntity.x_offset(pBlockEntity.x_offset() - STEP);
        } else if (!blockState.getValue(HexieBridges.worldBlock().electricFenceDoor().open())) {
            pBlockEntity.x_offset(HexieBridges.worldBlock().electricFenceDoorEntity().minOffset());
        }
        pPoseStack.translate(pBlockEntity.x_offset(), 0.0, 0.0);
        modelR.renderToBuffer(
                pPoseStack,
                pBufferSource.getBuffer(
                        RenderType.entityTranslucent(
                                ElectricFenceDoorRightModel.RIGHT_LAYER.getModel())),
                getModelLight(pBlockEntity, pPackedLight),
                pPackedOverlay);
        pPoseStack.popPose();
    }

    private static int getModelLight(ElectricFenceDoorEntityView blockEntity, int packedLight) {
        if (blockEntity.asBlockEntity().getLevel() == null) {
            return packedLight;
        }

        // The block itself is intentionally non-occluding, but the tall door model also reaches
        // into the block above. Sample there as well so the model does not inherit a zero light
        // value from the block position when the light engine treats that position as enclosed.
        int lightAbove = LevelRenderer.getLightColor(
                blockEntity.asBlockEntity().getLevel(), blockEntity.asBlockEntity().getBlockPos().above());

        int blockLight = Math.max(packedLight & 0xF0, lightAbove & 0xF0);
        int skyLight = Math.max(packedLight & 0xF00000, lightAbove & 0xF00000);
        return blockLight | skyLight;
    }
}
