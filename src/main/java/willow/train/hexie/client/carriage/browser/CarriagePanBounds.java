package willow.train.hexie.client.carriage.browser;

/** Keep at least an 12-pixel (one block at the fixed preview scale) span of the selected carriage's projected bounding box in view. */
public final class CarriagePanBounds {
    private CarriagePanBounds() {}
    public static double clamp(double value, double minX, double maxX, double minY, double maxY, double dx, double dy) {
        double low = Double.NEGATIVE_INFINITY, high = Double.POSITIVE_INFINITY;
        double[][] axes = {{minX, maxX, 9, 169, dx}, {minY, maxY, 37, 106, dy}};
        for (double[] axis : axes) {
            if (Math.abs(axis[4]) < 1e-8) continue;
            double a = (axis[2] + 12 - axis[1]) / axis[4];
            double b = (axis[3] - 12 - axis[0]) / axis[4];
            low = Math.max(low, Math.min(a, b));
            high = Math.min(high, Math.max(a, b));
        }
        return low <= high ? Math.max(low, Math.min(high, value)) : 0;
    }
}
