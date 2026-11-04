package willow.train.hexie.client.render.track;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static willow.train.hexie.client.render.track.BallastMesh.Frame;
import static willow.train.hexie.client.render.track.BallastMesh.Point;

final class TrackBedSampling {
    private static final double MAX_DEVIATION = 1.0 / 64;
    private static final double MAX_LENGTH = 4;
    // Create normally samples twice per metre. Bound the search even on unusually dense modded curves.
    private static final int MAX_STEPS = 16;

    private TrackBedSampling() {}

    record Sample(int[] indices, double[] stations) {}

    static List<Frame> simplify(List<Frame> frames) {
        int[] indices = retained(frames);
        var result = new ArrayList<Frame>(indices.length);
        for (int index : indices) result.add(frames.get(index));
        return result;
    }

    static int[] retained(List<Frame> frames) {
        return sample(frames).indices();
    }

    static Sample sample(List<Frame> frames) {
        int size = frames.size();
        double[] distance = new double[size];
        if (size == 0) return new Sample(new int[0], distance);
        for (int i = 1; i < size; i++)
            distance[i] = distance[i - 1] + frames.get(i).center().sub(frames.get(i - 1).center()).length();

        int[] indices = new int[size];
        int count = 1;
        int start = 0;
        while (start < size - 1) {
            int end = Math.min(start + MAX_STEPS, size - 1);
            while (end > start + 1 && (distance[end] - distance[start] > MAX_LENGTH
                    || !fits(frames, distance, start, end))) end--;
            indices[count++] = end;
            start = end;
        }
        return new Sample(Arrays.copyOf(indices, count), distance);
    }

    private static boolean fits(List<Frame> frames, double[] distance, int start, int end) {
        Frame a = frames.get(start), b = frames.get(end);
        double length = distance[end] - distance[start];
        for (int i = start + 1; i < end; i++) {
            double t = length > 0 ? (distance[i] - distance[start]) / length : (i - start) / (double) (end - start);
            Frame original = frames.get(i);
            double centerError = original.center().sub(a.center().lerp(b.center(), t)).length();
            // Every vertex is within 2 m of the centreline and 0.5 m above it. The sum bounds
            // its displacement for both the straight-edged ballast and the slab's normalized joint frames.
            double sideError = axisError(original.side(), a.side().lerp(b.side(), t));
            double upError = axisError(original.up(), a.up().lerp(b.up(), t));
            if (centerError + 2 * sideError + .5 * upError > MAX_DEVIATION) return false;
        }
        return true;
    }

    private static double axisError(Point original, Point interpolated) {
        return Math.max(original.sub(interpolated).length(), original.sub(interpolated.unit()).length());
    }
}
