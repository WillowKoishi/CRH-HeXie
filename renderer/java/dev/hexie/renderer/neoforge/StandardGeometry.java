package dev.hexie.renderer.neoforge;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.hexie.renderer.*;
import dev.hexie.renderer.mc.*;
import dev.hexie.renderer.mc.ViewPassOwnership.*;
import dev.hexie.renderer.parse.CompiledMesh;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntSupplier;
import net.minecraft.client.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Matrix4f;
import static dev.hexie.renderer.mc.ClientRenderState.engine;
import static org.lwjgl.opengl.GL45C.*;

final class StandardGeometry {
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final ResourceLocation GRATE = ResourceLocation.withDefaultNamespace("textures/block/iron_bars.png");
    private static final float[] IDENTITY_3X3 = {1, 0, 0, 0, 1, 0, 0, 0, 1};
    static final int FLOATS_PER_VERTEX = 14;

    static final String VERTEX_SHADER = """
        #version 450 core
        layout(location = 0) in vec3 a_Pos;
        layout(location = 1) in vec3 a_Normal;
        layout(location = 2) in vec2 a_Uv;
        layout(location = 3) in vec4 a_Color;
        layout(location = 4) in vec2 a_Light;
        uniform mat4 u_ModelView;
        uniform mat4 u_Projection;
        uniform vec3 u_DrawOffset;
        out vec3 v_Pos;
        out vec3 v_Normal;
        out vec2 v_Uv;
        out vec4 v_Color;
        out vec2 v_Light;
        void main() {
            vec3 relPos = a_Pos + u_DrawOffset;
            v_Pos = relPos;
            v_Normal = a_Normal;
            v_Uv = a_Uv;
            v_Color = a_Color;
            v_Light = a_Light;
            gl_Position = u_Projection * (u_ModelView * vec4(relPos, 1.0));
        }
        """;

    static final String FRAGMENT_SHADER = """
        #version 450 core
        #ifdef GL_ARB_shader_storage_buffer_object
        #extension GL_ARB_shader_storage_buffer_object : enable
        #endif
        in vec3 v_Pos;
        in vec3 v_Normal;
        in vec2 v_Uv;
        in vec4 v_Color;
        in vec2 v_Light;
        uniform sampler2D u_Tex;
        layout(binding = 1) uniform sampler3D u_Volume;
        layout(binding = 2) uniform sampler2D u_Lightmap;
        uniform vec3 u_VolumeOrigin, u_VolumeSize;
        uniform int u_Cutout;
        uniform float u_FogStart;
        uniform float u_FogEnd;
        uniform vec4 u_FogColor;
        layout(location = 0) out vec4 fragColor;
        """ + ForwardLight.FRAGMENT + """
        void main() {
            vec4 tex = texture(u_Tex, v_Uv);
            if (u_Cutout != 0 && tex.a < 0.5) discard;
            vec3 albedo = tex.rgb * v_Color.rgb;
            vec3 nrm = normalize(gl_FrontFacing ? v_Normal : -v_Normal);
            vec2 light = v_Light;
            if (u_VolumeSize.x > 0.0) {
                vec3 cell = v_Pos - u_VolumeOrigin;
                vec3 edge = min(cell, u_VolumeSize - cell);
                float weight = smoothstep(0.0, 1.0, min(edge.x, min(edge.y, edge.z)));
                light = mix(light, texture(u_Volume, cell / u_VolumeSize).rg, weight);
            }
            float hexieLocal = smoothstep(32.0, 20.0, length(v_Pos));
            float blockScale = hexieEnabled != 0 ? mix(1.0, 0.12, hexieLocal) : 1.0;
            vec2 uv = (light * vec2(blockScale, 1.0) * 15.0 + 0.5) / 16.0;
            vec3 color = albedo * texture(u_Lightmap, uv).rgb * (0.72 + 0.28 * max(-0.5, nrm.y));
        #if defined(GL_ARB_shader_storage_buffer_object) || (__VERSION__ >= 430)
            color += hexieDirect(v_Pos, albedo, nrm);
            color += hexieDaylight(v_Pos, albedo, nrm, light.y);
        #endif
            if (u_FogEnd > u_FogStart) {
                float dist = length(v_Pos);
                float f = clamp((dist - u_FogStart) / (u_FogEnd - u_FogStart), 0.0, 1.0) * u_FogColor.a;
                color = mix(color, u_FogColor.rgb, f);
            }
            fragColor = vec4(color, tex.a * v_Color.a);
        }
        """;

    private static int program, vao;
    private static final int[] vbos = new int[3], vboCaps = new int[3];
    private static int vboRing, vboCursorFloats;
    private static long vboFrame = Long.MIN_VALUE;
    private static int locTex, locCutout, locModelView, locProjection, locDrawOffset, locFogStart, locFogEnd, locFogColor;
    private static int locVolumeOrigin, locVolumeSize;
    private static final float[] MAT_MV = new float[16];
    private static final float[] MAT_PROJ = new float[16];
    private static float[] vertexScratch = new float[0];
    private static final ImageSource.ImageCapabilities CAPS = new ImageSource.ImageCapabilities(Set.of(ImageSource.Format.SRGB8_ALPHA8), 0L);

    private record BatchKey(long group, int start, String material) {}

    private static final class StaticBatch {
        int vbo;
        int capacityFloats;
        int vertexCount;
        RendererBackend.Section section;
        Pose pose;
        long volumeRevision = Long.MIN_VALUE;
        Vec anchor = Vec.ZERO;
    }

    private static final Map<BatchKey, StaticBatch> STATIC_BATCHES = new HashMap<>();

    private static final class CachedImage {
        long revision = Long.MIN_VALUE;
        ResourceLocation location;
        DynamicTexture texture;
    }

    private static final Map<String, CachedImage> IMAGE_CACHE = new HashMap<>();

