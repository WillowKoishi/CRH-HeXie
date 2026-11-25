package dev.hexie.renderer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.lwjgl.opengl.GL45C.*;

/** Six consecutive R32UI layers per map. Only publish after all builds and barriers complete. */
public final class GpuConeCube implements AutoCloseable {
    private final int program;
    private final ConeCube.Grid grid;
    private final int maps;
    private final FrameBudget ledger;
    private int texture;
    public static String shader(String filename) throws IOException {
        try (var in=GpuConeCube.class.getResourceAsStream(filename)) {
            if (in==null) throw new FileNotFoundException(filename);
            String source=new String(in.readAllBytes(),StandardCharsets.UTF_8);
            if (source.contains("// @hexie cone queries"))
                source=source.replace("// @hexie cone queries",shader("cone_query.glsl"));
            return source;
        }
    }
    public GpuConeCube(int program,ConeCube.Input input,int completedFaces) {
        this(program,List.of(input),new int[]{completedFaces});
    }
    public GpuConeCube(int program,List<ConeCube.Input> inputs,int[] completedFaces) {
        this(program,inputs,completedFaces,null);
    }
    GpuConeCube(int program,List<ConeCube.Input> inputs,int[] completedFaces,FrameBudget ledger) {
        this.ledger=ledger;
        this.program=program;
        maps=inputs.size();
        if (maps==0 || completedFaces.length!=maps || maps>glGetInteger(GL_MAX_ARRAY_TEXTURE_LAYERS)/6)
            throw new IllegalArgumentException("cube array length");
        grid=inputs.get(0).grid();
        for (var input:inputs) if (!grid.equals(input.grid())) throw new IllegalArgumentException("mixed cube layout");
        texture=glCreateTextures(GL_TEXTURE_2D_ARRAY);
        int boxes=glCreateBuffers();
        int previous=glGetInteger(GL_CURRENT_PROGRAM);
        int image=glGetIntegeri(GL_IMAGE_BINDING_NAME,0),level=glGetIntegeri(GL_IMAGE_BINDING_LEVEL,0);
        int layer=glGetIntegeri(GL_IMAGE_BINDING_LAYER,0),format=glGetIntegeri(GL_IMAGE_BINDING_FORMAT,0);
        int access=glGetIntegeri(GL_IMAGE_BINDING_ACCESS,0),layered=glGetIntegeri(GL_IMAGE_BINDING_LAYERED,0);
        try (var storage=new SsboBindings(program,"Boxes")) {
            glTextureStorage3D(texture,1,GL_R32UI,grid.size(),grid.size(),6*maps);
            glTextureParameteri(texture,GL_TEXTURE_MIN_FILTER,GL_NEAREST);
            glTextureParameteri(texture,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
            glBindImageTexture(0,texture,0,true,0,GL_READ_WRITE,GL_R32UI);
            storage.bind(0,boxes);
            glUseProgram(program);
            for (int m=0;m<maps;m++) {
                var input=inputs.get(m);
                boolean complete=input.complete() && completedFaces[m]>=6;
                glUniform1i(6,6*m);
                configure(input.boxes().size(),2,complete);
                GpuTimer.measure(ledger,()->glDispatchCompute((grid.size()+7)/8,(grid.size()+7)/8,6));
                if (complete && !input.boxes().isEmpty()) {
                    glNamedBufferData(boxes,pack(input.boxes()),GL_STREAM_DRAW);
                    glUniform1i(3,0);
                    int max=glGetIntegeri(GL_MAX_COMPUTE_WORK_GROUP_COUNT,0);
                    for (int base=0;base<input.boxes().size();) {
                        int groups=Math.min(max,input.boxes().size()-base);
                        glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
                        glUniform1i(5,base);
                        GpuTimer.measure(ledger,()->glDispatchCompute(groups,1,6));
                        base+=groups;
                    }
                }
            }
            glMemoryBarrier(GL_TEXTURE_FETCH_BARRIER_BIT|GL_SHADER_IMAGE_ACCESS_BARRIER_BIT|GL_TEXTURE_UPDATE_BARRIER_BIT);
            checkDispatch();
        } catch (RuntimeException | Error e) {
            close(); throw e;
        } finally {
            glBindImageTexture(0,image,level,layered!=0,layer,access,format);
            glUseProgram(previous); glDeleteBuffers(boxes);
        }
    }
    public int texture() { return texture; }
    public int[] query(List<ConeCube.Box> receivers) { return query(receivers,null,null); }
    /** Both selected maps must prove clear. Indices may vary per lane (e.g. own and foreign). */
    public int[] query(List<ConeCube.Box> receivers,int[] own,int[] foreign) {
        return query(receivers,own,foreign,program,false);
    }
    int[] query(List<ConeCube.Box> receivers,int[] own,int[] foreign,int evaluator,boolean proofOnly) {
        int[] result=new int[receivers.size()];
        if (result.length==0) return result;
        if (texture==0) throw new IllegalStateException("released cube");
        int[] data=pack(receivers);
        if (own!=null && own.length!=result.length || foreign!=null && foreign.length!=result.length)
            throw new IllegalArgumentException("receiver map count");
        for (int i=0;i<result.length;i++) {
            int a=own==null?0:own[i], b=foreign==null?a:foreign[i];
            if (a<0 || b<0 || a>=maps || b>=maps) data[8*i]=data[8*i+4]+1;
            data[8*i+3]=a; data[8*i+7]=b;
        }
        int boxes=glCreateBuffers(), output=glCreateBuffers(), previous=glGetInteger(GL_CURRENT_PROGRAM);
        int active=glGetInteger(GL_ACTIVE_TEXTURE); glActiveTexture(GL_TEXTURE0);
        int old=glGetInteger(GL_TEXTURE_BINDING_2D_ARRAY), sampler=glGetIntegeri(GL_SAMPLER_BINDING,0);
        try (var storage=new SsboBindings(evaluator,"Boxes","Results")) {
            glBindTexture(GL_TEXTURE_2D_ARRAY,texture); glBindSampler(0,0);
            glNamedBufferData(boxes,data,GL_STREAM_DRAW);
            glNamedBufferData(output,4L*result.length,GL_STREAM_READ);
            storage.bind(0,boxes); storage.bind(1,output);
            glUseProgram(evaluator);
            if (proofOnly) { glUniform1i(0,grid.size()); glUniform1i(1,result.length); glUniform1i(4,grid.extent()); }
            else configure(result.length,1,true);
            int max=glGetIntegeri(GL_MAX_COMPUTE_WORK_GROUP_COUNT,0);
            for (long first=0;first<result.length;) {
                int groups=(int)Math.min(max,(result.length-first+63)/64);
                glUniform1i(5,(int)first); GpuTimer.measure(ledger,()->glDispatchCompute(groups,1,1));
                first+=64L*groups;
            }
            glMemoryBarrier(GL_BUFFER_UPDATE_BARRIER_BIT|GL_SHADER_STORAGE_BARRIER_BIT);
            glGetNamedBufferSubData(output,0,result);
            checkDispatch();
            return result;
        } finally {
            glBindTexture(GL_TEXTURE_2D_ARRAY,old); glBindSampler(0,sampler); glActiveTexture(active);
            glUseProgram(previous); glDeleteBuffers(output); glDeleteBuffers(boxes);
        }
    }
    private static void checkDispatch() {
        int error=glGetError();
        if (error!=GL_NO_ERROR) throw new IllegalStateException("unpublished compute result, GL error "+error);
    }
    private void configure(int count,int operation,boolean complete) {
        glUniform1i(0,grid.size()); glUniform1i(1,count);
        glUniform1i(2,complete?1:0); glUniform1i(3,operation); glUniform1i(4,grid.extent());
    }
    private static int[] pack(List<ConeCube.Box> boxes) {
        int[] data=new int[Math.max(8,Math.multiplyExact(8,boxes.size()))];
        for (int i=0;i<boxes.size();i++) {
            var b=boxes.get(i);
            if (b==null) { data[8*i]=1; continue; }
            for (int j=0;j<3;j++) { data[8*i+j]=b.low(j); data[8*i+4+j]=b.high(j); }
        }
        return data;
    }
    @Override public void close() { if (texture!=0) { glDeleteTextures(texture); texture=0; } }
}
