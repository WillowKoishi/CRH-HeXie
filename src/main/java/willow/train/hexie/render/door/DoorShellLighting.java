package willow.train.hexie.render.door;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import willow.train.hexie.bridge.world.BlockEntityViews.CustomRenderedDoorEntityView;
import willow.train.hexie.render.carriage.CRH2StaticSections;

// split from willow/train/hexie/client/render/blockentity/CustomRenderedDoorRenderer.java, lines 111-131, 139-161
final class DoorShellLighting {
    static MultiBufferSource shellBuffers(CustomRenderedDoorEntityView entity, PoseStack pose, MultiBufferSource buffer) {
        var crh1Lighting = CRH1DoorRenderer.lighting();
        if (crh1Lighting != null) {
            var toWorld = new org.joml.Matrix4f(crh1Lighting.localToWorld())
                    .mul(new org.joml.Matrix4f(pose.last().pose()).invert());
            return layer -> new ShellLightConsumer(buffer.getBuffer(layer), crh1Lighting.level(), toWorld);
        }
        var lighting = CRH2DoorRenderContext.current();
        if (lighting != null) {
            var toWorld = new org.joml.Matrix4f(lighting.localToWorld())
                    .mul(new org.joml.Matrix4f(pose.last().pose()).invert());
            return layer -> new ShellLightConsumer(buffer.getBuffer(layer), lighting.level(), toWorld);
        }
        if (entity.asBlockEntity().getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel level) {
            var at = entity.asBlockEntity().getBlockPos();
            var toWorld = new org.joml.Matrix4f().translation(at.getX(), at.getY(), at.getZ())
                    .mul(new org.joml.Matrix4f(pose.last().pose()).invert());
            return layer -> new ShellLightConsumer(buffer.getBuffer(layer), level, toWorld);
        }
        return buffer;
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
            light=CRH2StaticSections.lightAt(level,lights,point.x,point.y,point.z);
            delegate.addVertex(x,y,z); return this;
        }
        @Override public VertexConsumer setColor(int r,int g,int b,int a) { delegate.setColor(r,g,b,a); return this; }
        @Override public VertexConsumer setUv(float u,float v) { delegate.setUv(u,v); return this; }
        @Override public VertexConsumer setUv1(int u,int v) { delegate.setUv1(u,v); return this; }
        @Override public VertexConsumer setUv2(int u,int v) { delegate.setUv2(light&65535,light>>>16); return this; }
        @Override public VertexConsumer setNormal(float x,float y,float z) { delegate.setNormal(x,y,z); return this; }
    }
}
