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

    /** Turn horizontal momentum with the carriage; gravity stays on the world Y axis. */
    public Vec3 rotateHorizontalFrom(SupportFrame previous, Vec3 velocity) {
        if (velocity.x == 0 && velocity.z == 0) return velocity;
        double cosine = previous.z.x * z.x + previous.z.z * z.z;
        double sine = previous.z.z * z.x - previous.z.x * z.z;
        if (sine == 0 && cosine >= 0) return velocity;
        double lengthSquared = cosine * cosine + sine * sine;
        // Heading is undefined when the carriage's forward axis is vertical.
        if (lengthSquared < 1e-12) return velocity;
        double inverseLength = 1 / Math.sqrt(lengthSquared);
        cosine *= inverseLength;
        sine *= inverseLength;
        return new Vec3(velocity.x * cosine + velocity.z * sine, velocity.y,
                velocity.z * cosine - velocity.x * sine);
    }
}
