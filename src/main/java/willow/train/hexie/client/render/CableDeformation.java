package willow.train.hexie.client.render;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Transports the authored cable rings, cross sections and UVs between two moving sockets.
 * Slack changes the curve before it changes cable length; only a span longer than the
 * original centreline stretches the cable. No mesh or curve is rebuilt per vertex. */
public final class CableDeformation {
    private static final int SEGMENTS = 128;
    private static final float EPSILON = 1e-6f;

    /** Tangents point along the cable: out of the start socket, into the end socket. */
    public record Endpoint(Vector3fc position, Vector3fc tangent, Vector3fc up) {
        public Endpoint {
            position = copyFinite(position);
            tangent = unit(tangent, new Vector3f(0, 0, 1));
            up = perpendicular(up, tangent);
        }
    }

    /** Explicit source rings avoid assigning the four corners of a bend to different arc positions. */
    public record Ring(Vector3fc center, Vector3fc tangent, Vector3fc up, List<Vector3fc> vertices) {
        public Ring {
            center = copyFinite(center);
            tangent = unit(tangent, new Vector3f(0, 0, 1));
            up = perpendicular(up, tangent);
            vertices = vertices.stream().map(CableDeformation::copyFinite).map(v -> (Vector3fc) v).toList();
        }
    }

    public static final class RestShape {
        private final Path path;
        private final Endpoint start, end;
        private final Vector3f sag;
        private final ConcurrentHashMap<VertexKey, Binding> bindings = new ConcurrentHashMap<>();

        public RestShape(List<? extends Vector3fc> centers, Vector3fc referenceUp) {
            this(makeRings(centers, referenceUp));
        }

        public RestShape(List<Ring> rings) {
            if (rings.size() < 2) throw new IllegalArgumentException("A cable needs at least two rings");
            Vector3f[] positions = new Vector3f[rings.size()];
            Quaternionf[] rotations = new Quaternionf[rings.size()];
            for (int i = 0; i < rings.size(); i++) {
                Ring ring = rings.get(i);
                positions[i] = new Vector3f(ring.center());
                rotations[i] = frame(ring.tangent(), ring.up());
            }
            path = new Path(positions, rotations);
            if (!(path.length > EPSILON)) throw new IllegalArgumentException("Cable has no rest length");
            Ring a = rings.getFirst(), b = rings.getLast();
            start = new Endpoint(a.center(), a.tangent(), a.up());
            end = new Endpoint(b.center(), b.tangent(), b.up());
            Vector3f sum = new Vector3f();
            for (int i = 1; i < positions.length - 1; i++) {
                Vector3f chordPoint = new Vector3f(start.position()).lerp(end.position(), path.fractions[i]);
                sum.add(new Vector3f(positions[i]).sub(chordPoint));
            }
            sag = unit(sum, new Vector3f(referenceDown(start, end)));
            for (int i = 0; i < rings.size(); i++) for (Vector3fc vertex : rings.get(i).vertices())
                bindings.put(VertexKey.of(vertex.x(), vertex.y(), vertex.z()), bindAt(path.fractions[i], vertex.x(), vertex.y(), vertex.z()));
        }

        public Endpoint start() { return start; }
        public Endpoint end() { return end; }
        public double length() { return path.length; }
        public Vector3f sagDirection() { return new Vector3f(sag); }

        private Binding bind(float x, float y, float z) {
            return bindings.computeIfAbsent(VertexKey.of(x, y, z), ignored -> {
                float best = Float.POSITIVE_INFINITY, fraction = 0;
                Vector3f point = new Vector3f(x, y, z);
                for (int i = 0; i < path.positions.length - 1; i++) {
                    Vector3f a = path.positions[i], b = path.positions[i + 1];
                    Vector3f delta = new Vector3f(b).sub(a);
                    float lengthSquared = delta.lengthSquared();
                    float t = lengthSquared < 1e-14f ? 0 : Math.clamp(new Vector3f(point).sub(a).dot(delta) / lengthSquared, 0, 1);
                    float distance = new Vector3f(a).fma(t, delta).distanceSquared(point);
                    if (distance < best) {
                        best = distance;
                        fraction = path.fractions[i] + t * (path.fractions[i + 1] - path.fractions[i]);
                    }
                }
                return bindAt(fraction, x, y, z);
            });
        }

