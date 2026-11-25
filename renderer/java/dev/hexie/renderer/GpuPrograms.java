package dev.hexie.renderer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import static org.lwjgl.opengl.GL45C.*;

/** Per-context programs and reusable scratch buffers owned by one renderer. */
final class GpuPrograms implements AutoCloseable {
    final int cone,proof,froxel,eval,hiz,csm;
    private final List<Integer> programs=new ArrayList<>();
    private final int[] buffers=new int[8];
    private final long[] sizes=new long[buffers.length];
    private final long capacity;
    long surfaceBytes;
    static final class Unavailable extends RuntimeException {
        Unavailable(String message) { super(message); }
    }
    static void check() {
        int error=glGetError();
        if (error!=GL_NO_ERROR) throw new Unavailable("GPU result unavailable, GL error "+error);
    }
    private long bytes,peak;
    private int computes,allocations;
    FrameBudget ledger;
    GpuPrograms(long capacity) {
        this.capacity=capacity;
        try {
            cone=link("cone.comp"); proof=link("light_eval.comp#proof"); froxel=link("froxel.comp"); eval=link("light_eval.comp");
            hiz=link("hiz_cull.comp"); csm=link("csm.vert","csm.frag");
        } catch (RuntimeException | Error failure) { close(); throw failure; }
    }
    private int link(String... files) {
        int program=glCreateProgram();
        programs.add(program);
        for (String file:files) {
            boolean proof=file.endsWith("#proof");
            if (proof) file=file.substring(0,file.indexOf('#'));
            int type=file.endsWith(".comp")?GL_COMPUTE_SHADER:file.endsWith(".vert")?GL_VERTEX_SHADER:GL_FRAGMENT_SHADER;
            if (type==GL_COMPUTE_SHADER) computes++;
            int shader=glCreateShader(type);
            try {
                String source=GpuConeCube.shader(file);
                if (proof) source=source.replace("#version 450 core","#version 450 core\n#define HEXIE_CONE_PASS 1");
                glShaderSource(shader,source); glCompileShader(shader);
                if (glGetShaderi(shader,GL_COMPILE_STATUS)==GL_FALSE)
                    throw new IllegalStateException(file+" "+glGetShaderInfoLog(shader));
                glAttachShader(program,shader);
            } catch (IOException ex) { throw new UncheckedIOException(ex); }
            finally { glDeleteShader(shader); }
        }
        glLinkProgram(program);
        if (glGetProgrami(program,GL_LINK_STATUS)==GL_FALSE) throw new IllegalStateException(glGetProgramInfoLog(program));
        return program;
    }
    int computes() { return computes; }
    int allocations() { return allocations; }
    long peak() { return peak; }
    long availableSurfaceBytes() { return capacity-bytes; }
    int buffer(int index,long required) {
        long size=Math.max(16L,required);
        if (size>sizes[index]) {
            if (size>capacity-surfaceBytes-(bytes-sizes[index]) || size>glGetInteger64(GL_MAX_SHADER_STORAGE_BLOCK_SIZE))
                throw new Unavailable("GPU buffer capacity");
            if (buffers[index]==0) { buffers[index]=glCreateBuffers(); allocations++; }
            glNamedBufferData(buffers[index],size,GL_DYNAMIC_DRAW);
            bytes+=size-sizes[index]; sizes[index]=size; peak=Math.max(peak,bytes);
        }
        return buffers[index];
    }
    int ints(int slot,int[] data) {
        int id=buffer(slot,4L*data.length);
        if (data.length!=0) glNamedBufferSubData(id,0,data);
        return id;
    }
    int doubles(int slot,double[] data) {
        int id=buffer(slot,8L*data.length);
        if (data.length!=0) glNamedBufferSubData(id,0,data);
        return id;
    }
    private int maxGroups() { return glGetIntegeri(GL_MAX_COMPUTE_WORK_GROUP_COUNT,0); }
    void run(int program,int lanes,String[] blocks,int... ids) {
        if (lanes==0) return;
        int groups=program==froxel?lanes:(int)((lanes+63L)/64);
        int max=maxGroups();
        try (var binding=new SsboBindings(program,blocks)) {
            for (int i=0;i<ids.length;i++) binding.bind(i,ids[i]);
            glUseProgram(program);
            GpuTimer.measure(ledger,()->{
                for (int first=0;first<groups;) {
                    int count=Math.min(max,groups-first);
                    glUniform1i(12,first);
                    glDispatchCompute(count,1,1);
                    first+=count;
                }
            });
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT|GL_BUFFER_UPDATE_BARRIER_BIT);
        }
        check();
    }
    public void close() {
        for (int buffer:buffers) if (buffer!=0) glDeleteBuffers(buffer);
        for (int p:programs) glDeleteProgram(p);
        programs.clear(); Arrays.fill(buffers,0);
    }
}
