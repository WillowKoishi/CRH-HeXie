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

/** The same original cable triangles and UVs as buffered rendering, moved with posed instances. */
public final class CableTriangles {
    private static final RendererReloadCache<PartialModel, List<Face>> MODELS = new RendererReloadCache<>(CableTriangles::bake);

    private CableTriangles() {}
    public static List<Face> forModel(PartialModel model) { return MODELS.get(model); }

    private static List<Face> bake(PartialModel partial) {
        List<Face> faces = new ArrayList<>();
        for (var layer : SpecialModels.smoothLit(partial).meshes()) {
            var mesh = layer.mesh();
            var vertices = new FullVertexView();
            int count = mesh.vertexCount();
            var memory = MemoryBlock.malloc(count * vertices.stride());
            try {
                vertices.ptr(memory.ptr()); vertices.vertexCount(count); mesh.write(vertices);
                for (int i = 0; i < count; i += 4) {
                    add(faces, vertices, layer.material(), i, i + 1, i + 2);
                    add(faces, vertices, layer.material(), i + 2, i + 3, i);
                }
            } finally { memory.free(); }
        }
        return List.copyOf(faces);
    }

    private static void add(List<Face> faces, FullVertexView source, Material material, int a, int b, int c) {
        Vertex first = new Vertex(source, a), second = new Vertex(source, b), third = new Vertex(source, c);
        if (first.samePosition(second) || second.samePosition(third) || third.samePosition(first)) return;
        var unit = new FullVertexView();
        var memory = MemoryBlock.mallocTracked(4 * unit.stride());
        unit.nativeMemoryOwner(memory); unit.ptr(memory.ptr()); unit.vertexCount(4);
        int[] indices = {a, b, c, c};
        for (int i = 0; i < 4; i++) {
            source.write(unit, indices[i], i);
            unit.x(i, i == 1 ? 1 : 0); unit.y(i, i >= 2 ? 1 : 0); unit.z(i, 0);
            unit.normalX(i, i == 0 ? 1 : 0); unit.normalY(i, i == 1 ? 1 : 0); unit.normalZ(i, i >= 2 ? 1 : 0);
        }
        faces.add(new Face(new SingleMeshModel(new SimpleQuadMesh(unit, "HeXie cable triangle"), material), first, second, third));
    }

    private record Vertex(float x, float y, float z, float nx, float ny, float nz) {
        Vertex(FullVertexView vertices, int index) {
            this(vertices.x(index), vertices.y(index), vertices.z(index), vertices.normalX(index), vertices.normalY(index), vertices.normalZ(index));
        }
        boolean samePosition(Vertex other) { return x == other.x && y == other.y && z == other.z; }
        Vector3f position(CableDeformation deformation) { return deformation.position(x, y, z, new Vector3f()); }
        void normal(CableDeformation deformation, PoseStack.Pose mount, Vector3f destination) {
            deformation.normal(x, y, z, nx, ny, nz, destination);
            mount.normal().transform(destination).normalize();
        }
    }

    public record Face(Model model, Vertex a, Vertex b, Vertex c) {
        public void deform(PosedInstance instance, PoseStack.Pose mount, CableDeformation deformation, Vector3f normal) {
            Vector3f first = a.position(deformation), edgeB = b.position(deformation).sub(first), edgeC = c.position(deformation).sub(first);
            normal.set(edgeB).cross(edgeC);
            if (normal.lengthSquared() > 1e-14f) normal.normalize();
            else normal.set(0, 0, 1);
            instance.pose.set(edgeB.x, edgeB.y, edgeB.z, 0, edgeC.x, edgeC.y, edgeC.z, 0,
                    normal.x, normal.y, normal.z, 0, first.x, first.y, first.z, 1);
            mount.pose().mul(instance.pose, instance.pose);
            a.normal(deformation, mount, normal); instance.normal.setColumn(0, normal);
            b.normal(deformation, mount, normal); instance.normal.setColumn(1, normal);
            c.normal(deformation, mount, normal); instance.normal.setColumn(2, normal);
        }
    }
}
