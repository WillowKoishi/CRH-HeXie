package dev.hexie.renderer;

import java.util.*;
import java.util.function.Consumer;
import static org.lwjgl.opengl.GL45C.*;

/** A per-view geometry pass. Handles in Prepared are valid only during the submission callback. */
public final class GeometryPass {
    private GeometryPass() {}
    public record Lod(int count,int firstIndex,int baseVertex,int baseInstance) {}
    public record ObjectDraw(Bounds localBounds,Lod fine,Lod coarse) {}
    public record Input(int terrainDepth,int width,int height,float[] clipFromLocal,List<ObjectDraw> objects,
                        float lodPixels,Vec towardSun) {
        public Input {
            clipFromLocal=clipFromLocal.clone(); objects=List.copyOf(objects);
            if (clipFromLocal.length!=16 || width<1 || height<1) throw new IllegalArgumentException("view dimensions");
        }
    }
    public record Prepared(int pyramid,int commands,int count,int shadowArray,List<float[]> sunMatrices,
                           double[] splits,Vec shadowOrigin,FrameBudget budget) {
        /** Host has applied the appropriate material/program and paired VAO + EBO. */
        public void draw(int mode,int indexType) {
            if (count==0) return;
            glBindBuffer(GL_DRAW_INDIRECT_BUFFER,commands);
            GpuTimer.measure(budget,()->glMultiDrawElementsIndirect(mode,indexType,0L,count,20));
        }
    }
    static long bytes(Input in,RendererConfig c,int triangles) {
        long sum=0;
        for (int w=in.width,h=in.height;;w=Math.max(1,w/2),h=Math.max(1,h/2)) {
            sum+=4L*w*h;
            if (w==1 && h==1) break;
        }
        return sum+84L*in.objects.size()+(c.shadows()?4L*c.cascades()*c.cascadeSize()*c.cascadeSize()+36L*triangles:0);
    }
    /** Dedicated to a single view/pass/batch. Immutable texture storage survives frames. */
    static final class Storage implements AutoCloseable {
        final int width,height,resolution,layers;
        final int pyramid,shadow,fbo,vao,vertices,objects,commands,sampler,depthSampler;
        Storage(Input in,RendererConfig c) {
            width=in.width; height=in.height; layers=c.shadows()?c.cascades():0; resolution=c.cascadeSize();
            depthSampler=glCreateSamplers(); glSamplerParameteri(depthSampler,GL_TEXTURE_MIN_FILTER,GL_NEAREST);
            glSamplerParameteri(depthSampler,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
            sampler=glCreateSamplers(); glSamplerParameteri(sampler,GL_TEXTURE_MIN_FILTER,GL_NEAREST_MIPMAP_NEAREST);
            glSamplerParameteri(sampler,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
            pyramid=glCreateTextures(GL_TEXTURE_2D);
            int levels=32-Integer.numberOfLeadingZeros(Math.max(width,height));
            glTextureStorage2D(pyramid,levels,GL_R32F,width,height);
            glTextureParameteri(pyramid,GL_TEXTURE_MIN_FILTER,GL_NEAREST_MIPMAP_NEAREST);
            glTextureParameteri(pyramid,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
            objects=glCreateBuffers(); commands=glCreateBuffers();
            if (layers>0) {
                shadow=glCreateTextures(GL_TEXTURE_2D_ARRAY); fbo=glCreateFramebuffers();
                vao=glCreateVertexArrays(); vertices=glCreateBuffers();
                glTextureStorage3D(shadow,1,GL_DEPTH_COMPONENT32F,resolution,resolution,layers);
                glTextureParameteri(shadow,GL_TEXTURE_MIN_FILTER,GL_NEAREST); glTextureParameteri(shadow,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
                glNamedFramebufferDrawBuffer(fbo,GL_NONE); glNamedFramebufferReadBuffer(fbo,GL_NONE);
                glVertexArrayVertexBuffer(vao,0,vertices,0,12); glEnableVertexArrayAttrib(vao,0);
                glVertexArrayAttribFormat(vao,0,3,GL_FLOAT,false,0); glVertexArrayAttribBinding(vao,0,0);
            } else { shadow=0; fbo=0; vao=0; vertices=0; }
        }
        void closeAfterLastRead() {
            long fence=glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE,0);
            try {
                int status;
                do { status=glClientWaitSync(fence,GL_SYNC_FLUSH_COMMANDS_BIT,1_000_000_000L); }
                while (status==GL_TIMEOUT_EXPIRED);
                if (status==GL_WAIT_FAILED) throw new IllegalStateException("geometry retirement fence");
            } finally { glDeleteSync(fence); }
            close();
        }
        public void close() {
            glDeleteSamplers(sampler); glDeleteSamplers(depthSampler);
            glDeleteTextures(pyramid); glDeleteBuffers(objects); glDeleteBuffers(commands);
            if (shadow!=0) { glDeleteTextures(shadow); glDeleteFramebuffers(fbo); glDeleteVertexArrays(vao); glDeleteBuffers(vertices); }
        }
    }
    static void execute(Storage storage,GpuPrograms gpu,RendererConfig config,FrameBudget budget,FrozenScene scene,
            FroxelClusterer.Camera camera,Input in,Consumer<Prepared> submit) {
        int pyramid=storage.pyramid,shadow=storage.shadow,fbo=storage.fbo,vao=storage.vao,vertices=storage.vertices;
        int objects=storage.objects,commands=storage.commands;
        int levels=32-Integer.numberOfLeadingZeros(Math.max(in.width,in.height));
        glBindSampler(0,storage.sampler); glUseProgram(gpu.hiz);
        glUniform1i(0,0);
        GpuTimer.measure(budget,()->{
            for (int level=0;level<levels;level++) {
                glBindSampler(0,level==0?storage.depthSampler:storage.sampler);
                glBindTextureUnit(0,level==0?in.terrainDepth:pyramid);
                glBindImageTexture(0,pyramid,level,false,0,GL_WRITE_ONLY,GL_R32F);
                glUniform1i(1,Math.max(0,level-1));
                int x=(Math.max(1,in.width>>level)+7)/8,y=(Math.max(1,in.height>>level)+7)/8;
                glDispatchCompute(x,y,1);
                glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT|GL_TEXTURE_FETCH_BARRIER_BIT);
            }
        });
        int count=in.objects.size();
        if (count>0) {
            var data=org.lwjgl.system.MemoryUtil.memAlloc(Math.multiplyExact(count,64));
            try {
                for (var draw:in.objects) {
                    for (Vec v:List.of(draw.localBounds.low(),draw.localBounds.high())) {
                        boolean low=v==draw.localBounds.low();
                        for (int j=0;j<3;j++) data.putFloat(low?Math.nextDown((float)v.at(j)):Math.nextUp((float)v.at(j)));
                        data.putFloat(0);
                    }
                    for (var lod:List.of(draw.fine,draw.coarse))
                        data.putInt(lod.count).putInt(lod.firstIndex).putInt(lod.baseVertex).putInt(lod.baseInstance);
                }
                data.flip(); glNamedBufferData(objects,data,GL_STREAM_DRAW);
            } finally { org.lwjgl.system.MemoryUtil.memFree(data); }
            glNamedBufferData(commands,20L*count,GL_STREAM_DRAW);
            glBindTextureUnit(0,pyramid);
            glUniform1i(0,1); glUniform1i(2,count); glUniform1f(3,in.lodPixels);
            glUniformMatrix4fv(4,false,in.clipFromLocal);
            gpu.run(gpu.hiz,count,new String[]{"Objects","Commands"},objects,commands);
            glMemoryBarrier(GL_COMMAND_BARRIER_BIT|GL_SHADER_STORAGE_BARRIER_BIT);
        }
        var matrices=new ArrayList<float[]>();
        Vec origin=camera.cameraToWorld().translation();
        int n=config.shadows() && scene.resident && in.towardSun!=null
            && budget.faces(config.cascades())?config.cascades():0;
        double[] splits=new double[n];
        if (n>0) {
            int size=config.cascadeSize();
            float[] xyz=new float[Math.max(3,9*scene.world.size())]; int j=0;
            for (Triangle t:scene.world) for (Vec v:List.of(t.a(),t.b(),t.c())) {
                Vec local=v.minus(origin);
                xyz[j++]=(float)local.x(); xyz[j++]=(float)local.y(); xyz[j++]=(float)local.z();
            }
            glNamedBufferData(vertices,xyz,GL_STREAM_DRAW);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER,fbo); glBindVertexArray(vao);
            glViewport(0,0,size,size); glDisable(GL_SCISSOR_TEST); glDisable(GL_CULL_FACE);
            glDisable(GL_STENCIL_TEST); glDisable(GL_POLYGON_OFFSET_FILL);
            glEnable(GL_DEPTH_TEST); glDepthMask(true); glDepthFunc(GL_LESS); glDepthRange(0,1);
            glUseProgram(gpu.csm);
            Vec sun=unit(in.towardSun);
            Vec right=unit(sun.cross(Math.abs(sun.y())<0.9?new Vec(0,1,0):new Vec(1,0,0)));
            Vec up=sun.cross(right);
            for (int layer=0;layer<n;layer++) {
                double far=Math.min(config.sunDistance(),camera.far())*(layer+1)/n;
                splits[layer]=far;
                double h=far*Math.tan(camera.verticalFov()/2),w=h*camera.width()/camera.height();
                double radius=Math.sqrt(far*far+h*h+w*w);
                double zMin=-radius,zMax=radius;
                for (var t:scene.world) for (var p:List.of(t.a(),t.b(),t.c())) {
                    double z=sun.dot(p.minus(origin)); zMax=Math.max(z,zMax); zMin=Math.min(z,zMin);
                }
                float[] m=new float[16];
                for (int axis=0;axis<3;axis++) {
                    m[axis*4]=(float)(right.at(axis)/radius);
                    m[axis*4+1]=(float)(up.at(axis)/radius);
                    m[axis*4+2]=(float)(-2*sun.at(axis)/(zMax-zMin));
                }
                m[14]=(float)((zMax+zMin)/(zMax-zMin)); m[15]=1; matrices.add(m);
            }
            GpuTimer.measure(budget,()->{
                for (int layer=0;layer<n;layer++) {
                    float[] m=matrices.get(layer);
                    glNamedFramebufferTextureLayer(fbo,GL_DEPTH_ATTACHMENT,shadow,0,layer);
                    if (glCheckNamedFramebufferStatus(fbo,GL_DRAW_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)
                        throw new IllegalStateException("incomplete CSM layer");
                    int target=fbo;
                    glUniformMatrix4fv(0,false,m);
                    glClearNamedFramebufferfv(target,GL_DEPTH,0,new float[]{1});
                    glDrawArrays(GL_TRIANGLES,0,scene.world.size()*3);
                }
            });
        }
        GpuPrograms.check();
        submit.accept(new Prepared(pyramid,commands,count,n==0?0:shadow,List.copyOf(matrices),splits,origin,budget));
    }
    private static Vec unit(Vec v) {
        double n=Math.sqrt(v.dot(v)); if (!(n>0)) throw new IllegalArgumentException("direction");
        return v.times(1/n);
    }
}
