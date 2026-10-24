package willow.train.hexie.client.render.blockentity;

import static willow.train.hexie.client.model.LacqueredBoardLogo.LAYER_A;

import com.mojang.blaze3d.vertex.*;
import org.joml.Matrix4f;
import com.mojang.math.Axis;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.state.BlockState;

import willow.train.hexie.Main;
import willow.train.hexie.blocks.signs.TrainNoBlock;
import willow.train.hexie.blockentity.LacqueredBoardEntity;
import willow.train.hexie.client.model.LacqueredBoardLogo;

import java.util.List;

public class LacqueredBoardEntityRenderer implements BlockEntityRenderer<LacqueredBoardEntity> {

    private final LacqueredBoardLogo logoModel;
    RenderType renderType;
    public static final ResourceLocation STICKER =
            ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "textures/laquered_board/stick.png");


    private final Font font;

    public LacqueredBoardEntityRenderer(BlockEntityRendererProvider.Context pContext) {
        logoModel = new LacqueredBoardLogo<>(pContext.bakeLayer(LAYER_A));
        font = pContext.getFont();

        // 这一堆是用来加载动态贴图的
        this.renderType = RenderType.text(STICKER);
    }

    @Override
    public void render(
            LacqueredBoardEntity pBlockEntity,
            float pPartialTick,
            PoseStack pPoseStack,
            MultiBufferSource pBufferSource,
            int pPackedLight,
            int pPackedOverlay) {
        BlockState pState = pBlockEntity.getBlockState();

        pPoseStack.pushPose();

        FormattedCharSequence[] formattedcharsequence =
                pBlockEntity.getRenderMessages(
                        (p_173653_) -> {
                            List<FormattedCharSequence> list = this.font.split(p_173653_, 180);
                            return list.isEmpty() ? FormattedCharSequence.EMPTY : list.get(0);
                        }, 6);

        pPoseStack.translate(0.5d, -0.5d, 0.5d);
        float f = -pState.getValue(TrainNoBlock.FACING).getOpposite().toYRot();
        pPoseStack.mulPose(Axis.YP.rotationDegrees(f));
        pPoseStack.translate(-0.5d, 0.5d, -0.42d);
        pPoseStack.scale(1, -1, 1);

        pPoseStack.translate(pBlockEntity.getXOffset(), 0.0, 0.0); // offset

        pPoseStack.translate(0.4375, -2.3625, 0.0);
        pPoseStack.translate(0.00625, 0.00625 + 0.15, 0.0);
        pPoseStack.scale(0.9f, 0.9f, 1.0f);
        logoModel.renderToBuffer(
                pPoseStack,
                pBufferSource.getBuffer(RenderType.entityTranslucent(LAYER_A.getModel())),
                pPackedLight,
                pPackedOverlay);
        pPoseStack.scale(1 / 0.9f, 1 / 0.9f, 1.0f);
        pPoseStack.translate(-0.00625, -0.00625 - 0.15, 0.0);
        pPoseStack.translate(-0.4375, 2.3625, 0.0);

        pPoseStack.translate(0.0, -0.91, -0.01d);

        int[] beltRGBA = pBlockEntity.getRGBAColor(pBlockEntity.getBackGroundColor());

        pPoseStack.scale(0.2f, 0.2f, 0.2f); // standard size

        pPoseStack.scale(1.0f, 0.17955f, 1.0f);
        pPoseStack.translate(0.0, 0.9f, 0.0f);
        renderBelt(pPoseStack, pBufferSource, (int) (pPackedLight * 0.8), 255, 255, 255, 255);
        pPoseStack.translate(0.0, -0.9f, 0.0f);

        pPoseStack.translate(0, 0, 0.015f);

        switch (pBlockEntity.getBoardType()) {
            case 0:
                // 条带1
                pPoseStack.translate(0, 3.9, 0.0);
                pPoseStack.scale(0.44f, 0.3f, 1.0f);
                pPoseStack.translate(0.0, 0.9f, 0.0f);
                renderBelt(
                        pPoseStack,
                        pBufferSource,
                        (int) (pPackedLight * 0.8),
                        beltRGBA[0],
                        beltRGBA[1],
                        beltRGBA[2],
                        beltRGBA[3]);
                pPoseStack.translate(0.0, -0.9f, 0.0f);
                pPoseStack.scale(1 / 0.44f, 1.0f, 1.0f);

                // 条带2
                pPoseStack.translate((1 - 0.44) * 5, 0.0, 0.0);
                pPoseStack.scale(0.44f, 1.0f, 1.0f);
                pPoseStack.translate(0.0, 0.9f, 0.0f);
                renderBelt(
                        pPoseStack,
                        pBufferSource,
                        (int) (pPackedLight * 0.8),
                        beltRGBA[0],
                        beltRGBA[1],
                        beltRGBA[2],
                        beltRGBA[3]);
                pPoseStack.translate(0.0, -0.9f, 0.0f);
                pPoseStack.scale(1 / 0.44f, 1 / 0.3f, 1.0f);
                pPoseStack.translate(-(1 - 0.44) * 5, -3.9, 0.01);

                // 字1
                pPoseStack.scale(1.0f, 1 / 0.17955f, 1.0f);
                int length1 = this.font.width(formattedcharsequence[1]);
                pPoseStack.translate((2.2 - length1 * 0.05) / 2, 0.3f - 0.1f, 0.0f);
                pPoseStack.scale(0.05f, 0.05f, 1.0f);
                renderText(
                        formattedcharsequence[1],
                        0,
                        0,
                        pBlockEntity,
                        pPoseStack,
                        pBufferSource,
                        pPackedLight);
                pPoseStack.scale(20f, 20f, 1.0f);
                pPoseStack.translate(-(2.2 - length1 * 0.05) / 2, 0f, 0f);

                // 字2
                int length2 = this.font.width(formattedcharsequence[2]);
                pPoseStack.translate(2.8 + (2.2 - length2 * 0.05) / 2, 0.0f, 0.0f);
                pPoseStack.scale(0.05f, 0.05f, 1.0f);
                renderText(
                        formattedcharsequence[2],
                        0,
                        0,
                        pBlockEntity,
                        pPoseStack,
                        pBufferSource,
                        pPackedLight);
                pPoseStack.scale(20f, 20f, 1.0f);
                pPoseStack.translate(-(2.8 + (2.2 - length2 * 0.05) / 2), 0.0f, 0.0f);

                // 拼音1
                int length3 = this.font.width(formattedcharsequence[3]);
                pPoseStack.translate((2.2 - length3 * 0.023) / 2, 0.5 + 0.05f, 0.0f);
                pPoseStack.scale(0.023f, 0.023f, 1.0f);
                renderWhite(
                        formattedcharsequence[3],
                        0,
                        0,
                        pBlockEntity,
                        pPoseStack,
                        pBufferSource,
                        pPackedLight);
                pPoseStack.scale(1 / 0.023f, 1 / 0.023f, 1.0f);
                pPoseStack.translate(-(2.2 - length3 * 0.023) / 2, 0.0f, 0.0f);

                // 拼音2
                int length4 = this.font.width(formattedcharsequence[4]);
                pPoseStack.translate(3.9 - length4 * 0.0115, 0.0f, 0.0f);
                pPoseStack.scale(0.023f, 0.023f, 1.0f);
                renderWhite(
                        formattedcharsequence[4],
                        0,
                        0,
                        pBlockEntity,
                        pPoseStack,
                        pBufferSource,
                        pPackedLight);
                pPoseStack.scale(1 / 0.023f, 1 / 0.023f, 1.0f);
                pPoseStack.translate(-3.9 + length4 * 0.0115, -0.8f, 0.0f);

                // 车种
                int lenght5 = this.font.width(formattedcharsequence[5]);
                pPoseStack.translate(2.5 - lenght5 * 0.0075, 0.3f, 0.0f);
                pPoseStack.scale(0.015f, 0.015f, 1.0f);
                renderText(
                        formattedcharsequence[5],
                        0,
                        0,
                        pBlockEntity,
                        pPoseStack,
                        pBufferSource,
                        pPackedLight);
                pPoseStack.scale(1 / 0.015f, 1 / 0.015f, 1.0f);
                pPoseStack.translate(-2.5 + lenght5 * 0.0075, 0.68f, 0.0f);

                // 车次
                int lenght6 = this.font.width(formattedcharsequence[6]);
                pPoseStack.translate(2.5 - lenght6 * 0.004, 0.0f, 0.0f);
                pPoseStack.scale(0.008f, 0.008f, 1.0f);
                renderText(
                        formattedcharsequence[6],
                        0,
                        0,
                        pBlockEntity,
                        pPoseStack,
                        pBufferSource,
                        pPackedLight);
                pPoseStack.scale(1 / 0.008f, 1 / 0.008f, 1.0f);
                pPoseStack.translate(-2.5 + lenght6 * 0.004, 0.0f, 0.0f);
                break;
            default:
                // default 就是初始状态，不显示任何东西
                break;
        }

        pPoseStack.popPose();
    }

    private void renderText(
            FormattedCharSequence formattedcharsequence,
            int pX,
            int pY,
            LacqueredBoardEntity pBlockEntity,
            PoseStack pPoseStack,
            MultiBufferSource pBufferSource,
            int pPackedLight) {
        this.font.drawInBatch(
                formattedcharsequence,
                pX,
                pY,
                getForgroundColor(pBlockEntity) * 2,
                false,
                pPoseStack.last().pose(),
                pBufferSource,
                net.minecraft.client.gui.Font.DisplayMode.NORMAL,
                0,
                pPackedLight);
    }

    private static int getForgroundColor(LacqueredBoardEntity pBlockEntity) {
        int i = pBlockEntity.getForGroundColor();
        int j = (int) ((double) net.minecraft.util.FastColor.ABGR32.red(i) * 0.4D);
        int k = (int) ((double) net.minecraft.util.FastColor.ABGR32.green(i) * 0.4D);
        int l = (int) ((double) net.minecraft.util.FastColor.ABGR32.blue(i) * 0.4D);
        return net.minecraft.util.FastColor.ABGR32.color(0, l, k, j);
    }

    private static int getBeltForgroundColor(LacqueredBoardEntity pBlockEntity) {
        int i = pBlockEntity.getBeltForGroundColor();
        int j = (int) ((double) net.minecraft.util.FastColor.ABGR32.red(i) * 0.4D);
        int k = (int) ((double) net.minecraft.util.FastColor.ABGR32.green(i) * 0.4D);
        int l = (int) ((double) net.minecraft.util.FastColor.ABGR32.blue(i) * 0.4D);
        return net.minecraft.util.FastColor.ABGR32.color(0, l, k, j);
    }

    private void renderWhite(
            FormattedCharSequence formattedcharsequence,
            int pX,
            int pY,
            LacqueredBoardEntity pBlockEntity,
            PoseStack pPoseStack,
            MultiBufferSource pBufferSource,
            int pPackedLight) {
        this.font.drawInBatch(
                formattedcharsequence,
                pX,
                pY,
                getBeltForgroundColor(pBlockEntity) * 2,
                false,
                pPoseStack.last().pose(),
                pBufferSource,
                net.minecraft.client.gui.Font.DisplayMode.NORMAL,
                0,
                pPackedLight);
    }

    private void renderBelt(
            PoseStack pPoseStack,
            MultiBufferSource pBufferSource,
            int pPackedLight,
            int r,
            int g,
            int b,
            int a) {
        Matrix4f matrix4f = pPoseStack.last().pose();
        VertexConsumer vertexconsumer = pBufferSource.getBuffer(this.renderType);
        vertexconsumer
                .addVertex(matrix4f, 0.0F, 5.0F, 0)
                .setColor(r, g, b, a)
                .setUv(0.0F, 1.0F)
                .setLight(pPackedLight);
        vertexconsumer
                .addVertex(matrix4f, 5.0F, 5.0F, 0)
                .setColor(r, g, b, a)
                .setUv(1.0F, 1.0F)
                .setLight(pPackedLight);
        vertexconsumer
                .addVertex(matrix4f, 5.0F, 0.0F, 0)
                .setColor(r, g, b, a)
                .setUv(1.0F, 0.0F)
                .setLight(pPackedLight);
        vertexconsumer
                .addVertex(matrix4f, 0.0F, 0.0F, 0)
                .setColor(r, g, b, a)
                .setUv(0.0F, 0.0F)
                .setLight(pPackedLight);
    }
}
