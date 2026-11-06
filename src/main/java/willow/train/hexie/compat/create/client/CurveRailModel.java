package willow.train.hexie.compat.create.client;

import com.simibubi.create.content.trains.track.BezierConnection;
import dev.engine_room.flywheel.api.material.CardinalLightingMode;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.material.LightShaders;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.model.ModelUtil;
import dev.engine_room.flywheel.lib.model.baked.BakedModelBuilder;
import dev.engine_room.flywheel.lib.util.RendererReloadCache;
import net.createmod.catnip.render.SuperBufferFactory;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import willow.train.hexie.compat.create.TrackRailSurface;
import willow.train.hexie.init.track.TrackMaterialInit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Fit both ends of the existing textured partial to shared sections. Create retains its
 * segment transforms, lighting, visibility and instance lifetime on both render paths. */
public final class CurveRailModel extends BakedModelWrapper<BakedModel> {
    private record Pieces(TrackRailSurface surface, CurveRailModel[] models) {}
    private static final RendererReloadCache<Boolean, Map<BezierConnection.SegmentAngles, Pieces>> CACHE =
            new RendererReloadCache<>(unused -> new WeakHashMap<>());

    private final TrackRailSurface.Section from, to;
    private final Vec3 origin;
    private final Matrix4f inverse;
    private final double railOffset;
    private SuperByteBuffer buffer;
    private Model model;

    public static boolean applies(BezierConnection curve) {
        var material = curve.getMaterial();
        return material == TrackMaterialInit.STANDARD || material == TrackMaterialInit.BALLASTLESS
                || material == TrackMaterialInit.TIELESS;
    }

    public static synchronized CurveRailModel get(BezierConnection curve, int index, boolean first) {
        var angles = curve.getBakedSegments();
        var pieces = CACHE.get(true).computeIfAbsent(angles,
                unused -> new Pieces(new TrackRailSurface(curve), new CurveRailModel[2 * angles.length]));
        int key = 2 * index + (first ? 0 : 1);
        if (pieces.models[key] == null)
            pieces.models[key] = new CurveRailModel(curve, index, first, pieces.surface);
        return pieces.models[key];
    }

    private CurveRailModel(BezierConnection curve, int index, boolean first, TrackRailSurface surface) {
        super((first ? curve.getMaterial().getModelHolder().leftSegment()
                : curve.getMaterial().getModelHolder().rightSegment()).get());
        from = surface.section(index - 1);
        to = surface.section(index);
        origin = Vec3.atLowerCornerOf(curve.bePositions.getFirst());
        inverse = new Matrix4f(curve.getBakedSegments().railTransforms[index].get(first).pose()).invert();
        railOffset = first ? TrackRailSurface.HALF_GAUGE : -TrackRailSurface.HALF_GAUGE;
    }

    public synchronized SuperByteBuffer buffer() {
        if (buffer == null) buffer = SuperBufferFactory.getInstance().createForBlock(this, Blocks.AIR.defaultBlockState());
        return buffer;
    }

    /** One instanced mesh for the whole curve. Its vertices are in the track owner's
     * coordinates, so Flywheel can draw and light all sections in one call. */
    public static synchronized Model wholeCurve(BezierConnection curve) {
        var first = get(curve, 1, true);
        if (first.model != null) return first.model;
        var parts = new ArrayList<CurveRailModel>();
        for (int i = 1; i <= curve.getSegmentCount(); i++) {
            parts.add(get(curve, i, true));
            parts.add(get(curve, i, false));
        }
        var mesh = new BakedModelWrapper<BakedModel>(first.originalModel) {
            @Override public List<BakedQuad> getQuads(BlockState state, Direction face, RandomSource random) {
                return getQuads(state, face, random, ModelData.EMPTY, null);
            }

            @Override public List<BakedQuad> getQuads(BlockState state, Direction face, RandomSource random,
                                                       ModelData data, RenderType layer) {
                var result = new ArrayList<BakedQuad>();
                for (var part : parts)
                    for (var quad : part.originalModel.getQuads(state, face, random, data, layer))
                        result.add(part.fit(quad, false));
                return result;
            }
        };
        first.model = new BakedModelBuilder(mesh).materialFunc((type, shaded, ao) -> {
            var base = ModelUtil.getMaterial(type, shaded, ao);
            return base == null ? null : SimpleMaterial.builderOf(base).light(LightShaders.SMOOTH)
                    .cardinalLightingMode(shaded ? CardinalLightingMode.CHUNK : CardinalLightingMode.OFF).build();
        }).build();
        return first.model;
    }

    @Override public List<BakedQuad> getQuads(BlockState state, Direction face, RandomSource random) {
        return getQuads(state, face, random, ModelData.EMPTY, null);
    }

    @Override public List<BakedQuad> getQuads(BlockState state, Direction face, RandomSource random,
                                              ModelData data, RenderType layer) {
        return originalModel.getQuads(state, face, random, data, layer).stream().map(q -> fit(q, true)).toList();
    }

    private BakedQuad fit(BakedQuad quad, boolean segmentLocal) {
        int[] vertices = quad.getVertices().clone();
        int stride = vertices.length / 4;
        Vector3f[] points = new Vector3f[4];
        for (int i = 0; i < 4; i++) {
            int j = i * stride;
            double x = Float.intBitsToFloat(vertices[j]), y = Float.intBitsToFloat(vertices[j + 1]);
            var section = Float.intBitsToFloat(vertices[j + 2]) < .25f ? from : to;
            Vec3 point = section.center().subtract(origin).add(section.side().scale(railOffset + x))
                    .add(section.heightAxis().scale(y));
            points[i] = point.toVector3f();
            if (segmentLocal) inverse.transformPosition(points[i]);
            vertices[j] = Float.floatToRawIntBits(points[i].x);
            vertices[j + 1] = Float.floatToRawIntBits(points[i].y);
            vertices[j + 2] = Float.floatToRawIntBits(points[i].z);
        }
        Vector3f n = points[2].sub(points[0], new Vector3f()).cross(points[3].sub(points[1])).normalize();
        int packed = Math.round(127 * n.x) & 255 | (Math.round(127 * n.y) & 255) << 8
                | (Math.round(127 * n.z) & 255) << 16;
        for (int i = 0; i < 4; i++) vertices[i * stride + 7] = packed;
        return new BakedQuad(vertices, quad.getTintIndex(), Direction.getNearest(n.x, n.y, n.z),
                quad.getSprite(), quad.isShade(), quad.hasAmbientOcclusion());
    }
}
