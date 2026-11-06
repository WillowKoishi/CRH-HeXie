package willow.train.hexie.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.render.SpecialModels;
import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.instance.PosedInstance;
import dev.engine_room.flywheel.lib.memory.MemoryBlock;
import dev.engine_room.flywheel.lib.model.SimpleQuadMesh;
import dev.engine_room.flywheel.lib.model.SingleMeshModel;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.util.RendererReloadCache;
import dev.engine_room.flywheel.lib.vertex.FullVertexView;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3f;

/** A tilted, reach-limited bellows contact face can bend differently at every corner.
 * Each original triangle is baked once; standard posed instances move its three vertices
 * and their normals independently without uploading a new mesh as the two cars move. */
final class BellowsTriangles {
    private static final RendererReloadCache<PartialModel, List<Face>> MODELS = new RendererReloadCache<>(BellowsTriangles::bake);

    static List<Face> forModel(PartialModel model) { return MODELS.get(model); }

    private static List<Face> bake(PartialModel partial) {
        List<Face> faces = new ArrayList<>();
        for (var layer : SpecialModels.smoothLit(partial).meshes()) {
            var mesh = layer.mesh();
            var vertices = new FullVertexView();
            int count = mesh.vertexCount();
            var memory = MemoryBlock.malloc(count * vertices.stride());
            try {
                vertices.ptr(memory.ptr());
                vertices.vertexCount(count);
                mesh.write(vertices);
                for (int i = 0; i < count; i += 4) {
                    addFace(faces, vertices, layer.material(), i, i + 1, i + 2);
                    addFace(faces, vertices, layer.material(), i + 2, i + 3, i);
                }
            } finally {
                memory.free();
            }
        }
        return List.copyOf(faces);
    }

    private static void addFace(List<Face> faces, FullVertexView source, Material material, int a, int b, int c) {
        var first = new Vertex(source, a);
        var second = new Vertex(source, b);
        var third = new Vertex(source, c);
        if (first.samePosition(second) || second.samePosition(third) || third.samePosition(first)) return;
        var unit = new FullVertexView();
        var memory = MemoryBlock.mallocTracked(4 * unit.stride());
        unit.nativeMemoryOwner(memory);
        unit.ptr(memory.ptr());
        unit.vertexCount(4);
        int[] indices = {a, b, c, c};
        for (int i = 0; i < 4; i++) {
            source.write(unit, indices[i], i);
            // Encode 3 corners as (0,0), (1,0), (0,1). Normals select the three columns of i.normal.
            unit.x(i, i == 1 ? 1 : 0);
            unit.y(i, i >= 2 ? 1 : 0);
            unit.z(i, 0);
            unit.normalX(i, i == 0 ? 1 : 0);
            unit.normalY(i, i == 1 ? 1 : 0);
            unit.normalZ(i, i >= 2 ? 1 : 0);
        }
        var model = new SingleMeshModel(new SimpleQuadMesh(unit, "HeXie bellows triangle"), material);
        faces.add(new Face(model, first, second, third));
    }

    private record Vertex(float x, float y, float z, float nx, float ny, float nz) {
        Vertex(FullVertexView v, int i) { this(v.x(i), v.y(i), v.z(i), v.normalX(i), v.normalY(i), v.normalZ(i)); }
        boolean samePosition(Vertex other) { return x == other.x && y == other.y && z == other.z; }
        float depth(PartialCouplerRenderer.BellowsDeformation d) { return (float) d.depth(x, y, z); }
        void normal(PartialCouplerRenderer.BellowsDeformation d, PoseStack.Pose mount, Vector3f out) {
            d.normal(x, y, z, nx, ny, nz, out);
            mount.normal().transform(out).normalize();
        }
    }

    record Face(Model model, Vertex a, Vertex b, Vertex c) {
        void deform(PosedInstance instance, PoseStack.Pose mount, PartialCouplerRenderer.BellowsDeformation d, Vector3f normal) {
            float za = a.depth(d), zb = b.depth(d), zc = c.depth(d);
            float ux = b.x - a.x, uy = b.y - a.y, uz = zb - za;
            float vx = c.x - a.x, vy = c.y - a.y, vz = zc - za;
            // Keep an outward third axis for profiles deriving tangent space from the instance transform.
            normal.set(ux, uy, uz).cross(vx, vy, vz);
            if (normal.lengthSquared() > 0) normal.normalize();
            instance.pose.set(ux, uy, uz, 0, vx, vy, vz, 0, normal.x, normal.y, normal.z, 0, a.x, a.y, za, 1);
            mount.pose().mul(instance.pose, instance.pose);
            a.normal(d, mount, normal);
            instance.normal.setColumn(0, normal);
            b.normal(d, mount, normal);
            instance.normal.setColumn(1, normal);
            c.normal(d, mount, normal);
            instance.normal.setColumn(2, normal);
        }
    }

    private BellowsTriangles() {}
}