    static void dispose() {
        ForwardLight.invalidatePrograms();
        if (program != 0) glDeleteProgram(program);
        if (vao != 0) glDeleteVertexArrays(vao);
        for (int vbo : vbos) if (vbo != 0) glDeleteBuffers(vbo);
        Arrays.fill(vbos, 0);
        Arrays.fill(vboCaps, 0);
        for (var b : STATIC_BATCHES.values()) {
            if (b.vbo != 0) glDeleteBuffers(b.vbo);
        }
        STATIC_BATCHES.clear();
        program = 0;
        vao = 0;
        vboRing = vboCursorFloats = 0;
        vboFrame = Long.MIN_VALUE;
        vertexScratch = new float[0];
        var tm = Minecraft.getInstance() != null ? Minecraft.getInstance().getTextureManager() : null;
        for (var c : IMAGE_CACHE.values()) {
            if (tm != null && c.location != null) tm.release(c.location);
            else if (c.texture != null) c.texture.close();
        }
        IMAGE_CACHE.clear();
    }

    private static ResourceLocation resolveTexture(String material, Pass layer) {
        if (material.startsWith("decal:")) {
            String id = material.substring("decal:".length());
            var srcOpt = engine().imageSource(id);
            if (srcOpt.isPresent()) {
                var entry = IMAGE_CACHE.computeIfAbsent(id, k -> new CachedImage());
                var res = srcOpt.get().tryAcquire(entry.revision, CAPS);
                if (res instanceof ImageSource.PixelLease lease) {
                    try {
                        NativeImage img = new NativeImage(NativeImage.Format.RGBA, lease.width(), lease.height(), false);
                        var buf = lease.pixels();
                        for (int y = 0; y < lease.height(); y++) {
                            int srcRow = lease.rows() == ImageSource.RowOrder.TOP_DOWN ? y : (lease.height() - 1 - y);
                            int rowOff = srcRow * lease.stride();
                            for (int x = 0; x < lease.width(); x++) {
                                int p = rowOff + x * 4;
                                int r = buf.get(p) & 0xFF;
                                int g = buf.get(p + 1) & 0xFF;
                                int b = buf.get(p + 2) & 0xFF;
                                int a = buf.get(p + 3) & 0xFF;
                                img.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
                            }
                        }
                        var tm = Minecraft.getInstance().getTextureManager();
                        if (entry.location != null) tm.release(entry.location);
                        entry.texture = new DynamicTexture(img);
                        String safe = id.replaceAll("[^a-z0-9_.-]", "_");
                        entry.location = ResourceLocation.fromNamespaceAndPath("hexie", "dynamic/decal_" + safe);
                        tm.register(entry.location, entry.texture);
                        entry.revision = lease.revision();
                    } finally {
                        lease.release().run();
                    }
                } else if (res instanceof ImageSource.Removed) {
                    if (entry.location != null) Minecraft.getInstance().getTextureManager().release(entry.location);
                    entry.location = null;
                    entry.texture = null;
                    entry.revision = Long.MIN_VALUE;
                }
                if (entry.location != null) return entry.location;
            }
        }
        if (material.equals("atlas:blocks") || material.startsWith("atlas:blocks:")) {
            return InventoryMenu.BLOCK_ATLAS;
        }
        if (material.startsWith("tex:")) {
            ResourceLocation parsed = ResourceLocation.tryParse(material.substring("tex:".length()));
            if (parsed != null) return parsed;
        }
        return layer == Pass.CUTOUT || layer == Pass.SHADOW_CUTOUT ? GRATE : WHITE;
    }

    static void expect(Object view, boolean shadow, boolean transparent) {
        var scene = engine().snapshot();
        if (scene == null) return;
        scene.sections().forEach((group, section) -> {
            if (!scene.world().containsKey(group)) return;
            for (var mesh : section.submeshes()) {
                if (mesh.start() == mesh.end() || !transparent && isTranslucent(mesh)) continue;
                var key = new Key(view, layer(mesh, shadow), group, mesh.material() + "/" + mesh.start());
                engine().ownership().assignDefault(key, Owner.HEXIE);
            }
        });
        if (hasDemoLamps(scene))
            engine().ownership().assignDefault(new Key(view, shadow ? Pass.SHADOW_SOLID : Pass.SOLID, 0, "lamp markers"), Owner.HEXIE);
    }

    private static boolean hasDemoLamps(RendererBackend.Snapshot scene) {
        if (ClientRenderState.demo == null || !ClientRenderState.demo.isActive()) return false;
        for (var l : scene.frame().lights()) {
            if (l.ownerGroup() == 0L || l.ownerGroup() == 1L) return true;
        }
        return false;
    }

    private static boolean isTranslucent(RendererBackend.Submesh mesh) {
        return mesh.translucent() || mesh.material().equals("translucent") || mesh.material().endsWith(":translucent");
    }

    private static Pass layer(RendererBackend.Submesh mesh, boolean shadow) {
        if (isTranslucent(mesh)) return shadow ? Pass.SHADOW_TRANSLUCENT : Pass.TRANSLUCENT;
        if (mesh.material().equals("cutout") || mesh.material().endsWith(":cutout"))
            return shadow ? Pass.SHADOW_CUTOUT : Pass.CUTOUT;
        return shadow ? Pass.SHADOW_SOLID : Pass.SOLID;
    }

    static void draw(Object view, Pass pass, Camera camera, Matrix4f modelView, Matrix4f projection, WorldLightVolume volume) {
        var scene = engine().snapshot();
        if (scene == null || scene.sections().isEmpty()) return;
        long start = System.nanoTime();
        try {
            boolean shadow = pass.name().startsWith("SHADOW_");
            if (!shadow && engine().route().tier() == RenderSettings.Tier.FULL && ensureForwardProgram()) {
                drawPerPixel(view, pass, scene, camera, modelView, projection, volume);
            } else {
                drawVanilla(view, pass, scene, camera, modelView);
            }
        } finally {
            ClientRenderState.timings.geomDrawNs += System.nanoTime() - start;
        }
    }

