package willow.train.hexie.render.coupler;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import willow.train.hexie.bridge.world.BlockEntityViews.CRH1HeadEntityView;
import willow.train.hexie.render.carriage.StaticShellLighting;

/** Sample the same continuous world-light field for CRH1's moving head parts. */
public final class CRH1HeadLighting {
    private CRH1HeadLighting() {}

    public static MultiBufferSource worldBuffers(CRH1HeadEntityView head, PoseStack pose, MultiBufferSource buffers) {
        if (!(head.asBlockEntity().getLevel() instanceof ClientLevel level)) return buffers;
        var at = head.asBlockEntity().getBlockPos();
        var renderedToWorld = new Matrix4f().translation(at.getX(), at.getY(), at.getZ())
                .mul(new Matrix4f(pose.last().pose()).invert());
        return layer -> new LightConsumer(buffers.getBuffer(layer), level, renderedToWorld);
    }

    private static final class LightConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final ClientLevel level;
        private final Matrix4f renderedToWorld;
        private final Vector3f point = new Vector3f();
        private final Map<BlockPos, Integer> lights = new HashMap<>();
        private int light;

        private LightConsumer(VertexConsumer delegate, ClientLevel level, Matrix4f renderedToWorld) {
            this.delegate = delegate;
            this.level = level;
            this.renderedToWorld = renderedToWorld;
        }

        @Override public VertexConsumer addVertex(float x, float y, float z) {
            renderedToWorld.transformPosition(point.set(x, y, z));
            light = StaticShellLighting.lightAt(level, lights, point.x, point.y, point.z);
            delegate.addVertex(x, y, z);
            return this;
        }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { delegate.setColor(r, g, b, a); return this; }
        @Override public VertexConsumer setUv(float u, float v) { delegate.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { delegate.setUv1(u, v); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { delegate.setUv2(light & 65535, light >>> 16); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { delegate.setNormal(x, y, z); return this; }
    }
}
