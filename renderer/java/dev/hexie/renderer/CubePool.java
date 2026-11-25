package dev.hexie.renderer;

import java.util.*;
import java.util.function.Supplier;

/** R32UI hot maps. A fence keeps evicted storage charged until its final read completes. */
final class CubePool<K> implements AutoCloseable {
    private record MapEntry(GpuConeCube cube,long bytes) {}
    private record Retired(MapEntry entry,long fence) {}
    private final LinkedHashMap<K,MapEntry> entries=new LinkedHashMap<>(16,0.75f,true);
    private final List<Retired> retired=new ArrayList<>();
    private final long capacity;
    private final int count;
    private long bytes,peak;
    CubePool(long capacity,int count) { this.capacity=capacity; this.count=count; }
    long bytes() { return bytes; }
    long peak() { return peak; }
    GpuConeCube acquire(K key,int size,FrameBudget budget,Supplier<GpuConeCube> build) {
        var hit=entries.get(key);
        if (hit!=null) return hit.cube;
        long cost=24L*size*size;
        if (size<=0 || size>4096 || count==0 || cost>capacity || budget.config().limits().faces()-budget.usage().faces()<6 || !budget.hasTime()) return null;
        collect();
        while (!entries.isEmpty() && (entries.size()>=count || bytes+cost>capacity)) {
            var it=entries.values().iterator(); retire(it.next()); it.remove(); collect();
        }
        if (bytes+cost>capacity || !budget.faces(6)) return null;
        var cube=build.get();
        entries.put(key,new MapEntry(cube,cost)); bytes+=cost; peak=Math.max(peak,bytes);
        return cube;
    }
    private void retire(MapEntry e) {
        retired.add(new Retired(e,org.lwjgl.opengl.GL32C.glFenceSync(org.lwjgl.opengl.GL32C.GL_SYNC_GPU_COMMANDS_COMPLETE,0)));
    }
    private void collect() {
        var it=retired.iterator();
        while (it.hasNext()) {
            var r=it.next();
            int status=org.lwjgl.opengl.GL32C.glClientWaitSync(r.fence,0,0);
            if (status==org.lwjgl.opengl.GL32C.GL_ALREADY_SIGNALED || status==org.lwjgl.opengl.GL32C.GL_CONDITION_SATISFIED) {
                org.lwjgl.opengl.GL32C.glDeleteSync(r.fence); r.entry.cube.close(); bytes-=r.entry.bytes; it.remove();
            }
        }
    }
    public void close() {
        for (var e:entries.values()) e.cube.close();
        for (var r:retired) { org.lwjgl.opengl.GL32C.glDeleteSync(r.fence); r.entry.cube.close(); }
        entries.clear(); retired.clear(); bytes=0;
    }
}
