package willow.train.hexie.client.render.blockentity;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.createmod.catnip.data.Couple;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.blocks.carriage.OBJRenderableEndFaceBlock;
import willow.train.hexie.blockentity.OBJRenderableEndFaceEntity;

public class OBJRenderableEndFaceRenderer implements BlockEntityRenderer<OBJRenderableEndFaceEntity> {
    public static float STEP = 0.025f;

    public OBJRenderableEndFaceRenderer(BlockEntityRendererProvider.Context pContext) {}

    @Override
    public void render(
            OBJRenderableEndFaceEntity pBlockEntity,
            float pPartialTick,
            PoseStack pose,
            MultiBufferSource pBufferSource,
            int pPackedLight,
            int pPackedOverlay) {
        boolean is_rotate = pBlockEntity.isRotateDoor;
        BlockState blockState = pBlockEntity.getBlockState();
        boolean isOpened = pBlockEntity.isDoorOpen;
        Couple<PartialModel> doorModels = pBlockEntity.getDoorModels();
        if(doorModels == null) return;
        PartialModel left = doorModels.get(true);
        PartialModel right = doorModels.get(false);

        float f = -blockState.getValue(OBJRenderableEndFaceBlock.FACING).getOpposite().toYRot() - 90;
        SuperByteBuffer leftBuffer = CachedBuffers.partial(left, blockState);
        SuperByteBuffer rightBuffer = CachedBuffers.partial(right, blockState);
        pose.pushPose();
        pose.translate(0.5f, -1f, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(f));
        leftBuffer.translate(-.6f, 0, .375f);
        rightBuffer.translate(-.6f, 0, -.375f);
        float level1 = .4f;
        if(isOpened && pBlockEntity.counter < 1) {
            pBlockEntity.counter += STEP;
        } else if (!isOpened && pBlockEntity.counter > 0) {
            pBlockEntity.counter -= STEP;
        }
        if(!is_rotate) {
            double distance_1 = .2;
            if (pBlockEntity.counter < level1) {
                double ctk = (pBlockEntity.counter)/level1;
                leftBuffer.translate(ctk * distance_1, 0, 0);
                rightBuffer.translate(ctk * distance_1, 0, 0);
            } else {
                double ctk = (pBlockEntity.counter - level1) / (1 - level1);
                double level2 = .4;
                leftBuffer.translate(distance_1, 0, ctk * level2);
                rightBuffer.translate(distance_1, 0, -ctk * level2);
            }
        } else {
            leftBuffer.rotateYDegrees(pBlockEntity.counter * 90);
            rightBuffer.rotateYDegrees(- pBlockEntity.counter * 90);
        }
        leftBuffer.renderInto(pose, pBufferSource.getBuffer(RenderType.cutout()));
        rightBuffer.renderInto(pose, pBufferSource.getBuffer(RenderType.cutout()));
        pose.popPose();
    }
}