    static boolean ensureForwardProgram() {
        if (program != 0) return true;
        int vs = compileShader(GL_VERTEX_SHADER, VERTEX_SHADER);
        if (vs == 0) return false;
        int fs = compileShader(GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        if (fs == 0) {
            glDeleteShader(vs);
            return false;
        }
        int p = glCreateProgram();
        glAttachShader(p, vs);
        glAttachShader(p, fs);
        glLinkProgram(p);
        glDeleteShader(vs);
        glDeleteShader(fs);
        if (glGetProgrami(p, GL_LINK_STATUS) != GL_TRUE) {
            glDeleteProgram(p);
            return false;
        }
        int array = glCreateVertexArrays();
        setupAttrib(array, 0, 3, 0);
        setupAttrib(array, 1, 3, 3 * Float.BYTES);
        setupAttrib(array, 2, 2, 6 * Float.BYTES);
        setupAttrib(array, 3, 4, 8 * Float.BYTES);
        setupAttrib(array, 4, 2, 12 * Float.BYTES);
        program = p;
        vao = array;
        locTex = glGetUniformLocation(p, "u_Tex");
        locVolumeOrigin = glGetUniformLocation(p, "u_VolumeOrigin");
        locVolumeSize = glGetUniformLocation(p, "u_VolumeSize");
        locCutout = glGetUniformLocation(p, "u_Cutout");
        locModelView = glGetUniformLocation(p, "u_ModelView");
        locProjection = glGetUniformLocation(p, "u_Projection");
        locDrawOffset = glGetUniformLocation(p, "u_DrawOffset");
        locFogStart = glGetUniformLocation(p, "u_FogStart");
        locFogEnd = glGetUniformLocation(p, "u_FogEnd");
        locFogColor = glGetUniformLocation(p, "u_FogColor");
        return true;
    }

    private static void setupAttrib(int array, int loc, int size, int offset) {
        glEnableVertexArrayAttrib(array, loc);
        glVertexArrayAttribFormat(array, loc, size, GL_FLOAT, false, offset);
        glVertexArrayAttribBinding(array, loc, 0);
    }

    private static int compileShader(int type, String src) {
        int id = glCreateShader(type);
        glShaderSource(id, src);
        glCompileShader(id);
        if (glGetShaderi(id, GL_COMPILE_STATUS) != GL_TRUE) {
            glDeleteShader(id);
            return 0;
        }
        return id;
    }

    private record PreparedDraw(StaticBatch batch, int textureId) {}

    private static void evictStaleBatches(RendererBackend.Snapshot scene) {
        if (STATIC_BATCHES.isEmpty()) return;
        var it = STATIC_BATCHES.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            var sec = scene.sections().get(e.getKey().group());
            if (sec == null || sec != e.getValue().section) {
                if (e.getValue().vbo != 0) glDeleteBuffers(e.getValue().vbo);
                it.remove();
            }
        }
    }

    private static StaticBatch buildOrGetStaticBatch(long group, RendererBackend.Section section,
                                                     RendererBackend.Submesh mesh, Pose pose,
                                                     RendererBackend.Snapshot scene, ClientLevel world,
                                                     WorldLightVolume volume) {
        var key = new BatchKey(group, mesh.start(), mesh.material());
        StaticBatch batch = STATIC_BATCHES.get(key);
        // Inside the 3D WorldLightVolume, fragment lighting reads u_Volume dynamically every frame;
        // do NOT rebuild every static VBO whenever a torch is placed or broken!
        if (batch != null && batch.section == section && batch.pose.equals(pose)) {
            return batch;
        }
        var tris = scene.triangles(group);
        CompiledMesh compiled = section.compiled();
        boolean useCompiled = compiled != null && !compiled.isEmpty() && compiled.triangles().size() == tris.size();
        int triCount = mesh.end() - mesh.start();
        int needed = triCount * 3 * FLOATS_PER_VERTEX;
        if (vertexScratch.length < needed) {
            vertexScratch = new float[Math.max(needed, Math.max(4096, vertexScratch.length * 2))];
        }
        float[] verts = vertexScratch;
        int cursor = 0;
        Vec anchor = pose.translation();
        int base = useCompiled ? (mesh.argb() & 0x00FFFFFF) : (group == 1 ? 0x337d96 : 0xa4a09a);
        float cr = ((base >> 16) & 255) / 255f;
        float cg = ((base >> 8) & 255) / 255f;
        float cb = (base & 255) / 255f;
        float ca = useCompiled ? (((mesh.argb() >>> 24) & 255) / 255f)
                : (mesh.material().equals("translucent") ? (100f / 255f) : 1f);
        for (int i = mesh.start(); i < mesh.end(); i++) {
            if (useCompiled) {
                var ct = compiled.triangles().get(i).transform(pose);
                Vec pa = ct.a().pos(), pb = ct.b().pos(), pc = ct.c().pos();
                cursor = pushVertex(verts, cursor, pa, anchor, ct.a().normal(), (float) ct.a().u(), (float) ct.a().v(),
                        cr, cg, cb, ca, volume.vertexLight(world, pa, ct.a().normal()));
                cursor = pushVertex(verts, cursor, pb, anchor, ct.b().normal(), (float) ct.b().u(), (float) ct.b().v(),
                        cr, cg, cb, ca, volume.vertexLight(world, pb, ct.b().normal()));
                cursor = pushVertex(verts, cursor, pc, anchor, ct.c().normal(), (float) ct.c().u(), (float) ct.c().v(),
                        cr, cg, cb, ca, volume.vertexLight(world, pc, ct.c().normal()));
            } else {
                var t = tris.get(i).transform(pose);
                var n = t.b().minus(t.a()).cross(t.c().minus(t.a()));
                double length = Math.sqrt(n.dot(n));
                if (length <= 1e-12) continue;
                n = n.times(1.0 / length);
                boolean even = ((i - mesh.start()) & 1) == 0;
                float u1 = 1f, v1 = even ? 0f : 1f;
                float u2 = even ? 1f : 0f, v2 = 1f;
                cursor = pushVertex(verts, cursor, t.a(), anchor, n, 0f, 0f, cr, cg, cb, ca, volume.vertexLight(world, t.a(), n));
                cursor = pushVertex(verts, cursor, t.b(), anchor, n, u1, v1, cr, cg, cb, ca, volume.vertexLight(world, t.b(), n));
                cursor = pushVertex(verts, cursor, t.c(), anchor, n, u2, v2, cr, cg, cb, ca, volume.vertexLight(world, t.c(), n));
            }
        }
        if (batch == null) {
            batch = new StaticBatch();
            STATIC_BATCHES.put(key, batch);
        }
        batch.section = section;
        batch.pose = pose;
        batch.volumeRevision = volume.contentRevision();
        batch.anchor = anchor;
        batch.vertexCount = cursor / FLOATS_PER_VERTEX;
        if (cursor > 0) {
            if (batch.vbo == 0) batch.vbo = glCreateBuffers();
            float[] upload = cursor == verts.length ? verts : Arrays.copyOf(verts, cursor);
            if (cursor > batch.capacityFloats) {
                glNamedBufferData(batch.vbo, upload, GL_STATIC_DRAW);
                batch.capacityFloats = cursor;
            } else {
                glNamedBufferSubData(batch.vbo, 0L, upload);
            }
        }
        return batch;
    }

