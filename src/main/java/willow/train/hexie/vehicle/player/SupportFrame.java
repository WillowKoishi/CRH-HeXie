package willow.train.hexie.vehicle.player;

import net.minecraft.world.phys.Vec3;

/** Immutable rigid frame. Standing and walking do not depend on a railway backend. */
public record SupportFrame(Vec3 origin, Vec3 x, Vec3 y, Vec3 z) {
    public Vec3 world(Vec3 local) {
        return origin.add(x.scale(local.x)).add(y.scale(local.y)).add(z.scale(local.z));
    }

    public Vec3 local(Vec3 world) {
        Vec3 d = world.subtract(origin);
        return new Vec3(d.dot(x), d.dot(y), d.dot(z));
    }

    public Vec3 carryFrom(SupportFrame previous, Vec3 world) {
        return world(previous.local(world));
    }
}