        private Binding bindAt(float fraction, float x, float y, float z) {
            Vector3f center = path.center(fraction, new Vector3f());
            Vector3f offset = path.rotation(fraction).conjugate().transform(new Vector3f(x, y, z).sub(center));
            return new Binding(fraction, offset.x, offset.y, offset.z);
        }
    }

    private record VertexKey(int x, int y, int z) {
        static VertexKey of(float x, float y, float z) {
            return new VertexKey(Math.round(x * 100000), Math.round(y * 100000), Math.round(z * 100000));
        }
    }
    private record Binding(float fraction, float x, float y, float z) {}
    private final RestShape rest;
    private final Path target;
    private final double length;

    private CableDeformation(RestShape rest, Path target) {
        this.rest = rest;
        this.target = target;
        length = ringLength(target, rest.path.fractions);
    }

    public RestShape restShape() { return rest; }
    /** Length of the actual mesh's ring-centre polygon, not a denser invisible spline. */
    public double length() { return length; }
    public double stretch() { return length / rest.length(); }

    public static CableDeformation solve(RestShape rest, Endpoint start, Endpoint end, Vector3fc preferredSag) {
        Objects.requireNonNull(rest);
        Vector3f displacement = new Vector3f(end.position()).sub(start.position());
        float distance = displacement.length();
        Vector3f chord = unit(displacement, start.tangent());
        Vector3f sag = perpendicular(preferredSag, chord);
        double slack = Math.max(0, rest.length() - distance);
        // The control polygon has length <= distance + 2*handle. This bound guarantees
        // the zero-sag spline fits the available length even when socket directions differ.
        float handle = (float) Math.min(rest.length() / 6, slack / 4);
        CurveSampler sampler = new CurveSampler(start, end, sag, handle);
        Path curve = sampler.sample(0);
        if (slack > 1e-7) {
            float low = 0, high = (float) Math.max(rest.length(), .01);
            for (int i = 0; i < 12 && ringLength(sampler.sample(high), rest.path.fractions) < rest.length(); i++) high *= 2;
            for (int i = 0; i < 23; i++) {
                float middle = (low + high) / 2;
                Path trial = sampler.sample(middle);
                if (ringLength(trial, rest.path.fractions) < rest.length()) low = middle;
                else high = middle;
            }
            curve = sampler.sample((low + high) / 2);
        }
        return new CableDeformation(rest, new Path(curve.positions, transport(curve.positions, start, end)));
    }

    /** Authored geometry, useful for uncoupled inspection and exact rest-shape validation. */
    public static CableDeformation undeformed(RestShape rest) { return new CableDeformation(rest, rest.path); }

    public Vector3f position(float x, float y, float z, Vector3f destination) {
        return target.map(rest.bind(x, y, z), destination);
    }

    public Vector3f normal(float x, float y, float z, float nx, float ny, float nz, Vector3f destination) {
        Binding binding = rest.bind(x, y, z);
        Quaternionf originalRotation = rest.path.rotation(binding.fraction), rotated = target.rotation(binding.fraction);
        Vector3f originalRight = originalRotation.transform(new Vector3f(1, 0, 0));
        Vector3f originalUp = originalRotation.transform(new Vector3f(0, 1, 0));
        Vector3f right = rotated.transform(new Vector3f(1, 0, 0));
        Vector3f up = rotated.transform(new Vector3f(0, 1, 0));
        Vector3f originalDerivative = rest.path.derivative(binding);
        Vector3f derivative = target.derivative(binding);
        // Normals are covectors: J_target^-T * J_source^T. The third column
        // includes bending and twist of the cross section, not only axial stretch.
        float cx = nx * originalRight.x + ny * originalRight.y + nz * originalRight.z;
        float cy = nx * originalUp.x + ny * originalUp.y + nz * originalUp.z;
        float cz = nx * originalDerivative.x + ny * originalDerivative.y + nz * originalDerivative.z;
        Vector3f cross = new Vector3f(up).cross(derivative);
        float determinant = right.dot(cross);
        destination.set(cross).mul(cx).fma(cy, new Vector3f(derivative).cross(right)).fma(cz, new Vector3f(right).cross(up));
        if (!destination.isFinite() || destination.lengthSquared() < 1e-14f || Math.abs(determinant) < 1e-10f) {
            originalRotation.conjugate().transform(destination.set(nx, ny, nz));
            rotated.transform(destination);
        } else if (determinant < 0) destination.negate();
        if (destination.lengthSquared() < 1e-14f) return destination.set(0, 1, 0);
        return destination.normalize();
    }