    private static void drawPerPixel(Object view, Pass pass, RendererBackend.Snapshot scene, Camera camera, Matrix4f modelView,
                                     Matrix4f projection, WorldLightVolume volume) {
        var pos = camera.getPosition();
        Vec eye = new Vec(pos.x, pos.y, pos.z);
        ClientLevel world = Minecraft.getInstance().level;
        long start = System.nanoTime();
        int volumeId = volume.update(world, scene);
        ClientRenderState.timings.volumeNs += System.nanoTime() - start;
        if (pass == Pass.SOLID) evictStaleBatches(scene);

        var mc = Minecraft.getInstance();
        var texManager = mc.getTextureManager();
        var draws = new ArrayList<PreparedDraw>();
        for (var entry : scene.sections().entrySet()) {
            long group = entry.getKey();
            Pose pose = scene.world().get(group);
            if (pose == null) continue;
            var section = entry.getValue();
            for (var mesh : section.submeshes()) {
                Pass layer = layer(mesh, false);
                if (layer != pass || mesh.start() == mesh.end()) continue;
                var key = new Key(view, pass, group, mesh.material() + "/" + mesh.start());
                engine().ownership().assignDefault(key, Owner.HEXIE);
                engine().ownership().submit(key, Owner.HEXIE, () -> {
                    StaticBatch batch = buildOrGetStaticBatch(group, section, mesh, pose, scene, world, volume);
                    if (batch.vertexCount <= 0 || batch.vbo == 0) return;
                    ResourceLocation texLoc = resolveTexture(mesh.material(), layer);
                    int texId = texManager.getTexture(texLoc).getId();
                    draws.add(new PreparedDraw(batch, texId));
                });
            }
        }

        if (!draws.isEmpty()) {
            modelView.get(MAT_MV);
            projection.get(MAT_PROJ);
            float[] fogColor = RenderSystem.getShaderFogColor();
            float fogStart = RenderSystem.getShaderFogStart();
            float fogEnd = RenderSystem.getShaderFogEnd();
            drawPassBatches(pass, eye, draws, volumeId, volume.grid(),
                    mc.gameRenderer.lightTexture()::turnOnLightLayer,
                    MAT_MV, MAT_PROJ, fogStart, fogEnd, fogColor);
        }

        if (pass == Pass.SOLID && hasDemoLamps(scene)) {
            var matrix = RenderSystem.getModelViewStack();
            matrix.pushMatrix().set(modelView);
            RenderSystem.applyModelViewMatrix();
            try (var memory = new ByteBufferBuilder(4096)) {
                var buffers = MultiBufferSource.immediate(memory);
                lamps(view, pass, scene, eye, buffers);
            } finally {
                matrix.popMatrix();
                RenderSystem.applyModelViewMatrix();
            }
        }
    }

