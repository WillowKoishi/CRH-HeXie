package willow.train.hexie.render.coupler;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Vector3f;

import static willow.train.hexie.render.coupler.BellowsGeometry.BellowsDeformation;

// split from willow/train/hexie/client/render/PartialCouplerRenderer.java, lines 188-215
final class BellowsVertices {
    static final class DeformingConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final PoseStack.Pose pose;
        private final BellowsDeformation deformation;
        private final Vector3f normal = new Vector3f();
        private float x, y, z;

        DeformingConsumer(VertexConsumer delegate, PoseStack.Pose pose, BellowsDeformation deformation) {
            this.delegate = delegate; this.pose = pose; this.deformation = deformation;
        }

        @Override public VertexConsumer addVertex(float x, float y, float z) {
            this.x = x; this.y = y; this.z = z;
            delegate.addVertex(pose.pose(), x, y, (float) deformation.depth(x, y, z));
            return this;
        }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { delegate.setColor(r, g, b, a); return this; }
        @Override public VertexConsumer setUv(float u, float v) { delegate.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { delegate.setUv1(u, v); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { delegate.setUv2(u, v); return this; }
        @Override public VertexConsumer setNormal(float nx, float ny, float nz) {
            deformation.normal(x, y, z, nx, ny, nz, normal);
            pose.normal().transform(normal);
            normal.normalize();
            delegate.setNormal(normal.x, normal.y, normal.z);
            return this;
        }
    }
}
