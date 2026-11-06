package willow.train.hexie.client.render.track;

import org.lwjgl.system.MemoryStack;

import static org.lwjgl.opengl.GL32C.*;

/** Copies only the selected atlas face, including rotated UVs and the current animation frame. */
final class CurveBedTextureCopy {
    private static final String VERTEX = """
            #version 150
            out vec2 tile;
            void main() {
                tile = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                gl_Position = vec4(tile * 2.0 - 1.0, 0.0, 1.0);
            }
            """;
    private static final String FRAGMENT = """
            #version 150
            uniform sampler2D sourceAtlas;
            uniform vec4 row0, row1;
            in vec2 tile;
            out vec4 pixel;
            void main() {
                vec2 uv = mix(mix(row0.xy, row0.zw, tile.x), mix(row1.xy, row1.zw, tile.x), tile.y);
                ivec2 size = textureSize(sourceAtlas, 0);
                pixel = texelFetch(sourceAtlas, clamp(ivec2(uv * size), ivec2(0), size - 1), 0);
            }
            """;

    private static int program, vao, framebuffer, sampler, row0, row1;

    private CurveBedTextureCopy() {}

    static void copy(int source, CurveBedTexture tile, boolean allocate) {
        if (program == 0) initialize();
        int target = tile.getId();
        int oldTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        int oldFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        int oldProgram = glGetInteger(GL_CURRENT_PROGRAM);
        int oldVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        try (var stack = MemoryStack.stackPush()) {
            var viewport = stack.mallocInt(4);
            var colorMask = stack.malloc(4);
            var polygon = stack.mallocInt(2);
            glGetIntegerv(GL_VIEWPORT, viewport);
            glGetBooleani_v(GL_COLOR_WRITEMASK, 0, colorMask);
            glGetIntegerv(GL_POLYGON_MODE, polygon);
            int[] flags = {GL_CULL_FACE, GL_SCISSOR_TEST, GL_COLOR_LOGIC_OP, GL_RASTERIZER_DISCARD, GL_DITHER};
            boolean[] enabled = new boolean[flags.length];
            for (int i = 0; i < flags.length; i++) {
                enabled[i] = glIsEnabled(flags[i]);
                if (enabled[i]) glDisable(flags[i]);
            }
            boolean blend = glIsEnabledi(GL_BLEND, 0);
            try {
                glDisablei(GL_BLEND, 0);
                glColorMaski(0, true, true, true, true);
                glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
                if (allocate) {
                    glBindTexture(GL_TEXTURE_2D, target);
                    int unpack = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
                    glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
                    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, tile.width, tile.height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0L);
                    glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpack);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, tile.maxMip);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_LINEAR);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
                    int wrap = tile.repeating ? GL_REPEAT : GL_CLAMP_TO_EDGE;
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wrap);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, wrap);
                }
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
                glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, target, 0);
                glViewport(0, 0, tile.width, tile.height);
                glUseProgram(program);
                glBindVertexArray(vao);
                glBindTexture(GL_TEXTURE_2D, source);
                glUniform1i(sampler, glGetInteger(GL_ACTIVE_TEXTURE) - GL_TEXTURE0);
                float[] uv = tile.sourceUv;
                glUniform4f(row0, uv[0], uv[1], uv[2], uv[3]);
                glUniform4f(row1, uv[4], uv[5], uv[6], uv[7]);
                glDrawArrays(GL_TRIANGLES, 0, 3);
                glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, 0, 0);
                if (tile.maxMip > 0) {
                    glBindTexture(GL_TEXTURE_2D, target);
                    glGenerateMipmap(GL_TEXTURE_2D);
                }
            } finally {
                glBindTexture(GL_TEXTURE_2D, oldTexture);
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER, oldFramebuffer);
                glBindVertexArray(oldVao);
                glUseProgram(oldProgram);
                glViewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
                glColorMaski(0, colorMask.get(0) != 0, colorMask.get(1) != 0, colorMask.get(2) != 0, colorMask.get(3) != 0);
                glPolygonMode(GL_FRONT_AND_BACK, polygon.get(0));
                if (blend) glEnablei(GL_BLEND, 0);
                for (int i = 0; i < flags.length; i++) if (enabled[i]) glEnable(flags[i]);
            }
        }
    }

    private static void initialize() {
        int linked = glCreateProgram();
        try {
            attach(linked, GL_VERTEX_SHADER, VERTEX);
            attach(linked, GL_FRAGMENT_SHADER, FRAGMENT);
            glLinkProgram(linked);
            if (glGetProgrami(linked, GL_LINK_STATUS) == GL_FALSE) throw new IllegalStateException(glGetProgramInfoLog(linked));
        } catch (RuntimeException ex) {
            glDeleteProgram(linked);
            throw ex;
        }
        program = linked;
        vao = glGenVertexArrays();
        framebuffer = glGenFramebuffers();
        sampler = glGetUniformLocation(program, "sourceAtlas");
        row0 = glGetUniformLocation(program, "row0");
        row1 = glGetUniformLocation(program, "row1");
    }

    private static void attach(int program, int type, String source) {
        int shader = glCreateShader(type);
        try {
            glShaderSource(shader, source);
            glCompileShader(shader);
            if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) throw new IllegalStateException(glGetShaderInfoLog(shader));
            glAttachShader(program, shader);
        } finally { glDeleteShader(shader); }
    }

    static void clear() {
        if (program == 0) return;
        glDeleteProgram(program);
        glDeleteVertexArrays(vao);
        glDeleteFramebuffers(framebuffer);
        program = 0;
    }
}
