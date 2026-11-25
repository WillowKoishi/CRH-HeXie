package dev.hexie.renderer.mc;

import dev.hexie.renderer.*;
import java.util.*;
import static dev.hexie.renderer.mc.RenderSettings.Field.*;

/** Adapter for the two-group, one-point-light prototype in the pinned core. */
public final class StageOneBackend implements RendererBackend {
    @FunctionalInterface public interface CubeQuery {
        int[] visible(ConeCube.Input input,List<ConeCube.Box> receivers);
    }
    private final CubeQuery gpu;
    private Section fixed, moving;
    private StageOneScene scene;
    public StageOneBackend(CubeQuery gpu) { this.gpu=gpu; }

    @Override public int effective(RenderSettings settings, RenderSettings.Field field,RenderSettings.Tier tier) {
        int value=settings.effective(field,tier);
        int res=Math.min(512,settings.effective(FOREIGN_RES,tier));
        boolean cube=gpu!=null && res>0 && 24L*res*res<=((long)settings.get(MEMORY_MIB)<<20);
        return switch(field) {
            case ACTIVE_LIGHTS -> Math.min(1,value);
            case FOREIGN_RES -> cube?res:0;
            case FOREIGN_CUBES -> cube?1:0;
            case MAP_FACES -> cube?6:0;
            case POINT_LIGHTS, EXACT_RAYS, MEMORY_MIB -> value;
            default -> 0;
        };
    }

    @Override public Lighting prepare(Snapshot snapshot,RenderSettings settings) {
        var input=snapshot.frame();
        var light=input.lights().stream().filter(l->l.enabled() && l.attachment()==l.ownerGroup()
                && l.support(64)==LightState.Support.SUPPORTED).findFirst().orElse(null);
        if (light==null || snapshot.sections().size()!=2) return Lighting.OFF;
        var child=input.poses().stream().filter(p->p.attachParentId()==light.ownerGroup()).findFirst().orElse(null);
        if (child==null) return Lighting.OFF;
        if (input.deforms().stream().anyMatch(d->d.groupId()!=child.groupId())) return Lighting.OFF;
        var a=snapshot.sections().get(light.ownerGroup());
        var b=snapshot.sections().get(child.groupId());
        if (a==null || b==null || a.triangles().isEmpty() || b.triangles().isEmpty()) return Lighting.OFF;
        if (a!=fixed || b!=moving) {
            // Primitive IDs are local to an asset. Give each instance its own range.
            scene=new StageOneScene(light.ownerGroup(),child.groupId(),renumber(a,0),renumber(b,a.triangles().size()));
            fixed=a;
            moving=b;
        }
        var frame=new PresentationFrame(input.frameId(),input.partialTick(),input.poses(),input.deforms(),List.of(light));
        int count=a.triangles().size()+b.triangles().size();
        // C is the prototype fallback. A one-texel invalid CPU cube is never queried.
        var limits=new StageOneScene.Limits(true,0,0,settings.get(EXACT_RAYS),0,0);
        var frozen=scene.freeze(frame,1,16,limits);
        var centers=frozen.triangles().stream().map(t->t.a().plus(t.b()).plus(t.c()).times(1.0/3)).toList();
        int[] visible=new int[count];
        int resolution=Math.min(512,settings.effective(FOREIGN_RES,RenderSettings.Tier.FULL));
        boolean map=gpu!=null && resolution>0 && 24L*resolution*resolution <= ((long)settings.get(MEMORY_MIB)<<20);
        if (map) {
            var grid=new ConeCube.Grid(resolution,light.radius());
            var boxes=ConeCube.prepare(grid,light.localPosition(),frozen.triangles(),0,count,true);
            visible=gpu.visible(boxes,centers.stream().map(p->ConeCube.receiverBox(grid,p,light.localPosition(),0)).toList());
        }
        var energies=new double[count];
        int rays=0;
        for (int i=0;i<count;i++) {
            var sample=centers.get(i);
            var dir=sample.minus(light.localPosition());
            if (dir.dot(dir)>=light.radius()*light.radius()) continue;
            var visibility=Visibility.VISIBLE;
            if (visible[i]==0) {
                if (rays>=settings.get(EXACT_RAYS)) continue;
                rays++;
                visibility=frozen.exact(sample,i);
            }
            energies[i]=light.energy(Math.sqrt(dir.dot(dir)),visibility,64);
        }
        int rayCount=rays;
        return new Lighting() {
            public int lights() { return 1; }
            public int faces() { return map ? 6 : 0; }
            public int rays() { return rayCount; }
            public double energy(long group,int index) {
                return energies[index+(group==light.ownerGroup()?0:a.triangles().size())];
            }
        };
    }
    private static List<Triangle> renumber(Section section,int first) {
        var result=new ArrayList<Triangle>();
        for (var t:section.triangles()) result.add(new Triangle(first++,t.a(),t.b(),t.c()));
        return result;
    }
}
