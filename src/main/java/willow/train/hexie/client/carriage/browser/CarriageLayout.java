package willow.train.hexie.client.carriage.browser;

/** Pure layout math: positions are unwrapped so crossing the seam never jumps. */
public final class CarriageLayout {
    private final double[] centers;
    private final double period;

    public CarriageLayout(double[] lengths, double[] frontGaps, double[] rearGaps) {
        if (lengths.length == 0 || lengths.length != frontGaps.length || lengths.length != rearGaps.length)
            throw new IllegalArgumentException("Mismatched or empty carriage layout");
        centers = new double[lengths.length];
        double cursor = 0;
        for (int i = 0; i < lengths.length; i++) {
            if (!Double.isFinite(lengths[i]) || lengths[i] <= 0
                    || !Double.isFinite(frontGaps[i]) || !Double.isFinite(rearGaps[i]))
                throw new IllegalArgumentException("Invalid carriage dimensions");
            centers[i] = cursor;
            int next = (i + 1) % lengths.length;
            double step = lengths[i] / 2 + lengths[next] / 2 + rearGaps[i] + frontGaps[next];
            if (!Double.isFinite(step) || step < .25)
                throw new IllegalArgumentException("Coupling distances overlap carriage centers");
            cursor += step;
        }
        period = cursor;
    }

    public int size() { return centers.length; }
    public int model(long index) { return (int) Math.floorMod(index, (long) size()); }
    public double center(long index) {
        return Math.floorDiv(index, (long) size()) * period + centers[model(index)];
    }
    public double period() { return period; }
    /** Index immediately at/before an unwrapped position, including negative cycles. */
    public long floorIndex(double position) {
        long cycle = (long) Math.floor(position / period);
        double local = position - cycle * period;
        int low = 0, high = centers.length;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (centers[mid] <= local) low = mid + 1;
            else high = mid;
        }
        return cycle * size() + low - 1;
    }
    public static double ease(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * t * (t * (t * 6 - 15) + 10);
    }
}