    public Vector3f center(float restFraction, Vector3f destination) { return target.center(restFraction, destination); }

    /** The root solve reuses its sample storage; only the final curve needs frames. */
    private static final class CurveSampler {
        private final Vector3f a, b, c, d, sag;
        private final Path path;
        CurveSampler(Endpoint start, Endpoint end, Vector3f sag, float handle) {
            a = new Vector3f(start.position()); b = new Vector3f(a).fma(handle, start.tangent());
            d = new Vector3f(end.position()); c = new Vector3f(d).fma(-handle, end.tangent());
            this.sag = sag;
            Vector3f[] points = new Vector3f[SEGMENTS + 1];
            Arrays.setAll(points, i -> new Vector3f());
            path = new Path(points, null);
        }
        Path sample(float amplitude) {
            for (int i = 0; i <= SEGMENTS; i++) {
                float t = i / (float) SEGMENTS, s = 1 - t;
                path.positions[i].set(a).mul(s * s * s).fma(3 * s * s * t, b)
                        .fma(3 * s * t * t, c).fma(t * t * t, d)
                        .fma(amplitude * 16 * t * t * s * s, sag);
            }
            path.positions[0].set(a); path.positions[SEGMENTS].set(d);
            path.measure();
            return path;
        }
    }

    private static double ringLength(Path path, float[] fractions) {
        double length = 0;
        Vector3f previous = path.center(fractions[0], new Vector3f()), current = new Vector3f();
        for (int i = 1; i < fractions.length; i++) {
            path.center(fractions[i], current);
            length += current.distance(previous);
            previous.set(current);
        }
        return length;
    }

    private static Quaternionf[] transport(Vector3f[] points, Endpoint start, Endpoint end) {
        Quaternionf[] rotations = new Quaternionf[points.length];
        Vector3f previousTangent = new Vector3f(start.tangent()), up = new Vector3f(start.up());
        rotations[0] = frame(previousTangent, up);
        for (int i = 1; i < points.length; i++) {
            Vector3f tangent = i == points.length - 1 ? new Vector3f(end.tangent())
                    : unit(new Vector3f(points[i + 1]).sub(points[i - 1]), previousTangent);
            Quaternionf turn = previousTangent.dot(tangent) < -.9999f
                    ? new Quaternionf().rotationAxis((float) Math.PI, perpendicular(up, previousTangent))
                    : new Quaternionf().rotationTo(previousTangent, tangent);
            turn.transform(up);
            up = perpendicular(up, tangent);
            rotations[i] = frame(tangent, up);
            previousTangent.set(tangent);
        }
        Vector3f wantedUp = perpendicular(end.up(), end.tangent());
        float twist = (float) Math.atan2(new Vector3f(up).cross(wantedUp).dot(end.tangent()), up.dot(wantedUp));
        // Distribute terminal socket roll along the cable without a world-axis flip.
        for (int i = 1; i < rotations.length; i++) rotations[i].rotateZ(twist * i / (rotations.length - 1f));
        return rotations;
    }

    private static List<Ring> makeRings(List<? extends Vector3fc> centers, Vector3fc referenceUp) {
        if (centers.size() < 2) throw new IllegalArgumentException("A cable needs at least two rings");
        Vector3f[] points = centers.stream().map(CableDeformation::copyFinite).toArray(Vector3f[]::new);
        Vector3f first = unit(new Vector3f(points[1]).sub(points[0]), new Vector3f(0, 0, 1));
        Vector3f last = unit(new Vector3f(points[points.length - 1]).sub(points[points.length - 2]), first);
        Quaternionf[] rotations = transport(points, new Endpoint(points[0], first, referenceUp),
                new Endpoint(points[points.length - 1], last, referenceUp));
        var result = new ArrayList<Ring>();
        for (int i = 0; i < points.length; i++) result.add(new Ring(points[i],
                rotations[i].transform(new Vector3f(0, 0, 1)), rotations[i].transform(new Vector3f(0, 1, 0)), List.of()));
        return result;
    }

