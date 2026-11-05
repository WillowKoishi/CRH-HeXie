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
        if(!entity.isLowerSide(state)) return;
        if (state.getBlock() instanceof willow.train.hexie.blocks.emu.crh2a.CRH2DoorBlock door) {
            renderCRH2(entity, door, partial, pose, buffer, light, overlay);
            return;
        }

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

        float counter = entity.crh2Progress(partial);

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
        // 注意到SuperByteBuffer未继承BlockEntityRenderer的打包光照值。
        // 但是原神你们知道吗，显式传递以确保门板与周围车厢同亮。
        lower.light(light).overlay(overlay).renderInto(pose, consumer);
        upper.light(light).overlay(overlay).renderInto(pose, consumer);
        pose.popPose();
    }

    private void renderCRH2(CustomRenderedDoorEntity entity,
                            willow.train.hexie.blocks.emu.crh2a.CRH2DoorBlock door, float partial,
                            PoseStack pose, MultiBufferSource buffer, int light, int overlay) {
        var state = entity.getBlockState();
        boolean mirrored = !entity.isLeftSide(state);
        MultiBufferSource shellBuffers = buffer;
        var lighting = willow.train.hexie.client.render.CRH2DoorRenderContext.current();
        if (lighting != null) {
            var toWorld = new org.joml.Matrix4f(lighting.localToWorld())
                    .mul(new org.joml.Matrix4f(pose.last().pose()).invert());
            shellBuffers = layer -> new ShellLightConsumer(buffer.getBuffer(layer), lighting.level(), toWorld);
        } else if (entity.getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel level) {
            var at = entity.getBlockPos();
            var toWorld = new org.joml.Matrix4f().translation(at.getX(),at.getY(),at.getZ())
                    .mul(new org.joml.Matrix4f(pose.last().pose()).invert());
            shellBuffers = layer -> new ShellLightConsumer(buffer.getBuffer(layer), level, toWorld);
        }
        VertexConsumer consumer = shellBuffers.getBuffer(RenderType.cutout());
        pose.pushPose();
        pose.translate(.5,0,.5);
        pose.mulPose(Axis.YP.rotationDegrees(switch (state.getValue(net.minecraft.world.level.block.DoorBlock.FACING)) {
            case EAST -> -90; case SOUTH -> 180; case WEST -> 90; default -> 0;
        }));
        pose.translate(-.5,0,-.5);
        if (door.isSlideDoor()) {
            CachedBuffers.partial(PartialModelInit.crh2PassengerDoorFrame(door.doorType(), entity.hasPassengerFrame(), mirrored), state)
                    .disableDiffuse().light(light).overlay(overlay).renderInto(pose,consumer);
            pose.translate(door.slideOffset(mirrored, entity.hasReverseSlide(), entity.crh2Progress(partial)),0,0);
        } else {
            double x = door.cabHingeX(mirrored);
            double z = door.cabHingeZ();
            pose.translate(x,0,z);
            pose.mulPose(Axis.YP.rotationDegrees(door.cabAngleDegrees(mirrored,entity.crh2Progress(partial))));
            pose.translate(-x,0,-z);
        }
        CachedBuffers.partial(PartialModelInit.crh2DoorLayer(door.doorType(), mirrored, false), state).disableDiffuse()
                .light(light).overlay(overlay).renderInto(pose,consumer);
        // The same authored leaf, UVs and animated pose; only window pixels occupy this layer.
        CachedBuffers.partial(PartialModelInit.crh2DoorLayer(door.doorType(), mirrored, true), state).disableDiffuse()
                .light(light).overlay(overlay).renderInto(pose,shellBuffers.getBuffer(RenderType.translucent()));
        pose.popPose();
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
