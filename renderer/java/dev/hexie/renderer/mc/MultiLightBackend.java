package dev.hexie.renderer.mc;

import dev.hexie.renderer.*;
import java.util.*;
import java.util.function.Supplier;
import static dev.hexie.renderer.mc.RenderSettings.Field.*;

/** Connects mc-common's RenderBridge snapshots to core's budgeted MultiLightRenderer. */
public final class MultiLightBackend implements RendererBackend, AutoCloseable {
    private final Supplier<RenderTier> tierSupplier;
    private RendererConfig activeConfig;
    private RenderTier activeTier;
    private MultiLightRenderer renderer;
    private Map<StructureRevision.ModelRef, List<Triangle>> currentModels = Map.of();

    public MultiLightBackend(Supplier<RenderTier> tierSupplier) {
        this.tierSupplier = Objects.requireNonNull(tierSupplier);
    }

    public static RendererConfig toCoreConfig(RenderSettings s) {
        var limits = new RendererConfig.Limits(
                s.get(MAP_FACES), s.get(EXACT_RAYS), s.get(DDA_STEPS),
                s.get(ACTIVE_LIGHTS), s.get(CLUSTER_INDICES));
        return new RendererConfig(
                s.get(POINT_LIGHTS) != 0,
                s.get(TILE_PIXELS), s.get(DEPTH_LAYERS), s.get(PRIORITY_LIGHTS),
                s.get(OWN_MIB), s.get(OWN_MIN_RES), s.get(OWN_MAX_RES),
                s.get(FOREIGN_CUBES), s.get(FOREIGN_RES),
                limits,
                s.get(CELL_WIDTH_1024) / 1024.0,
                s.get(CSM_LEVELS), s.get(CSM_RES), s.get(CSM_DISTANCE),
                s.get(GPU_MICROSECONDS) / 1000.0, s.get(MEMORY_MIB),
                s.fullQuality() ? RendererConfig.Policy.FULL_QUALITY : RendererConfig.Policy.FIXED_FRAME_TIME);
    }

    @Override
    public int effective(RenderSettings settings, RenderSettings.Field field, RenderSettings.Tier tier) {
        return settings.effective(field, tier);
    }

    @Override
    public Lighting prepare(Snapshot snapshot, RenderSettings settings) {
        var tier = tierSupplier.get();
        if (tier != RenderTier.FULL) return Lighting.OFF;
        var config = toCoreConfig(settings);
        if (!config.pointLights() || snapshot.sections().isEmpty()) return Lighting.OFF;

        if (renderer == null || !config.equals(activeConfig) || tier != activeTier) {
            close();
            activeConfig = config;
            activeTier = tier;
            renderer = new MultiLightRenderer(config, tier, ref -> currentModels.get(ref));
        }

        // Build per-section ModelRef -> triangles map for FrozenScene.
        // Section triangles already have model.localTransform baked in, so pass identity-transformed ModelRefs.
        var modelMap = new HashMap<StructureRevision.ModelRef, List<Triangle>>();
        var structures = new ArrayList<StructureRevision>(snapshot.sections().size());
        var receivers = new ArrayList<MultiLightRenderer.Receiver>();
        var groupOffsets = new HashMap<Long, Integer>();
        Vec worldLow = null, worldHigh = null;
        long primitiveId = 0;

        for (var entry : snapshot.sections().entrySet()) {
            long group = entry.getKey();
            var section = entry.getValue();
            var pose = snapshot.world().get(group);
            if (pose == null) continue;
            var origRev = section.revision();
            var bakedRefs = new ArrayList<StructureRevision.ModelRef>(origRev.models().size());
            var sectionTris = section.triangles();
            for (int m = 0; m < origRev.models().size(); m++) {
                var orig = origRev.models().get(m);
                var sub = section.submeshes().get(m);
                var ref = new StructureRevision.ModelRef(group + ":" + m + ":" + orig.geometryId(), orig.materialId(), Pose.IDENTITY);
                bakedRefs.add(ref);
                var slice = new ArrayList<Triangle>(sub.end() - sub.start());
                for (int i = sub.start(); i < sub.end(); i++) slice.add(sectionTris.get(i));
                modelMap.put(ref, slice);
            }
            structures.add(new StructureRevision(origRev.groupId(), origRev.revision(), origRev.host(), bakedRefs, List.of()));
            groupOffsets.put(group, receivers.size());
            var frameTris = snapshot.triangles(group);
            for (var local : frameTris) {
                var w = local.transform(pose);
                var center = w.a().plus(w.b()).plus(w.c()).times(1.0 / 3.0);
                var n = w.b().minus(w.a()).cross(w.c().minus(w.a()));
                double len = Math.sqrt(n.dot(n));
                n = len > 0 ? n.times(1.0 / len) : new Vec(0, 1, 0);
                receivers.add(new MultiLightRenderer.Receiver(center, n, primitiveId++));
                worldLow = worldLow == null ? w.low() : Vec.min(worldLow, w.low());
                worldHigh = worldHigh == null ? w.high() : Vec.max(worldHigh, w.high());
            }
        }
        if (receivers.isEmpty()) return Lighting.OFF;
        currentModels = modelMap;

        var frame = snapshot.frame();
        var budget = new FrameBudget(frame.frameId(), config);
        // Translate section-level deforms (instanceId == groupId) to model index 0 if present.
        var coreDeforms = new ArrayList<ProceduralDeform>(frame.deforms().size());
        for (var d : frame.deforms()) {
            coreDeforms.add(new ProceduralDeform(d.groupId(), 0, d.poseRevision(), d.frameVertices()));
        }
        var coreFrame = new PresentationFrame(frame.frameId(), frame.partialTick(), frame.poses(), coreDeforms, frame.lights());
        renderer.beginFrame(structures, coreFrame, true, budget);

        // Place an enclosing virtual camera so every scene receiver lands inside a valid froxel.
        var mid = worldLow.plus(worldHigh).times(0.5);
        var span = worldHigh.minus(worldLow);
        double radius = Math.max(4.0, Math.sqrt(span.dot(span)) * 0.5 + 4.0);
        var cam = new FroxelClusterer.Camera(
                new Pose(new Vec(mid.x(), mid.y(), mid.z() - radius * 2.2), 0, 0, 0, 1),
                1280, 720, Math.toRadians(90), 0.1, radius * 5.0);

        var result = renderer.render(new ViewPassKey(frame.frameId(), 0, 0, 0, 0), cam, receivers);
        var energies = new double[receivers.size()];
        for (var sample : result.samples()) {
            var rgb = sample.direct();
            double lum = 0.2126 * rgb.x() + 0.7152 * rgb.y() + 0.0722 * rgb.z();
            energies[sample.receiver()] += lum;
        }
        var usage = budget.usage();
        var activeRenderer = renderer;
        return new Lighting() {
            @Override public int lights() { return usage.activeLights(); }
            @Override public int faces() { return usage.faces(); }
            @Override public int rays() { return usage.rays(); }
            @Override public double energy(long group, int triangleIndex) {
                Integer base = groupOffsets.get(group);
                if (base == null) return 0;
                int idx = base + triangleIndex;
                return idx >= 0 && idx < energies.length ? energies[idx] : 0;
            }
            @Override public int blockLight(Vec lo, Vec hi) {
                int lifted = activeRenderer.packedLight(new Bounds(lo, hi), 0);
                return (lifted >>> 4) & 15;
            }
        };
    }

    @Override
    public void close() {
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
        activeConfig = null;
        activeTier = null;
    }
}
