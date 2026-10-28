package willow.train.hexie.client.render.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import willow.train.hexie.client.sign.SignLettering;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import willow.train.hexie.blocks.signs.TrainPanelBlock;
import willow.train.hexie.blockentity.CarriageTypeSignEntity;

import willow.train.hexie.client.sign.CarriageMarkingLayout;
import willow.train.hexie.client.sign.SignTextRenderer;
import net.minecraft.resources.ResourceLocation;

public class CarriageTypeSignRenderer implements BlockEntityRenderer<CarriageTypeSignEntity> {

public CarriageTypeSignRenderer(BlockEntityRendererProvider.Context pContext) {
    }

    /**
     * Renderer 中最重要的一个方法，即渲染方法。每一帧都会调用
     *
     * @param pBlockEntity 对应的BlockEntity
     * @param pPartialTick tick
     * @param pPoseStack 渲染栈
     * @param pBufferSource -
     * @param pPackedLight 光照
     * @param pPackedOverlay -
     */
    @Override
    public void render(
            CarriageTypeSignEntity pBlockEntity,
            float pPartialTick,
            PoseStack pPoseStack,
            MultiBufferSource pBufferSource,
            int pPackedLight,
            int pPackedOverlay) {
        BlockState blockstate = pBlockEntity.getBlockState();
        boolean revert = blockstate.getValue(BlockStateProperties.OPEN);

        FormattedCharSequence[] aformattedcharsequence =
                pBlockEntity.getRenderMessages(
                        net.minecraft.network.chat.Component::getVisualOrderText);

        if (aformattedcharsequence.length < 5) return;

        pPoseStack.pushPose();

        pPoseStack.translate(0.5d, 0.5d, 0.5d);
        float f = -blockstate.getValue(TrainPanelBlock.FACING).getOpposite().toYRot();
        pPoseStack.mulPose(Axis.YP.rotationDegrees(f));
        pPoseStack.translate(0.0d, -0.3d, -0.42d);
        // Measure the exact ink width used for drawing; never round each field to GUI pixels.
        float[] widths = new float[5];
        for (int i = 0; i < widths.length; i++)
            widths[i] = SignTextRenderer.width(profile(i), SignLettering.text(aformattedcharsequence[i]), 8, 180);
        var layout = CarriageMarkingLayout.arrange(widths, revert);
        float unit = .133f * .55f;
        // Both orientations share one layout. Reversed signs keep their right edge anchored.
        pPoseStack.translate(revert ? .4f - layout.width() * unit : -.4f, 0, 0);
        pPoseStack.scale(unit, -unit, unit);
        pPoseStack.translate(0, -1.7f, 0);
        for (int i = 0; i < layout.parts().length; i++) {
            var part = layout.parts()[i];
            pPoseStack.pushPose();
            pPoseStack.translate(part.x(), part.y(), 0);
            pPoseStack.scale(part.scaleX(), part.scaleY(), 1);
            renderText(aformattedcharsequence, i, 0, 0, pBlockEntity, pPoseStack, pBufferSource, pPackedLight);
            pPoseStack.popPose();
        }

        pPoseStack.popPose();
    }

    private void renderText(
            FormattedCharSequence[] aformattedcharsequence,
            int index,
            int pX,
            int pY,
            CarriageTypeSignEntity pBlockEntity,
            PoseStack pPoseStack,
            MultiBufferSource pBufferSource,
            int pPackedLight) {
        FormattedCharSequence formattedcharsequence = aformattedcharsequence[index];
        SignLettering.draw(profile(index),
                formattedcharsequence, pX, pY, getDarkColor(pBlockEntity) * 2, pPoseStack, pBufferSource, pPackedLight);
    }

    private static ResourceLocation profile(int index) {
        return index == 0 ? SignLettering.VEHICLE_CHINESE
                : index == 1 ? SignLettering.VEHICLE_PINYIN : SignLettering.VEHICLE_CODE;
    }

    private static int getDarkColor(CarriageTypeSignEntity pBlockEntity) {
        int i = pBlockEntity.getColor();
        int j = (int) ((double) net.minecraft.util.FastColor.ABGR32.red(i) * 0.4D);
        int k = (int) ((double) net.minecraft.util.FastColor.ABGR32.green(i) * 0.4D);
        int l = (int) ((double) net.minecraft.util.FastColor.ABGR32.blue(i) * 0.4D);
        return net.minecraft.util.FastColor.ABGR32.color(0, l, k, j);
    }
}
