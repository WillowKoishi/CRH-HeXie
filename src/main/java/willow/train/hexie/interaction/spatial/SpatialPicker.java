package willow.train.hexie.interaction.spatial;

import java.util.*;
import willow.train.hexie.interaction.spatial.PickGeometry.Ray;
import willow.train.hexie.interaction.spatial.model.*;

/** World-space ray distances are preserved through both rigid inverse transforms. */
public final class SpatialPicker {
    public static final Pose IDENTITY = new Pose(new Vec(0,0,0), Quat.IDENTITY);
    public record Hit(UUID targetId, Id controlId, double worldT) {}
    public record Result(Optional<Hit> hit, Optional<UUID> hostId, int visitedNodes, int narrowPhaseTests) {}
    @FunctionalInterface
    public interface PartTest {
        double hit(int part, double limit);
    }

    public record PartHit(int part, double distance) {}

    /** The caller supplies the already transformed ray, preserving its distance parameter. */
    public static Optional<PartHit> pickParts(SurfaceIndex.PartBounds bounds, Vec from, Vec ray,
                                               double reach, PartTest exact) {
        BitSet candidates = bounds.candidates(new Ray(from, ray), reach);
        int selected = -1;
        for (int i = candidates.nextSetBit(0); i >= 0; i = candidates.nextSetBit(i + 1)) {
            double distance = exact.hit(i, reach);
            if (distance >= 0 && distance < reach) {
                reach = distance;
                selected = i;
            }
        }
        return selected < 0 ? Optional.empty() : Optional.of(new PartHit(selected, reach));
    }

    static final class Query {
        int visited, tested;
        double limit;
        boolean occluded;
        Hit hit;
        UUID hostId;
        Query(double limit) { this.limit = limit; }
        void test(UUID host, PickPart part, Ray local) {
            tested++;
            double distance = local.local(part.localPose()).entry(part.box(), limit);
            if (!Double.isFinite(distance) || distance == limit && occluded) return;
            limit = distance;
            occluded = part.controlId().isEmpty();
            hostId = occluded ? null : host;
            hit = occluded ? null : new Hit(part.targetId(), part.controlId().orElseThrow(), distance);
        }
    }
    private static final class Host {
        final UUID id;
        Host(UUID id) { this.id = id; }
        long revision;
        Pose pose;
        AabbTree<PickPart> fixed = new AabbTree<>();
        final AabbTree<PickPart> moving = new AabbTree<>();
        final Map<UUID, AabbTree.Node<PickPart>> parts = new HashMap<>();
        AabbTree.Node<Host> leaf;
    }

    private final Map<UUID, Host> hosts = new HashMap<>();
    private final AabbTree<Host> world = new AabbTree<>();
    private long staticBuilds;
    public long staticBuilds() { return staticBuilds; }

    public void putHost(UUID id, Pose pose, long contentRevision, Iterable<PickPart> fixedParts) {
        Host host = hosts.get(id);
        if (host == null || host.revision != contentRevision) {
            var tree = new AabbTree<PickPart>();
            for (PickPart part : fixedParts) tree.insert(PickGeometry.transform(part.box(), part.localPose()), part);
            if (host == null) { host = new Host(id); hosts.put(id, host); }
            host.fixed = tree;
            host.revision = contentRevision;
            staticBuilds++;
        }
        host.pose = pose;
        refit(host);
    }

    public void moveHost(UUID id, Pose pose) {
        Host host = hosts.get(id);
        host.pose = pose;
        refit(host);
    }

    public void putMovingPart(UUID hostId, UUID partId, PickPart part) {
        Host host = hosts.get(hostId);
        var old = host.parts.remove(partId);
        if (old != null) host.moving.remove(old);
        host.parts.put(partId, host.moving.insert(PickGeometry.transform(part.box(), part.localPose()), part));
        refit(host);
    }

    public void removeMovingPart(UUID hostId, UUID partId) {
        Host host = hosts.get(hostId);
        var leaf = host.parts.remove(partId);
        if (leaf != null) { host.moving.remove(leaf); refit(host); }
    }

    public void removeHost(UUID id) {
        var host = hosts.remove(id);
        if (host != null && host.leaf != null) world.remove(host.leaf);
    }

    private void refit(Host host) {
        AABB fixed = host.fixed.bounds(), moving = host.moving.bounds();
        AABB local = fixed == null ? moving : moving == null ? fixed : PickGeometry.union(fixed, moving);
        if (local == null) {
            if (host.leaf != null) world.remove(host.leaf);
            host.leaf = null;
        } else {
            AABB exact = PickGeometry.transform(local, host.pose);
            host.leaf = host.leaf == null ? world.insert(AabbTree.expand(exact, .25), host)
                    : world.move(host.leaf, exact, .25);
        }
    }

    public Result pick(Vec eye, Vec direction, double reach) {
        double length = direction.length();
        if (!(length > 0) || !Double.isFinite(length) || !(reach >= 0)) throw new IllegalArgumentException("Invalid ray");
        Ray ray = new Ray(eye, direction.scale(1/length));
        Query query = new Query(reach);
        world.query(ray, query, host -> {
            Ray local = ray.local(host.pose);
            host.fixed.query(local, query, p -> query.test(host.id, p, local));
            host.moving.query(local, query, p -> query.test(host.id, p, local));
        });
        return new Result(Optional.ofNullable(query.hit), Optional.ofNullable(query.hostId), query.visited, query.tested);
    }
}
