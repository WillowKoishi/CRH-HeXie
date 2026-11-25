package willow.train.hexie.interaction.spatial;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import willow.train.hexie.interaction.spatial.model.*;
import willow.train.hexie.interaction.spatial.model.HostPatch.*;
import willow.train.hexie.interaction.spatial.model.SurfaceEditResult.Status;

/**
 * Owner-thread publication. Every changed host is prepared against its original unculled mesh
 * before any revision, ownership entry or published boundary is changed.
 */
public final class OpeningService {
    static final class Failure extends RuntimeException {
        final Status status;
        Failure(Status status) {
            this.status = status;
        }
    }
    private record Published(SurfaceGeometry base, SurfaceMesh mesh) {}
    private record CutPlan(SurfaceGeometry base, List<List<SolidMesh.Polygon>> cutters) {}
    private final Function<SurfaceHostId, Optional<SurfaceGeometry>> source;
    private final ToLongFunction<SurfaceHostId> baseRevision;
    private final Map<SurfaceHostId, Long> generations = new HashMap<>();
    private final Map<SurfaceHostId, Map<UUID, HostPatch>> owners = new HashMap<>();
    private final Map<SurfaceHostId, Published> published = new HashMap<>();
    private final LongAdder cutJobs = new LongAdder();

    public OpeningService(
            Function<SurfaceHostId, Optional<SurfaceGeometry>> source, ToLongFunction<SurfaceHostId> baseRevision) {
        this.source = source;
        this.baseRevision = baseRevision;
    }
    public OpeningService(Function<SurfaceHostId, Optional<SurfaceGeometry>> source) {
        this(source, h -> source.apply(h).map(SurfaceGeometry::topologyRevision).orElse(0L));
    }
    public long revision(SurfaceHostId h) {
        return baseRevision.applyAsLong(h) + generation(h);
    }
    public long generation(SurfaceHostId h) {
        return generations.getOrDefault(h, 0L);
    }
    public boolean hasOpenings(SurfaceHostId h) {
        return owners.containsKey(h);
    }
    public long cutJobs() {
        return cutJobs.sum();
    }

    void restore(UUID id, List<HostPatch> patches) {
        for (var p : patches) owners.computeIfAbsent(p.host(), unused -> new LinkedHashMap<>()).put(id, p);
    }
    public void restoreGeneration(SurfaceHostId h, long generation) {
        generations.put(h, generation);
    }

    public Optional<SurfaceMesh> mesh(SurfaceHostId h) {
        return source.apply(h).map(base -> {
            var last = published.get(h);
            if (last != null && last.base.equals(base)) return last.mesh;
            var patches = owners.getOrDefault(h, Map.of());
            if (patches.isEmpty()) return base.visible();
            try {
                var mesh = rebuild(plan(base, patches));
                published.put(h, new Published(base, mesh));
                return mesh;
            } catch (Failure | IllegalArgumentException invalid) {
                return last == null ? base.visible() : last.mesh;
            }
        });
    }

    SurfaceEditResult replace(UUID id, List<HostPatch> previous, List<HostPatch> next) {
        return replace(List.of(new Change(id, previous, next)));
    }

    record Change(UUID id, List<HostPatch> previous, List<HostPatch> next) {}
    SurfaceEditResult replace(List<Change> changes) {
        return replace(changes, Runnable::run, Runnable::run, () -> true).join();
    }

    private record Input(SurfaceGeometry base, long revision, Map<UUID, HostPatch> patches) {}
    private record Prepared(Map<SurfaceHostId, Published> meshes, SurfaceEditResult result) {}

