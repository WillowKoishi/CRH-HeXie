package willow.train.hexie.render.blockentity.part;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import java.util.function.BiConsumer;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.BlockEntityViews.OBJRenderableEndFaceEntityView;
import willow.train.hexie.render.door.DoorPartialModelCache;

public class OBJRenderableEndFaceRenderer implements BlockEntityRenderer<BlockEntity> {
    public static float STEP = 0.025f;

    public OBJRenderableEndFaceRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(BlockEntity blockEntity, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        if (!(blockEntity instanceof OBJRenderableEndFaceEntityView entity)) return;
        var consumer = buffers.getBuffer(RenderType.cutout());
        drawParts(entity, partialTick, pose, (model, transform) ->
                CachedBuffers.partial(model, entity.asBlockEntity().getBlockState()).renderInto(transform, consumer));
    }

    static void drawParts(OBJRenderableEndFaceEntityView entity, float partialTick, PoseStack pose,
                          BiConsumer<PartialModel, PoseStack> draw) {
        var models = HexieBridges.Client.blockEntityViewsClient().objRenderableEndFaceEntity().getDoorModelLocations(entity);
        var facing = entity.asBlockEntity().getBlockState().getValue(HexieBridges.worldBlock().objRenderableEndFaceBlock().facing());
        float progress = entity.progress(partialTick);
        pose.pushPose();
        pose.translate(.5f, -1f, .5f);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.getOpposite().toYRot() - 90));
        drawLeaf(entity.isRotateDoor(), progress, true, DoorPartialModelCache.get(models.first()), pose, draw);
        drawLeaf(entity.isRotateDoor(), progress, false, DoorPartialModelCache.get(models.second()), pose, draw);
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
