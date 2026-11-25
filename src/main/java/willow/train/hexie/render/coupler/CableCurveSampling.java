package willow.train.hexie.render.coupler;

import java.util.Arrays;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static willow.train.hexie.render.coupler.CableDeformation.Binding;
import static willow.train.hexie.render.coupler.CableDeformation.Endpoint;
import static willow.train.hexie.render.coupler.CableDeformation.frame;
import static willow.train.hexie.render.coupler.CableDeformation.perpendicular;
import static willow.train.hexie.render.coupler.CableDeformation.unit;

// split from willow/train/hexie/client/render/CableDeformation.java, lines 17, 185-241, 289-335
final class CableCurveSampling {
    private static final int SEGMENTS = 128;

    /** The root solve reuses its sample storage; only the final curve needs frames. */
    static final class CurveSampler {
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

    static double ringLength(Path path, float[] fractions) {
        double length = 0;
        Vector3f previous = path.center(fractions[0], new Vector3f()), current = new Vector3f();
        for (int i = 1; i < fractions.length; i++) {
            path.center(fractions[i], current);
            length += current.distance(previous);
            previous.set(current);
        }
        return length;
    }

    static Quaternionf[] transport(Vector3f[] points, Endpoint start, Endpoint end) {
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

    static final class Path {
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
            rotation(binding.fraction()).transform(destination.set(binding.x(), binding.y(), binding.z()));
            return destination.add(center(binding.fraction(), new Vector3f()));
        }
        Vector3f derivative(Binding binding) {
            float low = Math.max(0, binding.fraction() - .0005f), high = Math.min(1, binding.fraction() + .0005f);
            return map(new Binding(high, binding.x(), binding.y(), binding.z()), new Vector3f())
                    .sub(map(new Binding(low, binding.x(), binding.y(), binding.z()), new Vector3f())).div(high - low);
        }
    }
}
