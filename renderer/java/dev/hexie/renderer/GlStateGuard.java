package dev.hexie.renderer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.lwjgl.opengl.GL45C.*;

/** Full-tier draw scope. Capture after the host's last wrapper apply.
 * Explicit unit sets scope texture, image, UBO and SSBO reads to those the draw may mutate. */
public final class GlStateGuard implements AutoCloseable {
    private record Entry(long[] values, Runnable restore) {}
    private final List<Entry> entries=new ArrayList<>();
    private final int[] textures,images,ubos,ssbos;
    private boolean closed;
    /** Audit every unit. Draws with known ownership can use the scoped constructor. */
    public GlStateGuard() {
        this(units(GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS),units(GL_MAX_IMAGE_UNITS),
                units(GL_MAX_UNIFORM_BUFFER_BINDINGS),units(GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS));
    }
    private static int[] units(int limit) { return java.util.stream.IntStream.range(0,get(limit)).toArray(); }
    public GlStateGuard(int[] textures,int[] images,int[] ubos,int[] ssbos) {
        this.textures=textures.clone(); this.images=images.clone();
        this.ubos=ubos.clone(); this.ssbos=ssbos.clone();
        capture();
    }
    private void add(Runnable restore,long... values) { entries.add(new Entry(values,restore)); }
    private static int get(int key) { return glGetInteger(key); }
    private static int at(int key,int i) { return glGetIntegeri(key,i); }
    private void toggle(int key) {
        boolean enabled=glIsEnabled(key);
        add(()->{if (enabled) glEnable(key); else glDisable(key);},enabled?1:0);
    }
    private static long[] bits(float[] values) {
        long[] result=new long[values.length];
        for (int i=0;i<values.length;i++) result[i]=Float.floatToIntBits(values[i]);
        return result;
    }
    private void capture() {
        int draw=get(GL_DRAW_FRAMEBUFFER_BINDING), read=get(GL_READ_FRAMEBUFFER_BINDING);
        add(()->{glBindFramebuffer(GL_DRAW_FRAMEBUFFER,draw); glBindFramebuffer(GL_READ_FRAMEBUFFER,read);},draw,read);
        int[] buffers=new int[get(GL_MAX_DRAW_BUFFERS)];
        for (int i=0;i<buffers.length;i++) buffers[i]=get(GL_DRAW_BUFFER0+i);
        int readBuffer=get(GL_READ_BUFFER);
        add(()->{if (draw==0 && Arrays.stream(buffers,1,buffers.length).allMatch(i->i==GL_NONE)) glDrawBuffer(buffers[0]); else glDrawBuffers(buffers); glReadBuffer(readBuffer);},
                Arrays.stream(buffers).asLongStream().toArray());
        add(()->{},readBuffer);
        for (int i=0;i<get(GL_MAX_VIEWPORTS);i++) {
            final int view=i;
            float[] vp=new float[4]; int[] box=new int[4]; double[] range=new double[2];
            glGetFloati_v(GL_VIEWPORT,i,vp); glGetIntegeri_v(GL_SCISSOR_BOX,i,box);
            glGetDoublei_v(GL_DEPTH_RANGE,i,range);
            boolean enabled=glIsEnabledi(GL_SCISSOR_TEST,i);
            add(()->glViewportIndexedfv(view,vp),bits(vp));
            add(()->glScissorIndexedv(view,box),Arrays.stream(box).asLongStream().toArray());
            add(()->{if (enabled) glEnablei(GL_SCISSOR_TEST,view); else glDisablei(GL_SCISSOR_TEST,view);},enabled?1:0);
            add(()->glDepthRangeIndexed(view,range[0],range[1]),Double.doubleToLongBits(range[0]),Double.doubleToLongBits(range[1]));
        }
        for (int flag:new int[]{GL_FRAMEBUFFER_SRGB,GL_DEPTH_TEST,GL_STENCIL_TEST,GL_CULL_FACE,
                GL_POLYGON_OFFSET_FILL,GL_POLYGON_OFFSET_LINE,GL_POLYGON_OFFSET_POINT}) toggle(flag);
        int depth=get(GL_DEPTH_FUNC), write=get(GL_DEPTH_WRITEMASK), cull=get(GL_CULL_FACE_MODE), front=get(GL_FRONT_FACE);
        add(()->{glDepthFunc(depth); glDepthMask(write!=0); glCullFace(cull); glFrontFace(front);},depth,write,cull,front);
        float factor=glGetFloat(GL_POLYGON_OFFSET_FACTOR), units=glGetFloat(GL_POLYGON_OFFSET_UNITS);
        add(()->glPolygonOffset(factor,units),Float.floatToIntBits(factor),Float.floatToIntBits(units));
        stencil(GL_FRONT,GL_STENCIL_FUNC,GL_STENCIL_REF,GL_STENCIL_VALUE_MASK,GL_STENCIL_WRITEMASK,
                GL_STENCIL_FAIL,GL_STENCIL_PASS_DEPTH_FAIL,GL_STENCIL_PASS_DEPTH_PASS);
        stencil(GL_BACK,GL_STENCIL_BACK_FUNC,GL_STENCIL_BACK_REF,GL_STENCIL_BACK_VALUE_MASK,GL_STENCIL_BACK_WRITEMASK,
                GL_STENCIL_BACK_FAIL,GL_STENCIL_BACK_PASS_DEPTH_FAIL,GL_STENCIL_BACK_PASS_DEPTH_PASS);
        float[] color=new float[4]; glGetFloatv(0x8005 /* GL_BLEND_COLOR */,color);
        add(()->glBlendColor(color[0],color[1],color[2],color[3]),bits(color));
        for (int i=0;i<buffers.length;i++) blend(i);
        int program=get(GL_CURRENT_PROGRAM), pipeline=get(GL_PROGRAM_PIPELINE_BINDING);
        int vao=get(GL_VERTEX_ARRAY_BINDING), ebo=get(GL_ELEMENT_ARRAY_BUFFER_BINDING);
        add(()->{glUseProgram(program); glBindProgramPipeline(pipeline); glBindVertexArray(vao);
            if (vao!=0) glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,ebo);},program,pipeline,vao,ebo);
        if (vao!=0) {
            for (int i=0,n=get(GL_MAX_VERTEX_ATTRIB_BINDINGS);i<n;i++) vertex(i);
            for (int i=0,n=get(GL_MAX_VERTEX_ATTRIBS);i<n;i++) attribute(i);
        }
        buffer(GL_ARRAY_BUFFER,GL_ARRAY_BUFFER_BINDING);
        buffer(GL_DRAW_INDIRECT_BUFFER,GL_DRAW_INDIRECT_BUFFER_BINDING);
        buffer(GL_DISPATCH_INDIRECT_BUFFER,GL_DISPATCH_INDIRECT_BUFFER_BINDING);
        for (int i:ubos) range(i,GL_UNIFORM_BUFFER,GL_UNIFORM_BUFFER_BINDING,GL_UNIFORM_BUFFER_START,GL_UNIFORM_BUFFER_SIZE);
        for (int i:ssbos) range(i,GL_SHADER_STORAGE_BUFFER,GL_SHADER_STORAGE_BUFFER_BINDING,GL_SHADER_STORAGE_BUFFER_START,GL_SHADER_STORAGE_BUFFER_SIZE);
        buffer(GL_UNIFORM_BUFFER,GL_UNIFORM_BUFFER_BINDING);
        buffer(GL_SHADER_STORAGE_BUFFER,GL_SHADER_STORAGE_BUFFER_BINDING);
        int active=get(GL_ACTIVE_TEXTURE);
        try { for (int unit:textures) texture(unit); } finally { glActiveTexture(active); }
        add(()->glActiveTexture(active),active);
        for (int unit:images) image(unit);
    }
    private void stencil(int face,int func,int ref,int value,int write,int fail,int zfail,int pass) {
        int f=get(func),r=get(ref),v=get(value),w=get(write),s=get(fail),z=get(zfail),p=get(pass);
        add(()->{glStencilFuncSeparate(face,f,r,v); glStencilMaskSeparate(face,w); glStencilOpSeparate(face,s,z,p);},f,r,v,w,s,z,p);
    }
    private void blend(int i) {
        boolean enabled=glIsEnabledi(GL_BLEND,i);
        int a=at(GL_BLEND_EQUATION_RGB,i),b=at(GL_BLEND_EQUATION_ALPHA,i),c=at(GL_BLEND_SRC_RGB,i),
            d=at(GL_BLEND_DST_RGB,i),e=at(GL_BLEND_SRC_ALPHA,i),f=at(GL_BLEND_DST_ALPHA,i);
        int[] mask=new int[4]; glGetIntegeri_v(GL_COLOR_WRITEMASK,i,mask);
        add(()->{if (enabled) glEnablei(GL_BLEND,i); else glDisablei(GL_BLEND,i);
            glBlendEquationSeparatei(i,a,b); glBlendFuncSeparatei(i,c,d,e,f);
            glColorMaski(i,mask[0]!=0,mask[1]!=0,mask[2]!=0,mask[3]!=0);},
            enabled?1:0,a,b,c,d,e,f,mask[0],mask[1],mask[2],mask[3]);
    }
    private void attribute(int i) {
        int enabled=glGetVertexAttribi(i,GL_VERTEX_ATTRIB_ARRAY_ENABLED);
        int binding=glGetVertexAttribi(i,GL_VERTEX_ATTRIB_BINDING), size=glGetVertexAttribi(i,GL_VERTEX_ATTRIB_ARRAY_SIZE);
        int type=glGetVertexAttribi(i,GL_VERTEX_ATTRIB_ARRAY_TYPE), offset=glGetVertexAttribi(i,GL_VERTEX_ATTRIB_RELATIVE_OFFSET);
        int integer=glGetVertexAttribi(i,GL_VERTEX_ATTRIB_ARRAY_INTEGER), large=glGetVertexAttribi(i,GL_VERTEX_ATTRIB_ARRAY_LONG);
        int normalized=glGetVertexAttribi(i,GL_VERTEX_ATTRIB_ARRAY_NORMALIZED);
        add(()->{
            if (large!=0) glVertexAttribLFormat(i,size,type,offset);
            else if (integer!=0) glVertexAttribIFormat(i,size,type,offset);
            else glVertexAttribFormat(i,size,type,normalized!=0,offset);
            glVertexAttribBinding(i,binding);
            if (enabled!=0) glEnableVertexAttribArray(i); else glDisableVertexAttribArray(i);
        },enabled,binding,size,type,offset,integer,large,normalized);
    }
    private void buffer(int target,int binding) {
        int name=get(binding); add(()->glBindBuffer(target,name),name);
    }
    private void vertex(int i) {
        int name=at(GL_VERTEX_BINDING_BUFFER,i),stride=at(GL_VERTEX_BINDING_STRIDE,i), divisor=at(GL_VERTEX_BINDING_DIVISOR,i);
        long offset=glGetInteger64i(GL_VERTEX_BINDING_OFFSET,i);
        add(()->{glBindVertexBuffer(i,name,offset,stride); glVertexBindingDivisor(i,divisor);},name,offset,stride,divisor);
    }
    private void range(int i,int target,int binding,int start,int size) {
        int name=at(binding,i); long offset=glGetInteger64i(start,i), bytes=glGetInteger64i(size,i);
        add(()->{if (name!=0 && bytes>0) glBindBufferRange(target,i,name,offset,bytes);
                else glBindBufferBase(target,i,name);},name,offset,bytes);
    }
    private static final int[][] TARGETS={{GL_TEXTURE_1D,GL_TEXTURE_BINDING_1D},{GL_TEXTURE_2D,GL_TEXTURE_BINDING_2D},
        {GL_TEXTURE_3D,GL_TEXTURE_BINDING_3D},{GL_TEXTURE_1D_ARRAY,GL_TEXTURE_BINDING_1D_ARRAY},{GL_TEXTURE_2D_ARRAY,GL_TEXTURE_BINDING_2D_ARRAY},
        {GL_TEXTURE_CUBE_MAP,GL_TEXTURE_BINDING_CUBE_MAP},{GL_TEXTURE_CUBE_MAP_ARRAY,GL_TEXTURE_BINDING_CUBE_MAP_ARRAY},
        {GL_TEXTURE_RECTANGLE,GL_TEXTURE_BINDING_RECTANGLE},{GL_TEXTURE_BUFFER,GL_TEXTURE_BINDING_BUFFER},
        {GL_TEXTURE_2D_MULTISAMPLE,GL_TEXTURE_BINDING_2D_MULTISAMPLE},{GL_TEXTURE_2D_MULTISAMPLE_ARRAY,GL_TEXTURE_BINDING_2D_MULTISAMPLE_ARRAY}};
    private void texture(int unit) {
        glActiveTexture(GL_TEXTURE0+unit);
        for (int[] pair:TARGETS) {
            int name=get(pair[1]); add(()->{glActiveTexture(GL_TEXTURE0+unit); glBindTexture(pair[0],name);},name);
        }
        int sampler=at(GL_SAMPLER_BINDING,unit);
        add(()->glBindSampler(unit,sampler),sampler);
    }
    private void image(int i) {
        int n=at(GL_IMAGE_BINDING_NAME,i), l=at(GL_IMAGE_BINDING_LEVEL,i), layered=at(GL_IMAGE_BINDING_LAYERED,i),
            layer=at(GL_IMAGE_BINDING_LAYER,i), access=at(GL_IMAGE_BINDING_ACCESS,i), format=at(GL_IMAGE_BINDING_FORMAT,i);
        add(()->glBindImageTexture(i,n,l,layered!=0,layer,access,format),n,l,layered,layer,access,format);
    }
    /** Compare real GL state with the entry snapshot, including after close(). */
    public int differences() {
        GlStateGuard now=new GlStateGuard(textures,images,ubos,ssbos);
        int n=Math.abs(entries.size()-now.entries.size());
        for (int i=0;i<Math.min(entries.size(),now.entries.size());i++)
            if (!Arrays.equals(entries.get(i).values,now.entries.get(i).values)) n++;
        return n;
    }
    void restore() { for (Entry entry:entries) entry.restore.run(); }
    @Override public void close() {
        if (!closed) {
            restore();
            closed=true;
        }
    }
}
