package dev.hexie.renderer.neoforge;

import dev.hexie.renderer.*;
import java.io.*;
import java.util.List;
import static org.lwjgl.opengl.GL45C.*;

final class PrototypeCube implements AutoCloseable {
    private int program;
    int[] query(ConeCube.Input input,List<ConeCube.Box> samples) {
        if (program==0) program=compile();
        try (var cube=new GpuConeCube(program,input,6)) { return cube.query(samples); }
    }
    private static int compile() {
        int shader=glCreateShader(GL_COMPUTE_SHADER);
        try {
            glShaderSource(shader,GpuConeCube.shader("cone.comp"));
            glCompileShader(shader);
            if (glGetShaderi(shader,GL_COMPILE_STATUS)==0) throw new IllegalStateException(glGetShaderInfoLog(shader));
            int p=glCreateProgram();
            glAttachShader(p,shader);
            glLinkProgram(p);
            if (glGetProgrami(p,GL_LINK_STATUS)!=0) return p;
            String error=glGetProgramInfoLog(p);
            glDeleteProgram(p);
            throw new IllegalStateException(error);
        } catch (IOException e) { throw new UncheckedIOException(e); }
        finally { glDeleteShader(shader); }
    }
    public void close() { if (program!=0) glDeleteProgram(program); program=0; }
}
