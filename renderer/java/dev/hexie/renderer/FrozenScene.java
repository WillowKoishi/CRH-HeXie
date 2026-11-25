package dev.hexie.renderer;

import java.util.*;
import java.util.function.Function;

/** Immutable opaque LOD0 inputs. External entities are only receivers. A null asset means missing data. */
final class FrozenScene {
    record Caster(long owner,boolean fixed,Triangle local,Triangle world) {}
    final List<Caster> casters=new ArrayList<>();
    final List<Triangle> world=new ArrayList<>();
    final Map<Long,StructureRevision> revisions=new HashMap<>();
    final Map<Long,Pose> poses;
    final List<GroupLightTree.Light> lights=new ArrayList<>();
    final GroupLightTree tree;
    final Bvh bvh;
    final double[] nodes,lightData;
    final boolean resident;
    boolean truncated;
    FrozenScene(List<StructureRevision> structures,PresentationFrame frame,
                Function<StructureRevision.ModelRef,List<Triangle>> lod0,boolean complete,FrameBudget budget) {
        poses=frame.worldPoses();
        var deforms=new HashMap<List<Long>,ProceduralDeform>();
        for (var d:frame.deforms()) deforms.put(List.of(d.groupId(),d.instanceId()),d);
        long primitive=0;
        for (var structure:structures) {
            if (revisions.put(structure.groupId(),structure)!=null) throw new IllegalArgumentException("duplicate group");
            Pose pose=pose(structure.groupId());
            if (pose==null) { complete=false; continue; }
            for (int m=0;m<structure.models().size();m++) {
                var model=structure.models().get(m);
                var mesh=lod0.apply(model);
                if (mesh==null) { complete=false; continue; }
                var deform=deforms.remove(List.of(structure.groupId(),(long)m));
                if (deform!=null && deform.frameVertices().size()!=3L*mesh.size()) { complete=false; continue; }
                for (int i=0;i<mesh.size();i++) {
                    Triangle t=mesh.get(i);
                    if (deform!=null) {
                        var v=deform.frameVertices(); t=new Triangle(primitive,v.get(3*i),v.get(3*i+1),v.get(3*i+2));
                    } else t=t.transform(model.localTransform());
                    t=new Triangle(primitive++,t.a(),t.b(),t.c());
                    Triangle w=t.transform(pose);
                    casters.add(new Caster(structure.groupId(),deform==null,t,w)); world.add(w);
                }
            }
        }
        resident=complete && deforms.isEmpty();
        for (var state:frame.lights()) {
            if (!budget.config().pointLights()) break;
            if (!state.enabled() || state.support(64)!=LightState.Support.SUPPORTED || state.intensity()==0) continue;
            Pose attachment=pose(state.attachment());
            if (attachment==null || !budget.light()) { truncated=true; continue; }
            lights.add(new GroupLightTree.Light(lights.size(),state,attachment.apply(state.localPosition())));
        }
        tree=new GroupLightTree(lights);
        bvh=world.isEmpty()?null:Bvh.build(world);
        nodes=new double[world.isEmpty()?0:Math.multiplyExact(2*world.size()-1,20)];
        if (bvh!=null) bvh.writeNodes(nodes,0);
        lightData=new double[Math.multiplyExact(lights.size(),8)];
        for (var l:lights) {
            int i=l.index()*8;
            put(lightData,i,l.position()); lightData[i+3]=l.state().radius();
            put(lightData,i+4,l.state().linearRgb()); lightData[i+7]=l.state().intensity();
        }
    }
    Pose pose(long id) { return id==GroupPose.ROOT?Pose.IDENTITY:poses.get(id); }
    boolean empty(Bounds b) { return resident && (bvh==null || !bvh.intersects(b)); }
    static void put(double[] data,int i,Vec v) { data[i]=v.x(); data[i+1]=v.y(); data[i+2]=v.z(); }
}
