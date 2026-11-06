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
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.blocks.emu.crh2a.CRH2DoorBlock;
import willow.train.hexie.blockentity.CustomRenderedDoorEntity;
import willow.train.hexie.init.client.PartialModelInit;

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
        if (!entity.isLowerSide(state)) return;
        boolean shell = state.getBlock() instanceof CRH2DoorBlock;
        MultiBufferSource materials = shell ? shellBuffers(entity, pose, buffer) : buffer;
        pose.pushPose();
        drawParts(entity, entity.crh2Progress(partial), pose, (model, layer, transform) -> {
            SuperByteBuffer part = CachedBuffers.partial(model, state);
            if (shell) part.disableDiffuse();
            part.light(light).overlay(overlay).renderInto(transform, materials.getBuffer(layer));
        });
        pose.popPose();
    }

    @FunctionalInterface
    interface PartRenderer {
        void draw(PartialModel model, RenderType layer, PoseStack transform);
    }

    /** Compute each authored part's local pose for both buffered and instanced rendering. */
    static void drawParts(CustomRenderedDoorEntity entity, float progress, PoseStack pose, PartRenderer renderer) {
        BlockState state = entity.getBlockState();
        var facing = state.getValue(DoorBlock.FACING);
        boolean left = entity.isLeftSide(state);
        pose.translate(.5, 0, .5);
        if (state.getBlock() instanceof CRH2DoorBlock door) {
            boolean mirrored = !left;
            pose.mulPose(Axis.YP.rotationDegrees(switch (facing) {
                case EAST -> -90; case SOUTH -> 180; case WEST -> 90; default -> 0;
            }));
            pose.translate(-.5, 0, -.5);
            if (door.isSlideDoor()) {
                renderer.draw(PartialModelInit.crh2PassengerDoorFrame(door.doorType(), entity.hasPassengerFrame(), mirrored),
                        RenderType.cutout(), pose);
                pose.translate(door.slideOffset(mirrored, entity.hasReverseSlide(), progress), 0, 0);
            } else {
                double x = CRH2DoorBlock.cabHingeX(mirrored), z = CRH2DoorBlock.cabHingeZ();
                pose.translate(x, 0, z);
                pose.mulPose(Axis.YP.rotationDegrees(CRH2DoorBlock.cabAngleDegrees(mirrored, progress)));
                pose.translate(-x, 0, -z);
            }
            renderer.draw(PartialModelInit.crh2DoorLayer(door.doorType(), mirrored, false), RenderType.cutout(), pose);
            renderer.draw(PartialModelInit.crh2DoorLayer(door.doorType(), mirrored, true), RenderType.translucent(), pose);
            return;
        }

        pose.mulPose(Axis.YP.rotationDegrees(-facing.getOpposite().toYRot() - 180));
        pose.translate(left ? .5 : -.5, 0, -.5);
        if (entity.isSlideDoor()) {
            float first = .4f;
            if (progress > first) {
                double offset = .2 + (progress - first) / (1 - first) * .7;
                pose.translate(left ? offset : -offset, 0, -.2);
            } else {
                pose.translate(progress * (left ? .2 : -.2), 0, progress / first * -.2);
            }
        } else {
            pose.mulPose(Axis.YP.rotationDegrees(progress * (left ? 90 : -90)));
        }
        Couple<PartialModel> models = entity.getModels(state);
        renderer.draw(models.get(true), RenderType.cutoutMipped(), pose);
        pose.translate(0, 1, 0);
        renderer.draw(models.get(false), RenderType.cutoutMipped(), pose);
    }

    private static MultiBufferSource shellBuffers(CustomRenderedDoorEntity entity, PoseStack pose, MultiBufferSource buffer) {
        var lighting = willow.train.hexie.client.render.CRH2DoorRenderContext.current();
        if (lighting != null) {
            var toWorld = new org.joml.Matrix4f(lighting.localToWorld())
                    .mul(new org.joml.Matrix4f(pose.last().pose()).invert());
            return layer -> new ShellLightConsumer(buffer.getBuffer(layer), lighting.level(), toWorld);
        }
        if (entity.getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel level) {
            var at = entity.getBlockPos();
            var toWorld = new org.joml.Matrix4f().translation(at.getX(), at.getY(), at.getZ())
                    .mul(new org.joml.Matrix4f(pose.last().pose()).invert());
            return layer -> new ShellLightConsumer(buffer.getBuffer(layer), level, toWorld);
        }
        return buffer;
    }

    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(CustomRenderedDoorEntity entity) {
        return new net.minecraft.world.phys.AABB(entity.getBlockPos()).inflate(1.5);
    }

    /** Preserve the existing cached model renderer; replace only its lightmap with shell lighting. */
    private static final class ShellLightConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final net.minecraft.world.level.BlockAndTintGetter level;
        private final org.joml.Matrix4f renderedToWorld;
        private final org.joml.Vector3f point = new org.joml.Vector3f();
        private final java.util.Map<net.minecraft.core.BlockPos,Integer> lights = new java.util.HashMap<>();
        private int light;
        ShellLightConsumer(VertexConsumer delegate, net.minecraft.world.level.BlockAndTintGetter level,
                           org.joml.Matrix4f renderedToWorld) {
            this.delegate=delegate; this.level=level; this.renderedToWorld=renderedToWorld;
        }
        @Override public VertexConsumer addVertex(float x,float y,float z) {
            renderedToWorld.transformPosition(point.set(x,y,z));
            light=willow.train.hexie.client.render.CRH2StaticSections.lightAt(level,lights,point.x,point.y,point.z);
            delegate.addVertex(x,y,z); return this;
        }
        @Override public VertexConsumer setColor(int r,int g,int b,int a) { delegate.setColor(r,g,b,a); return this; }
        @Override public VertexConsumer setUv(float u,float v) { delegate.setUv(u,v); return this; }
        @Override public VertexConsumer setUv1(int u,int v) { delegate.setUv1(u,v); return this; }
        @Override public VertexConsumer setUv2(int u,int v) { delegate.setUv2(light&65535,light>>>16); return this; }
        @Override public VertexConsumer setNormal(float x,float y,float z) { delegate.setNormal(x,y,z); return this; }
    }
}
