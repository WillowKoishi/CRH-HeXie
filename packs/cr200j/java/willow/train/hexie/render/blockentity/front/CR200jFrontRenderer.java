package willow.train.hexie.render.blockentity.front;

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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.BlockEntityViews.CR200jFrontBlockEntityView;
import willow.train.hexie.render.init.PartialModelInit;

public class CR200jFrontRenderer implements BlockEntityRenderer<BlockEntity> {
    public static float STEP = 0.025f;
    public static float STEP2 = 0.0125f;

    public final PartialModel FAIRING_LEFT, FAIRING_RIGHT, WIND_SHIELD_ROOT, WIND_SHIELD;

    public CR200jFrontRenderer(BlockEntityRendererProvider.Context context) {
        FAIRING_LEFT = PartialModelInit.CR200J_HEAD_COUPLER_FAIRING_LEFT;
        FAIRING_RIGHT = PartialModelInit.CR200J_HEAD_COUPLER_FAIRING_RIGHT;
        WIND_SHIELD = PartialModelInit.CR200J_HEAD_WIND_SHIELD_2;
        WIND_SHIELD_ROOT = PartialModelInit.CR200J_HEAD_WIND_SHIELD_1;
    }

    @Override
    public void render(BlockEntity blockEntity, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        if (!(blockEntity instanceof CR200jFrontBlockEntityView entity)) return;
        advanceAnimation(entity);
        var consumer = buffers.getBuffer(RenderType.solid());
        drawParts(entity, pose, (model, transform) ->
                CachedBuffers.partial(model, entity.asBlockEntity().getBlockState()).renderInto(transform, consumer));
    }

    @Override
    public AABB getRenderBoundingBox(BlockEntity entity) {
        return AABB.ofSize(Vec3.atLowerCornerOf(entity.getBlockPos()), 10, 10, 10);
    }

    public static void advanceAnimation(CR200jFrontBlockEntityView entity) {
        if (entity.isFairingOpen()) {
            entity.counterFairing(entity.counterFairing() < 1 ? entity.counterFairing() + STEP : 1);
        } else {
            entity.counterFairing(entity.counterFairing() > 0 ? entity.counterFairing() - STEP : 0);
        }

        if (entity.isWindShieldActive() || (entity.counterWindShield() >= 0 && entity.counterWindShield() <= 1)) {
            double limit = .85;
            if (entity.counterWindShield() > .5 + limit || entity.counterWindShield() < .5 - limit) {
                entity.counterWindShield((float) Math.clamp(entity.counterWindShield(), .5 - limit, .5 + limit));
                entity.negative_direction(!entity.negative_direction());
            }
            entity.counterWindShield(entity.counterWindShield() + (entity.negative_direction() ? -STEP2 : STEP2));
        }
    }

    public void drawParts(CR200jFrontBlockEntityView entity, PoseStack pose, BiConsumer<PartialModel, PoseStack> draw) {
        var facing = entity.asBlockEntity().getBlockState().getValue(HexieBridges.worldBlock().objRenderableEndFaceBlock().facing());
        float heading = -facing.getOpposite().toYRot();
        float fairingAngle = 50 * entity.counterFairing();
        pose.pushPose();
        pose.translate(.5f, -.03f, .5f);
        pose.mulPose(Axis.YP.rotationDegrees(heading));
        pose.translate(0, 0, 4.625f);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(fairingAngle));
        draw.accept(FAIRING_LEFT, pose);
        pose.popPose();
        pose.mulPose(Axis.YP.rotationDegrees(-fairingAngle));
        draw.accept(FAIRING_RIGHT, pose);
        pose.popPose();

        double progress = Math.clamp(entity.counterWindShield(), 0f, 1f);
        double angle = 75 * (progress - .5);
        pose.pushPose();
        pose.translate(.5f, 0, .5f);
        pose.mulPose(Axis.YP.rotationDegrees(heading));
        pose.translate(0, 2.3f, 3.71f);
        pose.mulPose(Axis.XP.rotationDegrees(37.5f));
        pose.mulPose(Axis.YP.rotationDegrees(180f));
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees((float) angle));
        draw.accept(WIND_SHIELD_ROOT, pose);
        pose.popPose();
        pose.translate(Math.sin(Math.toRadians(angle)) * .9375, -.05, Math.cos(Math.toRadians(angle)) * .9375);
        pose.mulPose(Axis.YP.rotationDegrees((float) (20 * (progress - .5))));
        draw.accept(WIND_SHIELD, pose);
        pose.popPose();
    }

    @Override public boolean shouldRenderOffScreen(BlockEntity entity) {
        return true;
    }

    @Override public boolean shouldRender(BlockEntity entity, Vec3 camera) {
        return Vec3.atLowerCornerOf(entity.getBlockPos()).distanceTo(camera) < 128;
    }
}
