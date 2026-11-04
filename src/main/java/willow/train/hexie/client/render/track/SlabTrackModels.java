package willow.train.hexie.client.render.track;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackShape;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import willow.train.hexie.init.track.TrackMaterialInit;

import java.util.List;

import static willow.train.hexie.client.render.track.BallastMesh.*;
import static willow.train.hexie.client.render.track.SlabTrackMesh.*;

public final class SlabTrackModels {
    // In ascending.obj the mounting foot lies on y - z = 0.12938972564.
    // A 1/16 m recess normal to its 45-degree plane corresponds to sqrt(2)/16 vertically.
    private static final double ASCENDING_HEIGHT = .12938972564 + Math.sqrt(2) / 16;
    static final ModelProperty<BlockPos> POSITION = new ModelProperty<>();
    public static final ModelProperty<Double> FLEXI_PHASE = new ModelProperty<>();
    private record Key(Vec3 axis, double phase) {}
    // Diagonals have irrational lengths and can start at arbitrarily many phases.
    private static final Cache<Key, List<BakedQuad>> STRAIGHTS = CacheBuilder.newBuilder().maximumSize(256).build();

    public static boolean applies(BlockState state) {
        return state.getBlock() instanceof TrackBlock track && track.getMaterial() == TrackMaterialInit.BALLASTLESS;
    }

    static void clear() { STRAIGHTS.invalidateAll(); }

    static void addStraight(List<BakedQuad> result, TrackShape shape, BlockPos pos, Double flexiPhase) {
        if (shape == TrackShape.NONE) return;
        Vec3 origin = Vec3.atLowerCornerOf(pos);
        for (Vec3 axis : shape.getAxes()) {
            Vec3 center = new Vec3(.5, axis.y == 0 ? 0 : .5, .5);
            Vec3 start = center.subtract(axis.scale(.5));
            double phase = flexiPhase != null ? flexiPhase : phase(origin.add(start).dot(axis.normalize()));
            Key key = new Key(axis, phase);
            result.addAll(STRAIGHTS.asMap().computeIfAbsent(key, ignored -> bakeStraight(key, start)));
        }
    }

    private static List<BakedQuad> bakeStraight(Key key, Vec3 start) {
        Vec3 axis = key.axis;
        Point side = point(new Vec3(axis.z, 0, -axis.x).normalize());
        double top = axis.y == 0 ? HEIGHT : ASCENDING_HEIGHT;
        var frames = List.of(new Frame(point(start), side), new Frame(point(start.add(axis)), side));
        List<Quad> mesh = build(frames, TrackBedSampling.sample(frames), key.phase, top, top);
        var stone = BallastModels.material(Blocks.SMOOTH_STONE.defaultBlockState());
        return mesh.stream().map(quad -> BallastModels.bake(quad, stone)).toList();
    }

    static List<Quad> curve(BezierConnection curve) {
        List<Frame> frames = BallastModels.curveFrames(curve);
        if (frames.size() < 2) return List.of();
        Frame first = frames.getFirst();
        Point world = first.center().add(point(Vec3.atLowerCornerOf(curve.bePositions.getFirst())));
        Point tangent = point(curve.axes.getFirst().normalize());
        double start = world.x() * tangent.x() + world.y() * tangent.y() + world.z() * tangent.z();
        double fromHeight = BallastModels.meetsAscendingBlock(curve, true) ? ASCENDING_HEIGHT : HEIGHT;
        double toHeight = BallastModels.meetsAscendingBlock(curve, false) ? ASCENDING_HEIGHT : HEIGHT;
        return build(frames, TrackBedSampling.sample(frames), phase(start), fromHeight, toHeight);
    }

    private static Point point(Vec3 v) { return new Point(v.x, v.y, v.z); }
}
