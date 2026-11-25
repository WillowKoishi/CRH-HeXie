package willow.train.hexie.render.display;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.HexieBridges;

import static willow.train.hexie.render.display.PassengerPanelModels.EPS;
import static willow.train.hexie.render.display.PassengerPanelModels.Face;
import static willow.train.hexie.render.display.PassengerPanelModels.faces;
import static willow.train.hexie.render.display.PassengerPanelQuads.normal;
import static willow.train.hexie.render.display.PassengerPanelQuads.vertices;

// split from willow/train/hexie/client/render/display/PassengerPanelModels.java, lines 39-41, 151-278
final class PassengerPanelExterior {
    record Plane(Vec3 center, Vec3 right, Vec3 up, Vec3 normal, boolean layered) {
        Plane(Vec3 center, Vec3 right, Vec3 up, Vec3 normal) { this(center, right, up, normal, false); }
    }

    static Plane exterior(List<Face> faces, BlockState state) {
        if (faces.isEmpty() || !state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return null;
        // Both old carriage panels and CRH2 parts use the opposite of FACING as their painted exterior.
        var outward = Vec3.atLowerCornerOf(state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite().getNormal());
        return exterior(faces, outward, HexieBridges.signBlock().panelDisplayColumn().offset(HexieBridges.signBlock().panelDisplayColumn().get(state)));
    }

    static Plane exterior(List<Face> faces, Vec3 outward) {
        return exterior(faces, outward, 0);
    }

    static Plane exterior(List<Face> faces, Vec3 outward, int column) {
        var right = new Vec3(outward.z, 0, -outward.x);
        double left = Double.POSITIVE_INFINITY, rightmost = Double.NEGATIVE_INFINITY, top = Double.NEGATIVE_INFINITY;
        for (var face : faces) for (var vertex : vertices(face.quad())) {
            top = Math.max(top, vertex.position().y);
            double horizontal = vertex.position().dot(right);
            left = Math.min(left, horizontal); rightmost = Math.max(rightmost, horizontal);
        }
        // Columns are physical cell offsets along the readable exterior-right axis, including mirrored panels.
        double y = top - .25, horizontal = new Vec3(.5, 0, .5).dot(right) + column;
        Plane selected = null;
        double depth = Double.NEGATIVE_INFINITY;
        for (var face : faces) {
            var vertices = vertices(face.quad());
            var normal = normal(vertices);
            if (normal.dot(outward) < .9) continue;
            double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY;
            for (var vertex : vertices) { low = Math.min(low, vertex.position().y); high = Math.max(high, vertex.position().y); }
            if (y < low - EPS || y > high + EPS) continue;
            double constant = normal.dot(vertices.getFirst().position());
            if (!coversPair(faces, normal, constant, right, horizontal, y)) continue;
            var center = right.scale(horizontal).add(0, y, 0);
            double candidate = (constant - normal.dot(center)) / normal.dot(outward);
            if (candidate <= depth) continue;
            depth = candidate;
            selected = new Plane(center.add(outward.scale(candidate)), right,
                    normal.cross(right).normalize(), normal);
        }
        if (selected != null) return selected;
        // Sealed windows have coplanar painted jambs around a stepped pane and narrow frames.
        // Anchor to both outer jambs, not the projecting frame or the recessed glass.
        for (var face : faces) {
            var vertices = vertices(face.quad());
            var normal = normal(vertices);
            if (normal.dot(outward) < .9) continue;
            double constant = normal.dot(vertices.getFirst().position());
            var up = normal.cross(right).normalize();
            boolean supported = true;
            for (double row : new double[] {y - up.y * 2.75 / 32, y, y + up.y * 2.75 / 32}) {
                var jambs = rowIntervals(faces, normal, constant, right, row);
                var surface = rowIntervals(faces, normal, Double.NaN, right, row);
                if (!coversRange(jambs, left + EPS, left + EPS, EPS)
                        || !coversRange(jambs, rightmost - EPS, rightmost - EPS, EPS)
                        || !coversRange(surface, horizontal - 6.95 / 16, horizontal + 6.95 / 16, 1 / 64d)) {
                    supported = false; break;
                }
            }
            if (!supported) continue;
            var center = right.scale(horizontal).add(0, y, 0);
            double candidate = (constant - normal.dot(center)) / normal.dot(outward);
            if (candidate <= depth) continue;
            depth = candidate;
            selected = new Plane(center.add(outward.scale(candidate)), right, up, normal, true);
        }
        return selected;
    }

    /** A trim strip must not move both displays onto its extended, but nonexistent, plane. */
    private static boolean coversPair(List<Face> faces, Vec3 normal, double constant,
                                      Vec3 right, double horizontal, double y) {
        double halfHeight = normal.cross(right).normalize().y * 2.75 / 32;
        for (double row : new double[] {y - halfHeight, y, y + halfHeight})
            if (!coversRow(faces, normal, constant, right, horizontal, row)) return false;
        return true;
    }

    private static boolean coversRow(List<Face> faces, Vec3 normal, double constant,
                                     Vec3 right, double horizontal, double y) {
        return coversRange(rowIntervals(faces, normal, constant, right, y),
                horizontal - 6.95 / 16, horizontal + 6.95 / 16, EPS);
    }

    private static List<double[]> rowIntervals(List<Face> faces, Vec3 normal, double constant, Vec3 right, double y) {
        var intervals = new ArrayList<double[]>();
        for (var face : faces) {
            var vertices = vertices(face.quad());
            if (normal(vertices).dot(normal) < .9999) continue;
            if (!Double.isNaN(constant)
                    && vertices.stream().anyMatch(v -> Math.abs(normal.dot(v.position()) - constant) > EPS)) continue;
            double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY;
            var previous = vertices.getLast().position();
            for (var vertex : vertices) {
                var current = vertex.position();
                if (Math.abs(current.y - y) < EPS) {
                    double at = current.dot(right); low = Math.min(low, at); high = Math.max(high, at);
                }
                if ((current.y < y && previous.y > y) || (current.y > y && previous.y < y)) {
                    double amount = (y - previous.y) / (current.y - previous.y);
                    double at = previous.lerp(current, amount).dot(right);
                    low = Math.min(low, at); high = Math.max(high, at);
                }
                previous = current;
            }
            if (high >= low) intervals.add(new double[] {low, high});
        }
        intervals.sort(java.util.Comparator.comparingDouble(interval -> interval[0]));
        return intervals;
    }

    private static boolean coversRange(List<double[]> intervals, double from, double target, double tolerance) {
        double covered = from;
        for (var interval : intervals) {
            if (interval[1] < from - tolerance) continue;
            if (interval[0] > covered + tolerance) break;
            covered = Math.max(covered, interval[1]);
            if (covered >= target - tolerance) return true;
        }
        return false;
    }

    static boolean onExterior(BakedQuad quad, Plane plane) {
        var vertices = vertices(quad);
        if (normal(vertices).dot(plane.normal) < .9999) return false;
        for (var vertex : vertices)
            if (Math.abs(vertex.position().subtract(plane.center).dot(plane.normal)) > EPS) return false;
        return true;
    }
}
