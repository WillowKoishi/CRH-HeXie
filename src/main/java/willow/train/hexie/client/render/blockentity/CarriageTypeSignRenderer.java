package willow.train.hexie.client.render.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import willow.train.hexie.blocks.signs.TrainPanelBlock;
import willow.train.hexie.blockentity.CarriageTypeSignEntity;

import java.util.List;

public class CarriageTypeSignRenderer implements BlockEntityRenderer<CarriageTypeSignEntity> {

    private final Font font;

    public CarriageTypeSignRenderer(BlockEntityRendererProvider.Context pContext) {
        this.font = pContext.getFont();
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
                        (p_173653_) -> {
                            List<FormattedCharSequence> list = this.font.split(p_173653_, 180);
                            return list.isEmpty() ? FormattedCharSequence.EMPTY : list.get(0);
                        });

        if (aformattedcharsequence.length == 0) return;

        pPoseStack.pushPose();

        pPoseStack.translate(0.5d, 0.5d, 0.5d);
        float f = -blockstate.getValue(TrainPanelBlock.FACING).getOpposite().toYRot();
        pPoseStack.mulPose(Axis.YP.rotationDegrees(f));
        pPoseStack.translate(0.0d, -0.3d, -0.42d);
        // width 1.2，height 0.5
        // scale 0.133

        float size0 = ((float) this.font.width(aformattedcharsequence[0])) * 0.13f; // 硬座车
        float size1 = ((float) this.font.width(aformattedcharsequence[1])) * 0.08f; // YINGZUOCHE
        float size2 = ((float) this.font.width(aformattedcharsequence[2])) * 0.23f; // YZ
        float size3 = ((float) this.font.width(aformattedcharsequence[3])) * 0.12f; // 25T
        float size4 = ((float) this.font.width(aformattedcharsequence[4])) * 0.30f; // 345674

        if (revert) {
            pPoseStack.translate(0.4d - size1 * 0.133f * 0.5f, 0.0, 0.0);
        } else {
            pPoseStack.translate(-0.4d, 0.0d, 0.0d);
        }

        pPoseStack.scale(0.133f * 0.55f, -0.133f * 0.55f, 0.133f * 0.55f); // standard size

        pPoseStack.scale(0.08f, 0.08f, 1.0f);
        renderText(
                aformattedcharsequence,
                1,
                0,
                0,
                pBlockEntity,
                pPoseStack,
                pBufferSource,
                pPackedLight); // 硬座车
        pPoseStack.scale(12.5f, 12.5f, 1.0f);

        pPoseStack.translate((size1 - size0) / 2, -1.7, 0);

        pPoseStack.scale(0.13f, 0.18f, 1.0f);
        renderText(
                aformattedcharsequence,
                0,
                0,
                0,
                pBlockEntity,
                pPoseStack,
                pBufferSource,
                pPackedLight); // YINGZUOCHE
        pPoseStack.scale(7.6923076924f, 5.555555555555f, 1.0f);

        if (revert) {
            pPoseStack.translate(-size2 - size4 - size3 - 1, 0, 0);
        } else {
            pPoseStack.translate(size1, 0, 0);
        }

        pPoseStack.scale(0.23f, 0.32f, 1.0f);
        renderText(
                aformattedcharsequence,
                2,
                0,
                0,
                pBlockEntity,
                pPoseStack,
                pBufferSource,
                pPackedLight); // YZ
        pPoseStack.scale(4.347826086956f, 3.1250f, 1.0f);

        pPoseStack.translate(size2, 1.6, 0.0);

        pPoseStack.scale(0.12f, 0.12f, 1.0f);
        renderText(
                aformattedcharsequence,
                3,
                0,
                0,
                pBlockEntity,
                pPoseStack,
                pBufferSource,
                pPackedLight); // 25k
        pPoseStack.scale(8.333333333333f, 8.333333333333f, 1.0f);

        pPoseStack.translate(size3 + 1, -1.6, 0.0);

        pPoseStack.scale(0.26f, 0.32f, 1.0f);
        renderText(
                aformattedcharsequence,
                4,
                0,
                0,
                pBlockEntity,
                pPoseStack,
                pBufferSource,
                pPackedLight); // 345674

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
        this.font.drawInBatch(
                formattedcharsequence,
                pX,
                pY,
                getDarkColor(pBlockEntity) * 2,
                false,
                pPoseStack.last().pose(),
                pBufferSource,
                net.minecraft.client.gui.Font.DisplayMode.NORMAL,
                0,
                pPackedLight);
    }

    private static int getDarkColor(CarriageTypeSignEntity pBlockEntity) {
        int i = pBlockEntity.getColor();
        int j = (int) ((double) net.minecraft.util.FastColor.ABGR32.red(i) * 0.4D);
        int k = (int) ((double) net.minecraft.util.FastColor.ABGR32.green(i) * 0.4D);
        int l = (int) ((double) net.minecraft.util.FastColor.ABGR32.blue(i) * 0.4D);
        return net.minecraft.util.FastColor.ABGR32.color(0, l, k, j);
    }
}