    private static Quaternionf frame(Vector3fc tangent, Vector3fc up) {
        Vector3f forward = unit(tangent, new Vector3f(0, 0, 1));
        Vector3f vertical = perpendicular(up, forward), right = new Vector3f(vertical).cross(forward).normalize();
        vertical.set(forward).cross(right);
        return new Quaternionf().setFromNormalized(new Matrix3f().setColumn(0, right).setColumn(1, vertical).setColumn(2, forward));
    }

    private static Vector3f referenceDown(Endpoint start, Endpoint end) {
        return unit(new Vector3f(start.up()).add(end.up()).negate(), new Vector3f(0, -1, 0));
    }

    private static Vector3f copyFinite(Vector3fc value) {
        if (value == null || !Float.isFinite(value.x()) || !Float.isFinite(value.y()) || !Float.isFinite(value.z()))
            throw new IllegalArgumentException("Non-finite cable coordinate");
        return new Vector3f(value);
    }

    private static Vector3f unit(Vector3fc vector, Vector3fc fallback) {
        Vector3f value = copyFinite(vector);
        if (value.lengthSquared() < 1e-14f) value.set(fallback);
        if (value.lengthSquared() < 1e-14f) value.set(0, 0, 1);
        return value.normalize();
    }

    private static Vector3f perpendicular(Vector3fc vector, Vector3fc tangent) {
        Vector3f result = copyFinite(vector).fma(-vector.dot(tangent), tangent);
        if (result.lengthSquared() < 1e-10f) {
            Vector3f fallback = Math.abs(tangent.x()) < .8f ? new Vector3f(1, 0, 0) : new Vector3f(0, 0, 1);
            result.set(fallback).fma(-fallback.dot(tangent), tangent);
        }
        return result.normalize();
    }

    private static final class Path {
        final Vector3f[] positions;
        final Quaternionf[] rotations;
        final float[] fractions;
        double length;

        Path(Vector3f[] positions, Quaternionf[] rotations) {
            this.positions = positions; this.rotations = rotations;
            fractions = new float[positions.length];
            measure();
        }

        void measure() {
            double total = 0;
            for (int i = 1; i < positions.length; i++) { total += positions[i].distance(positions[i - 1]); fractions[i] = (float) total; }
            length = total;
            for (int i = 1; i < positions.length; i++) fractions[i] = total > 1e-12 ? (float) (fractions[i] / total) : i / (positions.length - 1f);
            fractions[fractions.length - 1] = 1;
        }

        private int segment(float fraction) {
            int index = Arrays.binarySearch(fractions, Math.clamp(fraction, 0, 1));
            if (index < 0) index = -index - 2;
            return Math.clamp(index, 0, fractions.length - 2);
        }
        private float ratio(float fraction, int index) {
            float width = fractions[index + 1] - fractions[index];
            return width < 1e-12f ? 0 : Math.clamp((fraction - fractions[index]) / width, 0, 1);
        }
        Vector3f center(float fraction, Vector3f destination) {
            int index = segment(fraction);
            return destination.set(positions[index]).lerp(positions[index + 1], ratio(fraction, index));
        }
        Quaternionf rotation(float fraction) {
            int index = segment(fraction);
            return new Quaternionf(rotations[index]).slerp(rotations[index + 1], ratio(fraction, index));
        }
        Vector3f map(Binding binding, Vector3f destination) {
            rotation(binding.fraction).transform(destination.set(binding.x, binding.y, binding.z));
            return destination.add(center(binding.fraction, new Vector3f()));
        }
        Vector3f derivative(Binding binding) {
            float low = Math.max(0, binding.fraction - .0005f), high = Math.min(1, binding.fraction + .0005f);
            return map(new Binding(high, binding.x, binding.y, binding.z), new Vector3f())
                    .sub(map(new Binding(low, binding.x, binding.y, binding.z), new Vector3f())).div(high - low);
        }
    }
}