    /** Executes all draws in a render pass under ONE GL state save/restore & ONE ForwardLight SSBO bind. */
    private static void drawPassBatches(Pass layer, Vec eye, List<PreparedDraw> draws, int volumeId,
                                        WorldLightVolume.Grid grid, Runnable enableLightmap,
                                        float[] modelView, float[] projection,
                                        float fogStart, float fogEnd, float[] fogColor) {
        int prevProg = glGetInteger(GL_CURRENT_PROGRAM);
        int prevVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int prevActiveTex = glGetInteger(GL_ACTIVE_TEXTURE);
        int prevMcActive = GlStateManager._getActiveTexture();
        int prevShaderTex2 = RenderSystem.getShaderTexture(2);
        int prevTex0 = glGetIntegeri(GL_TEXTURE_BINDING_2D, 0);
        int prevTex1 = glGetIntegeri(GL_TEXTURE_BINDING_3D, 1);
        int prevTex2 = glGetIntegeri(GL_TEXTURE_BINDING_2D, 2);
        int prevSampler1 = glGetIntegeri(GL_SAMPLER_BINDING, 1);
        int prevSampler2 = glGetIntegeri(GL_SAMPLER_BINDING, 2);
        int prevSampler0 = glGetIntegeri(GL_SAMPLER_BINDING, 0);
        boolean prevDepth = glIsEnabled(GL_DEPTH_TEST);
        int prevDepthFunc = glGetInteger(GL_DEPTH_FUNC);
        boolean prevDepthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
        boolean prevCull = glIsEnabled(GL_CULL_FACE);
        int prevCullMode = glGetInteger(GL_CULL_FACE_MODE);
        int prevFront = glGetInteger(GL_FRONT_FACE);
        boolean prevBlend = glIsEnabled(GL_BLEND);
        int prevEqRgb = glGetInteger(GL_BLEND_EQUATION_RGB);
        int prevEqAlpha = glGetInteger(GL_BLEND_EQUATION_ALPHA);
        int prevSrcRgb = glGetInteger(GL_BLEND_SRC_RGB);
        int prevDstRgb = glGetInteger(GL_BLEND_DST_RGB);
        int prevSrcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA);
        int prevDstAlpha = glGetInteger(GL_BLEND_DST_ALPHA);
        try {
            glUseProgram(program);
            glBindVertexArray(vao);
            glEnable(GL_DEPTH_TEST);
            glDepthFunc(GL_LEQUAL);
            glDepthMask(layer != Pass.TRANSLUCENT);
            if (layer == Pass.SOLID) {
                glEnable(GL_CULL_FACE);
                glCullFace(GL_BACK);
                glFrontFace(GL_CCW);
                glDisable(GL_BLEND);
            } else if (layer == Pass.CUTOUT) {
                glDisable(GL_CULL_FACE);
                glDisable(GL_BLEND);
            } else {
                glDisable(GL_CULL_FACE);
                glEnable(GL_BLEND);
                glBlendEquation(GL_FUNC_ADD);
                glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
            }
            RenderSystem.activeTexture(GL_TEXTURE2);
            glActiveTexture(GL_TEXTURE2);
            RenderSystem.bindTexture(prevTex2);
            enableLightmap.run();
            glBindTexture(GL_TEXTURE_2D, RenderSystem.getShaderTexture(2));
            glBindSampler(2, 0);
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_3D, volumeId);
            glBindSampler(1, 0);
            if (volumeId != 0 && grid != null) {
                glUniform3f(locVolumeOrigin, (float) (grid.x() - eye.x()), (float) (grid.y() - eye.y()), (float) (grid.z() - eye.z()));
                glUniform3f(locVolumeSize, grid.width(), grid.height(), grid.depth());
            } else glUniform3f(locVolumeSize, 0, 0, 0);
            RenderSystem.activeTexture(GL_TEXTURE0);
            glActiveTexture(GL_TEXTURE0);
            glBindSampler(0, 0);
            glUniform1i(locTex, 0);
            glUniform1i(locCutout, layer == Pass.CUTOUT ? 1 : 0);
            glUniformMatrix4fv(locModelView, false, modelView);
            glUniformMatrix4fv(locProjection, false, projection);
            glUniform3f(locDrawOffset, 0f, 0f, 0f);
            glUniform1f(locFogStart, fogStart);
            glUniform1f(locFogEnd, fogEnd);
            glUniform4f(locFogColor, fogColor[0], fogColor[1], fogColor[2], fogColor[3]);

