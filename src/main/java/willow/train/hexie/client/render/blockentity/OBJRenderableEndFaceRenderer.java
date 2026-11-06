package willow.train.hexie.client.render.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import java.util.function.BiConsumer;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import willow.train.hexie.blockentity.OBJRenderableEndFaceEntity;
import willow.train.hexie.blocks.carriage.OBJRenderableEndFaceBlock;

public class OBJRenderableEndFaceRenderer implements BlockEntityRenderer<OBJRenderableEndFaceEntity> {
    public static float STEP = 0.025f;

    public OBJRenderableEndFaceRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(OBJRenderableEndFaceEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        var consumer = buffers.getBuffer(RenderType.cutout());
        drawParts(entity, partialTick, pose, (model, transform) ->
                CachedBuffers.partial(model, entity.getBlockState()).renderInto(transform, consumer));
    }

    static void drawParts(OBJRenderableEndFaceEntity entity, float partialTick, PoseStack pose,
                          BiConsumer<PartialModel, PoseStack> draw) {
        var models = entity.getDoorModels();
        if (models == null) return;
        var facing = entity.getBlockState().getValue(OBJRenderableEndFaceBlock.FACING);
        float progress = entity.progress(partialTick);
        pose.pushPose();
        pose.translate(.5f, -1f, .5f);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.getOpposite().toYRot() - 90));
        drawLeaf(entity.isRotateDoor, progress, true, models.get(true), pose, draw);
        drawLeaf(entity.isRotateDoor, progress, false, models.get(false), pose, draw);
        pose.popPose();
    }

    private static void drawLeaf(boolean hinged, float progress, boolean left, PartialModel model, PoseStack pose,
                                 BiConsumer<PartialModel, PoseStack> draw) {
        int side = left ? 1 : -1;
        pose.pushPose();
        pose.translate(-.6f, 0, side * .375f);
        if (hinged) {
            pose.mulPose(Axis.YP.rotationDegrees(side * progress * 90));
        } else if (progress < .4f) {
            pose.translate(.2 * (progress / .4f), 0, 0);
        } else {
            pose.translate(.2, 0, side * .4 * ((progress - .4f) / (1 - .4f)));
        }
        draw.accept(model, pose);
        pose.popPose();
    }
}
