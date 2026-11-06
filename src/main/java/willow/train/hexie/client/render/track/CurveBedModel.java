package willow.train.hexie.client.render.track;

import dev.engine_room.flywheel.api.material.CardinalLightingMode;
import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.material.LightShaders;
import dev.engine_room.flywheel.lib.material.Materials;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.memory.MemoryBlock;
import dev.engine_room.flywheel.lib.model.EmptyModel;
import dev.engine_room.flywheel.lib.model.SimpleQuadMesh;
import dev.engine_room.flywheel.lib.model.SingleMeshModel;
import dev.engine_room.flywheel.lib.vertex.PosTexNormalVertexView;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.List;

import static willow.train.hexie.client.render.track.BallastMesh.*;

final class CurveBedModel {
    private static final Material BED = SimpleMaterial.builderOf(Materials.CUTOUT_MIPPED_BLOCK)
            .light(LightShaders.SMOOTH).cardinalLightingMode(CardinalLightingMode.CHUNK).build();

    private CurveBedModel() {}

    static Model of(List<Quad> faces, ResourceLocation texture) {
        if (faces.isEmpty()) return EmptyModel.INSTANCE;
        // The selected face is the whole bound texture. The sampler repeats even across long spans.
        var vertices = new PosTexNormalVertexView();
        int count = Math.multiplyExact(faces.size(), 4);
        var memory = MemoryBlock.mallocTracked(count * vertices.stride());
        vertices.nativeMemoryOwner(memory);
        vertices.ptr(memory.ptr());
        vertices.vertexCount(count);
        int i = 0;
        for (Quad face : faces) {
            put(vertices, i++, face.a(), face.normal());
            put(vertices, i++, face.b(), face.normal());
            put(vertices, i++, face.c(), face.normal());
            put(vertices, i++, face.d(), face.normal());
        }
        // Flywheel computes the endpoint-local bounding sphere once and shares its standard quad indices.
        return new SingleMeshModel(new SimpleQuadMesh(vertices, "HeXie curve bed"),
                SimpleMaterial.builderOf(BED).texture(texture).build());
    }

    static LongSet lightSections(BlockPos origin, List<Quad> faces) {
        var sections = new LongOpenHashSet();
        for (Quad face : faces) {
            Point a = face.a().p(), b = face.b().p(), c = face.c().p(), d = face.d().p();
            // Each short face needs its own section and neighbours within one block for smooth light.
            int minX = lowerSection(origin.getX(), a.x(), b.x(), c.x(), d.x());
            int minY = lowerSection(origin.getY(), a.y(), b.y(), c.y(), d.y());
            int minZ = lowerSection(origin.getZ(), a.z(), b.z(), c.z(), d.z());
            int maxX = upperSection(origin.getX(), a.x(), b.x(), c.x(), d.x());
            int maxY = upperSection(origin.getY(), a.y(), b.y(), c.y(), d.y());
            int maxZ = upperSection(origin.getZ(), a.z(), b.z(), c.z(), d.z());
            for (int x = minX; x <= maxX; x++)
                for (int y = minY; y <= maxY; y++)
                    for (int z = minZ; z <= maxZ; z++)
                        sections.add(SectionPos.asLong(x, y, z));
        }
        return sections;
    }

    private static int lowerSection(int origin, double a, double b, double c, double d) {
        return SectionPos.blockToSectionCoord(origin + Mth.floor(Math.min(Math.min(a, b), Math.min(c, d))) - 1);
    }

    private static int upperSection(int origin, double a, double b, double c, double d) {
        return SectionPos.blockToSectionCoord(origin + Mth.floor(Math.max(Math.max(a, b), Math.max(c, d))) + 1);
    }

    private static void put(PosTexNormalVertexView out, int i, Vertex vertex, Point normal) {
        Point p = vertex.p();
        out.x(i, (float) p.x());
        out.y(i, (float) p.y());
        out.z(i, (float) p.z());
        out.u(i, (float) vertex.u());
        out.v(i, (float) vertex.v());
        out.normalX(i, (float) normal.x());
        out.normalY(i, (float) normal.y());
        out.normalZ(i, (float) normal.z());
    }
}
