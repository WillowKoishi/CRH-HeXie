package dev.hexie.renderer.mc;

import dev.hexie.renderer.*;
import dev.hexie.renderer.parse.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import willow.train.hexie.mc.client.renderbridge.RenderBridge;

public final class HexieRenderEngine implements RenderBridge {
    private final Map<Long,RendererBackend.Section> sections = new LinkedHashMap<>();
    private final Map<String,ImageSource> imageSources = new ConcurrentHashMap<>();
    private final Map<String,CompiledMesh> registeredCompiled = new ConcurrentHashMap<>();
    private final Map<String,List<Triangle>> registeredTriangles = new ConcurrentHashMap<>();
    private final Function<StructureRevision.ModelRef,List<Triangle>> models;
    private final Function<StructureRevision.ModelRef,CompiledMesh> compiledModels;
    private final RendererBackend backend;
    private RendererBackend.Snapshot snapshot;
    private RendererBackend.Lighting lighting = RendererBackend.Lighting.OFF;
    private final FrameRoute route = new FrameRoute();
    private final ViewPassOwnership ownership = new ViewPassOwnership();
    private RenderSettings settings = RenderSettings.of(RenderSettings.Preset.MEDIUM);

    public HexieRenderEngine(Function<StructureRevision.ModelRef,List<Triangle>> models,RendererBackend backend) {
        this(models, null, backend);
    }

    public HexieRenderEngine(Function<StructureRevision.ModelRef,List<Triangle>> models,
                             Function<StructureRevision.ModelRef,CompiledMesh> compiledModels,
                             RendererBackend backend) {
        this.models=models;
        this.compiledModels=compiledModels;
        this.backend=backend;
    }

    public void registerImageSource(String imageId, ImageSource source) {
        if (source == null) imageSources.remove(imageId);
        else imageSources.put(imageId, source);
    }

    public Optional<ImageSource> imageSource(String imageId) {
        return Optional.ofNullable(imageSources.get(imageId));
    }

    public void registerCompiledModel(String geometryId, CompiledMesh mesh) {
        if (mesh == null) registeredCompiled.remove(geometryId);
        else registeredCompiled.put(geometryId, mesh);
    }

    public void registerModel(String geometryId, List<Triangle> triangles) {
        if (triangles == null) registeredTriangles.remove(geometryId);
        else registeredTriangles.put(geometryId, List.copyOf(triangles));
    }

    public void unregisterModel(String geometryId) {
        registeredCompiled.remove(geometryId);
        registeredTriangles.remove(geometryId);
    }

    public synchronized void removeGroup(long groupId) {
        sections.remove(groupId);
    }

    public synchronized boolean hasGroup(long groupId) {
        return sections.containsKey(groupId);
    }

    public synchronized int sectionCount() {
        return sections.size();
    }

    @Override public synchronized void structure(StructureRevision revision) {
        var old=sections.get(revision.groupId());
        if (old!=null && old.revision().revision()>=revision.revision()) return;
        boolean hasCompiled = compiledModels != null || revision.models().stream().anyMatch(m -> registeredCompiled.containsKey(m.geometryId()));
        if (revision.openings().isEmpty() && !hasCompiled) {
            List<Triangle> triangles=new ArrayList<>();
            var submeshes=new ArrayList<RendererBackend.Submesh>();
            for (var model:revision.models()) {
                int start=triangles.size();
                List<Triangle> raw = registeredTriangles.get(model.geometryId());
                if (raw == null) raw = models.apply(model);
                for (var t:raw) triangles.add(t.transform(model.localTransform()));
                submeshes.add(new RendererBackend.Submesh(model.materialId(),start,triangles.size()));
            }
            sections.put(revision.groupId(),new RendererBackend.Section(revision,triangles,submeshes));
            return;
        }
        // Build unculled LOD0 compiled mesh across all model refs
        var baseParts = new ArrayList<CompiledMesh>(revision.models().size());
        for (var model : revision.models()) {
            CompiledMesh part = resolveCompiled(model);
            baseParts.add(part.transform(model.localTransform()));
        }
        CompiledMesh host = CompiledMesh.merge(baseParts);
        var overlayParts = new ArrayList<CompiledMesh>();
        boolean overlayOnly = !revision.openings().isEmpty();

        for (var req : revision.openings()) {
            PlaneFrame frame = PlaneFrame.fromInward(req.placement().translation(), req.inward());
            String mask = req.cutMask().orElse("");
            if (mask.startsWith("decal:") || mask.startsWith("decal_overlay:")) {
                boolean isOverlay = mask.startsWith("decal_overlay:");
                if (!isOverlay) overlayOnly = false;
                String imgId = mask.substring(isOverlay ? "decal_overlay:".length() : "decal:".length());
                var p = req.parameters();
                var spec = new DecalProjector.DecalSpec(
                        imgId,
                        PlaneFrame.CardinalFace.fromNormal(frame.outward()),
                        p.getOrDefault("offsetU", 0.0),
                        p.getOrDefault("offsetV", 0.0),
                        p.getOrDefault("width", 1.0),
                        p.getOrDefault("height", 1.0),
                        p.getOrDefault("rotation", 0.0));
                CompiledMesh decal = DecalProjector.project(host, frame, spec);
                if (!decal.isEmpty()) overlayParts.add(decal);
            } else {
                overlayOnly = false;
                var winSpec = ParametricWindowSpec.fromOpening(req);
                var cut = CoplanarLayerCutter.cutAndBuildWindow(host, frame, winSpec);
                host = cut.cutHost();
                if (!cut.windowFrame().isEmpty()) overlayParts.add(cut.windowFrame());
                if (!cut.windowGlass().isEmpty()) overlayParts.add(cut.windowGlass());
            }
        }

        var allParts = new ArrayList<CompiledMesh>(1 + overlayParts.size());
        if (!overlayOnly) allParts.add(host);
        allParts.addAll(overlayParts);
        CompiledMesh baked = CompiledMesh.merge(allParts);

        List<Triangle> triangles = baked.toRendererTriangles(revision.groupId() << 20);
        var submeshes = new ArrayList<RendererBackend.Submesh>(baked.submeshes().size());
        for (var s : baked.submeshes()) {
            submeshes.add(new RendererBackend.Submesh(s.materialId(), s.start(), s.end(), s.argb(), s.translucent()));
        }
        sections.put(revision.groupId(), new RendererBackend.Section(revision, triangles, submeshes, baked));
    }

