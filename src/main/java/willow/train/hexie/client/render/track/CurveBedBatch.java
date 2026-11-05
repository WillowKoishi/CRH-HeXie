package willow.train.hexie.client.render.track;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteOrder;
import java.util.*;
import static willow.train.hexie.client.render.track.BallastMesh.*;

/** One buffer per material and region. Pieces belong to individual curves so edits can remove just their share. */
final class CurveBedBatch implements AutoCloseable {
    private static final VertexFormat.Mode MODE = VertexFormat.Mode.QUADS;
    private final int tint;
    final ResourceLocation texture;
    private final List<Piece> pieces = new ArrayList<>();
    private VertexBuffer buffer;
    private boolean dirty;

    CurveBedBatch(int tint, ResourceLocation texture) {
        this.tint = tint;
        this.texture = texture;
    }

    Piece add(ClientLevel level, long region, int lightBit, BlockPos owner, BlockPos regionOrigin, List<Quad> faces) {
        var piece = new Piece(level, region, lightBit, tint, owner, regionOrigin, faces);
        pieces.add(piece);
        dirty = true;
        return piece;
    }

    boolean remove(Piece piece) {
        pieces.remove(piece);
        dirty = true;
        return pieces.isEmpty();
    }

    void draw(ClientLevel level, int changedLightSections) {
        if (changedLightSections != 0) for (var piece : pieces) {
            if ((changedLightSections & piece.lightBit) != 0 && piece.relight(level)) dirty = true;
        }
        if (dirty) upload();
        buffer.bind();
        buffer.draw();
    }

    private void upload() {
        int words = 0;
        for (var piece : pieces) words += piece.vertices.length;
        try (var memory = new ByteBufferBuilder(words * Integer.BYTES)) {
            var packed = MemoryUtil.memIntBuffer(memory.reserve(words * Integer.BYTES), words);
            for (var piece : pieces) packed.put(piece.vertices);
            var format = pieces.getFirst().format;
            int count = words / (format.getVertexSize() / Integer.BYTES);
            var state = new MeshData.DrawState(format, count, MODE.indexCount(count), MODE, VertexFormat.IndexType.least(count));
            if (buffer == null) buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            buffer.bind();
            buffer.upload(new MeshData(Objects.requireNonNull(memory.build()), state));
        }
        dirty = false;
    }

    @Override public void close() { if (buffer != null) buffer.close(); }

    static BlockPos lightProbe(BlockPos origin, Quad q) {
        Point a = q.a().p(), b = q.b().p(), c = q.c().p(), d = q.d().p();
        return origin.offset(BlockPos.containing((a.x()+b.x()+c.x()+d.x())*.25, (a.y()+b.y()+c.y()+d.y())*.25+.5,
                (a.z()+b.z()+c.z()+d.z())*.25));
    }

    /** Packed geometry survives light changes. Only light words in affected sections need recomputing. */
    static final class Piece {
        final long region;
        final int lightBit;
        final int[] vertices;
        private final VertexFormat format;
        private final BlockPos[] probes;
        private final int[] probeLights, faceProbe;

        Piece(ClientLevel level, long region, int lightBit, int rgb, BlockPos origin, BlockPos regionOrigin, List<Quad> faces) {
            this.region = region;
            this.lightBit = lightBit;
            int size = faces.size();
            faceProbe = new int[size];
            var unique = new LinkedHashMap<BlockPos,Integer>();
            var delta = origin.subtract(regionOrigin);
            int bytes = size * 4 * DefaultVertexFormat.BLOCK.getVertexSize();
            try (var memory = new ByteBufferBuilder(bytes)) {
                var builder = new BufferBuilder(memory, MODE, DefaultVertexFormat.BLOCK);
                for (int i = 0; i < size; i++) {
                    var q = faces.get(i);
                    faceProbe[i] = unique.computeIfAbsent(lightProbe(origin, q), pos -> unique.size());
                    var n = q.normal();
                    float shade = level.getShade(Direction.getNearest((float)n.x(), (float)n.y(), (float)n.z()), true);
                    int red = (int)(((rgb >> 16) & 255)*shade), green = (int)(((rgb >> 8) & 255)*shade), blue = (int)((rgb & 255)*shade);
                    vertex(builder, delta, q.a(), n, red, green, blue);
                    vertex(builder, delta, q.b(), n, red, green, blue);
                    vertex(builder, delta, q.c(), n, red, green, blue);
                    vertex(builder, delta, q.d(), n, red, green, blue);
                }
                try (var mesh = builder.buildOrThrow()) {
                    // Shader packs can extend BLOCK with tangent, mid-UV and block attributes.
                    format = mesh.drawState().format();
                    var packed = mesh.vertexBuffer().order(ByteOrder.nativeOrder()).asIntBuffer();
                    vertices = new int[packed.remaining()];
                    packed.get(vertices);
                }
            }
            probes = unique.keySet().toArray(BlockPos[]::new);
            probeLights = new int[probes.length];
            Arrays.fill(probeLights, -1);
            relight(level);
        }

        private static void vertex(BufferBuilder builder, BlockPos delta, Vertex v, Point n, int r, int g, int b) {
            var p = v.p();
            builder.addVertex((float)(delta.getX()+p.x()), (float)(delta.getY()+p.y()), (float)(delta.getZ()+p.z()))
                    .setColor(r, g, b, 255).setUv((float)v.u(), (float)v.v()).setLight(0)
                    .setNormal((float)n.x(), (float)n.y(), (float)n.z());
        }

        boolean relight(ClientLevel level) {
            boolean changed = false;
            for (int i = 0; i < probes.length; i++) {
                int light = LevelRenderer.getLightColor(level, probes[i]);
                if (probeLights[i] != light) { probeLights[i] = light; changed = true; }
            }
            if (changed) {
                int stride = format.getVertexSize() / Integer.BYTES;
                int lightOffset = format.getOffset(VertexFormatElement.UV2) / Integer.BYTES;
                for (int face = 0; face < faceProbe.length; face++) {
                    int light = probeLights[faceProbe[face]];
                    for (int corner = 0; corner < 4; corner++) vertices[(4*face+corner)*stride+lightOffset] = light;
                }
            }
            return changed;
        }
    }
}
