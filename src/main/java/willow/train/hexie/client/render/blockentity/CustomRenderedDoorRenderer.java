package willow.train.hexie.client.render.blockentity;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
import willow.train.hexie.blockentity.CustomRenderedDoorEntity;

public class CustomRenderedDoorRenderer implements BlockEntityRenderer<CustomRenderedDoorEntity> {

    public CustomRenderedDoorRenderer(BlockEntityRendererProvider.Context pContext) {}

    @Override
    public void render(
            CustomRenderedDoorEntity entity,
            float partial,
            PoseStack pose,
            MultiBufferSource buffer,
            int light,
            int overlay) {
        BlockState state = entity.getBlockState();
        if(!entity.isLowerSide(state)) return;

        Couple<PartialModel> models = entity.getModels(state);

        boolean open = entity.isOpen();
        boolean slide = entity.isSlideDoor();
        boolean leftSide = entity.isLeftSide(state);

        SuperByteBuffer lower = CachedBuffers.partial(models.get(true), state);
        SuperByteBuffer upper = CachedBuffers.partial(models.get(false), state);

        VertexConsumer consumer = buffer.getBuffer(RenderType.cutoutMipped());

        pose.pushPose();
        float f = -state.getValue(OBJRenderableEndFaceBlock.FACING).getOpposite().toYRot();
        pose.translate(.5, 0, .5);
        pose.mulPose(Axis.YP.rotationDegrees(f - 180));
        pose.translate(leftSide ? .5 : -.5, 0, -.5);
        upper.translate(0, 1, 0);

        if(open && entity.animation_controller < 1) {
            entity.animation_controller += slide ? 0.025f : 0.05f;
        } else if (!open && entity.animation_controller > 0) {
            entity.animation_controller -= slide ? 0.025f : 0.05f;
        } else if(open && entity.animation_controller > 1){
            entity.animation_controller = 1;
        } else if(!open && entity.animation_controller < 0){
            entity.animation_controller = 0;
        }

        float counter = entity.animation_controller;

        if(slide){
            float level1 = .4f;
            if(counter > level1) {
            double d = (.2 + ((counter - level1)/(1 - level1)) * .7) * (leftSide ? 1 : -1);
                lower.translate(d, 0, -.2);
                upper.translate(d, 0, -.2);
            } else {
                lower.translate(counter * (leftSide ? .2 : -.2) ,0, (counter/level1) * - .2);
                upper.translate(counter * (leftSide ? .2 : -.2) ,0, (counter/level1) * - .2);
            }
        } else {
            lower.rotateYDegrees(counter * (leftSide ? 90 : -90));
            upper.rotateYDegrees(counter * (leftSide ? 90 : -90));
        }
        // SuperByteBuffer does not inherit the BlockEntityRenderer's packed light value.
        // Pass it explicitly so the door panels are lit like the surrounding carriage.
        lower.light(light).overlay(overlay).renderInto(pose, consumer);
        upper.light(light).overlay(overlay).renderInto(pose, consumer);
        pose.popPose();
    }
}
