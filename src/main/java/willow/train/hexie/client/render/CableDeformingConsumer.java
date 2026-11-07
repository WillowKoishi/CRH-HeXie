package willow.train.hexie.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Vector3f;

/** Keeps every authored UV, colour and lighting attribute while moving cable vertices. */
public final class CableDeformingConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final PoseStack.Pose mount;
    private final CableDeformation deformation;
    private final Vector3f scratch = new Vector3f();
    private float x, y, z;

    public CableDeformingConsumer(VertexConsumer delegate, PoseStack.Pose mount, CableDeformation deformation) {
        this.delegate = delegate; this.mount = mount; this.deformation = deformation;
    }

    @Override public VertexConsumer addVertex(float x, float y, float z) {
        this.x = x; this.y = y; this.z = z;
        deformation.position(x, y, z, scratch);
        delegate.addVertex(mount.pose(), scratch.x, scratch.y, scratch.z);
        return this;
    }
    @Override public VertexConsumer setColor(int r, int g, int b, int a) { delegate.setColor(r, g, b, a); return this; }
    @Override public VertexConsumer setUv(float u, float v) { delegate.setUv(u, v); return this; }
    @Override public VertexConsumer setUv1(int u, int v) { delegate.setUv1(u, v); return this; }
    @Override public VertexConsumer setUv2(int u, int v) { delegate.setUv2(u, v); return this; }
    @Override public VertexConsumer setNormal(float nx, float ny, float nz) {
        deformation.normal(x, y, z, nx, ny, nz, scratch);
        mount.normal().transform(scratch).normalize();
        delegate.setNormal(scratch.x, scratch.y, scratch.z);
        return this;
    }
}