    private CompiledMesh resolveCompiled(StructureRevision.ModelRef model) {
        CompiledMesh reg = registeredCompiled.get(model.geometryId());
        if (reg != null) return reg;
        if (compiledModels != null) {
            CompiledMesh cm = compiledModels.apply(model);
            if (cm != null) return cm;
        }
        List<Triangle> raw = registeredTriangles.get(model.geometryId());
        if (raw == null) raw = models.apply(model);
        var tris = new ArrayList<CompiledMesh.Tri>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            Triangle t = raw.get(i);
            Vec fn = t.b().minus(t.a()).cross(t.c().minus(t.a()));
            double len = Math.sqrt(fn.dot(fn));
            Vec n = len > 1e-12 ? fn.times(1.0 / len) : new Vec(0, 1, 0);
            tris.add(new CompiledMesh.Tri(i,
                    new CompiledMesh.Vertex(t.a(), n, 0, 0),
                    new CompiledMesh.Vertex(t.b(), n, 1, 0),
                    new CompiledMesh.Vertex(t.c(), n, 1, 1),
                    model.materialId(), 0xFFFFFFFF, false));
        }
        return CompiledMesh.of(tris);
    }
    @Override public synchronized void present(PresentationFrame frame) {
        if (snapshot!=null && snapshot.frame().frameId()==frame.frameId()) return;
        var deformed=new HashMap<Long,List<Triangle>>();
        for (var deform:frame.deforms()) {
            var section=sections.get(deform.groupId());
            if (section==null || deform.instanceId()!=deform.groupId()
                    || deform.frameVertices().size()!=3L*section.triangles().size())
                throw new IllegalArgumentException("stage-one deformation must match section LOD0");
            var triangles=new ArrayList<Triangle>();
            var v=deform.frameVertices();
            for (var rest:section.triangles()) {
                int i=3*triangles.size();
                triangles.add(new Triangle(rest.id(),v.get(i),v.get(i+1),v.get(i+2)));
            }
            if (deformed.put(deform.groupId(),List.copyOf(triangles))!=null) throw new IllegalArgumentException("duplicate deform");
        }
        var next=new RendererBackend.Snapshot(frame,Map.copyOf(sections),frame.worldPoses(),Map.copyOf(deformed));
        lighting.close();
        lighting=RendererBackend.Lighting.OFF;
        snapshot=next;
        ownership.beginFrame();
        if ((!frame.lights().isEmpty() || !next.sections().isEmpty())
                && settings.effective(RenderSettings.Field.ACTIVE_LIGHTS,route.tier())>0) {
            lighting=backend.prepare(next,settings);
        } else {
            ForwardLight.clear();
        }
    }
    public synchronized void clear() {
        lighting.close();
        backend.clear();
        lighting=RendererBackend.Lighting.OFF;
        snapshot=null;
        sections.clear();
        ownership.beginFrame();
    }
    public RendererBackend.Snapshot snapshot() { return snapshot; }
    public RendererBackend.Lighting lighting() { return lighting; }
    public ViewPassOwnership ownership() { return ownership; }
    public FrameRoute route() { return route; }
    public RenderSettings settings() { return settings; }
    public int effective(RenderSettings.Field field) { return backend.effective(settings,field,route.tier()); }
    public void settings(RenderSettings value) { settings=Objects.requireNonNull(value); }
}
