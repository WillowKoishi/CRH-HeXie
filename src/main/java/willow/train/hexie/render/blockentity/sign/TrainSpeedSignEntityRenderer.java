package willow.train.hexie.render.blockentity.sign;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.BlockEntityViews.TrainSpeedSignEntityView;
import willow.train.hexie.render.sign.SignLettering;

public class TrainSpeedSignEntityRenderer implements BlockEntityRenderer<BlockEntity> {

public TrainSpeedSignEntityRenderer(BlockEntityRendererProvider.Context pContext) {
    }

    @Override
    public void render(
            BlockEntity blockEntity,
            float pPartialTick,
            PoseStack pPoseStack,
            MultiBufferSource pBufferSource,
            int pPackedLight,
            int pPackedOverlay) {
        if (!(blockEntity instanceof TrainSpeedSignEntityView pBlockEntity)) return;
        BlockState blockState = pBlockEntity.asBlockEntity().getBlockState();

        float x_offset =
                pBlockEntity.isX_revert()
                        ? (0 - pBlockEntity.getX_offset())
                        : pBlockEntity.getX_offset();
        float y_offset =
                pBlockEntity.isY_revert()
                        ? (0 - pBlockEntity.getY_offset())
                        : pBlockEntity.getY_offset();

        FormattedCharSequence formattedcharsequence =
                pBlockEntity.getRenderMessages(
                        net.minecraft.network.chat.Component::getVisualOrderText);

        float text_length = SignLettering.width(SignLettering.SPEED, formattedcharsequence);
        float text_height = 8;

        pPoseStack.pushPose();

        pPoseStack.translate(0.5d, 0.5d, 0.5d);
        float f = -blockState.getValue(HorizontalDirectionalBlock.FACING).getOpposite().toYRot();
        pPoseStack.mulPose(Axis.YP.rotationDegrees(f));
        // Center lettering on the y=12..16 slab face after the initial 0.5 translation.
        double textYOffset = switch (blockState.getValue(HexieBridges.signBlock().trainSpeedSignBlock().type())) {
            case S25B, S25G, S25Z, S25K, S25T, S25M -> 6.0d / 16.0d;
            default -> 0.1d;
        };
        pPoseStack.translate(0.0d, textYOffset, 0.501d);

        pPoseStack.scale(1.0f, -1.0f, 1.0f);

        pPoseStack.translate(x_offset, y_offset, 0.0);

        pPoseStack.scale(.2f, .2f, 1.0f); // standard_size

        pPoseStack.scale(.1f, .1f, 1.0f);

        pPoseStack.translate(-text_length / 2, -text_height / 2, 0);

        renderText(
                formattedcharsequence,
                0,
                0,
                pBlockEntity,
                pPoseStack,
                pBufferSource,
                (int) (pPackedLight * 0.8));

        pPoseStack.popPose();
    }

    private static int getDarkColor(TrainSpeedSignEntityView pBlockEntity) {
        int i = pBlockEntity.getColor();
        int j = (int) ((double) net.minecraft.util.FastColor.ABGR32.red(i) * 0.4D);
        int k = (int) ((double) net.minecraft.util.FastColor.ABGR32.green(i) * 0.4D);
        int l = (int) ((double) net.minecraft.util.FastColor.ABGR32.blue(i) * 0.4D);
        return net.minecraft.util.FastColor.ABGR32.color(0, l, k, j);
    }

    private void renderText(
            FormattedCharSequence formattedcharsequence,
            int pX,
            int pY,
            TrainSpeedSignEntityView pBlockEntity,
            PoseStack pPoseStack,
            MultiBufferSource pBufferSource,
            int pPackedLight) {
        SignLettering.draw(SignLettering.SPEED, formattedcharsequence, pX, pY, getDarkColor(pBlockEntity) * 2, pPoseStack, pBufferSource, pPackedLight);
    }
}