            ForwardLight.draw(eye, IDENTITY_3X3, false, ForwardLight.TARGET_GEOMETRY, () -> {
                int boundTex = -1;
                for (var d : draws) {
                    if (d.textureId != boundTex) {
                        glBindTexture(GL_TEXTURE_2D, d.textureId);
                        boundTex = d.textureId;
                    }
                    Vec a = d.batch.anchor;
                    glUniform3f(locDrawOffset, (float) (a.x() - eye.x()), (float) (a.y() - eye.y()), (float) (a.z() - eye.z()));
                    glVertexArrayVertexBuffer(vao, 0, d.batch.vbo, 0L, FLOATS_PER_VERTEX * Float.BYTES);
                    glDrawArrays(GL_TRIANGLES, 0, d.batch.vertexCount);
                }
            });
        } finally {
            glUseProgram(prevProg);
            glBindVertexArray(prevVao);
            RenderSystem.activeTexture(GL_TEXTURE0);
            glActiveTexture(GL_TEXTURE0);
            RenderSystem.bindTexture(prevTex0);
            glBindTexture(GL_TEXTURE_2D, prevTex0);
            glBindSampler(0, prevSampler0);
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_3D, prevTex1);
            glBindSampler(1, prevSampler1);
            RenderSystem.activeTexture(GL_TEXTURE2);
            glActiveTexture(GL_TEXTURE2);
            RenderSystem.bindTexture(prevTex2);
            glBindTexture(GL_TEXTURE_2D, prevTex2);
            glBindSampler(2, prevSampler2);
            RenderSystem.setShaderTexture(2, prevShaderTex2);
            RenderSystem.activeTexture(prevMcActive);
            glActiveTexture(prevActiveTex);
            if (prevDepth) glEnable(GL_DEPTH_TEST); else glDisable(GL_DEPTH_TEST);
            glDepthFunc(prevDepthFunc);
            glDepthMask(prevDepthMask);
            if (prevCull) glEnable(GL_CULL_FACE); else glDisable(GL_CULL_FACE);
            glCullFace(prevCullMode);
            glFrontFace(prevFront);
            if (prevBlend) glEnable(GL_BLEND); else glDisable(GL_BLEND);
            glBlendEquationSeparate(prevEqRgb, prevEqAlpha);
            glBlendFuncSeparate(prevSrcRgb, prevDstRgb, prevSrcAlpha, prevDstAlpha);
        }
    }

    static void drawBatch(Pass layer, Vec eye, float[] verts, int cursor, IntSupplier materialTexture, int volumeId,
                          WorldLightVolume.Grid grid, Runnable enableLightmap, float[] modelView, float[] projection,
                          float fogStart, float fogEnd, float[] fogColor) {
        int vertCount = cursor / FLOATS_PER_VERTEX;
        if (vertCount == 0) return;
        long frame = engine().route().frameId();
        if (vboFrame != frame) {
            vboFrame = frame;
            vboRing = (vboRing + 1) % vbos.length;
            vboCursorFloats = 0;
        }
        if (cursor > vboCaps[vboRing] - vboCursorFloats) {
            // Submitted draws keep their old storage while the next batch starts a fresh buffer.
            int old = vbos[vboRing];
            int alloc = Math.max(cursor, Math.max(65536, vboCaps[vboRing] * 2));
            vbos[vboRing] = glCreateBuffers();
            glNamedBufferData(vbos[vboRing], (long) alloc * Float.BYTES, GL_STREAM_DRAW);
            if (old != 0) glDeleteBuffers(old);
            vboCaps[vboRing] = alloc;
            vboCursorFloats = 0;
        }
        int startFloat = vboCursorFloats;
        int firstVertex = startFloat / FLOATS_PER_VERTEX;
        float[] upload = cursor == verts.length ? verts : Arrays.copyOf(verts, cursor);
        int curVbo = vbos[vboRing];
        glNamedBufferSubData(curVbo, (long) startFloat * Float.BYTES, upload);
        vboCursorFloats += cursor;
        glVertexArrayVertexBuffer(vao, 0, curVbo, 0L, FLOATS_PER_VERTEX * Float.BYTES);

        // Surgical save/restore of ONLY the states this draw mutates (avoids ~350 glGet stalls/draw).
        int prevProg = glGetInteger(GL_CURRENT_PROGRAM);
        int prevVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int prevActiveTex = glGetInteger(GL_ACTIVE_TEXTURE);
        int prevMcActive = GlStateManager._getActiveTexture();
        int prevShaderTex2 = RenderSystem.getShaderTexture(2);
        int prevTex0 = glGetIntegeri(GL_TEXTURE_BINDING_2D, 0);
        int prevTex1 = glGetIntegeri(GL_TEXTURE_BINDING_3D, 1);
        int prevTex2 = glGetIntegeri(GL_TEXTURE_BINDING_2D, 2);
        int prevSampler1 = glGetIntegeri(GL_SAMPLER_BINDING, 1);
        int prevSampler2 = glGetIntegeri(GL_SAMPLER_BINDING, 2);
        int prevSampler0 = glGetIntegeri(GL_SAMPLER_BINDING, 0);
        boolean prevDepth = glIsEnabled(GL_DEPTH_TEST);
        int prevDepthFunc = glGetInteger(GL_DEPTH_FUNC);
        boolean prevDepthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
        boolean prevCull = glIsEnabled(GL_CULL_FACE);
        int prevCullMode = glGetInteger(GL_CULL_FACE_MODE);
        int prevFront = glGetInteger(GL_FRONT_FACE);
        boolean prevBlend = glIsEnabled(GL_BLEND);
        int prevEqRgb = glGetInteger(GL_BLEND_EQUATION_RGB);
        int prevEqAlpha = glGetInteger(GL_BLEND_EQUATION_ALPHA);
        int prevSrcRgb = glGetInteger(GL_BLEND_SRC_RGB);
        int prevDstRgb = glGetInteger(GL_BLEND_DST_RGB);
        int prevSrcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA);
        int prevDstAlpha = glGetInteger(GL_BLEND_DST_ALPHA);
        try {
            glUseProgram(program);
            glBindVertexArray(vao);
            glEnable(GL_DEPTH_TEST);
            glDepthFunc(GL_LEQUAL);
            glDepthMask(layer != Pass.TRANSLUCENT);
            if (layer == Pass.SOLID) {
                glEnable(GL_CULL_FACE);
                glCullFace(GL_BACK);
                glFrontFace(GL_CCW);
                glDisable(GL_BLEND);
            } else if (layer == Pass.CUTOUT) {
                glDisable(GL_CULL_FACE);
                glDisable(GL_BLEND);
            } else {
                glDisable(GL_CULL_FACE);
                glEnable(GL_BLEND);
                glBlendEquation(GL_FUNC_ADD);
                glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
            }
            // turnOnLightLayer binds for setup on the active MC unit as well as setting shader slot 2.
            RenderSystem.activeTexture(GL_TEXTURE2);
            glActiveTexture(GL_TEXTURE2);
            RenderSystem.bindTexture(prevTex2);
            enableLightmap.run();
            glBindTexture(GL_TEXTURE_2D, RenderSystem.getShaderTexture(2));
            glBindSampler(2, 0);
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_3D, volumeId);
            glBindSampler(1, 0);
            if (volumeId != 0) {
                glUniform3f(locVolumeOrigin, (float) (grid.x() - eye.x()), (float) (grid.y() - eye.y()), (float) (grid.z() - eye.z()));
                glUniform3f(locVolumeSize, grid.width(), grid.height(), grid.depth());
            } else glUniform3f(locVolumeSize, 0, 0, 0);
            RenderSystem.activeTexture(GL_TEXTURE0);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, materialTexture.getAsInt());
            glBindSampler(0, 0);
            glUniform1i(locTex, 0);
            glUniform1i(locCutout, layer == Pass.CUTOUT ? 1 : 0);
            glUniformMatrix4fv(locModelView, false, modelView);
            glUniformMatrix4fv(locProjection, false, projection);
            glUniform3f(locDrawOffset, 0f, 0f, 0f);
            glUniform1f(locFogStart, fogStart);
            glUniform1f(locFogEnd, fogEnd);
            glUniform4f(locFogColor, fogColor[0], fogColor[1], fogColor[2], fogColor[3]);
            ForwardLight.draw(eye, IDENTITY_3X3, false, ForwardLight.TARGET_GEOMETRY, () -> glDrawArrays(GL_TRIANGLES, firstVertex, vertCount));
        } finally {
            glUseProgram(prevProg);
            glBindVertexArray(prevVao);
            RenderSystem.activeTexture(GL_TEXTURE0);
            glActiveTexture(GL_TEXTURE0);
            RenderSystem.bindTexture(prevTex0);
            glBindTexture(GL_TEXTURE_2D, prevTex0);
            glBindSampler(0, prevSampler0);
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_3D, prevTex1);
            glBindSampler(1, prevSampler1);
            RenderSystem.activeTexture(GL_TEXTURE2);
            glActiveTexture(GL_TEXTURE2);
            RenderSystem.bindTexture(prevTex2);
            glBindTexture(GL_TEXTURE_2D, prevTex2);
            glBindSampler(2, prevSampler2);
            RenderSystem.setShaderTexture(2, prevShaderTex2);
            RenderSystem.activeTexture(prevMcActive);
            glActiveTexture(prevActiveTex);
            if (prevDepth) glEnable(GL_DEPTH_TEST); else glDisable(GL_DEPTH_TEST);
            glDepthFunc(prevDepthFunc);
            glDepthMask(prevDepthMask);
            if (prevCull) glEnable(GL_CULL_FACE); else glDisable(GL_CULL_FACE);
            glCullFace(prevCullMode);
            glFrontFace(prevFront);
            if (prevBlend) glEnable(GL_BLEND); else glDisable(GL_BLEND);
            glBlendEquationSeparate(prevEqRgb, prevEqAlpha);
            glBlendFuncSeparate(prevSrcRgb, prevDstRgb, prevSrcAlpha, prevDstAlpha);
        }
    }

    private static int pushVertex(float[] out, int o, Vec worldPos, Vec eye, Vec n,
                                  float u, float v, float r, float g, float b, float a, int light) {
        out[o++] = (float) (worldPos.x() - eye.x());
        out[o++] = (float) (worldPos.y() - eye.y());
        out[o++] = (float) (worldPos.z() - eye.z());
        out[o++] = (float) n.x();
        out[o++] = (float) n.y();
        out[o++] = (float) n.z();
        out[o++] = u;
        out[o++] = v;
        out[o++] = r;
        out[o++] = g;
        out[o++] = b;
        out[o++] = a;
        out[o++] = LightTexture.block(light) / 15f;
        out[o++] = LightTexture.sky(light) / 15f;
        return o;
    }

    private static void drawVanilla(Object view, Pass pass, RendererBackend.Snapshot scene, Camera camera, Matrix4f modelView) {
        var pos = camera.getPosition();
        Vec eye = new Vec(pos.x, pos.y, pos.z);
        var world = Minecraft.getInstance().level;
        boolean basicTier = engine().route().tier() == RenderSettings.Tier.BASIC;
        var lightCache = new HashMap<BlockPos, Integer>();
        var matrix = RenderSystem.getModelViewStack();
        matrix.pushMatrix().set(modelView);
        RenderSystem.applyModelViewMatrix();
        try (var memory = new ByteBufferBuilder(32768)) {
            var buffers = MultiBufferSource.immediate(memory);
            for (var entry : scene.sections().entrySet()) {
                long group = entry.getKey();
                Pose pose = scene.world().get(group);
                if (pose == null) continue;
                var section = entry.getValue();
                for (var mesh : section.submeshes()) {
                    boolean shadow = pass.name().startsWith("SHADOW_");
                    Pass layer = layer(mesh, shadow);
                    if (layer != pass || mesh.start() == mesh.end()) continue;
                    var key = new Key(view, pass, group, mesh.material() + "/" + mesh.start());
                    engine().ownership().assignDefault(key, Owner.HEXIE);
                    engine().ownership().submit(key, Owner.HEXIE, () -> {
                        ResourceLocation texLoc = resolveTexture(mesh.material(), layer);
                        // Without Sodium (BASIC tier), match legacy StaticShellSectionGeometry block lighting exactly:
                        // RenderType.solid/cutoutMipped/translucent + continuous trilinear lightAt (no entity diffuse darkening).
                        boolean useBlockFormat = basicTier && !shadow && InventoryMenu.BLOCK_ATLAS.equals(texLoc);
                        RenderType type;
                        if (useBlockFormat) {
                            type = switch (layer) {
                                case CUTOUT -> RenderType.cutoutMipped();
                                case TRANSLUCENT -> RenderType.translucent();
                                default -> RenderType.solid();
                            };
                        } else {
                            type = switch (layer) {
                                case CUTOUT, SHADOW_CUTOUT -> RenderType.entityCutoutNoCull(texLoc);
                                case TRANSLUCENT, SHADOW_TRANSLUCENT -> RenderType.entityTranslucent(texLoc);
                                default -> RenderType.entitySolid(texLoc);
                            };
                        }
                        var consumer = buffers.getBuffer(type);
                        CompiledMesh compiled = section.compiled();
                        boolean useCompiled = compiled != null && !compiled.isEmpty()
                                && compiled.triangles().size() == scene.triangles(group).size();
                        for (int i = mesh.start(); i < mesh.end(); i++) {
                            double energy = engine().lighting().energy(group, i);
                            if (useCompiled) {
                                var ct = compiled.triangles().get(i).transform(pose);
                                Vec pa = ct.a().pos(), pb = ct.b().pos(), pc = ct.c().pos();
                                int lightA = trilinearLightAt(world, lightCache, pa.x(), pa.y(), pa.z(), energy);
                                int lightB = trilinearLightAt(world, lightCache, pb.x(), pb.y(), pb.z(), energy);
                                int lightC = trilinearLightAt(world, lightCache, pc.x(), pc.y(), pc.z(), energy);
                                int color = shade(mesh.argb() & 0x00FFFFFF, energy, (mesh.argb() >>> 24) & 255);
                                vertex(consumer, pa.minus(eye), ct.a().normal(), color, (float) ct.a().u(), (float) ct.a().v(), lightA, useBlockFormat);
                                vertex(consumer, pb.minus(eye), ct.b().normal(), color, (float) ct.b().u(), (float) ct.b().v(), lightB, useBlockFormat);
                                vertex(consumer, pc.minus(eye), ct.c().normal(), color, (float) ct.c().u(), (float) ct.c().v(), lightC, useBlockFormat);
                                vertex(consumer, pc.minus(eye), ct.c().normal(), color, (float) ct.c().u(), (float) ct.c().v(), lightC, useBlockFormat);
                            } else {
                                var local = scene.triangles(group).get(i);
                                var t = local.transform(pose);
                                var n = t.b().minus(t.a()).cross(t.c().minus(t.a()));
                                double length = Math.sqrt(n.dot(n));
                                if (length == 0) continue;
                                n = n.times(1 / length);
                                int light = trilinearLightAt(world, lightCache, t.a().x(), t.a().y(), t.a().z(), energy);
                                int base = group == 1 ? 0x337d96 : 0xa4a09a;
                                int color = shade(base, energy, mesh.material().equals("translucent") ? 100 : 255);
                                vertex(consumer, t.a().minus(eye), n, color, 0, 0, light, useBlockFormat);
                                vertex(consumer, t.b().minus(eye), n, color, 1, 0, light, useBlockFormat);
                                vertex(consumer, t.c().minus(eye), n, color, 1, 1, light, useBlockFormat);
                                vertex(consumer, t.c().minus(eye), n, color, 1, 1, light, useBlockFormat);
                            }
                        }
                        buffers.endBatch(type);
                    });
                }
            }
            if ((pass == Pass.SOLID || pass == Pass.SHADOW_SOLID) && hasDemoLamps(scene))
                lamps(view, pass, scene, eye, buffers);
        } finally {
            matrix.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static void lamps(Object view, Pass pass, RendererBackend.Snapshot scene, Vec eye, MultiBufferSource.BufferSource buffers) {
        var key = new Key(view, pass, 0, "lamp markers");
        engine().ownership().assign(key, Owner.HEXIE);
        engine().ownership().submit(key, Owner.HEXIE, () -> {
            var type = RenderType.entityCutoutNoCull(WHITE);
            var consumer = buffers.getBuffer(type);
            for (var lamp : scene.frame().lights()) {
                if (lamp.ownerGroup() != 0L && lamp.ownerGroup() != 1L) continue;
                var ownerPose = scene.world().get(lamp.ownerGroup());
                if (ownerPose == null) continue;
                var p = ownerPose.apply(lamp.localPosition()).minus(eye);
                for (int axis = 0; axis < 3; axis++) {
                    var u = new Vec(axis == 0 ? .09 : 0, axis == 1 ? .09 : 0, axis == 2 ? .09 : 0);
                    var v = new Vec(u.z(), u.x(), u.y());
                    var normal = u.cross(v).times(1 / (.09 * .09));
                    for (var corner : new Vec[]{p.minus(u).minus(v), p.plus(u).minus(v), p.plus(u).plus(v), p.minus(u).plus(v)})
                        vertex(consumer, corner, normal, 0xffffbd52, 0, 0, LightTexture.FULL_BRIGHT);
                }
            }
            buffers.endBatch(type);
        });
    }

    /** Identical continuous trilinear light field to legacy StaticShellLighting.lightAt for non-Sodium fallback. */
    private static int trilinearLightAt(ClientLevel world, Map<BlockPos, Integer> cache,
                                        double x, double y, double z, double energy) {
        if (world == null) return LightTexture.FULL_BRIGHT;
        x -= 0.5; y -= 0.5; z -= 0.5;
        int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
        double fx = x - bx, fy = y - by, fz = z - bz, block = 0, sky = 0;
        for (int dx = 0; dx < 2; dx++) {
            for (int dy = 0; dy < 2; dy++) {
                for (int dz = 0; dz < 2; dz++) {
                    var at = new BlockPos(bx + dx, by + dy, bz + dz);
                    int l = cache.computeIfAbsent(at, p -> LevelRenderer.getLightColor(world, p));
                    double weight = (dx == 0 ? 1 - fx : fx) * (dy == 0 ? 1 - fy : fy) * (dz == 0 ? 1 - fz : fz);
                    block += (l & 65535) * weight;
                    sky += (l >>> 16) * weight;
                }
            }
        }
        int light = (int) Math.round(block) | ((int) Math.round(sky) << 16);
        if (energy > 0) {
            light = LightTexture.pack(Math.max(LightTexture.block(light), (int) (15 * Math.min(1, energy))),
                    LightTexture.sky(light));
        }
        return light;
    }

    private static int shade(int base, double energy, int alpha) {
        int r = Math.min(255, (int) ((base >> 16) + 125 * energy));
        int g = Math.min(255, (int) ((base >> 8 & 255) + 85 * energy));
        int b = Math.min(255, (int) ((base & 255) + 35 * energy));
        return alpha << 24 | r << 16 | g << 8 | b;
    }

    private static void vertex(VertexConsumer c, Vec p, Vec n, int color, float u, float v, int light) {
        vertex(c, p, n, color, u, v, light, false);
    }

    private static void vertex(VertexConsumer c, Vec p, Vec n, int color, float u, float v, int light, boolean blockFormat) {
        var vc = c.addVertex((float) p.x(), (float) p.y(), (float) p.z()).setColor(color).setUv(u, v);
        if (!blockFormat) vc.setOverlay(OverlayTexture.NO_OVERLAY);
        vc.setLight(light).setNormal((float) n.x(), (float) n.y(), (float) n.z());
    }
}
