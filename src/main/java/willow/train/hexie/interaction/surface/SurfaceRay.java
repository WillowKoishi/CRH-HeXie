package willow.train.hexie.interaction.surface;

import net.minecraft.world.phys.Vec3;
import willow.train.hexie.interaction.spatial.model.AABB;
import willow.train.hexie.interaction.spatial.model.Pose;
import willow.train.hexie.interaction.spatial.model.Quat;
import willow.train.hexie.interaction.spatial.model.Vec;

record SurfaceRay(Vec origin, Vec direction) {
    static Vec vec(Vec3 p) { return new Vec(p.x, p.y, p.z); }
    static Vec3 mc(Vec p) { return new Vec3(p.x(), p.y(), p.z()); }
    static AABB box(net.minecraft.world.phys.AABB box) {
        return new AABB(new Vec(box.minX, box.minY, box.minZ), new Vec(box.maxX, box.maxY, box.maxZ));
    }

    SurfaceRay local(Pose pose) {
        var q = pose.rotation();
        var inverse = new Quat(-q.x(),-q.y(),-q.z(),q.w());
        return new SurfaceRay(inverse.rotate(origin.subtract(pose.position())),inverse.rotate(direction));
    }
    record Intersection(double distance, double b, double c) {}
    static double dot(Vec a, Vec b) { return a.x()*b.x()+a.y()*b.y()+a.z()*b.z(); }
    Intersection hit(Vec a, Vec b, Vec c, double reach) {
        var e1 = b.subtract(a);
        var e2 = c.subtract(a);
        var h = direction.cross(e2);
        double det = dot(e1,h);
        if (det <= 1e-12) return null;
        var relative = origin.subtract(a);
        double u = dot(relative,h)/det;
        var q = relative.cross(e1);
        double v = dot(direction,q)/det;
        double t = dot(e2,q)/det;
        if (u < -1e-9 || v < -1e-9 || u+v > 1+1e-9 || t < 0 || t > reach) return null;
        u = Math.clamp(u,0,1);
        return new Intersection(t,u,Math.clamp(v,0,1-u));
    }
}
