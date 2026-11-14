package willow.train.hexie.client.render.track;

import com.simibubi.create.content.trains.track.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.BlockGetter;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.function.UnaryOperator;
import willow.train.hexie.compat.create.railx.RailxTracks;
import static willow.train.hexie.client.render.track.BallastMesh.*;

public final class BallastModels {
    /** A block face can occupy just one panel of a sprite shared by several block models. */
    public static final class Material {
        private final TextureAtlasSprite sprite;
        private final int color;
        private final float[] uv = new float[8];

        Material(TextureAtlasSprite sprite, int color, BakedQuad face) {
            this.sprite = sprite;
            this.color = color;
            for (int i = 0; i < 4; i++) {
                if (face == null) {
                    uv[2 * i] = sprite.getU(i >= 2 ? 1 : 0);
                    uv[2 * i + 1] = sprite.getV(i == 1 || i == 2 ? 1 : 0);
                } else {
                    var vertices = face.getVertices();
                    int offset = i * (vertices.length / 4);
                    uv[2 * i] = Float.intBitsToFloat(vertices[offset + 4]);
                    uv[2 * i + 1] = Float.intBitsToFloat(vertices[offset + 5]);
                }
            }
        }

        public TextureAtlasSprite sprite() { return sprite; }
        public int color() { return color; }
        public float atlasU(double u, double v) { return interpolate(0, u, v); }
        public float atlasV(double u, double v) { return interpolate(1, u, v); }

        ResourceLocation curveTexture(boolean repeating) {
            return CurveBedTexture.forFace(sprite, uv, repeating);
        }

        private float interpolate(int c, double u, double v) {
            // Use the model's four atlas coordinates, including its rotation, mirroring and baked UV inset.
            u = .0001 + Math.clamp(u, 0, 1) * .9998;
            v = .0001 + Math.clamp(v, 0, 1) * .9998;
            double first = uv[c] + u * (uv[6 + c] - uv[c]);
            double last = uv[2 + c] + u * (uv[4 + c] - uv[2 + c]);
            return (float) (first + v * (last - first));
        }
    }
    private record Key(TrackShape shape, BlockState texture) {}
    private static final Map<Key,List<BakedQuad>> STRAIGHTS = new java.util.concurrent.ConcurrentHashMap<>();
    public static void clear() { STRAIGHTS.clear(); SlabTrackModels.clear(); }
    public static Material material(BlockState state) {
        var mc=Minecraft.getInstance();
        var model=mc.getBlockRenderer().getBlockModel(state);
        var top=model.getQuads(state,Direction.UP,RandomSource.create(42),ModelData.EMPTY,null);
        BakedQuad face = top.isEmpty() ? null : top.getFirst();
        if (face == null) {
            var unculled = model.getQuads(state,null,RandomSource.create(42),ModelData.EMPTY,null);
            face = unculled.stream().filter(q -> q.getDirection() == Direction.UP).findFirst()
                    .orElse(unculled.isEmpty() ? null : unculled.getFirst());
        }
        if (face == null) {
            for (var side : Direction.values()) {
                var quads = model.getQuads(state,side,RandomSource.create(42),ModelData.EMPTY,null);
                if (!quads.isEmpty()) { face = quads.getFirst(); break; }
            }
        }
        if (face == null) return new Material(mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(MissingTextureAtlasSprite.getLocation()), 0xffffff, null);
        int color=face.isTinted()?mc.getBlockColors().getColor(state,null,null,face.getTintIndex()):0xffffff;
        return new Material(face.getSprite(),color,face);
    }
    public static List<BakedQuad> straight(TrackShape shape,BlockState texture) {
        return STRAIGHTS.computeIfAbsent(new Key(shape,texture),
                key -> bakeStraight(shape, texture, UnaryOperator.identity()));
    }
    public static List<BakedQuad> bakeStraight(TrackShape shape, BlockState texture, UnaryOperator<Point> renderPosition) {
        if(shape==TrackShape.NONE)return List.of();
        List<BakedQuad> result=new ArrayList<>();
        var material=material(texture);
        for(Vec3 axis:shape.getAxes()) {
            Vec3 center=new Vec3(.5,axis.y==0?0:.5,.5);
            Point side=point(new Vec3(axis.z,0,-axis.x).normalize());
            var mesh=sweep(List.of(new Frame(point(center.subtract(axis.scale(.5))),side),
                    new Frame(point(center.add(axis.scale(.5))),side)), renderPosition);
            for(Quad quad:mesh)result.add(bake(quad,material));
        }
        return List.copyOf(result);
    }
    public static List<Quad> curve(BezierConnection curve) {
        return curve(curve, inferredEnds(curve));
    }

    static List<Quad> curve(BezierConnection curve, CurveEnds ends) {
        return sweepCurve(TrackBedSampling.simplify(curveFrames(curve, ends)));
    }

    enum End { NORMAL, ASCENDING, FLEXIBLE }
    record CurveEnds(End first, End second) {
        End get(boolean firstEnd) { return firstEnd ? first : second; }
    }

