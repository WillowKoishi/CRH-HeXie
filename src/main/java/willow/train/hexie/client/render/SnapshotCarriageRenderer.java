package willow.train.hexie.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.client.render.bogey.DF11GBogeyRenderer;
import willow.train.hexie.client.render.bogey.BogeySuspensionPose;
import willow.train.hexie.blocks.bogeys.BogeyFloorData;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import willow.train.hexie.compat.create.SnapshotBogeyAnimation;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;
import willow.train.hexie.datafix.Constants;

/** Reuses Create's structure renderer and each registered bogey renderer, without its two-support limit. */
public final class SnapshotCarriageRenderer extends ContraptionEntityRenderer<SnapshotCarriageEntity> {
    public SnapshotCarriageRenderer(EntityRendererProvider.Context context) { super(context); }

    @Override public boolean shouldRender(SnapshotCarriageEntity entity,
            net.minecraft.client.renderer.culling.Frustum frustum, double cameraX, double cameraY, double cameraZ) {
        if (super.shouldRender(entity, frustum, cameraX, cameraY, cameraZ)) return true;
        // Keep Create's readiness and vanilla's distance checks; only extend the visible mesh envelope.
        if (entity.getContraption() == null || !entity.isAliveOrStale() || !entity.isReadyForRender()
                || !entity.shouldRender(cameraX, cameraY, cameraZ)) return false;
        float partial = net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        var extra = SnapshotCouplerRenderer.adapterBounds(entity, partial);
        return extra != null && frustum.isVisible(extra.inflate(.5));
    }

    @Override
    public void render(SnapshotCarriageEntity entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        super.render(entity, yaw, partialTick, poseStack, buffers, packedLight);
        if (entity.getContraption() == null) return;
        int overlay = OverlayTexture.NO_OVERLAY;
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
            try (var suspension = suspensionPose(be, frame);
                 var sample = willow.train.hexie.compat.create.bogey.CreateAddonBogeyRenderer.snapshotSample(
                         frame.wheelAngle(), frame.speed(), partialTick)) {
                be.getStyle().render(block.getSize(), partialTick, poseStack, buffers,
                        light(entity, frame, partialTick), overlay, (float) (frame.wheelAngle() % 360),
                        data(entity, be, frame), true);
            }
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    static void transform(PoseStack stack, SnapshotBogeyAnimation.Frame frame) {
        stack.translate(frame.x(), frame.y(), frame.z());
        stack.mulPose(frame.rotation());
    }

    static BogeySuspensionPose.Scope suspensionPose(AbstractBogeyBlockEntity be, SnapshotBogeyAnimation.Frame frame) {
        var position = be.getBlockPos();
        var shift = BogeyFloorData.displacement(be.getBlockState(), BogeyFloorData.longitudinal(be.getBogeyData()));
        float floorHeight = (float) BogeyFloorData.offset(BogeyFloorData.heightFromBogeyData(be.getBogeyData()));
        boolean xAxis = be.getBlockState().getOptionalValue(BlockStateProperties.HORIZONTAL_AXIS)
                .orElse(Direction.Axis.Z) == Direction.Axis.X;
        return BogeySuspensionPose.push(BogeySuspensionPose.relative(
                new Vector3f(frame.x(), frame.y(), frame.z()), frame.rotation(),
                // The solved carriage already includes floor height. Cancel it only for mechanical attachments.
                new Vector3f((float) (position.getX() + .5 + shift.x), position.getY() + .5f - floorHeight,
                        (float) (position.getZ() + .5 + shift.z)),
                new Quaternionf().rotationY(xAxis ? (float) Math.PI / 2 : 0)));
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
