package willow.train.hexie.core.sticker;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Shared facing-plane coordinate math, 16x16 precision grid snapping, edge-distance calculation,
 * rounded-rectangle contour generation, and ray-plane intersection for gizmo dragging.
 */
public final class StickerPlaneMath {
    private StickerPlaneMath() {}

    public record UV(double u, double v) {}

    /** Pixel distances (in 1/16 block units) from the previewed window edges to the 4 edges of the block face. */
    public record PrecisionSnap(float snappedOffsetU, float snappedOffsetV,
                                int leftPx, int rightPx, int topPx, int bottomPx) {}

    public static Vec3 axisU(Direction facing) {
        return switch (facing) {
            case NORTH -> new Vec3(-1, 0, 0);
            case SOUTH -> new Vec3(1, 0, 0);
            case EAST -> new Vec3(0, 0, -1);
            case WEST -> new Vec3(0, 0, 1);
            case UP -> new Vec3(1, 0, 0);
            case DOWN -> new Vec3(1, 0, 0);
        };
    }

    public static Vec3 axisV(Direction facing) {
        return switch (facing) {
            case NORTH, SOUTH, EAST, WEST -> new Vec3(0, 1, 0);
            case UP -> new Vec3(0, 0, -1);
            case DOWN -> new Vec3(0, 0, 1);
        };
    }

    public static Vec3 outward(Direction facing) {
        return new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ());
    }

    public static Vec3 faceCenter(BlockPos pos, Direction facing) {
        return Vec3.atCenterOf(pos).add(outward(facing).scale(0.5));
    }

    public static Vec3 unproject(BlockPos anchorPos, Direction facing, double u, double v, double outwardOffset) {
        return faceCenter(anchorPos, facing)
                .add(axisU(facing).scale(u))
                .add(axisV(facing).scale(v))
                .add(outward(facing).scale(outwardOffset));
    }

    public static UV project(BlockPos anchorPos, Direction facing, Vec3 worldPoint) {
        Vec3 delta = worldPoint.subtract(faceCenter(anchorPos, facing));
        return new UV(delta.dot(axisU(facing)), delta.dot(axisV(facing)));
    }

    /** Intersects a world ray with the anchor block's facing plane (returns null if parallel). */
    public static UV intersectPlane(BlockPos anchorPos, Direction facing, Vec3 rayOrigin, Vec3 rayDir) {
        Vec3 n = outward(facing);
        double denom = rayDir.dot(n);
        if (Math.abs(denom) < 1e-7) return null;
        Vec3 fc = faceCenter(anchorPos, facing);
        double t = fc.subtract(rayOrigin).dot(n) / denom;
        if (t < 0) return null;
        Vec3 hit = rayOrigin.add(rayDir.scale(t));
        return project(anchorPos, facing, hit);
    }

    /**
     * Snaps a hit point on a block face to the 16x16 pixel grid and computes distances in 1/16th
     * block pixels to the block's left, right, top, and bottom edges.
     */
    public static PrecisionSnap snap16(BlockPos pos, Direction facing, Vec3 hitWorld, float width, float height) {
        UV raw = project(pos, facing, hitWorld);
        int su = (int) Math.round((raw.u() + 0.5) * 16.0);
        int sv = (int) Math.round((raw.v() + 0.5) * 16.0);
        su = Math.max(0, Math.min(16, su));
        sv = Math.max(0, Math.min(16, sv));
        float offU = su / 16.0f - 0.5f;
        float offV = sv / 16.0f - 0.5f;
        int leftPx = Math.round((offU - width * 0.5f + 0.5f) * 16.0f);
        int rightPx = Math.round((0.5f - (offU + width * 0.5f)) * 16.0f);
        int bottomPx = Math.round((offV - height * 0.5f + 0.5f) * 16.0f);
        int topPx = Math.round((0.5f - (offV + height * 0.5f)) * 16.0f);
        return new PrecisionSnap(offU, offV, leftPx, rightPx, topPx, bottomPx);
    }

    /** Generates paired outer/inner CCW rounded-rectangle loops with identical vertex count for watertight ring meshing. */
    public static List<UV> sampleRoundedRect(double cx, double cy, double hw, double hh, double radius, int stepsPerCorner) {
        double cr = Math.max(0.0, Math.min(radius, Math.min(hw, hh)));
        int steps = Math.max(1, stepsPerCorner);
        var pts = new ArrayList<UV>(4 * (steps + 1));
        double[] ccx = {cx + hw - cr, cx + hw - cr, cx - hw + cr, cx - hw + cr};
        double[] ccy = {cy - hh + cr, cy + hh - cr, cy + hh - cr, cy - hh + cr};
        double[] startAng = {-Math.PI * 0.5, 0.0, Math.PI * 0.5, Math.PI};
        for (int c = 0; c < 4; c++) {
            for (int i = 0; i <= steps; i++) {
                double a = startAng[c] + (Math.PI * 0.5) * i / steps;
                pts.add(new UV(ccx[c] + Math.cos(a) * cr, ccy[c] + Math.sin(a) * cr));
            }
        }
        return pts;
    }
}