    static CurveEnds curveEnds(BezierConnection curve, BlockGetter level, CurveEnds previous) {
        return new CurveEnds(placedEnd(curve, level, true, previous), placedEnd(curve, level, false, previous));
    }

    private static End placedEnd(BezierConnection curve, BlockGetter level, boolean first, CurveEnds previous) {
        var state = level.getBlockState(curve.bePositions.get(first));
        if (state.getBlock() instanceof TrackBlock) {
            if (ModList.get().isLoaded("railx") && RailxTracks.isFlexible(state)) return End.FLEXIBLE;
            return state.getValue(TrackBlock.SHAPE).getAxes().stream().anyMatch(axis -> axis.y != 0)
                    ? End.ASCENDING : End.NORMAL;
        }
        // A remote chunk may be absent. Keep its last known placement until it loads;
        // geometry is only a fallback for connections whose remote block has never been seen.
        return previous == null ? inferredEnd(curve, first) : previous.get(first);
    }

    private static CurveEnds inferredEnds(BezierConnection curve) {
        return new CurveEnds(inferredEnd(curve, true), inferredEnd(curve, false));
    }

    private static End inferredEnd(BezierConnection curve, boolean first) {
        Vec3 anchor = Vec3.atBottomCenterOf(curve.bePositions.get(first));
        Vec3 side = curve.normals.get(first).cross(curve.axes.get(first)).normalize();
        if (Math.abs(side.y) > 1e-6 || curve.starts.get(first).closerThan(anchor.add(curve.axes.get(first).scale(.5)), 1e-5))
            return End.FLEXIBLE;
        return meetsAscendingBlock(curve, first) ? End.ASCENDING : End.NORMAL;
    }

    static List<Frame> curveFrames(BezierConnection curve) {
        return curveFrames(curve, inferredEnds(curve));
    }

    static List<Frame> curveFrames(BezierConnection curve, CurveEnds ends) {
        int count = curve.getSegmentCount();
        List<Frame> frames = new ArrayList<>(count + 1);
        for (BezierConnection.Segment segment : curve) {
            // RailX stores cant in the connection's endpoint normals. Create's iterator transports
            // them along the tangent and returns exactly the lateral axis used to place both rails.
            Vec3 side = segment.normal;
            Vec3 up = segment.derivative.cross(side).normalize();
            Vec3 base = segment.position.subtract(up.scale(3.0 / 16));
            if (segment.index == 0 || segment.index == count) {
                boolean first = segment.index == 0;
                // Flexible straights rotate about y=1/8, while Create's curve segments are
                // elevated by 3/16. Meet the straight at its actual rotated edge.
                if (ends.get(first) == End.FLEXIBLE)
                    base = base.add(up.x / 16, (up.y - 1) / 16, up.z / 16);
                else if (ends.get(first) == End.ASCENDING) {
                    // The static 45-degree model ends on a vertical block face.
                    base = segment.position.subtract(0, 3.0 / 16, 0);
                    up = new Vec3(0, 1, 0);
                }
            }
            frames.add(new Frame(point(base), point(side), point(up)));
        }
        return frames;
    }

    static boolean meetsAscendingBlock(BezierConnection curve, boolean first) {
        Vec3 axis = curve.axes.get(first).normalize();
        if (Math.abs(Math.abs(axis.y) - Math.sqrt(.5)) > 1e-6) return false;
        // Create's ascending block spans a whole block horizontally and vertically.
        // A flexible axis of the same pitch has a half-metre handle from its bottom center.
        Vec3 edge = Vec3.atCenterOf(curve.bePositions.get(first)).add(axis.scale(Math.sqrt(.5)));
        return edge.closerThan(curve.starts.get(first), 1e-5);
    }
    private static Point point(Vec3 v){return new Point(v.x,v.y,v.z);}
    static BakedQuad bake(Quad quad,Material material) {
        int[] data=new int[32];
        Vertex[] vertices={quad.a(),quad.b(),quad.c(),quad.d()};
        Point n=quad.normal();
        int normal=((byte)(n.x()*127)&255)|(((byte)(n.y()*127)&255)<<8)|(((byte)(n.z()*127)&255)<<16);
        int rgb=material.color();
        int color=0xff000000|((rgb&255)<<16)|(rgb&0xff00)|((rgb>>16)&255);
        for(int i=0;i<4;i++) {
            Vertex v=vertices[i];int o=i*8;
            data[o]=Float.floatToRawIntBits((float)v.p().x()); data[o+1]=Float.floatToRawIntBits((float)v.p().y());
            data[o+2]=Float.floatToRawIntBits((float)v.p().z());data[o+3]=color;
            data[o+4]=Float.floatToRawIntBits(material.atlasU(v.u(),v.v()));
            data[o+5]=Float.floatToRawIntBits(material.atlasV(v.u(),v.v()));data[o+7]=normal;
        }
        return new BakedQuad(data,-1,Direction.getNearest((float)n.x(),(float)n.y(),(float)n.z()),material.sprite(),true);
    }
}
