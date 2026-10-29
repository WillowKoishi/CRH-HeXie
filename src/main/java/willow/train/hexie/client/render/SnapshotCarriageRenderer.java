package willow.train.hexie.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.client.render.bogey.DF11GBogeyRenderer;
import willow.train.hexie.compat.create.SnapshotBogeyAnimation;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;
import willow.train.hexie.datafix.Constants;

/** Reuses Create's structure renderer and each registered bogey renderer, without its two-support limit. */
public final class SnapshotCarriageRenderer extends ContraptionEntityRenderer<SnapshotCarriageEntity> {
    public SnapshotCarriageRenderer(EntityRendererProvider.Context context) { super(context); }

    @Override
    public void render(SnapshotCarriageEntity entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int overlay) {
        super.render(entity, yaw, partialTick, poseStack, buffers, overlay);
        if (entity.getContraption() == null) return;
        SnapshotCouplerRenderer.render(entity, partialTick, poseStack, buffers, overlay);
        if (VisualizationManager.supportsVisualization(entity.level())) return;
        var client = entity.getContraption().getOrCreateClientContraptionLazy();
        var animation = entity.bogeyAnimation();
        poseStack.pushPose();
        entity.applyLocalTransforms(poseStack, partialTick);
        for (int i = 0; i < animation.frames().size(); i++) {
            var frame = animation.frame(i, partialTick);
            if (!(client.getBlockEntity(frame.position()) instanceof AbstractBogeyBlockEntity be)
                    || !(be.getBlockState().getBlock() instanceof AbstractBogeyBlock<?> block)) continue;
            poseStack.pushPose();
            transform(poseStack, frame);
            be.getStyle().render(block.getSize(), partialTick, poseStack, buffers,
                    light(entity, frame, partialTick), overlay, (float) (frame.wheelAngle() % 360),
                    data(entity, be, frame), true);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    static void transform(PoseStack stack, SnapshotBogeyAnimation.Frame frame) {
        stack.translate(frame.x(), frame.y(), frame.z());
        stack.mulPose(frame.rotation());
    }

    static CompoundTag data(SnapshotCarriageEntity entity, AbstractBogeyBlockEntity be,
                            SnapshotBogeyAnimation.Frame frame) {
        CompoundTag data = be.getBogeyData();
        // The complete model orientation is already in the support transform. Keep renderer-local
        // forward/backward variants, but don't apply Create's assembly-direction correction twice.
        NBTHelper.writeEnum(data, Constants.BOGEY_ASSEMBLY_DIRECTION_KEY, Direction.NORTH);
        data.putFloat(DF11GBogeyRenderer.QJBogeyRenderer.TRAIN_SPEED_KEY, frame.speed());
        return data;
    }

    static int light(SnapshotCarriageEntity entity, SnapshotBogeyAnimation.Frame frame, float partialTick) {
        Vec3 world = entity.toGlobalVector(new Vec3(frame.x(), frame.y(), frame.z()), partialTick);
        BlockPos pos = BlockPos.containing(world);
        return LightTexture.pack(entity.level().getBrightness(LightLayer.BLOCK, pos),
                entity.level().getBrightness(LightLayer.SKY, pos));
    }
}
