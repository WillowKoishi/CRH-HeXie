package willow.train.hexie.client.render.blockentity;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.blocks.carriage.OBJRenderableEndFaceBlock;
import willow.train.hexie.init.client.PartialModelInit;
import willow.train.hexie.blockentity.CR200jFrontBlockEntity;

public class CR200jFrontRenderer implements BlockEntityRenderer<CR200jFrontBlockEntity> {
    public static float STEP = 0.025f;
    public static float STEP2 = 0.0125f;

    public final PartialModel FAIRING_LEFT, FAIRING_RIGHT, WIND_SHIELD_ROOT, WIND_SHIELD;

    public CR200jFrontRenderer(BlockEntityRendererProvider.Context pContext) {
        FAIRING_LEFT = PartialModelInit.CR200J_HEAD_COUPLER_FAIRING_LEFT;
        FAIRING_RIGHT = PartialModelInit.CR200J_HEAD_COUPLER_FAIRING_RIGHT;
        WIND_SHIELD = PartialModelInit.CR200J_HEAD_WIND_SHIELD_2;
        WIND_SHIELD_ROOT = PartialModelInit.CR200J_HEAD_WIND_SHIELD_1;
    }

    @Override
    public void render(
            CR200jFrontBlockEntity entity,
            float pPartialTick,
            PoseStack pose,
            MultiBufferSource pBufferSource,
            int pPackedLight,
            int pPackedOverlay) {
        BlockState blockState = entity.getBlockState();
        boolean fairing = entity.isFairingOpen();
        boolean windShield = entity.isWindShieldActive();
        VertexConsumer consumer = pBufferSource.getBuffer(RenderType.solid());

        SuperByteBuffer fairing_left = CachedBuffers.partial(FAIRING_LEFT, blockState);
        SuperByteBuffer fairing_right = CachedBuffers.partial(FAIRING_RIGHT, blockState);
        SuperByteBuffer wind_shield_root = CachedBuffers.partial(WIND_SHIELD_ROOT, blockState);
        SuperByteBuffer wind_shield = CachedBuffers.partial(WIND_SHIELD, blockState);
        float f = -blockState.getValue(OBJRenderableEndFaceBlock.FACING).getOpposite().toYRot();

        pose.pushPose();
        pose.translate(0.5f, -0.03f, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(f));
        pose.translate(0, 0, 4.625f);
        if(fairing) {
            if (entity.counterFairing < 1)
                entity.counterFairing += STEP;
            else
                entity.counterFairing = 1;
        } else {
            if (entity.counterFairing > 0)
                entity.counterFairing -= STEP;
            else
                entity.counterFairing = 0;
        }
        double offset = 0, rotation = 50;
        fairing_left.translate(0, 0, - entity.counterFairing * offset);
        fairing_right.translate(0, 0, - entity.counterFairing * offset);
        fairing_left.rotateYDegrees((float) (rotation * entity.counterFairing));
        fairing_right.rotateYDegrees((float) (- rotation * entity.counterFairing));
        fairing_left.renderInto(pose, consumer);
        fairing_right.renderInto(pose, consumer);
        pose.popPose();

        pose.pushPose();
        double radius = 0.9375;
        double rotate_angle = 37.5, rotate_angle_2 = 10;
        double level = 0.85;
        pose.translate(0.5f, -0f, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(f));
        pose.translate(0, 2.3f, 3.71f);
        pose.mulPose(Axis.XP.rotationDegrees(37.5f));
        pose.mulPose(Axis.YP.rotationDegrees(180f));
        if(windShield || (!windShield && entity.counterWindShield >= 0 && entity.counterWindShield <= 1)) {
            if(entity.counterWindShield > .5 + level || entity.counterWindShield < .5 - level) {
                if (entity.counterWindShield > .5 + level)
                    entity.counterWindShield = (float) (.5 + level);
                else if (entity.counterWindShield < .5 - level)
                    entity.counterWindShield = (float) (.5 - level);
                entity.negative_direction = !entity.negative_direction;
            }
            entity.counterWindShield += entity.negative_direction ? - STEP2 : STEP2;
        }
        double px = Math.min(1, (Math.max(entity.counterWindShield, 0)));
        double angle = 2 * rotate_angle * (px - .5f);
        wind_shield_root.rotateYDegrees((float) (angle));
        wind_shield.translate(Math.sin(Math.toRadians(angle)) * radius, -.05,Math.cos(Math.toRadians(angle)) * radius);
        wind_shield.rotateYDegrees((float) (2 * rotate_angle_2 * (px - .5f)));
        wind_shield_root.renderInto(pose, consumer);
        wind_shield.renderInto(pose, consumer);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(CR200jFrontBlockEntity pBlockEntity) {
        return true;
    }

    @Override
    public boolean shouldRender(CR200jFrontBlockEntity pBlockEntity, Vec3 pCameraPos) {
        return Vec3.atLowerCornerOf(pBlockEntity.getBlockPos()).distanceTo(pCameraPos) < 128;
    }
}
