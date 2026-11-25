package willow.train.hexie.render.blockentity.sign;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.world.BlockEntityViews.CarriageNoSignEntityView;
import willow.train.hexie.render.model.CarriageNoSignModel;
import willow.train.hexie.render.sign.SignLettering;

// 渲染器类

public class CarriageNoSignRenderer implements BlockEntityRenderer<BlockEntity> {
    private final CarriageNoSignModel model; // 模型

/**
     * 为了调用到我们的模型，这个构造体应该改成这个样子 调用 BlockEntityRendererProvider.Context pContext 之后获得模型就像下面这样写就好了
     *
     * @param pContext -
     */
    public CarriageNoSignRenderer(BlockEntityRendererProvider.Context pContext) {
        model =
                new CarriageNoSignModel(
                        pContext.bakeLayer(CarriageNoSignModel.CARRIAGE_NO_SIGN_MODEL_LAYER));
    }

    /**
     * render() 方法负责渲染，这里的 this.model.render()...这个方法负责渲染我们的模型
     *
     * @param pBlockEntity -
     * @param pPartialTick -
     * @param pPoseStack -
     * @param pBufferSource -
     * @param pPackedLight -
     * @param pPackedOverlay -
     */
    @Override
    public void render(
            BlockEntity blockEntity,
            float pPartialTick,
            PoseStack pPoseStack,
            MultiBufferSource pBufferSource,
            int pPackedLight,
            int pPackedOverlay) {
        if (!(blockEntity instanceof CarriageNoSignEntityView pBlockEntity)) return;
        boolean isLeftSide = pBlockEntity.isLeftSide();
        BlockState blockState = pBlockEntity.asBlockEntity().getBlockState();

        FormattedCharSequence formattedcharsequence =
                pBlockEntity.getRenderMessages(
                        net.minecraft.network.chat.Component::getVisualOrderText);

        pPoseStack.pushPose();

        pPoseStack.translate(0.5d, -0.5d, 0.5d);
        float f = -blockState.getValue(HorizontalDirectionalBlock.FACING).getOpposite().toYRot();
        pPoseStack.mulPose(Axis.YP.rotationDegrees(f));
        pPoseStack.translate(0.0, -0.9d, -0.43d);

        if (isLeftSide) {
            pPoseStack.translate(-0.45d, 0.0, 0.0);
        } else {
            pPoseStack.translate(0.45d, 0.0d, 0.0d);
        }

        pPoseStack.scale(1.5f, 1.5f, 1.5f);
        if (isLeftSide) {
            pPoseStack.translate(0.03f, -0.55f, 0.0f);
        } else {
            pPoseStack.translate(-0.03f, -0.55f, 0.0f);
        }

        // 这句话是渲染模型用的
        this.model.renderToBuffer(
                pPoseStack,
                pBufferSource.getBuffer(
                        RenderType.entitySolid(
                                CarriageNoSignModel.CARRIAGE_NO_SIGN_MODEL_LAYER.getModel())),
                pPackedLight,
                pPackedOverlay);
        if (isLeftSide) {
            pPoseStack.translate(-0.03f, 0.55f, 0.0f);
        } else {
            pPoseStack.translate(0.03f, 0.55f, 0.0f);
        }

        pPoseStack.scale(0.6666666667f, 0.6666666667f, 0.6666666667f);
        pPoseStack.translate(0.0, 1.48d, 0.001d);

        if (isLeftSide) {
            pPoseStack.translate(
                    (0.125f - ((float) SignLettering.width(SignLettering.CARRIAGE_NUMBER, formattedcharsequence) * 0.133f * 0.08f)) / 2
                            - 0.02,
                    0.0d,
                    0.001d);
        } else {
            pPoseStack.translate(
                    -(0.125f - ((float) SignLettering.width(SignLettering.CARRIAGE_NUMBER, formattedcharsequence) * 0.133f * 0.08f))
                                    / 2
                            + 0.02,
                    0.0d,
                    0.001d);
            pPoseStack.translate(
                    -((float) SignLettering.width(SignLettering.CARRIAGE_NUMBER, formattedcharsequence) * 0.133f * 0.08f), 0.0d, 0.0d);
        }
        pPoseStack.scale(0.133f, -0.133f, 0.133f); // standard size
        pPoseStack.scale(0.08f, 0.08f, 0.08f);
        renderText(
                formattedcharsequence, 0, 0, pBlockEntity, pPoseStack, pBufferSource, pPackedLight);

        pPoseStack.popPose();
    }

    private void renderText(
            FormattedCharSequence formattedcharsequence,
            int pX,
            int pY,
            CarriageNoSignEntityView pBlockEntity,
            PoseStack pPoseStack,
            MultiBufferSource pBufferSource,
            int pPackedLight) {
        SignLettering.draw(SignLettering.CARRIAGE_NUMBER, formattedcharsequence, pX, pY, getDarkColor(pBlockEntity) * 2, pPoseStack, pBufferSource, pPackedLight);
    }

    private static int getDarkColor(CarriageNoSignEntityView pBlockEntity) {
        int i = pBlockEntity.getColor();
        int j = (int) ((double) net.minecraft.util.FastColor.ABGR32.red(i) * 0.4D);
        int k = (int) ((double) net.minecraft.util.FastColor.ABGR32.green(i) * 0.4D);
        int l = (int) ((double) net.minecraft.util.FastColor.ABGR32.blue(i) * 0.4D);
        return net.minecraft.util.FastColor.ABGR32.color(0, l, k, j);
    }

    @Override
    public boolean shouldRenderOffScreen(BlockEntity pBlockEntity) {
        return BlockEntityRenderer.super.shouldRenderOffScreen(pBlockEntity);
    }

    @Override
    public int getViewDistance() {
        return BlockEntityRenderer.super.getViewDistance();
    }

    @Override
    public boolean shouldRender(BlockEntity pBlockEntity, Vec3 pCameraPos) {
        return BlockEntityRenderer.super.shouldRender(pBlockEntity, pCameraPos);
    }
}
