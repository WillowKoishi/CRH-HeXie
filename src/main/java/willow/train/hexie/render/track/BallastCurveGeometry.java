package willow.train.hexie.render.track;

import com.simibubi.create.content.trains.track.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import static willow.train.hexie.render.track.BallastMesh.*;
import static willow.train.hexie.render.track.BallastModels.point;
import willow.train.hexie.bridge.HexieBridges;

// split from willow/train/hexie/client/render/track/BallastModels.java, lines 104-182
final class BallastCurveGeometry {
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

    static End placedEnd(BezierConnection curve, BlockGetter level, boolean first, CurveEnds previous) {
        var state = level.getBlockState(curve.bePositions.get(first));
        if (state.getBlock() instanceof TrackBlock) {
            if (ModList.get().isLoaded("railx") && HexieBridges.railx().isFlexible(state)) return End.FLEXIBLE;
            return state.getValue(TrackBlock.SHAPE).getAxes().stream().anyMatch(axis -> axis.y != 0)
                    ? End.ASCENDING : End.NORMAL;
        }
        // A remote chunk may be absent. Keep its last known placement until it loads;
        // geometry is only a fallback for connections whose remote block has never been seen.
        return previous == null ? inferredEnd(curve, first) : previous.get(first);
    }

    static CurveEnds inferredEnds(BezierConnection curve) {
        return new CurveEnds(inferredEnd(curve, true), inferredEnd(curve, false));
    }

    static End inferredEnd(BezierConnection curve, boolean first) {
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
}
