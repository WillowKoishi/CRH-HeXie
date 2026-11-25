package willow.train.hexie.ui.flow;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Preview-space limits and visibility for camera panning. */
public final class CarriageFlowPanBounds {
    static final int LEFT = 9, RIGHT = 169, TOP = 37, BOTTOM = 106;

    private CarriageFlowPanBounds() {}

    /** Keep at least one block of the selected carriage's projected bounds in view. */
    public static double clamp(double value, double minX, double maxX, double minY, double maxY, double dx, double dy) {
        double low = Double.NEGATIVE_INFINITY, high = Double.POSITIVE_INFINITY;
        double[][] axes = {{minX, maxX, LEFT, RIGHT, dx}, {minY, maxY, TOP, BOTTOM, dy}};
        for (double[] axis : axes) {
            if (Math.abs(axis[4]) < 1e-8) continue;
            double a = (axis[2] + 12 - axis[1]) / axis[4];
            double b = (axis[3] - 12 - axis[0]) / axis[4];
            low = Math.max(low, Math.min(a, b));
            high = Math.min(high, Math.max(a, b));
        }
        return low <= high ? Math.max(low, Math.min(high, value)) : 0;
    }

    /**
     * Pan at which half of the neighbouring carriage's length intersects the preview.
     * A cross section counts when its projected bounding rectangle intersects the viewport.
     */
    public static double panForHalfVisible(Matrix4f camera, double centerZ, double width,
                                           double height, double length, int direction) {
        Vector3f center = camera.transformPosition(new Vector3f(0, (float) (height / 2), (float) centerZ));
        Vector3f across = camera.transformDirection(new Vector3f((float) (width / 2), 0, 0));
        Vector3f up = camera.transformDirection(new Vector3f(0, (float) (height / 2), 0));
        Vector3f along = camera.transformDirection(new Vector3f(0, 0, 1));
        double[][] axes = {{1, 0}, {0, 1}, {-across.y, across.x}, {-up.y, up.x}};
        double low = Double.NEGATIVE_INFINITY, high = Double.POSITIVE_INFINITY;
        for (double[] axis : axes) {
            double x = axis[0], y = axis[1];
            if (x * x + y * y < 1e-12) continue;
            double viewportCenter = x * (LEFT + RIGHT) / 2 + y * (TOP + BOTTOM) / 2;
            double radius = Math.abs(x) * (RIGHT - LEFT) / 2 + Math.abs(y) * (BOTTOM - TOP) / 2
                    + Math.abs(x * across.x + y * across.y) + Math.abs(x * up.x + y * up.y);
            double offset = x * center.x + y * center.y - viewportCenter;
            double rate = x * along.x + y * along.y;
            if (Math.abs(rate) < 1e-8) {
                if (Math.abs(offset) > radius) return Double.NaN;
                continue;
            }
            double a = (-radius - offset) / rate, b = (radius - offset) / rate;
            low = Math.max(low, Math.min(a, b));
            high = Math.min(high, Math.max(a, b));
        }
        if (high <= low) return Double.NaN;
        // A narrow viewport may never show half of a long carriage; use its maximum visible span.
        double required = Math.min(length / 2, high - low);
        if (direction < 0) return length / 2 - required - low;
        return -length / 2 + required - high;
    }
}
