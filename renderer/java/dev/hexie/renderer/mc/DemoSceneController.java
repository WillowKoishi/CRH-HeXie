package dev.hexie.renderer.mc;

import dev.hexie.renderer.*;
import java.util.*;
import static dev.hexie.renderer.StructureRevision.*;

public final class DemoSceneController {
    private final Map<String,List<Triangle>> meshes=new HashMap<>();
    private final HexieRenderEngine engine;
    private Pose origin;
    private long ticks,revision;
    private int lights=1;
    public DemoSceneController(RendererBackend backend) {
        engine=new HexieRenderEngine(m->Objects.requireNonNull(meshes.get(m.geometryId()),m.geometryId()),backend);
    }
    public HexieRenderEngine engine() { return engine; }
    public void start(Vec at,boolean corridor) {
        start(translated(at),corridor);
    }
    public void start(Pose at,boolean corridor) {
        clear();
        origin=at;
        var room=new ArrayList<Triangle>();
        int length=corridor?16:7;
        for (int z=0;z<length;z++) {
            box(room,-2,0,z,2,0.0625,z+1);
            if (corridor) {
                box(room,-2,0,z,-1.9375,3,z+1);
                box(room,1.9375,0,z,2,3,z+1);
            }
        }
        for (int x=-2;x<2;x++) for (int y=0;y<3;y++) box(room,x,y,length,x+1,y+1,length+0.0625);
        var shell=new ArrayList<Triangle>();
        box(shell,-0.75,0,0,0.75,2.5,0.0625);
        meshes.put("receiver",room);
        meshes.put("wall",shell);
        var glass=new ArrayList<Triangle>();
        box(glass,1.75,1.2,2,1.8125,2.4,3);
        meshes.put("glass",glass);
        var grate=new ArrayList<Triangle>();
        box(grate,-1.8,1.2,2,-1.7375,2.4,3);
        meshes.put("grate",grate);
        for (int i=0;i<2;i++) {
            String key=i==0?"receiver":"wall";
            engine.structure(new StructureRevision(i,++revision,new SurfaceHostId.World("demo",new Cell(0,0,0)),
                    i==0?List.of(new ModelRef(key,key,Pose.IDENTITY),new ModelRef("glass","translucent",Pose.IDENTITY),
                            new ModelRef("grate","cutout",Pose.IDENTITY)):List.of(new ModelRef(key,key,Pose.IDENTITY)),List.of()));
        }
    }
    public boolean isActive() { return origin != null; }
    public void tick() { if (origin!=null) ticks++; }
    private static Pose translated(Vec v) { return new Pose(v,0,0,0,1); }
    private static Pose wall(long tick) { return translated(new Vec(1.7*Math.sin(tick*0.06),0,3)); }
    public void appendTo(List<GroupPose> poses, List<ProceduralDeform> deforms, List<LightState> outLights) {
        if (origin==null) return;
        poses.add(new GroupPose(0,GroupPose.ROOT,origin,Optional.empty()));
        poses.add(new GroupPose(1,0,wall(ticks),Optional.of(wall(ticks-1))));
        for (int i=0;i<lights;i++) outLights.add(new LightState(i,0,0,
                new Vec(i==0?0:1.5*Math.sin(i*2.4),1.6,0.5+(i%15)),new Vec(1,0.67,0.28),3.2,15,true,
                LightState.LightKind.POINT,0,0));
    }
    public void frame(long id,float partial) {
        var poses=new ArrayList<GroupPose>();
        var deforms=new ArrayList<ProceduralDeform>();
        var lamps=new ArrayList<LightState>();
        appendTo(poses, deforms, lamps);
        engine.present(new PresentationFrame(id,partial,poses,deforms,lamps));
    }
    public void clearDemoOnly() {
        origin=null; ticks=0;
        engine.removeGroup(0L);
        engine.removeGroup(1L);
        meshes.clear();
    }
    public int lights() { return lights; }
    public void lights(int count) {
        if (count<0 || count>8192) throw new IllegalArgumentException("0..8192");
        lights=count;
    }
    public void clear() { origin=null; ticks=0; engine.clear(); meshes.clear(); }
    private static void box(List<Triangle> triangles,double x,double y,double z,double X,double Y,double Z) {
        Vec[] v={new Vec(x,y,z),new Vec(X,y,z),new Vec(X,Y,z),new Vec(x,Y,z),
                 new Vec(x,y,Z),new Vec(X,y,Z),new Vec(X,Y,Z),new Vec(x,Y,Z)};
        for (int[] f:new int[][]{{0,3,2,1},{4,5,6,7},{0,4,7,3},{1,2,6,5},{0,1,5,4},{3,7,6,2}}) {
            triangles.add(new Triangle(triangles.size(),v[f[0]],v[f[1]],v[f[2]]));
            triangles.add(new Triangle(triangles.size(),v[f[0]],v[f[2]],v[f[3]]));
        }
    }
}
