package dev.hexie.renderer.mc;

import dev.hexie.renderer.*;
import dev.hexie.renderer.parse.CompiledMesh;
import java.util.*;

public interface RendererBackend {
    record Submesh(String material,int start,int end,int argb,boolean translucent) {
        public Submesh(String material,int start,int end) { this(material,start,end,0xFFFFFFFF,false); }
    }
    record Section(StructureRevision revision,List<Triangle> triangles,List<Submesh> submeshes,CompiledMesh compiled) {
        public Section { triangles=List.copyOf(triangles); submeshes=List.copyOf(submeshes); }
        public Section(StructureRevision revision,List<Triangle> triangles,List<Submesh> submeshes) {
            this(revision,triangles,submeshes,CompiledMesh.EMPTY);
        }
    }
    record Snapshot(PresentationFrame frame,Map<Long,Section> sections,Map<Long,Pose> world,
                    Map<Long,List<Triangle>> deformed) {
        public List<Triangle> triangles(long group) {
            return deformed.getOrDefault(group,sections.get(group).triangles());
        }
    }
    record Receiver(Object owner,Object material,Object view,Vec camera,float[] rotation,boolean packedNormal) {}
    interface Lighting extends AutoCloseable {
        Lighting OFF = new Lighting() {};
        default double energy(long group,int triangleIndex) { return 0; }
        default void drawReceiver(Receiver receiver,Runnable original) { original.run(); }
        default int blockLight(Vec low,Vec high) { return 0; }
        default int lights() { return 0; }
        default int faces() { return 0; }
        default int rays() { return 0; }
        @Override default void close() {}
    }
    Lighting prepare(Snapshot scene,RenderSettings budget);
    default void clear() {}
    default int effective(RenderSettings settings,RenderSettings.Field field,RenderSettings.Tier tier) {
        return settings.effective(field,tier);
    }
}