    CompletableFuture<SurfaceEditResult> replace(
            List<Change> changes, Executor worker, Executor owner, BooleanSupplier attachmentCurrent) {
        var inputs = new LinkedHashMap<SurfaceHostId, Input>();
        try {
            for (var change : changes) {
                if (change.previous.equals(change.next)) continue;
                for (var p : change.previous) input(inputs, p.host()).patches.remove(change.id);
                for (var p : change.next) {
                    var input = input(inputs, p.host());
                    if (input.revision != p.anchorHit().geometryRevision()) throw new Failure(Status.StaleAnchor);
                    if (input.patches.put(change.id, p) != null) throw new Failure(Status.InvalidGeometry);
                }
            }
        } catch (Failure failure) {
            return CompletableFuture.completedFuture(new SurfaceEditResult(failure.status, 0));
        }
        if (inputs.isEmpty()) return CompletableFuture.completedFuture(
                new SurfaceEditResult(attachmentCurrent.getAsBoolean() ? Status.Applied : Status.StaleAnchor, 0));
        return CompletableFuture.supplyAsync(() -> prepare(inputs), worker).thenApplyAsync(batch -> {
            if (!batch.result.applied()) return batch.result;
            if (!attachmentCurrent.getAsBoolean()
                    || inputs.entrySet().stream().anyMatch(e -> revision(e.getKey()) != e.getValue().revision))
                return new SurfaceEditResult(Status.StaleAnchor, batch.result.cutJobs());
            published.putAll(batch.meshes);
            inputs.forEach((host, input) -> {
                if (input.patches.isEmpty())
                    owners.remove(host);
                else
                    owners.put(host, input.patches);
                generations.merge(host, 1L, Long::sum);
            });
            return batch.result;
        }, owner);
    }
    private Input input(Map<SurfaceHostId, Input> batch, SurfaceHostId host) {
        return batch.computeIfAbsent(host,
                h
                -> new Input(source.apply(h).orElseThrow(() -> new Failure(Status.InvalidGeometry)), revision(h),
                        new LinkedHashMap<>(owners.getOrDefault(h, Map.of()))));
    }
    private Prepared prepare(Map<SurfaceHostId, Input> inputs) {
        var prepared = new LinkedHashMap<SurfaceHostId, Published>();
        int jobs = 0;
        try {
            var plans = new LinkedHashMap<SurfaceHostId, CutPlan>();
            inputs.forEach((h, input) -> plans.put(h, plan(input.base, input.patches)));
            for (var entry : plans.entrySet()) {
                jobs++;
                var plan = entry.getValue();
                prepared.put(entry.getKey(), new Published(plan.base, rebuild(plan)));
            }
            return new Prepared(prepared, new SurfaceEditResult(Status.Applied, jobs));
        } catch (Failure failure) {
            return new Prepared(Map.of(), new SurfaceEditResult(failure.status, jobs));
        } catch (IllegalArgumentException invalid) {
            return new Prepared(Map.of(), new SurfaceEditResult(Status.InvalidGeometry, jobs));
        }
    }

    HostPatch translate(HostPatch patch, Vec offset) {
        var base = source.apply(patch.host()).orElseThrow(() -> new Failure(Status.InvalidGeometry)).cuttingMesh();
        var frame = ProjectionFrame.along(patch.direction());
        var old = patch.anchorHit();
        if (old.faceId() < 0 || old.faceId() >= base.triangles().size()) throw new Failure(Status.StaleAnchor);
        var point = base.triangles().get(old.faceId()).at(old.bWeight(), old.cWeight()).add(offset);
        var uv = frame.project(point);
        AnchorHit nearest = null;
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i < base.triangles().size(); i++) {
            var t = base.triangles().get(i);
            if (ProjectionFrame.dot(t.normal(), frame.w()) >= 0) continue;
            var a = frame.project(t.a());
            var b = frame.project(t.b());
            var c = frame.project(t.c());
            double area = LayerProjection.cross(a, b, c);
            double bw = LayerProjection.cross(a, uv, c) / area, cw = LayerProjection.cross(a, b, uv) / area;
            if (bw < -SolidMesh.EPS || cw < -SolidMesh.EPS || bw + cw > 1 + SolidMesh.EPS) continue;
            double distance = t.at(bw, cw).subtract(point).length();
            if (distance < best) {
                best = distance;
                nearest = new AnchorHit(revision(patch.host()), i, Math.max(0, bw), Math.max(0, cw));
            }
        }
        if (nearest == null) throw new Failure(Status.IncompleteCoverage);
        var shift = frame.project(offset);
        return new HostPatch(patch.host(), nearest, patch.direction(), patch.mode(),
                translate(patch.footprint(), shift), patch.cutMask().map(mask -> translate(mask, shift)));
    }

    private static CutMask translate(CutMask mask, UV shift) {
        Function<List<UV>, List<UV>> move =
                ring -> ring.stream().map(p -> new UV(p.u() + shift.u(), p.v() + shift.v())).toList();
        return new CutMask(move.apply(mask.outer()), mask.holes().stream().map(move).toList());
    }

    private static CutPlan plan(SurfaceGeometry base, Map<UUID, HostPatch> patches) {
        if (patches.isEmpty()) return new CutPlan(base, List.of());
        var original = base.cuttingMesh();
        if (original.triangles().isEmpty() || !SolidMesh.closed(original)) throw new Failure(Status.InvalidGeometry);
        var cutters = new ArrayList<List<SolidMesh.Polygon>>();
        // Stable Boolean operation order across save/reopen and content-only rewrites.
        for (var patch : new TreeMap<>(patches).values())
            cutters.addAll(new LayerProjection(original, patch).cutters(original, patch));
        return new CutPlan(base, cutters);
    }
    private SurfaceMesh rebuild(CutPlan plan) {
        cutJobs.increment();
        if (plan.cutters.isEmpty()) return plan.base.visible();
        var polygons = SolidMesh.polygons(plan.base.cuttingMesh());
        for (var cutter : plan.cutters) polygons = SolidMesh.subtract(polygons, cutter);
        var mesh = SolidMesh.stitch(polygons);
        if (!SolidMesh.closed(mesh)) throw new Failure(Status.InvalidGeometry);
        return mesh;
    }
}
