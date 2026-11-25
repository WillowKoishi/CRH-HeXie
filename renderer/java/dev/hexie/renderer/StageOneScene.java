package dev.hexie.renderer;

import java.util.*;

/** One point light and its fixed receivers live in owner-group space. The moving shell is
 * a child group. Its current triangles are shared by all three paths for this frame. */
public final class StageOneScene {
    private final long owner, wall;
    private final List<Triangle> fixed,rest;
    private final Bvh staticBlas,wallTopology;
    public StageOneScene(long owner,long wall,List<Triangle> receivers,List<Triangle> shell) {
        this.owner=owner; this.wall=wall;
        fixed=List.copyOf(receivers); rest=List.copyOf(shell);
        var ids=new HashSet<Long>();
        for (var group:List.of(fixed,rest)) for (var t:group)
            if (!ids.add(t.id())) throw new IllegalArgumentException("duplicate primitive");
        staticBlas=Bvh.build(fixed); wallTopology=Bvh.build(rest);
    }
    /** Resolve the two structural revisions once. Draw and query use the same instance-local
     * primitive ordering even when both groups reference the same mesh. Rigid motion is
     * subsequently supplied solely through PresentationFrame. */
    public static StageOneScene compile(StructureRevision owner,StructureRevision wall,
            java.util.function.Function<StructureRevision.ModelRef,List<Triangle>> lod0) {
        if (owner.groupId()==wall.groupId()) throw new IllegalArgumentException("distinct groups required");
        List<List<Triangle>> meshes=new ArrayList<>();
        long primitive=0;
        for (var revision:List.of(owner,wall)) {
            if (!revision.openings().isEmpty()) throw new UnsupportedOperationException("prototype expects baked LOD0");
            List<Triangle> group=new ArrayList<>();
            for (var model:revision.models()) for (var source:lod0.apply(model)) {
                Triangle t=source.transform(model.localTransform());
                group.add(new Triangle(primitive++,t.a(),t.b(),t.c()));
            }
            meshes.add(group);
        }
        return new StageOneScene(owner.groupId(),wall.groupId(),meshes.get(0),meshes.get(1));
    }
    public record Limits(boolean resident,int boxCapacity,int faces,int rays,int ddaVisits,int cellCapacity) {
        public static Limits complete() {
            return new Limits(true,Integer.MAX_VALUE,6,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE);
        }
        public Limits {
            if (Math.min(Math.min(boxCapacity,faces),Math.min(rays,Math.min(ddaVisits,cellCapacity)))<0)
                throw new IllegalArgumentException("negative budget");
        }
    }
    public Frame freeze(PresentationFrame frame,int resolution,double cellWidth,Limits limits) {
        if (frame.lights().size()!=1) throw new UnsupportedOperationException("one point light");
        LightState l=frame.lights().get(0);
        if (l.support(64)!=LightState.Support.SUPPORTED || l.ownerGroup()!=owner || l.attachment()!=owner)
            throw new UnsupportedOperationException("prototype light attachment or range");
        GroupPose motion=frame.poses().stream().filter(p->p.groupId()==wall).findFirst().orElseThrow();
        if (motion.attachParentId()!=owner) throw new IllegalArgumentException("shell parent");
        Pose local=motion.localAt(frame.partialTick());
        List<Vec> deform=null;
        for (var d:frame.deforms()) if (d.groupId()==wall && d.instanceId()==wall) {
            if (deform!=null || d.frameVertices().size()!=3*rest.size()) throw new IllegalArgumentException("shell topology");
            deform=d.frameVertices();
        }
        var updated=new LinkedHashMap<Long,Triangle>();
        int i=0;
        for (var t:rest) {
            Triangle source=deform==null?t:new Triangle(t.id(),deform.get(i),deform.get(i+1),deform.get(i+2));
            updated.put(t.id(),source.transform(local)); i+=3;
        }
        List<Triangle> all=new ArrayList<>(fixed); all.addAll(updated.values());
        return new Frame(l,all,wallTopology.refit(updated),resolution,cellWidth,limits);
    }
    public final class Frame {
        private final LightState light;
        private final List<Triangle> triangles;
        private final Bvh dynamicBlas;
        private final ConeCube.Grid grid;
        private final ConeCube cube;
        private final VoxelGrid voxels;
        private final Limits limits;
        private int raysLeft;
        private final VoxelGrid.VisitBudget visits;
        private Frame(LightState light,List<Triangle> all,Bvh dynamic,int size,double width,Limits limits) {
            this.light=light; triangles=List.copyOf(all); dynamicBlas=dynamic; this.limits=limits; raysLeft=limits.rays;
            visits=new VoxelGrid.VisitBudget(limits.ddaVisits);
            grid=size==0?null:new ConeCube.Grid(size,light.radius());
            cube=grid==null?null:ConeCube.build(ConeCube.prepare(grid,light.localPosition(),all,0,
                    limits.boxCapacity,limits.resident),limits.faces);
            voxels=width==0 || limits.ddaVisits==0?null:new VoxelGrid(all,width,limits.cellCapacity);
        }
        public List<Triangle> triangles() { return triangles; }
        public ConeCube.Grid grid() { return grid; }
        public int[] texels() { return cube==null?new int[0]:cube.texels(); }
        public Visibility cone(Vec receiver) {
            return cube==null?Visibility.UNDETERMINED:cube.query(ConeCube.receiverBox(grid,receiver,light.localPosition(),0));
        }
        private boolean claimRay() {
            if (raysLeft==0) return false;
            raysLeft--;
            return true;
        }
        public Visibility exact(Vec r,long id) {
            if (!limits.resident || !claimRay()) return Visibility.UNDETERMINED;
            Visibility own=staticBlas.query(r,light.localPosition(),id);
            return own==Visibility.OCCLUDED?own:own.and(dynamicBlas.query(r,light.localPosition(),id));
        }
        public Visibility visibility(Vec r,long id) {
            Visibility result=cone(r);
            return result==Visibility.VISIBLE?result:exact(r,id);
        }
        public Visibility voxel(Vec r,long id,Set<VoxelGrid.Cell> missing) {
            return limits.resident && voxels!=null?voxels.query(r,light.localPosition(),id,visits,missing,this::claimRay):Visibility.UNDETERMINED;
        }
        public double energy(Vec r,long id) {
            Vec dir=r.minus(light.localPosition());
            return light.energy(Math.sqrt(dir.dot(dir)),visibility(r,id),64);
        }
    }
}
