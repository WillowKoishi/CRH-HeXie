package willow.train.hexie.interaction.spatial;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import willow.train.hexie.interaction.spatial.model.*;
import willow.train.hexie.interaction.spatial.model.SurfaceDetails.Editable;

/** All mutations run on the owning thread. Clipboard data contains no placement, hole or timetable binding. */
public final class SurfaceEditor {
    public record Clipboard(Id kind, Editable value) {}
    private final OpeningService geometry;
    private final SurfaceIndex index = new SurfaceIndex();

    public SurfaceEditor(OpeningService geometry) {
        this.geometry = geometry;
    }
    public OpeningService geometry() {
        return geometry;
    }
    public Optional<SurfaceAttachment> get(UUID id) {
        return index.get(id);
    }
    public Set<UUID> at(SurfaceHostId host, Face face) {
        return index.at(host, host.cell(), face);
    }
    public void restore(SurfaceAttachment a) {
        index.put(a);
        geometry.restore(a.attachmentId(), patches(a));
    }
    private static List<HostPatch> patches(SurfaceAttachment a) {
        return a.details().map(SurfaceDetails::openings).orElse(List.of());
    }
    public SurfaceEditResult put(SurfaceAttachment a) {
        return put(a, Runnable::run, Runnable::run).join();
    }
    public CompletableFuture<SurfaceEditResult> put(SurfaceAttachment a, Executor worker, Executor owner) {
        return put(a, worker, owner, () -> true);
    }
    public CompletableFuture<SurfaceEditResult> put(SurfaceAttachment a, Executor worker, Executor owner,
            BooleanSupplier ownerCurrent) {
        var previous = get(a.attachmentId());
        var covered = new HashSet<>(a.spanCells());
        covered.add(a.anchor().cell());
        for (var p : patches(a))
            if (!covered.contains(p.host().cell()) || !p.host().equals(a.anchor().at(p.host().cell())))
                return CompletableFuture.completedFuture(
                        new SurfaceEditResult(SurfaceEditResult.Status.IncompleteCoverage, 0));
        var change = new OpeningService.Change(
                a.attachmentId(), previous.map(SurfaceEditor::patches).orElse(List.of()), patches(a));
        return geometry.replace(List.of(change), worker, owner,
                        () -> ownerCurrent.getAsBoolean() && get(a.attachmentId()).equals(previous))
                .thenApply(result -> {
                    if (result.applied()) index.put(a);
                    return result;
                });
    }

    public Optional<SurfaceEditResult> brush(UUID hit) {
        return brush(hit, Runnable::run, Runnable::run).join();
    }
    public CompletableFuture<Optional<SurfaceEditResult>> brush(UUID hit, Executor worker, Executor owner) {
        return brush(hit, worker, owner, () -> true);
    }
    public CompletableFuture<Optional<SurfaceEditResult>> brush(UUID hit, Executor worker, Executor owner,
            BooleanSupplier ownerCurrent) {
        var old = get(hit);
        if (old.isEmpty()) return CompletableFuture.completedFuture(Optional.empty());
        var change = new OpeningService.Change(hit, patches(old.get()), List.of());
        return geometry.replace(List.of(change), worker, owner,
                () -> ownerCurrent.getAsBoolean() && get(hit).equals(old)).thenApply(result -> {
            if (result.applied()) index.remove(hit);
            return Optional.of(result);
        });
    }
    public Optional<Clipboard> copy(UUID hit) {
        return get(hit).flatMap(a -> a.details().map(d -> new Clipboard(a.kind(), d.editable())));
    }
    public boolean paste(UUID hit, Clipboard clip) {
        return get(hit).filter(a -> a.kind().equals(clip.kind())).isPresent() && edit(hit, clip.value());
    }
    public boolean edit(UUID hit, Editable value) {
        return get(hit)
                .flatMap(a -> a.details().map(d -> {
                    index.put(a.withDetails(d.edit(value)));
                    return true;
                }))
                .orElse(false);
    }

    /** Exchange placements on one panel. Dedicated holes travel with their owning attachment. */
    public boolean swap(UUID left, UUID right) {
        return swap(left, right, Runnable::run, Runnable::run).join();
    }
    public CompletableFuture<Boolean> swap(UUID left, UUID right, Executor worker, Executor owner) {
        return swap(left, right, worker, owner, () -> true);
    }
    public CompletableFuture<Boolean> swap(UUID left, UUID right, Executor worker, Executor owner,
            BooleanSupplier ownerCurrent) {
        var a = get(left).orElse(null);
        var b = get(right).orElse(null);
        if (a == null || b == null || left.equals(right) || !a.anchor().equals(b.anchor()) || a.face() != b.face()
                || !a.spanCells().equals(b.spanCells()) || a.details().isEmpty() || b.details().isEmpty())
            return CompletableFuture.completedFuture(false);
        var x = a.details().get();
        var y = b.details().get();
        Vec offset = y.bounds().min().add(y.bounds().max()).subtract(x.bounds().min().add(x.bounds().max())).scale(.5);
        try {
            var first = x.openings().stream().map(p -> geometry.translate(p, offset)).toList();
            var second = y.openings().stream().map(p -> geometry.translate(p, offset.scale(-1))).toList();
            return geometry
                    .replace(List.of(new OpeningService.Change(left, x.openings(), first),
                                     new OpeningService.Change(right, y.openings(), second)),
                            worker, owner,
                            () -> ownerCurrent.getAsBoolean() && get(left).filter(a::equals).isPresent()
                                    && get(right).filter(b::equals).isPresent())
                    .thenApply(result -> {
                        if (!result.applied()) return false;
                        index.put(a.withDetails(x.place(y.bounds(), first, y.facets())));
                        index.put(b.withDetails(y.place(x.bounds(), second, x.facets())));
                        return true;
                    });
        } catch (OpeningService.Failure failure) {
            return CompletableFuture.completedFuture(false);
        }
    }
}
