package dev.hexie.renderer;

import java.util.*;
import java.util.function.Function;
import static org.lwjgl.opengl.GL45C.*;

/** Render-thread entry point. Geometry and receiver coordinates use the same frozen world frame.
 * Model indices are deformation instance IDs. The resolver supplies baked opaque LOD0 including openings.
 * Positive camera Z points into the screen. The caller supplies an explicit completeness bit for world streaming. */
public final class MultiLightRenderer implements AutoCloseable {
    public record Receiver(Vec position,Vec normal,long primitiveId) {}
    public record Sample(int receiver,int light,long lightId,Vec direct,Visibility visibility,boolean coneProof) {}
    public record Result(List<Sample> samples,boolean complete,boolean duplicate,double omittedUpperBound) {}
    public record Resources(int computePrograms,int scratchSsbos,long estimatedPeakBytes) {}
    private record OwnKey(long owner,long revision,Vec light,double radius,int size,double error,List<Triangle> geometry) {}
    private record ForeignKey(long frame,long owner,Vec light,double radius,int size) {}
    private final RenderTier tier;
    private final RendererConfig config;
    private final Function<StructureRevision.ModelRef,List<Triangle>> assets;
    private final CubePool<OwnKey> own;
    private final CubePool<ForeignKey> foreign;
    private final GpuPrograms gpu;
    private FrameBudget budget;
    private FrozenScene scene;
    private VoxelGrid voxels;
    private GpuVoxelGrid gpuGrid;
    private final Set<ViewPassKey> submitted=new HashSet<>();
    private double maximumEnergy;
    private long surfacePeak,transferPeak;
    private record SurfaceKey(long view,long pass,long owner,long batch) {}
    private record Surface(GeometryPass.Storage storage,long bytes,long frame) {}
    private final Map<SurfaceKey,Surface> surfaces=new HashMap<>();
    private long surfaceBytes;
    public MultiLightRenderer(RendererConfig config,RenderTier tier,Function<StructureRevision.ModelRef,List<Triangle>> assets) {
        this.tier=Objects.requireNonNull(tier); this.config=Objects.requireNonNull(config);
        this.assets=Objects.requireNonNull(assets);
        long total=(long)config.memoryMiB()<<20;
        long ownBytes=Math.min(total/2,(long)config.ownMiB()<<20);
        long single=config.foreignSize()>4096?0:24L*config.foreignSize()*config.foreignSize();
        long foreignBytes=Math.min((total-ownBytes)/2,single==0?0:
                single*Math.min(config.foreignCount(),(total/single)+1));
        own=new CubePool<>(ownBytes,Integer.MAX_VALUE); foreign=new CubePool<>(foreignBytes,config.foreignCount());
        gpu=tier==RenderTier.FULL && total>0?new GpuPrograms(total-ownBytes-foreignBytes):null;
    }
    public Resources resources() {
        return new Resources(gpu==null?0:gpu.computes(),gpu==null?0:gpu.allocations(),
                (gpu==null?0:gpu.peak())+own.peak()+foreign.peak()+surfacePeak+transferPeak);
    }
    public void beginFrame(List<StructureRevision> structures,PresentationFrame frame,boolean complete,FrameBudget ledger) {
        if (frame.frameId()!=ledger.frameId() || !config.equals(ledger.config()))
            throw new IllegalArgumentException("frame ledger mismatch");
        if (budget!=null && ledger.frameId()<=budget.frameId()) throw new IllegalArgumentException("frame already frozen");
        var next=gpu!=null?new FrozenScene(structures,frame,assets,complete,ledger):null;
        var iterator=surfaces.values().iterator();
        while (iterator.hasNext()) {
            var s=iterator.next();
            if (budget!=null && s.frame!=budget.frameId()) {
                s.storage.closeAfterLastRead(); surfaceBytes-=s.bytes; iterator.remove();
            }
        }
        if (gpu!=null) { gpu.ledger=ledger; gpu.surfaceBytes=surfaceBytes; }
        scene=next; budget=ledger; submitted.clear(); voxels=null; gpuGrid=null;
        maximumEnergy=0;
        for (var light:frame.lights()) if (light.enabled() && light.support(64)==LightState.Support.SUPPORTED) {
            Vec rgb=light.linearRgb();
            maximumEnergy+=Math.max(rgb.x(),Math.max(rgb.y(),rgb.z()))*light.intensity();
        }
    }
    public int packedLight(Bounds draw,int original) {
        if (scene==null || !config.pointLights()) return original;
        return ExternalEntityLighting.packedLight(original,tier,draw,scene.lights,scene::empty);
    }
    /** Convenience bridge: freeze once for this ledger, then submit each distinct view/pass/batch. */
    public Result render(List<StructureRevision> structures,PresentationFrame frame,boolean resident,FrameBudget ledger,
            ViewPassKey key,FroxelClusterer.Camera camera,List<Receiver> receivers) {
        if (key.frameId()!=frame.frameId() || key.frameId()!=ledger.frameId())
            throw new IllegalArgumentException("view/frame mismatch");
        if (ledger!=budget) beginFrame(structures,frame,resident,ledger);
        return render(key,camera,receivers);
    }
    public Result render(ViewPassKey key,FroxelClusterer.Camera camera,List<Receiver> receivers) {
        requireFrame(key);
        if (submitted.contains(key)) return new Result(List.of(),true,true,0);
        Result result;
        if (scene==null || !config.pointLights() || receivers.isEmpty()) result=new Result(List.of(),true,false,0);
        else if (!scene.resident || !budget.hasTime()) result=new Result(List.of(),false,false,maximumEnergy*receivers.size());
        else {
            int previous=glGetInteger(GL_CURRENT_PROGRAM);
            try { result=evaluate(camera,receivers); }
            catch (GpuPrograms.Unavailable unavailable) { result=new Result(List.of(),false,false,maximumEnergy*receivers.size()); }
            finally { glUseProgram(previous); }
        }
        submitted.add(key);
        return result;
    }
    /** Submit after host terrain has completed. The callback binds draw state and consumes this view's handles. */
    public boolean renderGeometry(ViewPassKey key,FroxelClusterer.Camera camera,GeometryPass.Input input,
            java.util.function.Consumer<GeometryPass.Prepared> submit) {
        requireFrame(key);
        if (scene==null || submitted.contains(key) || !budget.hasTime()) return false;
        long bytes=GeometryPass.bytes(input,config,scene.world.size());
        var surfaceKey=new SurfaceKey(key.viewId(),key.passId(),key.ownerId(),key.batchId());
        Surface previous=surfaces.get(surfaceKey);
        if (bytes>gpu.availableSurfaceBytes()-surfaceBytes+(previous==null?0:previous.bytes))
            return false;
        GeometryPass.Storage storage;
        if (previous==null || previous.storage.width!=input.width() || previous.storage.height!=input.height()) {
            if (previous!=null) previous.storage.closeAfterLastRead();
            storage=new GeometryPass.Storage(input,config);
        } else storage=previous.storage;
        surfaceBytes+=bytes-(previous==null?0:previous.bytes);
        surfaces.put(surfaceKey,new Surface(storage,bytes,key.frameId()));
        gpu.surfaceBytes=surfaceBytes;
        try (var guard=new GlStateGuard(new int[]{0},new int[]{0},new int[0],new int[0])) {
            GeometryPass.execute(storage,gpu,config,budget,scene,camera,input,p->{
                guard.restore(); submit.accept(p);
            });
        }
        submitted.add(key); surfacePeak=Math.max(surfacePeak,surfaceBytes);
        return true;
    }
    private void requireFrame(ViewPassKey key) {
        if (budget==null || key.frameId()!=budget.frameId()) throw new IllegalArgumentException("wrong presentation frame");
    }
    private record Lists(int[] ranges,int[] indices,boolean truncated) {}
    private Lists lists(FroxelClusterer.Grid grid,int lightsBuffer) {
        int cells=grid.boxes().size(), program=gpu.froxel;
        double[] bounds=new double[8*cells];
        for (int i=0;i<cells;i++) {
            FrozenScene.put(bounds,8*i,grid.boxes().get(i).low()); FrozenScene.put(bounds,8*i+4,grid.boxes().get(i).high());
        }
        int[] ranges=new int[2*cells];
        int cellBuffer=gpu.doubles(1,bounds),rangeBuffer=gpu.ints(2,ranges);
        String[] blocks={"Lights","Cells","Ranges","Indices"};
        glProgramUniform1i(program,0,scene.lights.size()); glProgramUniform1i(program,1,cells);
        glProgramUniform1i(program,2,0); glProgramUniform1i(program,3,config.priorityLights());
        gpu.run(program,cells,blocks,lightsBuffer,cellBuffer,rangeBuffer,gpu.buffer(3,16));
        if (ranges.length!=0) glGetNamedBufferSubData(rangeBuffer,0,ranges);
        GpuPrograms.check();
        int total=0; boolean omitted=false;
        for (int i=0;i<cells;i++) {
            int requested=ranges[2*i+1], kept=budget.indices(requested);
            ranges[2*i]=total; ranges[2*i+1]=kept; total+=kept;
            omitted|=requested!=kept;
        }
        int[] indices=new int[total];
        if (total>0) {
            int output=gpu.buffer(3,4L*total);
            gpu.ints(2,ranges); glProgramUniform1i(program,2,1);
            gpu.run(program,cells,blocks,lightsBuffer,cellBuffer,rangeBuffer,output);
            glGetNamedBufferSubData(output,0,indices); GpuPrograms.check();
        }
        return new Lists(ranges,indices,omitted);
    }
    private Result evaluate(FroxelClusterer.Camera camera,List<Receiver> receivers) {
        var grid=new FroxelClusterer(camera,config).cluster(receivers.stream().map(Receiver::position).toList());
        int lightsBuffer=gpu.doubles(0,scene.lightData);
        var lists=lists(grid,lightsBuffer);
        boolean complete=!scene.truncated && !lists.truncated;
        double omitted=complete?0:maximumEnergy*receivers.size();
        int total=0;
        for (int c:grid.receiverCells()) if (c>=0) total=Math.addExact(total,lists.ranges[2*c+1]);
        if (total==0) return new Result(List.of(),complete,false,omitted);
        double[] positions=new double[8*receivers.size()];
        int[] work=new int[Math.multiplyExact(total,4)];
        boolean[] cone=new boolean[total];
        var pending=new LinkedHashMap<Integer,List<Integer>>();
        int next=0;
        for (int r=0;r<receivers.size();r++) {
            Receiver receiver=receivers.get(r);
            FrozenScene.put(positions,8*r,receiver.position); FrozenScene.put(positions,8*r+4,receiver.normal);
            positions[8*r+3]=receiver.primitiveId>=0?receiver.primitiveId+1.0:0.0;
            int c=grid.receiverCells()[r]; if (c<0) continue;
            BitSet proven=new BitSet(scene.lights.size());
            scene.tree.proven(Bounds.point(receiver.position),scene::empty,proven::set);
            for (int j=0;j<lists.ranges[2*c+1];j++) {
                int l=lists.indices[lists.ranges[2*c]+j], i=next++;
                work[4*i]=r; work[4*i+1]=l;
                if (proven.get(l)) work[4*i+2]=1;
                else pending.computeIfAbsent(l,k->new ArrayList<>()).add(i);
            }
        }
        for (var e:pending.entrySet()) proveCubes(scene.lights.get(e.getKey()),e.getValue(),work,receivers,cone);
        for (int i=0;i<total;i++) if (work[4*i+2]==0 && budget.ray()) work[4*i+3]=1;
        int receiverBuffer=gpu.doubles(1,positions), nodes=gpu.doubles(4,scene.nodes);
        int queries=gpu.ints(5,work), output=gpu.buffer(6,32L*total);
        glProgramUniform1i(gpu.eval,0,total); glProgramUniform1i(gpu.eval,1,scene.nodes.length/20);
        int steps=budget.hasTime()?config.limits().ddaSteps()-budget.usage().ddaSteps():0;
        if (gpuGrid==null) gpuGrid=new GpuVoxelGrid(scene.world,steps==0?0:config.cellWidth(),(long)config.memoryMiB()<<12);
        int gridBuffer=gpu.ints(7,gpuGrid.data);
        glProgramUniform3iv(gpu.eval,8,gpuGrid.origin); glProgramUniform3iv(gpu.eval,9,gpuGrid.size);
        glProgramUniform1d(gpu.eval,10,config.cellWidth()); glProgramUniform1i(gpu.eval,11,gpuGrid.enabled?steps:0);
        gpu.run(gpu.eval,total,new String[]{"Lights","Receivers","Nodes","Work","Output","GridData"},
                lightsBuffer,receiverBuffer,nodes,queries,output,gridBuffer);
        if (gpuGrid.enabled && steps>0) {
            int[] used={0}; glGetNamedBufferSubData(gridBuffer,0,used); budget.chargeSteps(used[0]);
        }
        double[] direct=new double[4*total]; glGetNamedBufferSubData(output,0,direct); GpuPrograms.check();
        int unused=0;
        for (int i=0;i<total;i++) if (work[4*i+3]!=0 && direct[4*i+3]==2) unused++;
        budget.returnUnusedRays(unused);
        List<Sample> samples=new ArrayList<>(total);
        for (int i=0;i<total;i++) {
            int p=4*i,r=work[p],l=work[p+1], status=(int)direct[p+3];
            Vec energy=new Vec(direct[p],direct[p+1],direct[p+2]);
            Visibility v=status>0?Visibility.VISIBLE:status<0?Visibility.OCCLUDED:Visibility.UNDETERMINED;
            if (v==Visibility.UNDETERMINED && (work[p+3]!=0 || budget.ray())) {
                var receiver=receivers.get(r); var light=scene.lights.get(l);
                v=exact(receiver,light);
                if (v==Visibility.VISIBLE) energy=direct(receiver,light);
            }
            if (v==Visibility.UNDETERMINED) {
                Vec upper=direct(receivers.get(r),scene.lights.get(l));
                double bound=Math.max(upper.x(),Math.max(upper.y(),upper.z()));
                if (bound>0) { complete=false; omitted+=bound; }
            }
            samples.add(new Sample(r,l,scene.lights.get(l).state().lightId(),energy,v,cone[i]));
        }
        return new Result(List.copyOf(samples),complete,false,omitted);
    }
    private static Vec direct(Receiver receiver,GroupLightTree.Light light) {
        Vec d=light.position().minus(receiver.position);
        double len=Math.sqrt(d.dot(d)), cosine=len==0?1:Math.max(0,receiver.normal.dot(d)/len);
        return light.state().linearRgb().times(cosine*light.state().energy(len,Visibility.VISIBLE,64));
    }
    private Visibility exact(Receiver r,GroupLightTree.Light light) {
        if (scene.bvh==null) return Visibility.VISIBLE;
        if (gpuGrid!=null && gpuGrid.enabled && config.cellWidth()>0 && config.limits().ddaSteps()>budget.usage().ddaSteps()
                && Math.scalb(1.0,Math.getExponent(config.cellWidth()))==config.cellWidth()) {
            if (voxels==null) voxels=new VoxelGrid(scene.world,config.cellWidth(),Integer.MAX_VALUE);
            var v=voxels.query(r.position,light.position(),r.primitiveId,budget::step,Set.of(),()->true);
            if (v!=Visibility.UNDETERMINED) return v;
        }
        return scene.bvh.query(r.position,light.position(),r.primitiveId);
    }
    private void proveCubes(GroupLightTree.Light l,List<Integer> jobs,int[] work,List<Receiver> receivers,boolean[] cone) {
        // Amortize builds and transfers over multiple pending receivers of a light.
        if (jobs.size()<2 || !budget.hasTime()) return;
        long ownerId=l.state().ownerGroup();
        Pose ownerPose=scene.pose(ownerId);
        List<Triangle> fixed=new ArrayList<>(),others=new ArrayList<>();
        for (var c:scene.casters) {
            if (c.fixed() && c.owner()==ownerId) fixed.add(c.local()); else others.add(c.world());
        }
        // Reading a hot map amortizes setup across a tile. A cold foreign cube
        // costs six populated faces; small batches go directly to the frame BVH.
        int resolution=others.isEmpty()?config.ownMinSize():config.foreignSize();
        int threshold=others.isEmpty()?Math.max(2,resolution/8):Math.max(2,resolution*resolution/16);
        if (jobs.size()<threshold) return;
        int[] a=null,b=null;
        if (!fixed.isEmpty()) {
            if (config.ownMiB()==0 || config.ownMinSize()==0 || config.ownMaxSize()==0 ||
                    ownerPose==null || l.state().attachment()!=ownerId) return;
            int selected=Math.min(4096,config.ownMinSize());
            int largest=Math.min(4096,config.ownMaxSize());
            while (selected<largest && jobs.size()>selected*selected/16)
                selected=Math.min(largest,selected*2);
            final int size=selected;
            Pose inverse=new Pose(Vec.ZERO,-ownerPose.x(),-ownerPose.y(),-ownerPose.z(),ownerPose.w());
            var points=new ArrayList<Vec>(jobs.size());
            double magnitude=1;
            for (var t:fixed) magnitude=Math.max(magnitude,Math.max(maxAbs(t.low()),maxAbs(t.high())));
            magnitude=Math.max(magnitude,Math.max(maxAbs(ownerPose.translation()),maxAbs(l.position())));
            for (int i:jobs) {
                Vec r=receivers.get(work[4*i]).position;
                magnitude=Math.max(magnitude,maxAbs(r));
                points.add(inverse.apply(r.minus(ownerPose.translation())));
            }
            double error=Math.ulp(magnitude)*256;
            var grid=new ConeCube.Grid(size,l.state().radius());
            var key=new OwnKey(ownerId,scene.revisions.get(ownerId).revision(),l.state().localPosition(),grid.radius(),size,error,List.copyOf(fixed));
            var map=own.acquire(key,size,budget,()->build(grid,key.light,fixed,error));
            if (map==null) return;
            a=query(map,grid,points,key.light,error);
        }
        if (!others.isEmpty()) {
            int size=Math.min(config.foreignSize(),4096);
            if (size==0 || config.foreignCount()==0) return;
            var grid=new ConeCube.Grid(size,l.state().radius());
            var key=new ForeignKey(budget.frameId(),ownerId,l.position(),grid.radius(),size);
            var map=foreign.acquire(key,size,budget,()->build(grid,l.position(),others,0));
            if (map==null) return;
            b=query(map,grid,jobs.stream().map(i->receivers.get(work[i*4]).position).toList(),l.position(),0);
        }
        for (int j=0;j<jobs.size();j++) if ((a==null || a[j]==1) && (b==null || b[j]==1)) {
            int i=jobs.get(j); work[4*i+2]=1; cone[i]=true;
        }
    }
    private static double maxAbs(Vec v) { return Math.max(Math.abs(v.x()),Math.max(Math.abs(v.y()),Math.abs(v.z()))); }
    private GpuConeCube build(ConeCube.Grid grid,Vec origin,List<Triangle> geometry,double error) {
        var input=ConeCube.prepare(grid,origin,geometry,error,Integer.MAX_VALUE,true);
        transferPeak=Math.max(transferPeak,32L*Math.max(1,input.boxes().size()));
        return new GpuConeCube(gpu.cone,List.of(input),new int[]{6},budget);
    }
    private int[] query(GpuConeCube cube,ConeCube.Grid grid,List<Vec> points,Vec origin,double error) {
        transferPeak=Math.max(transferPeak,36L*points.size());
        return cube.query(points.stream().map(p->ConeCube.receiverBox(grid,p,origin,error)).toList(),null,null,gpu.proof,true);
    }
    @Override public void close() {
        for (var s:surfaces.values()) s.storage.closeAfterLastRead();
        surfaces.clear(); own.close(); foreign.close(); if (gpu!=null) gpu.close(); }
}
